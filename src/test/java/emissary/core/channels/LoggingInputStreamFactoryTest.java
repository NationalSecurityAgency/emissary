package emissary.core.channels;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingInputStreamFactoryTest {
    private ListAppender<ILoggingEvent> appender = null;
    private final Logger logger = (Logger) LoggerFactory.getLogger(LoggingInputStreamFactoryTest.class);

    @BeforeEach
    void setup() throws IOException {
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() throws Exception {
        logger.detachAppender(appender);
    }

    @Test
    void testInputStreamFactory() throws IOException {
        final byte[] bytes = new byte[100];
        final TestInputStreamFactory tisf = new TestInputStreamFactory(bytes);

        assertThrows(NullPointerException.class,
                () -> LoggingInputStreamFactory.create(null, "Identifier", logger, false));
        assertThrows(NullPointerException.class, () -> LoggingInputStreamFactory.create(tisf, null, logger, false));
        assertThrows(NullPointerException.class,
                () -> LoggingInputStreamFactory.create(tisf, "Identifier", null, false));

        final InputStreamFactory loggingFalseIsf = LoggingInputStreamFactory.create(tisf, "Identifier", logger, false);

        testLogging(loggingFalseIsf, false);

        final InputStreamFactory loggingTrueIsf = LoggingInputStreamFactory.create(tisf, "Identifier", logger, true);

        testLogging(loggingTrueIsf, true);
    }

    private void testLogging(final InputStreamFactory isf, final boolean testStackTrace) throws IOException {
        assertTrue(appender.list.isEmpty());

        try (InputStream is = isf.create()) {
            check("Identifier : 0 : created : lST=" + testStackTrace, testStackTrace);

            assertTrue(is.markSupported());

            check("Identifier : 0 : markSupported : r=true", testStackTrace);

            is.mark(100);

            check("Identifier : 0 : mark : rl=100", testStackTrace);

            assertEquals(0, is.read());

            check("Identifier : 0 : read-0 : r=0", testStackTrace);

            assertEquals(10, is.read(new byte[10]));

            check("Identifier : 0 : read-1 : r=10", testStackTrace);

            assertEquals(5, is.read(new byte[10], 5, 5));

            check("Identifier : 0 : read-3 : off=5 len=5 : r=5", testStackTrace);

            assertEquals(84, is.available());

            check("Identifier : 0 : available : r=84", testStackTrace);

            assertEquals(15, is.skip(15));

            check("Identifier : 0 : skip : n=15 : r=15", testStackTrace);

            is.reset();

            check("Identifier : 0 : reset", testStackTrace);
        }

        check("Identifier : 0 : close", testStackTrace);
    }

    private void check(final String message, final boolean testStackTrace) {
        assertEquals(1, appender.list.size());

        final ILoggingEvent loggingEvent = appender.list.get(0);

        assertEquals(message, loggingEvent.getFormattedMessage());

        final IThrowableProxy throwableProxy = loggingEvent.getThrowableProxy();

        if (testStackTrace) {
            assertNotNull(throwableProxy);
            assertEquals("java.lang.Throwable", throwableProxy.getClassName());
            assertEquals("DEBUG", throwableProxy.getMessage());
        } else {
            assertNull(throwableProxy);
        }

        appender.list.clear();
    }

    private static class TestInputStreamFactory implements InputStreamFactory {
        private final byte[] bytes;

        public TestInputStreamFactory(final byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public InputStream create() {
            return new BufferedInputStream(new ByteArrayInputStream(bytes));
        }
    }
}
