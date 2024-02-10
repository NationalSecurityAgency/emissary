package emissary.util.io;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.read.ListAppender;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.lang3.Validate;
import org.apache.log4j.MDC;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static emissary.util.io.LoggingPrintStream.NORMAL_SEPARATOR;
import static emissary.util.io.LoggingPrintStream.THROWABLE_PREFIX;
import static org.apache.commons.io.output.NullOutputStream.INSTANCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoggingPrintStreamTest {
    private static final String LOG_MSG_1 = "Log message NUMBER 1";
    private static final String LOG_MSG_2 = "Log message NUMBER 2";
    private static final Object LOG_OBJ_1 = 1234567890;
    private static final Exception EXCEPTION_CAUSE = new Exception("Test Exception 'cause'");
    private static final Exception EXCEPTION_ONE = new Exception("Test Exception NUMBER 1", EXCEPTION_CAUSE);
    private static final Exception EXCEPTION_TWO = new Exception("Test Exception NUMBER 2", EXCEPTION_CAUSE);

    @Test
    void testArguments() {
        final Logger logger = (Logger) LoggerFactory.getLogger(LoggingPrintStream.class);
        final org.slf4j.event.Level slf4jLevel = org.slf4j.event.Level.TRACE;

        assertThrows(NullPointerException.class, () -> new LoggingPrintStream(null, "TEST", logger, slf4jLevel, 1, TimeUnit.SECONDS));
        assertThrows(NullPointerException.class, () -> new LoggingPrintStream(INSTANCE, null, logger, slf4jLevel, 1, TimeUnit.SECONDS));
        assertThrows(NullPointerException.class, () -> new LoggingPrintStream(INSTANCE, "TEST", null, slf4jLevel, 1, TimeUnit.SECONDS));
        assertThrows(NullPointerException.class, () -> new LoggingPrintStream(INSTANCE, "TEST", logger, null, 1, TimeUnit.SECONDS));
        assertThrows(IllegalArgumentException.class,
                () -> new LoggingPrintStream(INSTANCE, "TEST", logger, slf4jLevel, -1, TimeUnit.SECONDS));
        assertThrows(NullPointerException.class, () -> new LoggingPrintStream(INSTANCE, "TEST", logger, slf4jLevel, 1, null));
    }

    @Test
    void testClose() {
        final Logger logger = (Logger) LoggerFactory.getLogger(LoggingPrintStream.class);
        final org.slf4j.event.Level slf4jLevel = org.slf4j.event.Level.TRACE;

        LoggingPrintStream loggingPrintStream = new LoggingPrintStream(NullOutputStream.INSTANCE, "TEST", logger, slf4jLevel, 30, TimeUnit.SECONDS);
        try {
            loggingPrintStream.close();
        } finally {
            assertThrows(RejectedExecutionException.class, () -> loggingPrintStream.println(LOG_MSG_1));
            assertThrows(RejectedExecutionException.class, () -> EXCEPTION_ONE.printStackTrace(loggingPrintStream));
        }
    }

    @Test
    void testLoggingLevel() {
        LogbackTester logbackTester = new LogbackTester(LoggingPrintStreamTest.class.getName());
        LoggingPrintStream loggingPrintStreamDebug =
                new LoggingPrintStream(INSTANCE, logbackTester.name + "_DEBUG", logbackTester.logger,
                        org.slf4j.event.Level.DEBUG, 30, TimeUnit.SECONDS);
        LoggingPrintStream loggingPrintStreamError =
                new LoggingPrintStream(INSTANCE, logbackTester.name + "_ERROR", logbackTester.logger,
                        org.slf4j.event.Level.ERROR, 30, TimeUnit.SECONDS);
        LoggingPrintStream loggingPrintStreamInfo =
                new LoggingPrintStream(INSTANCE, logbackTester.name + "_INFO", logbackTester.logger,
                        org.slf4j.event.Level.INFO, 30, TimeUnit.SECONDS);
        LoggingPrintStream loggingPrintStreamTrace =
                new LoggingPrintStream(INSTANCE, logbackTester.name + "_TRACE", logbackTester.logger,
                        org.slf4j.event.Level.TRACE, 30, TimeUnit.SECONDS);
        LoggingPrintStream loggingPrintStreamWarn =
                new LoggingPrintStream(INSTANCE, logbackTester.name + "_WARN", logbackTester.logger,
                        org.slf4j.event.Level.WARN, 30, TimeUnit.SECONDS);
        try {
            logbackTester.logger.setLevel(Level.ALL);

            loggingPrintStreamDebug.print(LOG_MSG_1);
            EXCEPTION_ONE.printStackTrace(loggingPrintStreamDebug);
            loggingPrintStreamDebug.close();
            loggingPrintStreamError.print(LOG_MSG_1);
            EXCEPTION_ONE.printStackTrace(loggingPrintStreamError);
            loggingPrintStreamError.close();
            loggingPrintStreamInfo.print(LOG_MSG_1);
            EXCEPTION_ONE.printStackTrace(loggingPrintStreamInfo);
            loggingPrintStreamInfo.close();
            loggingPrintStreamTrace.print(LOG_MSG_1);
            EXCEPTION_ONE.printStackTrace(loggingPrintStreamTrace);
            loggingPrintStreamTrace.close();
            loggingPrintStreamWarn.print(LOG_MSG_1);
            EXCEPTION_ONE.printStackTrace(loggingPrintStreamWarn);
            loggingPrintStreamWarn.close();
        } finally {
            final Level[] expectedLevels =
                    {Level.DEBUG, Level.DEBUG, Level.ERROR, Level.ERROR, Level.INFO, Level.INFO, Level.TRACE, Level.TRACE, Level.WARN, Level.WARN};
            final String[] expectedMessages =
                    {logbackTester.name + "_DEBUG" + NORMAL_SEPARATOR + LOG_MSG_1,
                            THROWABLE_PREFIX + logbackTester.name + "_DEBUG",
                            logbackTester.name + "_ERROR" + NORMAL_SEPARATOR + LOG_MSG_1,
                            THROWABLE_PREFIX + logbackTester.name + "_ERROR",
                            logbackTester.name + "_INFO" + NORMAL_SEPARATOR + LOG_MSG_1,
                            THROWABLE_PREFIX + logbackTester.name + "_INFO",
                            logbackTester.name + "_TRACE" + NORMAL_SEPARATOR + LOG_MSG_1,
                            THROWABLE_PREFIX + logbackTester.name + "_TRACE",
                            logbackTester.name + "_WARN" + NORMAL_SEPARATOR + LOG_MSG_1,
                            THROWABLE_PREFIX + logbackTester.name + "_WARN"};
            final boolean[] expectedThrowables = {false, true, false, true, false, true, false, true, false, true};

            logbackTester.checkLogList(expectedLevels, expectedMessages, expectedThrowables);
        }
    }

    @Test
    void testMdcContextMap() {
        final org.slf4j.event.Level slf4jLevel = org.slf4j.event.Level.INFO;
        final Logger logger = (Logger) LoggerFactory.getLogger(LoggingPrintStreamTest.class.getName() + ".testMdcContextMap");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        LoggingPrintStream loggingPrintStream =
                new LoggingPrintStream(INSTANCE, "STDTEST", logger, slf4jLevel, 30, TimeUnit.SECONDS);
        try {
            final ConsoleAppender<ILoggingEvent> consoleAppender = new ConsoleAppender<>();
            final PatternLayoutEncoder patternLayoutEncoder = new PatternLayoutEncoder();

            patternLayoutEncoder.setPattern("%5p %c - %X{MDC_KEY1} %X{MDC_KEY2} - %m%n");
            patternLayoutEncoder.setContext(logger.getLoggerContext());
            patternLayoutEncoder.start();

            consoleAppender.setEncoder(patternLayoutEncoder);
            consoleAppender.setContext(logger.getLoggerContext());
            consoleAppender.start();
            consoleAppender.setOutputStream(baos);

            logger.addAppender(consoleAppender);
            logger.setLevel(Level.INFO);
            logger.setAdditive(false);

            MDC.put("MDC_KEY1", "MDC_VALUE1");
            MDC.put("MDC_KEY2", "MDC_VALUE2");

            loggingPrintStream.println("MDC_TEST_MESSAGE");
            loggingPrintStream.close();
            logger.detachAndStopAllAppenders();

        } finally {
            final String logMessage = baos.toString();

            assertEquals(" INFO emissary.util.io.LoggingPrintStreamTest.testMdcContextMap - MDC_VALUE1 MDC_VALUE2 - STDTEST : MDC_TEST_MESSAGE\n",
                    logMessage);
        }
    }

    @Test
    void testOutputStreamArgumentOne() {
        final org.slf4j.event.Level slf4jLevel = org.slf4j.event.Level.INFO;

        LogbackTester logbackTester = new LogbackTester(LoggingPrintStreamTest.class.getName());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        LoggingPrintStream loggingPrintStream =
                new LoggingPrintStream(baos, logbackTester.name, logbackTester.logger, slf4jLevel, 30, TimeUnit.SECONDS);
        try {
            loggingPrintStream.println(LOG_MSG_1);
            loggingPrintStream.close();
        } finally {
            final String streamString = new String(baos.toByteArray(), StandardCharsets.UTF_8);

            assertEquals(LOG_MSG_1 + "\n", streamString, "OutputStream did not contain expected data");
            logbackTester.checkLogList(new Level[] {Level.INFO}, new String[] {logbackTester.name + NORMAL_SEPARATOR + LOG_MSG_1},
                    new boolean[] {false});
        }
    }

    @Test
    void testOutputStreamArgumentTwo() {
        final org.slf4j.event.Level slf4jLevel = org.slf4j.event.Level.INFO;

        LogbackTester logbackTester = new LogbackTester(LoggingPrintStreamTest.class.getName());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        LoggingPrintStream loggingPrintStream =
                new LoggingPrintStream(baos, logbackTester.name, logbackTester.logger, slf4jLevel, 30, TimeUnit.SECONDS);
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        try {
            EXCEPTION_ONE.printStackTrace(loggingPrintStream);
            EXCEPTION_ONE.printStackTrace(printWriter);
            loggingPrintStream.close();
        } finally {
            final String streamString = new String(baos.toByteArray(), StandardCharsets.UTF_8);
            final String expectedString = stringWriter.toString();

            assertEquals(expectedString, streamString, "OutputStream did not contain expected data");
            logbackTester.checkLogList(new Level[] {Level.INFO}, new String[] {THROWABLE_PREFIX + logbackTester.name}, new boolean[] {true});
        }
    }

    @Test
    void testSingleThread() {
        final org.slf4j.event.Level slf4jLevel = org.slf4j.event.Level.INFO;

        LogbackTester logbackTester = new LogbackTester(LoggingPrintStreamTest.class.getName());
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(byteArrayOutputStream);
        LoggingPrintStream loggingPrintStream =
                new LoggingPrintStream(printStream, logbackTester.name, logbackTester.logger, slf4jLevel, 30, TimeUnit.SECONDS);
        try {
            EXCEPTION_ONE.printStackTrace(loggingPrintStream);
            loggingPrintStream.println(LOG_MSG_1);
            EXCEPTION_TWO.printStackTrace(loggingPrintStream);
            loggingPrintStream.println(LOG_OBJ_1);
            loggingPrintStream.println(LOG_MSG_2);
            loggingPrintStream.close();
        } finally {
            final Level[] expectedLevels = {Level.INFO, Level.INFO, Level.INFO, Level.INFO, Level.INFO};
            final String[] expectedMessages =
                    {THROWABLE_PREFIX + logbackTester.name,
                            logbackTester.name + NORMAL_SEPARATOR + LOG_MSG_1,
                            THROWABLE_PREFIX + logbackTester.name,
                            logbackTester.name + NORMAL_SEPARATOR + LOG_OBJ_1,
                            logbackTester.name + NORMAL_SEPARATOR + LOG_MSG_2};
            final boolean[] expectedThrowables = {true, false, true, false, false};

            logbackTester.checkLogList(expectedLevels, expectedMessages, expectedThrowables);
        }
    }

    @Test
    void testMultiThread() throws Exception {
        final org.slf4j.event.Level slf4jLevel = org.slf4j.event.Level.INFO;

        LogbackTester logbackTester = new LogbackTester(LoggingPrintStreamTest.class.getName());
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(byteArrayOutputStream);
        LoggingPrintStream loggingPrintStream =
                new LoggingPrintStream(printStream, logbackTester.name, logbackTester.logger, slf4jLevel, 30, TimeUnit.SECONDS);
        final int iterations = 25;
        final int numberOfThreads = 5;

        final ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);

        for (int i = 0; i < numberOfThreads; i++) {
            final String message = "Message from instance " + i;
            final Exception exception = new Exception("Exception from instance " + i);

            executorService.submit(() -> {
                for (int j = 0; j < iterations; j++) {
                    loggingPrintStream.println(message);
                    exception.printStackTrace(loggingPrintStream);
                }
            });
        }
        try {
            executorService.shutdown();
            executorService.awaitTermination(10, TimeUnit.SECONDS);

            loggingPrintStream.close();
        } finally {
            assertEquals(iterations * numberOfThreads * 2, logbackTester.appender.list.size(), "Wrong number of log messages!");
        }
    }

    public static class LogbackTester implements Closeable {
        private static final AtomicInteger INSTANCE = new AtomicInteger(0);

        public final String name;
        public final Logger logger;
        public final ListAppender<ILoggingEvent> appender;

        public LogbackTester(final String prefix) {
            Validate.notNull(prefix, "Required: prefix != null");

            name = prefix + "_" + INSTANCE.incrementAndGet();
            logger = (Logger) LoggerFactory.getLogger(name);
            appender = new ListAppender<>();

            appender.setContext(logger.getLoggerContext());
            appender.start();
            logger.addAppender(appender);
            logger.setAdditive(false);
        }

        public void checkLogList(final Level[] levels, final String[] messages, final boolean[] throwables) {
            Validate.notNull(levels, "Required: levels != null");
            Validate.notNull(messages, "Required: messages != null");
            Validate.notNull(throwables, "Required: throwables != null");
            Validate.isTrue(levels.length == messages.length, "Required: levels.length == messages.length");
            Validate.isTrue(levels.length == throwables.length, "Required: levels.length == throwables.length");

            assertEquals(levels.length, appender.list.size(), "Expected lengths do not match number of log messages");

            for (int i = 0; i < appender.list.size(); i++) {
                final ILoggingEvent item = appender.list.get(i);

                assertEquals(levels[i], item.getLevel(), "Levels not equal for element " + i);
                assertEquals(messages[i], item.getFormattedMessage(), "Messages not equal for element " + i);
                assertEquals(throwables[i], item.getThrowableProxy() != null, "Throwables not equal for element " + i);
            }
        }

        @Override
        public void close() {
            logger.detachAndStopAllAppenders();
        }
    }
}
