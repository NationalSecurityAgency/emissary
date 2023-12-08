package emissary.core.channels;

import emissary.test.core.junit5.UnitTest;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingChannelFactoryTest extends UnitTest {
    private ListAppender<ILoggingEvent> appender;
    private final Logger logger = (Logger) LoggerFactory.getLogger(LoggingChannelFactoryTest.class);

    @BeforeEach
    void setup() throws IOException {
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
    }

    @Test
    void testChannelFactory(final @TempDir Path tempDir) throws IOException {
        final Path path = tempDir.resolve("testBytes");
        final SeekableByteChannelFactory fileSbcf = new TestFileChannelFactory(path, logger);
        final SeekableByteChannelFactory loggingFalseSbcf = LoggingChannelFactory.create(fileSbcf, "Identifier", logger,
                false);

        testLogging(appender, loggingFalseSbcf, false);

        path.toFile().delete();

        final SeekableByteChannelFactory loggingTrueSbcf = LoggingChannelFactory.create(fileSbcf, "Identifier", logger,
                true);

        testLogging(appender, loggingTrueSbcf, true);
    }

    private static void testLogging(final ListAppender<ILoggingEvent> appender, final SeekableByteChannelFactory sbcf,
            final boolean testStackTrace) throws IOException {
        assertTrue(appender.list.isEmpty());

        try (SeekableByteChannel sbc = sbcf.create()) {
            check(appender, "Identifier : 0 : created : lST=" + testStackTrace, testStackTrace);

            sbc.write(ByteBuffer.allocate(100));

            check(appender, "Identifier : 0 : write : bbP=0 bbC=100 sbcP=0 : bW=100", testStackTrace);

            sbc.truncate(67);

            check(appender, "Identifier : 0 : truncate : s=67", testStackTrace);

            sbc.position();

            check(appender, "Identifier : 0 : position : p=67", testStackTrace);

            sbc.position(0);

            check(appender, "Identifier : 0 : position : nP=0", testStackTrace);

            sbc.size();

            check(appender, "Identifier : 0 : size : s=67", testStackTrace);

            sbc.read(ByteBuffer.allocate(100));

            check(appender, "Identifier : 0 : read : bbP=0 bbC=100 sbcP=0 : r=67", testStackTrace);

            sbc.isOpen();

            check(appender, "Identifier : 0 : isOpen : o=true", testStackTrace);
        }

        check(appender, "Identifier : 0 : close", testStackTrace);
    }

    private static void check(final ListAppender<ILoggingEvent> appender, final String message,
            final boolean testStackTrace) {
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

    private static class TestFileChannelFactory implements SeekableByteChannelFactory {
        private final Path path;
        private final Logger logger;

        public TestFileChannelFactory(final Path path, final Logger logger) {
            this.path = path;
            this.logger = logger;
        }

        @Override
        public SeekableByteChannel create() {
            try {
                return FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE);
            } catch (IOException e) {
                logger.error("Could not access file!", e);

                return null;
            }
        }
    }
}
