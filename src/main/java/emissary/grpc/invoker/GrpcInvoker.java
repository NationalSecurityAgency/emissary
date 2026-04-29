package emissary.grpc.invoker;

import emissary.grpc.exceptions.GrpcExceptionUtils;
import emissary.grpc.future.CompletableFutureAdaptors;
import emissary.grpc.pool.ConnectionFactory;
import emissary.grpc.retry.RetryHandler;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.GeneratedMessageV3;
import io.grpc.ManagedChannel;
import io.grpc.stub.AbstractBlockingStub;
import io.grpc.stub.AbstractFutureStub;
import org.apache.commons.pool2.ObjectPool;

import java.util.concurrent.CompletableFuture;
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
    public <Q extends GeneratedMessageV3, R extends GeneratedMessageV3, S extends AbstractBlockingStub<S>> R invoke(
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
    public <Q extends GeneratedMessageV3, R extends GeneratedMessageV3, S extends AbstractFutureStub<S>> CompletableFuture<R> invokeAsync(
            ObjectPool<ManagedChannel> channelPool, Function<ManagedChannel, S> stubFactory,
            BiFunction<S, Q, ListenableFuture<R>> callLogic, Q request) {
        return retryHandler.executeAsync(() -> {
            ManagedChannel channel = ConnectionFactory.acquireChannel(channelPool);
            try {
                S stub = stubFactory.apply(channel);
                ListenableFuture<R> listenable = callLogic.apply(stub, request);
                CompletableFuture<R> completable = CompletableFutureAdaptors.fromListenableFuture(listenable);
                return completable.handle(handleFuture(channel, channelPool));
            } catch (RuntimeException e) {
                ConnectionFactory.invalidateChannel(channel, channelPool);
                throw GrpcExceptionUtils.toContextualRuntimeException(e);
            }
        }).toCompletableFuture();
    }

    /**
     * Creates a handler for gRPC future completion that manages channel lifecycle and exception mapping. Runs when the
     * future completes regardless of success or failure.
     *
     * @param channel the borrowed channel
     * @param channelPool the pool the channel came from
     * @param <R> response type
     * @return handler function suitable for {@link CompletableFuture#handle}
     */
    private static <R extends GeneratedMessageV3> BiFunction<R, Throwable, R> handleFuture(
            ManagedChannel channel, ObjectPool<ManagedChannel> channelPool) {
        return (response, throwable) -> {
            if (throwable == null) {
                ConnectionFactory.returnChannel(channel, channelPool);
                return response;
            }
            ConnectionFactory.invalidateChannel(channel, channelPool);
            throw GrpcExceptionUtils.toContextualRuntimeException(
                    GrpcExceptionUtils.unwrapAsyncThrowable(throwable));
        };
    }
}
