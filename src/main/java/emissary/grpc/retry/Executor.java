package emissary.grpc.retry;

import io.github.resilience4j.retry.Retry;
import org.slf4j.event.Level;

import java.util.function.Supplier;

/**
 * A helper class for executing a supplier with retry logic based on a configured {@link Policy}.
 * <p>
 * This executor wraps a {@link Supplier} with {@link io.github.resilience4j.retry.Retry} behavior, managing retry
 * attempts, tracking the number of executions, and determining appropriate log levels based on configured thresholds.
 *
 * @param <T> The type of the result produced by the supplier.
 */
public class Executor<T> {
    private final Supplier<T> supplier;
    private final int maxAttempts;
    private final int numFailsBeforeWarn;
    private final boolean unlimitedAttempts;
    private int attemptNumber = 0;

    public Executor(Supplier<T> supplier, Policy policy) {
        this.supplier = Retry.decorateSupplier(policy.getRetry(), supplier);
        this.maxAttempts = policy.getMaxAttempts();
        this.numFailsBeforeWarn = policy.getNumFailsBeforeWarn();
        this.unlimitedAttempts = policy.getIsUnlimited();
    }

    public boolean canContinue() {
        return unlimitedAttempts || attemptNumber < maxAttempts;
    }

    public T execute() {
        attemptNumber++;
        return supplier.get();
    }

    public Level getLogLevel() {
        return attemptNumber <= numFailsBeforeWarn ? Level.INFO : Level.WARN;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }
}
