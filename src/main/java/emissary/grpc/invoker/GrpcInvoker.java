package emissary.grpc.invoker;

import emissary.grpc.exceptions.GrpcExceptionUtils;
import emissary.grpc.future.CompletableFutureAdaptors;
import emissary.grpc.pool.ConnectionFactory;
import emissary.grpc.retry.RetryHandler;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.Message;
import io.grpc.ManagedChannel;
import io.grpc.stub.AbstractBlockingStub;
import io.grpc.stub.AbstractFutureStub;
import org.apache.commons.pool2.ObjectPool;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public class GrpcInvoker {
    private final RetryHandler retryHandler;

    public GrpcInvoker(RetryHandler retryHandler) {
        this.retryHandler = retryHandler;
    }

    /**
     * Executes a unary gRPC call to a given endpoint using an {@link AbstractBlockingStub}. If the gRPC connection fails
     * due to an allowed Exception, the call will be tried again per the configurations set using {@link RetryHandler}. All
     * other Exceptions are thrown on the spot. Will also throw an Exception once max attempts have been reached.
     *
     * @param channelPool object pool of gRPC connections for a given endpoint
     * @param stubFactory function that creates the appropriate gRPC stub from a {@link ManagedChannel}
     * @param callLogic function that performs the actual gRPC call using the stub and request
     * @param request the protobuf request message to send
     * @return the response returned by the gRPC call
     * @param <Q> the protobuf request type
     * @param <R> the protobuf response type
     * @param <S> the gRPC stub type
     */
    public <Q extends Message, R extends Message, S extends AbstractBlockingStub<S>> R invoke(
            ObjectPool<ManagedChannel> channelPool, Function<ManagedChannel, S> stubFactory,
            BiFunction<S, Q, R> callLogic, Q request) {
        return retryHandler.execute(() -> {
            ManagedChannel channel = ConnectionFactory.acquireChannel(channelPool);
            try {
                S stub = stubFactory.apply(channel);
                R response = callLogic.apply(stub, request);
                ConnectionFactory.returnChannel(channel, channelPool);
                return response;
            } catch (RuntimeException e) {
                ConnectionFactory.invalidateChannel(channel, channelPool);
                throw GrpcExceptionUtils.toContextualRuntimeException(e);
            }
        });
    }

    /**
     * Executes a unary gRPC call to a given endpoint using an {@link AbstractFutureStub}. If the gRPC connection fails due
     * to an allowed Exception, the call will be tried again per the configurations set using {@link RetryHandler}. All
     * other Exceptions are thrown on the spot. Will also throw an Exception once max attempts have been reached.
     *
     * @param channelPool object pool of gRPC connections for a given endpoint
     * @param stubFactory function that creates the appropriate gRPC stub from a {@link ManagedChannel}
     * @param callLogic function that performs the actual gRPC call using the stub and request
     * @param request the protobuf request message to send
     * @return the future that waits for the response returned by the gRPC call
     * @param <Q> the protobuf request type
     * @param <R> the protobuf response type
     * @param <S> the gRPC stub type
     */
    public <Q extends Message, R extends Message, S extends AbstractFutureStub<S>> CompletableFuture<R> invokeAsync(
            ObjectPool<ManagedChannel> channelPool, Function<ManagedChannel, S> stubFactory,
            BiFunction<S, Q, ListenableFuture<R>> callLogic, Q request) {
        AtomicReference<ListenableFuture<R>> listenableRef = new AtomicReference<>();
        CompletionStage<R> stage = retryHandler.executeAsync(() -> {
            ManagedChannel channel = ConnectionFactory.acquireChannel(channelPool);
            try {
                S stub = stubFactory.apply(channel);
                listenableRef.set(callLogic.apply(stub, request));
                CompletableFuture<R> completable = CompletableFutureAdaptors.fromListenableFuture(listenableRef.get());
                return attachHandlingHook(completable, channel, channelPool);
            } catch (RuntimeException e) {
                ConnectionFactory.invalidateChannel(channel, channelPool);
                throw GrpcExceptionUtils.toContextualRuntimeException(e);
            }
        });
        return attachCancellationHook(stage.toCompletableFuture(), listenableRef);
    }

    /**
     * Attaches a completion hook to the provided {@link CompletableFuture} using
     * {@link CompletableFuture#handle(BiFunction)}. This hook manages channel lifecycle and exception mapping, and runs
     * when the future completes regardless of its success or failure.
     *
     * @param future the future to attach the hook to
     * @param channel the borrowed channel
     * @param channelPool the pool the channel came from
     * @param <R> response type
     * @return a future with explicit handling logic
     */
    private static <R extends Message> CompletableFuture<R> attachHandlingHook(
            CompletableFuture<R> future, ManagedChannel channel, ObjectPool<ManagedChannel> channelPool) {
        return future.handle((response, throwable) -> {
            if (throwable == null) {
                ConnectionFactory.returnChannel(channel, channelPool);
                return response;
            }
            ConnectionFactory.invalidateChannel(channel, channelPool);
            throw GrpcExceptionUtils.toContextualRuntimeException(
                    GrpcExceptionUtils.unwrapAsyncThrowable(throwable));
        });
    }

    /**
     * Attaches a completion hook to the provided {@link CompletableFuture} using
     * {@link CompletableFuture#whenComplete(BiConsumer)}, while explicitly ignoring the returned dependent future. This
     * helper exists to preserve cancellation semantics when working with chained {@code CompletableFuture} operations.
     * <p>
     * {@code CompletableFuture} propagation is directional:
     * <ul>
     * <li>Normal completion (success or failure) flows downstream from a source future to its dependent futures.</li>
     * <li>Cancellation of a dependent future doesn't propagate upstream to the source future.</li>
     * </ul>
     * <p>
     * Hooks added to futures (e.g. {@link CompletableFuture#whenComplete(BiConsumer)}) create new dependent futures. If
     * those returned futures are used directly, cancellation applied to them will not automatically cancel the original
     * future.
     *
     * @param future the future to attach the hook to
     * @param currentRpc reference to the currently active RPC future
     * @param <R> the result type
     * @return the root future
     */
    @SuppressWarnings({"FutureReturnValueIgnored", "Interruption"})
    private static <R extends Message> CompletableFuture<R> attachCancellationHook(
            CompletableFuture<R> future, AtomicReference<ListenableFuture<R>> currentRpc) {
        future.whenComplete((response, throwable) -> {
            if (future.isCancelled()) {
                ListenableFuture<R> rpc = currentRpc.get();
                if (rpc != null) {
                    rpc.cancel(true);
                }
            }
        });
        return future;
    }
}
