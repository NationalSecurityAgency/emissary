package emissary.grpc.future;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.GeneratedMessageV3;
import jakarta.annotation.Nonnull;

import java.util.concurrent.CompletableFuture;

public class CompletableFutureAdaptors {
    private CompletableFutureAdaptors() {}

    /**
     * Converts a Guava {@link ListenableFuture} future to a Java {@link CompletableFuture}.
     *
     * @param future Guava future
     * @return completable future
     * @param <R> result type
     */
    public static <R extends GeneratedMessageV3> CompletableFuture<R> fromListenableFuture(ListenableFuture<R> future) {
        CompletableFuture<R> completableFuture = new CompletableFuture<>();
        Futures.addCallback(future, new FutureCallback<>() {
            @Override
            public void onSuccess(R result) {
                completableFuture.complete(result);
            }

            @Override
            public void onFailure(@Nonnull Throwable t) {
                completableFuture.completeExceptionally(t);
            }
        }, MoreExecutors.directExecutor()); // Direct executor runs callback immediately on thread completing Guava future
        return completableFuture;
    }
}
