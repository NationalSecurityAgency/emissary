package emissary.grpc.future;

import emissary.grpc.exceptions.GrpcExceptionUtils;

import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class CompletableFutureFinalizers {
    private CompletableFutureFinalizers() {}

    /**
     * Waits for all provided asynchronous computations to complete and returns their results. This method blocks the
     * calling thread until each {@link CompletableFuture} in the input is complete. If any future completes exceptionally,
     * the corresponding {@link CompletableFuture#join()} call will throw a {@link RuntimeException}, and iteration will
     * stop at that point, unless explicit behavior has been passed through the {@code exceptionally} argument.
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
                .map(future -> get(future, exceptionally))
                .collect(Collectors.toCollection(factory));
    }

    /**
     * Waits for all provided asynchronous computations to complete and returns their results. This method blocks the
     * calling thread until each {@link CompletableFuture} in the input is complete. If any future completes exceptionally,
     * the corresponding {@link CompletableFuture#join()} call will throw a {@link RuntimeException}, and iteration will
     * stop at that point, unless explicit behavior has been passed through the {@code exceptionally} argument.
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
                .collect(Collectors.toMap(Map.Entry::getKey, e -> get(e.getValue(), exceptionally), (a, b) -> b, factory));
    }

    /**
     * Waits for a provided asynchronous computation to complete and then return its results. This method blocks the calling
     * thread until the {@link CompletableFuture} input has completed. If any future completes exceptionally, the
     * corresponding {@link CompletableFuture#join()} call will throw a {@link RuntimeException}, and iteration will stop at
     * that point, unless explicit behavior has been passed through the {@code exceptionally} argument.
     *
     * @param future future representing an asynchronous computation
     * @param exceptionally function for handling exceptions thrown by the future, throws normally if {@code null}
     * @return the result returned by an asynchronous computation
     * @param <R> result type
     */
    private static <R> R get(CompletableFuture<R> future, Function<Throwable, R> exceptionally) {
        try {
            if (exceptionally == null) {
                return future.join();
            }
            return future.exceptionally(exceptionally).join();
        } catch (RuntimeException e) {
            throw GrpcExceptionUtils.toContextualRuntimeException(
                    GrpcExceptionUtils.unwrapAsyncThrowable(e));
        }
    }
}
