package emissary.grpc.retry;

import emissary.config.Configurator;
import emissary.grpc.exceptions.PoolException;
import emissary.grpc.exceptions.ServiceNotAvailableException;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.event.RetryOnErrorEvent;
import io.github.resilience4j.retry.event.RetryOnRetryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

/**
 * Config class for handling retries after failed gRPG connections to a remote server. Uses the
 * {@link io.github.resilience4j resilience4j} package with exponential backoff.
 * <p>
 * Configuration Keys:
 * <ul>
 * <li>{@code GRPC_RETRY_INITIAL_WAIT_MILLIS} - The amount of time to wait after the first failure before applying
 * exponential backoff, default={@code 64}</li>
 * <li>{@code GRPC_RETRY_MAX_ATTEMPTS} - Maximum number of times to attempt execution, <b>including initial attempt
 * before retries</b>, default={@code 4}</li>
 * <li>{@code GRPC_RETRY_MAX_WAIT_MILLIS} - The maximum amount of time to wait to retry during exponential backoff,
 * default={@code 1000}</li>
 * <li>{@code GRPC_RETRY_MULTIPLIER} - Multiplier used to determine wait-time for successive retries,
 * default={@code 2.0}</li>
 * <li>{@code GRPC_RETRY_NUM_FAILS_BEFORE_WARN} - Determines the number of execution failures before logger should start
 * sending warnings, default={@code 3}</li>
 * <li>{@code GRPC_RETRY_UNLIMITED} - When {@code true}, the retry cycle restarts indefinitely after exhausting
 * {@code GRPC_RETRY_MAX_ATTEMPTS} until the call succeeds. Use this when downstream systems must block until the gRPC
 * request completes and a permanent failure is not acceptable, default={@code false}</li>
 * </ul>
 * To calculate exponential backoff wait time, let:
 * <ul>
 * <li>{@code M = GRPC_RETRY_MULTIPLIER}</li>
 * <li>{@code N = GRPC_RETRY_MAX_ATTEMPTS} (includes the initial attempt)</li>
 * <li>{@code W0 = GRPC_RETRY_INITIAL_WAIT_MILLIS}</li>
 * <li>{@code Wmax = GRPC_RETRY_MAX_WAIT_MILLIS}</li>
 * </ul>
 * The maximum wait (in milliseconds) before the {@code i}th retry is:
 * 
 * <pre>
 * w(i) = min(W0 * (M ^ (i - 1)), Wmax)
 * </pre>
 * 
 * The maximum total wait time (in milliseconds), assuming all attempts fail, is:
 * 
 * <pre>
 * T = sum(w(i) for i in [1, N - 1])
 * </pre>
 */
public final class RetryHandler {
    public static final String GRPC_RETRY_INITIAL_WAIT_MILLIS = "GRPC_RETRY_INITIAL_WAIT_MILLIS";
    public static final String GRPC_RETRY_MAX_ATTEMPTS = "GRPC_RETRY_MAX_ATTEMPTS";
    public static final String GRPC_RETRY_MAX_WAIT_MILLIS = "GRPC_RETRY_MAX_WAIT_MILLIS";
    public static final String GRPC_RETRY_MULTIPLIER = "GRPC_RETRY_MULTIPLIER";
    public static final String GRPC_RETRY_NUM_FAILS_BEFORE_WARN = "GRPC_RETRY_NUM_FAILS_BEFORE_WARN";
    public static final String GRPC_RETRY_UNLIMITED = "GRPC_RETRY_UNLIMITED";

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final Logger logger = LoggerFactory.getLogger(RetryHandler.class);

    private final String internalName;
    private final int maxAttempts;
    private final int numFailsBeforeWarn;
    private final boolean retryUnlimited;
    private final Retry retry;

    /**
     * Constructs the retry policy for a given gRPC service.
     * <p>
     * See {@link RetryHandler} for supported configuration keys and defaults.
     *
     * @param configG configuration provider for retry logic
     * @param retryName unique name to internally identify the retry policy
     */
    public RetryHandler(Configurator configG, String retryName) {
        internalName = retryName;
        maxAttempts = configG.findIntEntry(GRPC_RETRY_MAX_ATTEMPTS, 4);
        numFailsBeforeWarn = configG.findIntEntry(GRPC_RETRY_NUM_FAILS_BEFORE_WARN, 3);
        retryUnlimited = configG.findBooleanEntry(GRPC_RETRY_UNLIMITED, false);

        retry = Retry.of(internalName, RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(
                        configG.findIntEntry(GRPC_RETRY_INITIAL_WAIT_MILLIS, 64),
                        configG.findDoubleEntry(GRPC_RETRY_MULTIPLIER, 2.0),
                        configG.findLongEntry(GRPC_RETRY_MAX_WAIT_MILLIS, 1000)))
                .retryExceptions(PoolException.class, ServiceNotAvailableException.class)
                .build());

        retry.getEventPublisher()
                .onRetry(this::logMessageOnRetry)
                .onError(this::logMessageOnError);
    }

    private void logMessageOnRetry(RetryOnRetryEvent event) {
        int attemptNumber = event.getNumberOfRetryAttempts();
        Level level = attemptNumber <= numFailsBeforeWarn ? Level.INFO : Level.WARN;
        logger.atLevel(level).log("{} failed gRPC connection attempt #{} with event error: {}", internalName, attemptNumber, event);
    }

    private void logMessageOnError(RetryOnErrorEvent event) {
        logger.error("{} failed gRPC connection attempt #{} with event error: {}", internalName, maxAttempts, event);
    }

    public <T> T execute(Supplier<T> supplier) {
        if (!retryUnlimited) {
            return Retry.decorateSupplier(retry, supplier).get();
        }
        int cycleCount = 0;
        while (true) {
            try {
                return Retry.decorateSupplier(retry, supplier).get();
            } catch (ServiceNotAvailableException | PoolException e) {
                logger.warn("{} exhausted {} max retry attempts (cycle #{}), restarting due to unlimited retry mode",
                        internalName, maxAttempts, ++cycleCount);
            }
        }
    }

    public <T> CompletionStage<T> executeAsync(Supplier<CompletionStage<T>> supplier) {
        if (!retryUnlimited) {
            return Retry.decorateCompletionStage(retry, scheduler, supplier).get();
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        attemptAsyncUnlimited(supplier, result, 0);
        return result;
    }

    private <T> void attemptAsyncUnlimited(Supplier<CompletionStage<T>> supplier, CompletableFuture<T> result, int cycleCount) {
        Retry.decorateCompletionStage(retry, scheduler, supplier).get()
                .whenComplete((value, throwable) -> {
                    if (throwable == null) {
                        result.complete(value);
                    } else {
                        Throwable cause = unwrapCompletionCause(throwable);
                        if (cause instanceof ServiceNotAvailableException || cause instanceof PoolException) {
                            logger.warn("{} exhausted {} max retry attempts (cycle #{}), restarting due to unlimited retry mode",
                                    internalName, maxAttempts, cycleCount + 1);
                            scheduler.execute(() -> attemptAsyncUnlimited(supplier, result, cycleCount + 1));
                        } else {
                            result.completeExceptionally(throwable);
                        }
                    }
                });
    }

    private static Throwable unwrapCompletionCause(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
