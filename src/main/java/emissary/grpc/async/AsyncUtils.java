package emissary.grpc.async;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import jakarta.annotation.Nonnull;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class AsyncUtils {
    private AsyncUtils() {}

    /**
     * Converts a Guava future to a Java completable future.
     *
     * @param future Guava future
     * @return completable future
     * @param <T> result type
     */
    public static <T> CompletableFuture<T> toCompletableFuture(ListenableFuture<T> future) {
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
    public static Throwable unwrapThrowable(Throwable throwable) {
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
