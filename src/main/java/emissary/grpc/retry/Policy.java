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
 * Configuration Keys:
 * <ul>
 * <li>{@code GRPC_RETRY_INITIAL_WAIT_MILLIS} - The amount of time to wait after the first failure before applying
 * exponential backoff, default={@code 64}</li>
 * <li>{@code GRPC_RETRY_MAX_ATTEMPTS} - Maximum number of times to attempt execution, default={@code 4}</li>
 * <li>{@code GRPC_RETRY_MULTIPLIER} - Multiplier used to determine wait-time for successive retries, default={@code 2}</li>
 * <li>{@code GRPC_RETRY_NUM_FAILS_BEFORE_WARN} - Determines the number of execution failures before logger should start
 * sending warnings, default={@code 20}</li>
 * <li>{@code GRPC_RETRY_UNLIMITED} - If {@code true}, retries will continue indefinitely until successful execution, otherwise
 * terminates after reaching the max number of attempts, default={@code false}</li>
 * </ul>
 * Exponential Backoff Equations:
 * <ul>
 * <li>The max wait of the {@code i}th retry is equal to:<br>
 * {@code GRPC_RETRY_INITIAL_WAIT_MILLIS} * ({@code GRPC_RETRY_MULTIPLIER} ^ ({@code GRPC_RETRY_MAX_ATTEMPTS} - {@code i}))</li>
 * <li>The upper bound of total wait time is equal to:<br>
 * {@code GRPC_RETRY_INITIAL_WAIT_MILLIS} * (({@code GRPC_RETRY_MULTIPLIER} ^ ({@code GRPC_RETRY_MAX_ATTEMPTS} - 1)) - 1) /
 * ({@code GRPC_RETRY_MULTIPLIER} - 1)</li>
 * </ul>
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

    /**
     * Constructs the retry policy for a given gRPC service.
     * <p>
     * See {@link Policy} for supported configuration keys and defaults.
     *
     * @param configG configuration provider for retry logic
     * @param retryRegistryName unique name to internally identify the retry policy
     */
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
