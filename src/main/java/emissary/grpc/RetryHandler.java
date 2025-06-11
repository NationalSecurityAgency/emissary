package emissary.grpc;

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

import java.util.function.Supplier;

/**
 * Config class for handling retries after failed gRPG connections to a remote server. Uses the
 * {@link io.github.resilience4j resilience4j} package with exponential backoff.
 * <p>
 * Configuration Keys:
 * <ul>
 * <li>{@code GRPC_RETRY_INITIAL_WAIT_MILLIS} - The amount of time to wait after the first failure before applying
 * exponential backoff, default={@code 64}</li>
 * <li>{@code GRPC_RETRY_MAX_ATTEMPTS} - Maximum number of times to attempt execution, including initial attempt,
 * default={@code 4}</li>
 * <li>{@code GRPC_RETRY_MULTIPLIER} - Multiplier used to determine wait-time for successive retries,
 * default={@code 2}</li>
 * <li>{@code GRPC_RETRY_NUM_FAILS_BEFORE_WARN} - Determines the number of execution failures before logger should start
 * sending warnings, default={@code 20}</li>
 * </ul>
 * Exponential Backoff Equations:
 * <ul>
 * <li>The max wait of the {@code i}th retry is equal to:<br>
 * {@code GRPC_RETRY_INITIAL_WAIT_MILLIS} * ({@code GRPC_RETRY_MULTIPLIER} ^ ({@code GRPC_RETRY_MAX_ATTEMPTS} -
 * {@code i}))</li>
 * <li>The upper bound of total wait time is equal to:<br>
 * {@code GRPC_RETRY_INITIAL_WAIT_MILLIS} * (({@code GRPC_RETRY_MULTIPLIER} ^ ({@code GRPC_RETRY_MAX_ATTEMPTS} - 1)) -
 * 1) / ({@code GRPC_RETRY_MULTIPLIER} - 1)</li>
 * </ul>
 */
public final class RetryHandler {
    public static final String GRPC_RETRY_INITIAL_WAIT_MILLIS = "GRPC_RETRY_INITIAL_WAIT_MILLIS";
    public static final String GRPC_RETRY_MAX_ATTEMPTS = "GRPC_RETRY_MAX_ATTEMPTS";
    public static final String GRPC_RETRY_MULTIPLIER = "GRPC_RETRY_MULTIPLIER";
    public static final String GRPC_RETRY_NUM_FAILS_BEFORE_WARN = "GRPC_RETRY_NUM_FAILS_BEFORE_WARN";

    private static final Logger logger = LoggerFactory.getLogger(RetryHandler.class);

    private final String registryName;
    private final int maxAttempts;
    private final int numFailsBeforeWarn;
    private final Retry retry;

    /**
     * Constructs the retry policy for a given gRPC service.
     * <p>
     * See {@link RetryHandler} for supported configuration keys and defaults.
     *
     * @param configG configuration provider for retry logic
     * @param retryRegistryName unique name to internally identify the retry policy
     */
    public RetryHandler(Configurator configG, String retryRegistryName) {
        registryName = retryRegistryName;
        maxAttempts = configG.findIntEntry(GRPC_RETRY_MAX_ATTEMPTS, 4);
        numFailsBeforeWarn = configG.findIntEntry(GRPC_RETRY_NUM_FAILS_BEFORE_WARN, 20);

        this.retry = Retry.of(registryName, RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(
                        configG.findIntEntry(GRPC_RETRY_INITIAL_WAIT_MILLIS, 64),
                        configG.findIntEntry(GRPC_RETRY_MULTIPLIER, 2)))
                .retryExceptions(PoolException.class, ServiceNotAvailableException.class)
                .build());

        retry.getEventPublisher()
                .onRetry(this::logMessageOnRetry)
                .onError(this::logMessageOnError);
    }

    private void logMessageOnRetry(RetryOnRetryEvent event) {
        int attemptNumber = event.getNumberOfRetryAttempts();
        Level level = attemptNumber <= numFailsBeforeWarn ? Level.INFO : Level.WARN;
        logger.atLevel(level).log("{} failed gRPC connection attempt #{} with event error: {}", registryName, attemptNumber, event);
    }

    private void logMessageOnError(RetryOnErrorEvent event) {
        logger.error("{} failed gRPC connection attempt #{} with event error: {}", registryName, maxAttempts, event);
    }

    public <T> T execute(Supplier<T> supplier) {
        return Retry.decorateSupplier(retry, supplier).get();
    }
}
