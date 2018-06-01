package emissary.test.core;

import static ch.qos.logback.classic.Level.ALL;
import static org.slf4j.Logger.ROOT_LOGGER_NAME;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.util.ContextSelectorStaticBinder;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.encoder.Encoder;

/**
 * Temporarily captures Logback output (mostly useful for tests). originally copied from
 * https://gist.githubusercontent.com/olim7t/881318/raw/873fb48919bbe75a52567bff9547a3ebdd661da2/LogbackCapture.java
 */
public class LogbackCapture {

    // hopefully matches what is defined in logback.xml
    final static String EMISSARY_LOGBACK_PATTERN =
            "%d ${emissary.node.name}-${emissary.node.port} %5p %c - %X{shortName} %X{serviceLocation} - %m%na";

    /**
     * Start capturing.
     *
     * @param loggerName if null, defaults to the root logger
     * @param level if null, defaults to all levels
     * @param layoutPattern if null, defaults to "[%p] %m%n"
     */
    public static void start(String loggerName, Level level, String layoutPattern) {
        if (INSTANCE.get() != null)
            throw new IllegalStateException("already started");
        INSTANCE.set(new LogbackCapture(loggerName, level, layoutPattern));
    }

    public static void start(Class<?> clazz) {
        start(clazz.getCanonicalName(), null, EMISSARY_LOGBACK_PATTERN);
    }

    public static void start(Class<?> clazz, Level level) {
        start(clazz.getCanonicalName(), level, EMISSARY_LOGBACK_PATTERN);
    }

    /** Stop capturing and return the logs. */
    public static String stop() {
        LogbackCapture instance = INSTANCE.get();
        if (instance == null)
            throw new IllegalStateException("was not running");
        final String result = instance.stopInstance();
        INSTANCE.remove();
        return result;
    }

    private static final ThreadLocal<LogbackCapture> INSTANCE = new ThreadLocal<LogbackCapture>();

    private final Logger logger;
    private final OutputStreamAppender<ILoggingEvent> appender;
    private final Encoder<ILoggingEvent> encoder;
    private final ByteArrayOutputStream logs;

    private LogbackCapture(String loggerName, Level level, String layoutPattern) {
        logs = new ByteArrayOutputStream(4096);
        encoder = buildEncoder(layoutPattern);
        appender = buildAppender(encoder, logs);
        logger = getLogbackLogger(loggerName, level);
        logger.addAppender(appender);
    }

    private String stopInstance() {
        appender.stop();
        try {
            return logs.toString("UTF-16");
        } catch (final UnsupportedEncodingException cantHappen) {
            return null;
        }
    }

    private static Logger getLogbackLogger(String name, Level level) {
        if (name == null || name.isEmpty())
            name = ROOT_LOGGER_NAME;
        if (level == null)
            level = ALL;

        Logger logger = ContextSelectorStaticBinder.getSingleton().getContextSelector().getDefaultLoggerContext().getLogger(name);
        logger.setLevel(level);
        return logger;
    }

    private static Encoder<ILoggingEvent> buildEncoder(String layoutPattern) {
        if (layoutPattern == null)
            layoutPattern = "[%p] %m%n";
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setPattern(layoutPattern);
        encoder.setCharset(Charset.forName("UTF-16"));
        encoder.setContext(ContextSelectorStaticBinder.getSingleton().getContextSelector().getDefaultLoggerContext());
        encoder.start();
        return encoder;
    }

    private static OutputStreamAppender<ILoggingEvent> buildAppender(final Encoder<ILoggingEvent> encoder, final OutputStream outputStream) {
        OutputStreamAppender<ILoggingEvent> appender = new OutputStreamAppender<ILoggingEvent>();
        appender.setName("logcapture");
        appender.setContext(ContextSelectorStaticBinder.getSingleton().getContextSelector().getDefaultLoggerContext());
        appender.setEncoder(encoder);
        appender.setOutputStream(outputStream);
        appender.start();
        return appender;
    }
}
