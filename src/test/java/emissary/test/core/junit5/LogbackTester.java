package emissary.test.core.junit5;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.apache.commons.lang3.Validate;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LogbackTester implements Closeable {
    public final String name;
    public final Logger logger;
    public final ListAppender<ILoggingEvent> appender;

    public LogbackTester(final String name) {
        Validate.notNull(name, "Required: name != null");

        this.name = name;
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
            assertEquals(throwables[i], item.getThrowableProxy() != null, "Throwables not equal for elmeent " + i);
        }
    }

    @Override
    public void close() throws IOException {
        logger.detachAndStopAllAppenders();
    }
}
