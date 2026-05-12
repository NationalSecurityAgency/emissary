package emissary.grpc.future;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.Message;
import jakarta.annotation.Nonnull;

import java.util.concurrent.CompletableFuture;

public class CompletableFutureAdaptors {
    private CompletableFutureAdaptors() {}

    /**
     * Converts a Guava {@link ListenableFuture} future to a Java {@link CompletableFuture}.
     *
     * @param listenable Guava future
     * @return completable future
     * @param <R> result type
     */
    public static <R extends Message> CompletableFuture<R> fromListenableFuture(ListenableFuture<R> listenable) {
        CompletableFuture<R> completable = new CompletableFuture<>();

        FutureCallback<R> callback = new FutureCallback<>() {
            @Override
            public void onSuccess(R result) {
                completable.complete(result);
            }

            @Override
            public void onFailure(@Nonnull Throwable t) {
                if (listenable.isCancelled()) {
                    completable.cancel(false);
                } else {
                    completable.completeExceptionally(t);
                }
            }
        };

        // Direct executor runs callback immediately on thread completing Guava future
        Futures.addCallback(listenable, callback, MoreExecutors.directExecutor());
        return completable;
    }
}
