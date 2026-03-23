package emissary.grpc.async;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import jakarta.annotation.Nonnull;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
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
     * Waits for all provided asynchronous computations to complete and returns their results. This method blocks the
     * calling thread until each {@link CompletableFuture} in the input has complete. If any future completes exceptionally,
     * the corresponding {@link CompletableFuture#join()} call will throw a {@link RuntimeException}, and iteration will
     * stop at that point.
     *
     * @param futureMap map of keys to futures representing asynchronous computations
     * @return map of keys to their future results
     * @param <T> result type
     */
    public static <T> Map<String, T> awaitAll(Map<String, CompletableFuture<T>> futureMap) {
        CompletableFuture.allOf(futureMap.values().toArray(new CompletableFuture[0])).join();
        return futureMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().join()));
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
