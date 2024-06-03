package emissary.util.shell;

import emissary.test.core.junit5.UnitTest;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.annotation.Nullable;

import static emissary.log.MDCConstants.SERVICE_LOCATION;
import static emissary.log.MDCConstants.SHORT_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ProcessReaderTest extends UnitTest {

    static final String SOME_NAME = "some name";
    static final String SOME_LOCATION = "some location";
    static final String SOME_MESSAGE = "some message";
    static final String NOT_SET = "[not set]";
    static final String MSG_PATTERN = "SHORT_NAME: '%X{" + SHORT_NAME + "}' SERVICE_LOCATION: '%X{" + SERVICE_LOCATION + "}' - %m";
    // pattern used to construct expected values using String.format(...)
    static final String FORMAT_PATTERN = "SHORT_NAME: '%s' SERVICE_LOCATION: '%s' - %s";

    static final Logger logger = (Logger) LoggerFactory.getLogger(DummyProcessReader.class);
    @Nullable
    OutputStreamAppender<ILoggingEvent> appender;
    @Nullable
    PatternLayoutEncoder encoder;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        super.setUp();

        encoder = new PatternLayoutEncoder();
        encoder.setPattern(MSG_PATTERN);
        encoder.setContext(logger.getLoggerContext());
        encoder.start();

        appender = new OutputStreamAppender<>();
        appender.setEncoder(encoder);
        appender.setContext(logger.getLoggerContext());

        logger.addAppender(appender);
        logger.setLevel(Level.ALL);

        MDC.put(SERVICE_LOCATION, SOME_LOCATION);
        MDC.put(SHORT_NAME, SOME_NAME);
    }

    @AfterEach
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        MDC.remove(SERVICE_LOCATION);
        MDC.remove(SHORT_NAME);

        if (encoder != null) {
            encoder.stop();
        }
        if (appender != null) {
            appender.stop();
        }
        logger.detachAndStopAllAppenders();
    }

    /**
     * Used to validate that {@link MDC} values are properly passed down to child threads via the
     * {@link ProcessReader#applyLogContextMap()}
     */
    @Test
    void testFormattedLogMessageWithMDCTransfer() throws IOException, InterruptedException {

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            appender.setOutputStream(outputStream);
            appender.start();

            DummyProcessReader objectUnderTest = new DummyProcessReader();
            objectUnderTest.setContextMap(MDC.getCopyOfContextMap());
            objectUnderTest.start();
            objectUnderTest.join();

            String expected = String.format(FORMAT_PATTERN, SOME_NAME, SOME_LOCATION, SOME_MESSAGE);
            assertEquals(expected, outputStream.toString());
        }
    }

    @Test
    void testFormattedLogMessageWithoutMDCTransfer() throws IOException, InterruptedException {

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            appender.setOutputStream(outputStream);
            appender.start();

            DummyProcessReader objectUnderTest = new DummyProcessReader();
            // Skip the call to setContextMap()
            // objectUnderTest.setContextMap(MDC.getCopyOfContextMap());
            objectUnderTest.start();
            objectUnderTest.join();

            String expected = String.format(FORMAT_PATTERN, NOT_SET, NOT_SET, SOME_MESSAGE);
            assertEquals(expected, outputStream.toString());
        }
    }

    static class DummyProcessReader extends ProcessReader {
        final Logger logger = (Logger) LoggerFactory.getLogger(DummyProcessReader.class);

        @Override
        public void finish() {
            // nothing to do
        }

        @Override
        void runImpl() {
            // put default values in MDC map
            MDC.put(SERVICE_LOCATION, NOT_SET);
            MDC.put(SHORT_NAME, NOT_SET);

            // override MDC values if info was passed from parent thread
            applyLogContextMap();
            logger.info(SOME_MESSAGE);
        }
    }
}
