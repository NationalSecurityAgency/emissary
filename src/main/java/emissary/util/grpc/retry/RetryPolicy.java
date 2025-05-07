package emissary.util.grpc.retry;

import emissary.config.Configurator;
import emissary.util.grpc.exceptions.ServiceNotAvailableException;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Config class for handling retries after failed gRPG connections to a remote server. Uses `resilienc4j` with
 * exponential backoff.<br>
 * <br>
 * The max wait of final retry, with at least 2 attempts, will be equal to:<br>
 * {@code retryInitialWaitMS} * ({@code retryMultiplier} ^ ({@code retryMaxAttempts} - 2))<br>
 * <br>
 * The upper bound of total wait time will be equal to:<br>
 * {@code relayInitialWaitMS} * (({@code retryMultiplier} ^ ({@code retryMaxAttempts} - 1)) - 1) /
 * ({@code retryMultiplier} - 1)<br>
 * <br>
 * E.g. If {@code retryMultiplier}=2, then:<br>
 * max wait of final retry = {@code retryInitialWaitMS} * (2 ^ ({@code retryMaxAttempts} - 2))<br>
 * max wait of total time = {@code relayInitialWaitMS} * ((2 ^ ({@code retryMaxAttempts} - 1)) - 1)
 */
public final class RetryPolicy {
    private static final Logger logger = LoggerFactory.getLogger(RetryPolicy.class);

    private static final String RETRY_MAX_ATTEMPTS = "RETRY_MAX_ATTEMPTS";
    private static final String RETRY_INITIAL_WAIT_MS = "RETRY_INITIAL_WAIT_MS";
    private static final String RETRY_MULTIPLIER = "RETRY_MULTIPLIER";
    private static final String RETRY_UNLIMITED = "RETRY_UNLIMITED";
    private static final String RETRY_NUM_FAILS_BEFORE_WARN = "RETRY_NUM_FAILS_BEFORE_WARN";
    private static final String RETRY_REGISTRY_NAME = "RETRY_REGISTRY_NAME";

    private static class Defaults {
        private static final int RETRY_MAX_ATTEMPTS = 4;
        private static final int RETRY_INITIAL_WAIT_MS = 64;
        private static final int RETRY_MULTIPLIER = 2;
        private static final boolean RETRY_UNLIMITED = false;
        private static final int RETRY_NUM_FAILS_BEFORE_WARN = 20;
    }

    private final int maxAttempts;
    private final boolean isUnlimited;
    private final int numFailsBeforeWarn;
    private final Retry retry;

    public RetryPolicy(Configurator configG) {
        this.maxAttempts = configG.findIntEntry(RETRY_MAX_ATTEMPTS, Defaults.RETRY_MAX_ATTEMPTS);
        this.isUnlimited = configG.findBooleanEntry(RETRY_UNLIMITED, Defaults.RETRY_UNLIMITED);
        this.numFailsBeforeWarn = configG.findIntEntry(RETRY_NUM_FAILS_BEFORE_WARN, Defaults.RETRY_NUM_FAILS_BEFORE_WARN);

        int retryInitialWaitMs = configG.findIntEntry(RETRY_INITIAL_WAIT_MS, Defaults.RETRY_INITIAL_WAIT_MS);
        int retryMultiplier = configG.findIntEntry(RETRY_MULTIPLIER, Defaults.RETRY_MULTIPLIER);
        final RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(retryInitialWaitMs, retryMultiplier))
                .retryExceptions(ServiceNotAvailableException.class)
                .build();

        RetryRegistry registry = RetryRegistry.of(retryConfig);
        if (logger.isDebugEnabled()) {
            registry.getEventPublisher().onEntryAdded(entryAddedEvent -> entryAddedEvent.getAddedEntry().getEventPublisher()
                    .onRetry(event -> logger.debug("Retrying connection with event error: {}", event)));
        }
        this.retry = registry.retry(configG.findRequiredStringEntry(RETRY_REGISTRY_NAME));
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
