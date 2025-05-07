package emissary.util.grpc.retry;

import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.test.core.junit5.UnitTest;
import emissary.util.grpc.exceptions.ServiceNotAvailableException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class RetryExecutorTest extends UnitTest {
    private static final String RETRY_MAX_ATTEMPTS = "RETRY_MAX_ATTEMPTS";
    private static final String RETRY_NUM_FAILS_BEFORE_WARN = "RETRY_NUM_FAILS_BEFORE_WARN";
    private static final String RETRY_REGISTRY_NAME = "RETRY_REGISTRY_NAME";
    private static final int DEFAULT_RETRY_MAX_ATTEMPTS = 5;
    private static final int DEFAULT_RETRY_NUM_FAILS_BEFORE_WARN = 2;

    private RetryPolicy retryPolicy;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        Configurator configT = new ServiceConfigGuide();
        configT.addEntry(RETRY_MAX_ATTEMPTS, String.valueOf(DEFAULT_RETRY_MAX_ATTEMPTS));
        configT.addEntry(RETRY_NUM_FAILS_BEFORE_WARN, String.valueOf(DEFAULT_RETRY_NUM_FAILS_BEFORE_WARN));
        configT.addEntry(RETRY_REGISTRY_NAME, RETRY_REGISTRY_NAME);
        retryPolicy = new RetryPolicy(configT);
    }

    @Test
    void testRetryExecutorSuccess() {
        AtomicInteger callCount = new AtomicInteger();

        RetryExecutor<String> executor = new RetryExecutor<>(() -> {
            callCount.incrementAndGet();
            return "Success";
        }, retryPolicy);

        assertEquals("Success", executor.execute());
        assertEquals(1, executor.getAttemptNumber());
        assertTrue(executor.canContinue());
    }

    @Test
    void testRetryExecutorMaxAttempts() {
        AtomicInteger callCount = new AtomicInteger();

        RetryExecutor<String> executor = new RetryExecutor<>(() -> {
            callCount.incrementAndGet();
            throw new ServiceNotAvailableException("Fail");
        }, retryPolicy);

        while (executor.canContinue()) {
            try {
                executor.execute();
            } catch (ServiceNotAvailableException ignored) {
            }

            if (executor.getAttemptNumber() > DEFAULT_RETRY_MAX_ATTEMPTS) {
                fail("Went over max attempt limit");
            }
        }

        assertEquals(retryPolicy.getMaxAttempts(), executor.getAttemptNumber());
    }

    @Test
    void testLogLevelSwitch() {
        RetryExecutor<String> executor = new RetryExecutor<>(() -> {
            throw new ServiceNotAvailableException("fail");
        }, retryPolicy);

        for (int i = 0; i < DEFAULT_RETRY_MAX_ATTEMPTS; i++) {
            try {
                executor.execute();
            } catch (ServiceNotAvailableException ignored) {
            }

            if (i < DEFAULT_RETRY_NUM_FAILS_BEFORE_WARN) {
                assertEquals(Level.INFO, executor.getLogLevel());
            } else {
                assertEquals(Level.WARN, executor.getLogLevel());
            }
        }


    }
}

