package emissary.grpc;

import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.grpc.exceptions.ServiceNotAvailableException;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RetryHandlerTest extends UnitTest {
    private static final String DEFAULT_RETRY_REGISTRY_NAME = "TestRetryRegistry";
    private static final int DEFAULT_RETRY_MAX_ATTEMPTS = 5;
    private static final int DEFAULT_RETRY_NUM_FAILS_BEFORE_WARN = 2;
    private RetryHandler retryHandler;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        Configurator configT = new ServiceConfigGuide();
        configT.addEntry(RetryHandler.GRPC_RETRY_MAX_ATTEMPTS, String.valueOf(DEFAULT_RETRY_MAX_ATTEMPTS));
        configT.addEntry(RetryHandler.GRPC_RETRY_NUM_FAILS_BEFORE_WARN, String.valueOf(DEFAULT_RETRY_NUM_FAILS_BEFORE_WARN));
        retryHandler = new RetryHandler(configT, DEFAULT_RETRY_REGISTRY_NAME);
    }

    @Test
    void testRetryExecutorSuccess() {
        RetryHandler.Executor<String> executor = retryHandler.newExecutor(() -> "Success");

        assertEquals("Success", executor.execute());
        assertEquals(1, executor.getAttemptNumber());
    }

    @Test
    void testRetryExecutorMaxAttempts() {
        RetryHandler.Executor<String> executor = retryHandler.newExecutor(() -> {
            throw new ServiceNotAvailableException("Fail");
        });

        executor.execute(retryHandler.getMaxAttempts() * 2); // capped at configured max
        assertEquals(retryHandler.getMaxAttempts(), executor.getAttemptNumber());
    }

    @Test
    void testLogLevelSwitch() {
        RetryHandler.Executor<String> executor = retryHandler.newExecutor(() -> {
            throw new ServiceNotAvailableException("fail");
        });

        for (int i = 0; i <= DEFAULT_RETRY_MAX_ATTEMPTS; i++) {
            executor.execute(i);
            if (i <= DEFAULT_RETRY_NUM_FAILS_BEFORE_WARN) {
                assertEquals(Level.INFO, executor.getLogLevel());
            } else {
                assertEquals(Level.WARN, executor.getLogLevel());
            }
        }
    }
}
