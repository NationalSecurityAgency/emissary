package emissary.grpc.retry;

import emissary.config.Configurator;
import emissary.grpc.exceptions.PoolException;
import emissary.grpc.exceptions.ServiceNotAvailableException;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * Config class for handling retries after failed gRPG connections to a remote server. Uses the
 * {@link io.github.resilience4j resilience4j} package with exponential backoff.
 * <p>
 * The max wait of final retry, with at least {@code i} attempts, is equal to:<br>
 * {@code retryInitialWaitMS} * ({@code retryMultiplier} ^ ({@code retryMaxAttempts} - {@code i}))
 * <p>
 * The upper bound of total wait time is equal to:<br>
 * {@code retryInitialWaitMS} * (({@code retryMultiplier} ^ ({@code retryMaxAttempts} - 1)) - 1) /
 * ({@code retryMultiplier} - 1)
 */
public final class Policy {
    public static final String GRPC_RETRY_MAX_ATTEMPTS = "GRPC_RETRY_MAX_ATTEMPTS";
    public static final String GRPC_RETRY_INITIAL_WAIT_MILLIS = "GRPC_RETRY_INITIAL_WAIT_MILLIS";
    public static final String GRPC_RETRY_MULTIPLIER = "GRPC_RETRY_MULTIPLIER";
    public static final String GRPC_RETRY_UNLIMITED = "GRPC_RETRY_UNLIMITED";
    public static final String GRPC_RETRY_NUM_FAILS_BEFORE_WARN = "GRPC_RETRY_NUM_FAILS_BEFORE_WARN";

    private static final Logger logger = LoggerFactory.getLogger(Policy.class);

    private final int maxAttempts;
    private final boolean isUnlimited;
    private final int numFailsBeforeWarn;
    private final Retry retry;

    public Policy(Configurator configG, String retryRegistryName) {
        this.maxAttempts = configG.findIntEntry(GRPC_RETRY_MAX_ATTEMPTS, 4);
        this.isUnlimited = configG.findBooleanEntry(GRPC_RETRY_UNLIMITED, false);
        this.numFailsBeforeWarn = configG.findIntEntry(GRPC_RETRY_NUM_FAILS_BEFORE_WARN, 20);

        RetryRegistry registry = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(
                        configG.findIntEntry(GRPC_RETRY_INITIAL_WAIT_MILLIS, 64),
                        configG.findIntEntry(GRPC_RETRY_MULTIPLIER, 2)))
                .retryExceptions(PoolException.class, ServiceNotAvailableException.class)
                .build());

        if (logger.isDebugEnabled()) {
            registry.getEventPublisher().onEntryAdded(entryAddedEvent -> entryAddedEvent
                    .getAddedEntry()
                    .getEventPublisher()
                    .onRetry(retryEvent -> logger.debug("Retrying connection with event error: {}", retryEvent)));
        }

        this.retry = registry.retry(retryRegistryName);
    }

    public <T> Executor<T> generateExecutor(Supplier<T> supplier) {
        return new Executor<>(supplier, this);
    }

    public boolean getIsUnlimited() {
        return isUnlimited;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public int getNumFailsBeforeWarn() {
        return numFailsBeforeWarn;
    }

    public Retry getRetry() {
        return retry;
    }
}
