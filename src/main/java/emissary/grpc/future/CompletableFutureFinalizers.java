package emissary.grpc.future;

import emissary.grpc.exceptions.GrpcExceptionUtils;

import io.grpc.Status;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class CompletableFutureFinalizers {
    private CompletableFutureFinalizers() {}

    /**
     * Waits for a provided asynchronous computation to complete and then return its result. This method blocks the calling
     * thread until the {@link CompletableFuture} input has completed. If the future completes exceptionally, the
     * corresponding {@link CompletableFuture#get()} call will throw a {@link RuntimeException}, unless the future has been
     * preconfigured with explicit exception handling behavior. If the thread is interrupted while the future is still
     * completing, the call will be canceled and a {@link io.grpc.StatusRuntimeException} will be thrown.
     *
     * @param future future representing an asynchronous computation
     * @return the result returned by an asynchronous computation
     * @param <R> result type
     */
    public static <R> R awaitAndGet(CompletableFuture<R> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            future.cancel(false);
            Thread.currentThread().interrupt();
            throw GrpcExceptionUtils.toContextualRuntimeException(Status.CANCELLED.asRuntimeException());
        } catch (ExecutionException | RuntimeException e) {
            throw GrpcExceptionUtils.toContextualRuntimeException(
                    GrpcExceptionUtils.unwrapAsyncThrowable(e));
        }
    }

    /**
     * Waits for a provided asynchronous computation to complete and then return its result. This method blocks the calling
     * thread until the {@link CompletableFuture} input has completed. If the future completes exceptionally, the
     * corresponding {@link CompletableFuture#join()} call will throw a {@link RuntimeException}, unless explicit behavior
     * has been passed through the {@code exceptionally} argument. Note that the {@code exceptionally} argument will not run
     * if the future has already been preconfigured with exception handling behavior.
     *
     * @param future future representing an asynchronous computation
     * @param exceptionally function for handling exceptions thrown by the future, throws normally if {@code null}
     * @return the result returned by an asynchronous computation
     * @param <R> result type
     */
    public static <R> R awaitAndGet(CompletableFuture<R> future, @Nullable Function<Throwable, R> exceptionally) {
        if (exceptionally == null) {
            return awaitAndGet(future);
        }
        return awaitAndGet(future.exceptionally(exceptionally));
    }

    /**
     * Waits for all provided asynchronous computations to complete and returns their results. This method blocks the
     * calling thread until each {@link CompletableFuture} in the input is complete. If any future completes exceptionally,
     * the corresponding {@link CompletableFuture#join()} call will throw a {@link RuntimeException}, and iteration will
     * stop at that point, unless the future has been preconfigured with explicit exception handling behavior.
     *
     * @param futures collection of futures representing asynchronous computations
     * @param factory creates the output collection instance
     * @return a new collection of the results returned by the asynchronous computations
     * @param <R> result type
     * @param <C> collection type
     */
    public static <R, C extends Collection<R>> C awaitAllAndGet(
            Collection<CompletableFuture<R>> futures, Supplier<? extends C> factory) {
        return awaitAllAndGet(futures, factory, null);
    }

    /**
     * Waits for all provided asynchronous computations to complete and returns their results. This method blocks the
     * calling thread until each {@link CompletableFuture} in the input is complete. If any future completes exceptionally,
     * the corresponding {@link CompletableFuture#join()} call will throw a {@link RuntimeException}, and iteration will
     * stop at that point, unless explicit behavior has been passed through the {@code exceptionally} argument. Note that
     * the {@code exceptionally} argument will not run if the future has already been preconfigured with exception handling
     * behavior.
     *
     * @param futures collection of futures representing asynchronous computations
     * @param factory creates the output collection instance
     * @param exceptionally function for handling individual future exceptions, throws normally if {@code null}
     * @return a new collection of the results returned by the asynchronous computations
     * @param <R> result type
     * @param <C> collection type
     */
    public static <R, C extends Collection<R>> C awaitAllAndGet(
            Collection<CompletableFuture<R>> futures, Supplier<? extends C> factory, @Nullable Function<Throwable, R> exceptionally) {
        return futures.stream()
                .map(future -> awaitAndGet(future, exceptionally))
                .collect(Collectors.toCollection(factory));
    }

    /**
     * Waits for all provided asynchronous computations to complete and returns their results. This method blocks the
     * calling thread until each {@link CompletableFuture} in the input is complete. If any future completes exceptionally,
     * the corresponding {@link CompletableFuture#join()} call will throw a {@link RuntimeException}, and iteration will
     * stop at that point, unless the future has been preconfigured with explicit exception handling behavior.
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
        return awaitAllAndGet(futures, factory, null);
    }

    /**
     * Waits for all provided asynchronous computations to complete and returns their results. This method blocks the
     * calling thread until each {@link CompletableFuture} in the input is complete. If any future completes exceptionally,
     * the corresponding {@link CompletableFuture#join()} call will throw a {@link RuntimeException}, and iteration will
     * stop at that point, unless explicit behavior has been passed through the {@code exceptionally} argument. Note that
     * the {@code exceptionally} argument will not run if the future has already been preconfigured with exception handling
     * behavior.
     *
     * @param futures map of keys to futures representing asynchronous computations
     * @param factory creates the output map instance
     * @param exceptionally function for handling individual future exceptions, throws normally if {@code null}
     * @return a new map of keys to the results returned by the asynchronous computations
     * @param <K> key type
     * @param <R> result type
     * @param <M> map type
     */
    public static <K, R, M extends Map<K, R>> M awaitAllAndGet(
            Map<K, CompletableFuture<R>> futures, Supplier<? extends M> factory, @Nullable Function<Throwable, R> exceptionally) {
        return futures.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> awaitAndGet(e.getValue(), exceptionally), (a, b) -> b, factory));
    }
}
