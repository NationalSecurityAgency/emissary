package emissary.util.io;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.slf4j.event.Level;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * This class is designed to be put in place of StdOut and StdErr in order to log anything that is sent there.
 */
public class LoggingPrintStream extends PrintStream {
    /**
     * The prefix used for "throwable" messages.
     */
    public static final String THROWABLE_PREFIX = "Exception on ";
    /**
     * The string used to separate the stream name from the message for "normal" messages.
     */
    public static final String NORMAL_SEPARATOR = " : ";
    /**
     * The log string used to identify substitutions.
     */
    public static final String LOG_SUBSTITUTION = "{}";
    /**
     * The log format used for "throwable" messages.
     */
    public static final String THROWABLE_LOG_FORMAT = THROWABLE_PREFIX + LOG_SUBSTITUTION;
    /**
     * The log format used for "normal" messages.
     */
    public static final String NORMAL_LOG_FORMAT = LOG_SUBSTITUTION + NORMAL_SEPARATOR + LOG_SUBSTITUTION;
    /**
     * The name of the stream to be added to the log message.
     */
    private final String streamName;
    /**
     * The logger to be used for logging messages.
     */
    private final Logger logger;
    /**
     * The logging level to be used when logging messages.
     */
    private final Level level;
    /**
     * The time to wait for logging tasks to finish when closing.
     */
    private final long closeWaitTime;
    /**
     * The time unit to wait for logging tasks to finish when closing.
     */
    private final TimeUnit closeWaitTimeUnit;
    /**
     * Executor for executing logging statements.
     */
    private final ExecutorService executorService;
    /**
     * The number of lines to skip when a Throwable is received.
     */
    private int linesToSkip;

    /**
     * Constructs a custom PrintStream that logs anything it receives.
     * 
     * @param outputStream where the received data is to be written.
     * @param streamName the name of the outputStream to be included in the log messages.
     * @param logger that is to be used to log the messages.
     * @param level the logging level to use for log statements.
     * @param closeWaitTime the time to wait for logging tasks to finish when closing.
     * @param closeWaitTimeUnit the time unit to use for the closeWaitTime.
     */
    public LoggingPrintStream(final OutputStream outputStream, final String streamName, final Logger logger, final Level level,
            final long closeWaitTime, final TimeUnit closeWaitTimeUnit) {
        super(outputStream);

        Validate.notNull(streamName, "Required: streamName not null");
        Validate.notNull(logger, "Required: logger not null");
        Validate.notNull(level, "Required: level not null");
        Validate.isTrue(closeWaitTime >= 0, "Required: closeWaitTime >= 0");
        Validate.notNull(closeWaitTimeUnit, "Required: closeWaitTimeUnit not null");

        this.streamName = streamName;
        this.logger = logger;
        this.level = level;
        this.closeWaitTime = closeWaitTime;
        this.closeWaitTimeUnit = closeWaitTimeUnit;
        this.executorService = Executors.newSingleThreadExecutor(
                runnable -> new Thread(runnable, "LoggingPrintStream_" + streamName));

    }

    /**
     * Prints a string. If the argument is {@code null} then the string {@code "null"} is printed. Otherwise, the string's
     * characters are converted into bytes according to the character encoding given to the constructor, or the platform's
     * default character encoding if none specified. These bytes are written in exactly the manner of the
     * {@link #write(int)} method.
     *
     * @param string The {@code String} to be printed
     */
    @Override
    public void print(final String string) {
        if (linesToSkip == 0) {
            final Map<String, String> mdcContextMap = MDC.getCopyOfContextMap();

            executorService.submit(() -> log(logger, mdcContextMap, level, NORMAL_LOG_FORMAT, streamName, string));
        }

        super.print(string);
    }

    /**
     * Prints an Object and then terminate the line. This method calls at first String.valueOf(x) to get the printed
     * object's string value, then behaves as though it invokes {@link #print(String)} and then {@link #println()}.
     *
     * @param object The {@code Object} to be printed.
     */
    @Override
    public void println(final Object object) {
        if (object instanceof Throwable) {
            final Throwable throwable = (Throwable) object;

            try (StringWriter stringWriter = new StringWriter();
                    PrintWriter printWriter = new PrintWriter(stringWriter)) {
                throwable.printStackTrace(printWriter);

                final String throwableString = stringWriter.toString();

                try (StringReader stringReader = new StringReader(throwableString);
                        LineNumberReader lineNumberReader = new LineNumberReader(stringReader)) {
                    lineNumberReader.skip(Long.MAX_VALUE);
                    linesToSkip = lineNumberReader.getLineNumber();
                }
            } catch (IOException e) {
                logger.error("This should never happen as the Readers/Writers are backed by Strings.");
            }

            final Map<String, String> mdcContextMap = MDC.getCopyOfContextMap();

            executorService.submit(() -> log(logger, mdcContextMap, level, THROWABLE_LOG_FORMAT, streamName, throwable));
        }

        super.println(object);

        if (linesToSkip > 0) {
            linesToSkip--;
        }
    }

    /**
     * Closes the stream. This is done by flushing the stream and then closing the underlying output stream.
     *
     * @see java.io.OutputStream#close()
     */
    @Override
    public void close() {
        try {
            if (!executorService.isShutdown()) {
                executorService.shutdown();
                executorService.awaitTermination(closeWaitTime, closeWaitTimeUnit);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        super.close();
    }

    /**
     * Performs the actual logging of the message.
     * 
     * @param logger to use for logging the message.
     * @param mdcContextMap to use for logging the message.
     * @param level to use for logging the message.
     * @param string the message.
     * @param streamName the name of the stream.
     * @param stringOrException the string or exception to log.
     */
    private static void log(final Logger logger, final Map<String, String> mdcContextMap, final Level level, final String string,
            final String streamName, final Object stringOrException) {
        if (mdcContextMap != null) {
            MDC.setContextMap(mdcContextMap);
        }

        switch (level) {
            case DEBUG:
                logger.debug(string, streamName, stringOrException);
                break;
            case ERROR:
                logger.error(string, streamName, stringOrException);
                break;
            case INFO:
                logger.info(string, streamName, stringOrException);
                break;
            case TRACE:
                logger.trace(string, streamName, stringOrException);
                break;
            case WARN:
                logger.warn(string, streamName, stringOrException);
                break;
        }

        MDC.clear();
    }
}
