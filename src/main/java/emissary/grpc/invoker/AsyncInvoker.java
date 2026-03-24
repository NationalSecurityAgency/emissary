package emissary.grpc.invoker;

import emissary.grpc.exceptions.PoolException;
import emissary.grpc.exceptions.ServiceException;
import emissary.grpc.exceptions.ServiceNotAvailableException;
import emissary.grpc.pool.ConnectionFactory;
import emissary.grpc.retry.RetryHandler;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.GeneratedMessageV3;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.AbstractFutureStub;
import jakarta.annotation.Nonnull;
import org.apache.commons.pool2.ObjectPool;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class AsyncInvoker extends BaseInvoker {
    public AsyncInvoker(RetryHandler retryHandler, Logger logger) {
        super(retryHandler, logger);
    }

    /**
     * Executes a unary gRPC call to a given endpoint using an {@link AbstractFutureStub}. If the gRPC connection fails due
     * to a {@link PoolException} or a {@link ServiceNotAvailableException}, the call will be tried again per the
     * configurations set using {@link RetryHandler}. All other Exceptions are thrown on the spot. Will also throw an
     * Exception once max attempts have been reached.
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
    public <Q extends GeneratedMessageV3, R extends GeneratedMessageV3, S extends AbstractFutureStub<S>> CompletableFuture<R> invoke(
            ObjectPool<ManagedChannel> channelPool,
            Function<ManagedChannel, S> stubFactory,
            BiFunction<S, Q, ListenableFuture<R>> callLogic,
            Q request) {
        return retryHandler.executeAsync(() -> {
            ManagedChannel channel = ConnectionFactory.acquireChannel(channelPool);
            try {
                S stub = stubFactory.apply(channel);
                return handleFuture(callLogic.apply(stub, request), channel, channelPool);
            } catch (StatusRuntimeException e) {
                ConnectionFactory.invalidateChannel(channel, channelPool);
                ServiceException.handleGrpcStatusRuntimeException(e);
                throw e;
            } catch (RuntimeException e) {
                ConnectionFactory.invalidateChannel(channel, channelPool);
                throw e;
            }
        }).toCompletableFuture();
    }

    /**
     * Adds a handler for gRPC future completion that manages channel lifecycle and exception mapping. Runs when the future
     * completes regardless of success or failure.
     *
     * @param channel the borrowed channel
     * @param channelPool the pool the channel came from
     * @param <R> response type
     * @return handler suitable for {@link CompletableFuture#handle}
     */
    private <R extends GeneratedMessageV3> CompletionStage<R> handleFuture(
            ListenableFuture<R> listenable, ManagedChannel channel, ObjectPool<ManagedChannel> channelPool) {
        return toCompletableFuture(listenable)
                .handle((response, throwable) -> {
                    if (throwable == null) {
                        ConnectionFactory.returnChannel(channel, channelPool);
                        return response;
                    }
                    Throwable cause = unwrapThrowable(throwable);
                    ConnectionFactory.invalidateChannel(channel, channelPool);
                    if (cause instanceof StatusRuntimeException) {
                        ServiceException.handleGrpcStatusRuntimeException((StatusRuntimeException) cause);
                    }
                    if (cause instanceof RuntimeException) {
                        throw (RuntimeException) cause;
                    }
                    throw new IllegalStateException(cause);
                })
                .exceptionally(throwable -> {
                    Throwable cause = unwrapThrowable(throwable);
                    logger.error(cause.getMessage(), cause);
                    return null; // Return null instead of throwing exception and ruining entire batch
                });
    }

    /**
     * Converts a Guava future to a Java completable future.
     *
     * @param future Guava future
     * @return completable future
     * @param <T> result type
     */
    private static <T> CompletableFuture<T> toCompletableFuture(ListenableFuture<T> future) {
        CompletableFuture<T> completableFuture = new CompletableFuture<>();
        Futures.addCallback(future, new FutureCallback<>() {
            @Override
            public void onSuccess(T result) {
                completableFuture.complete(result);
            }

            @Override
            public void onFailure(@Nonnull Throwable t) {
                completableFuture.completeExceptionally(t);
            }
        }, MoreExecutors.directExecutor()); // Direct executor runs callback immediately on thread completing Guava future
        return completableFuture;
    }

    /**
     * Waits for all provided asynchronous computations. This method blocks the calling thread until each
     * {@link CompletableFuture} in the input has complete.
     *
     * @param futures futures representing asynchronous computations
     * @param <R> result type
     */
    public static <R> void awaitAll(Collection<CompletableFuture<R>> futures) {
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    /**
     * Waits for all provided asynchronous computations to complete and returns their results. This method blocks the
     * calling thread until each {@link CompletableFuture} in the input has complete. If any future completes exceptionally,
     * the corresponding {@link CompletableFuture#join()} call will throw a {@link RuntimeException}, and iteration will
     * stop at that point.
     *
     * @param futures collection of futures representing asynchronous computations
     * @param factory creates the output collection instance
     * @return a new collection of the results returned by the asynchronous computations
     * @param <R> result type
     * @param <C> collection type
     */
    public static <R, C extends Collection<R>> C awaitAllAndGet(
            Collection<CompletableFuture<R>> futures, Supplier<? extends C> factory) {
        awaitAll(futures);
        return futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toCollection(factory));
    }

    /**
     * Waits for all provided asynchronous computations to complete and returns their results. This method blocks the
     * calling thread until each {@link CompletableFuture} in the input has complete. If any future completes exceptionally,
     * the corresponding {@link CompletableFuture#join()} call will throw a {@link RuntimeException}, and iteration will
     * stop at that point.
     *
     * @param futures map of keys to futures representing asynchronous computations
     * @param factory creates the output map instance
     * @return a new map of keys to the results returned by the asynchronous computations
     * @param <K> key type
     * @param <R> result type
     * @param <M> map type
     */
    public static <K, R, M extends Map<K, R>> M awaitAllAndGet(
            Map<K, CompletableFuture<R>> futures, Supplier<? extends M> factory) {
        awaitAll(futures.values());
        return futures.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().join(), (a, b) -> b, factory));
    }

    /**
     * Unwraps common async wrapper exceptions, as Java async APIs often wrap the real causes of failure.
     *
     * @param throwable wrapped throwable
     * @return root cause when available, otherwise the original throwable
     */
    private static Throwable unwrapThrowable(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException || current instanceof ExecutionException) {
            if (current.getCause() == null) {
                break;
            }
            current = current.getCause();
        }
        return current;
    }
}
