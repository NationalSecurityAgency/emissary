package emissary.grpc.retry;

import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.grpc.exceptions.ServiceNotAvailableException;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ExecutorTest extends UnitTest {
    private static final String DEFAULT_RETRY_REGISTRY_NAME = "TestRetryRegistry";
    private static final String GRPC_RETRY_MAX_ATTEMPTS = "GRPC_RETRY_MAX_ATTEMPTS";
    private static final String GRPC_RETRY_NUM_FAILS_BEFORE_WARN = "GRPC_RETRY_NUM_FAILS_BEFORE_WARN";
    private static final int DEFAULT_RETRY_MAX_ATTEMPTS = 5;
    private static final int DEFAULT_RETRY_NUM_FAILS_BEFORE_WARN = 2;

    private Policy retryPolicy;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        Configurator configT = new ServiceConfigGuide();
        configT.addEntry(GRPC_RETRY_MAX_ATTEMPTS, String.valueOf(DEFAULT_RETRY_MAX_ATTEMPTS));
        configT.addEntry(GRPC_RETRY_NUM_FAILS_BEFORE_WARN, String.valueOf(DEFAULT_RETRY_NUM_FAILS_BEFORE_WARN));
        retryPolicy = new Policy(configT, DEFAULT_RETRY_REGISTRY_NAME);
    }

    @Test
    void testRetryExecutorSuccess() {
        Executor<String> executor = new Executor<>(() -> "Success", retryPolicy);

        assertEquals("Success", executor.execute());
        assertEquals(1, executor.getAttemptNumber());
        assertTrue(executor.canContinue());
    }

    @Test
    void testRetryExecutorMaxAttempts() {
        Executor<String> executor = new Executor<>(() -> {
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
        Executor<String> executor = new Executor<>(() -> {
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
