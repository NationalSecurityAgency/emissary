package emissary.test.core.junit5;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import org.apache.commons.lang3.Validate;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    public void checkLogList(List<SimplifiedLogEvent> events) {
        Validate.notNull(events, "Required: events != null");

        assertEquals(events.size(), appender.list.size(), "Expected event count does not match actual event count");

        for (int i = 0; i < appender.list.size(); i++) {
            final ILoggingEvent item = appender.list.get(i);
            final SimplifiedLogEvent event = events.get(i);
            assertEquals(event.level, item.getLevel(), "Levels not equal for element " + i);
            assertEquals(event.message, item.getFormattedMessage(), "Messages not equal for element " + i);
            if (event.throwable.isEmpty()) {
                assertNull(item.getThrowableProxy(), "Expected no exception for element " + i);
            } else {
                assertNotNull(item.getThrowableProxy(), "Expected an exception for element " + i);
                Throwable expected = event.throwable.get();
                IThrowableProxy proxy = item.getThrowableProxy();
                assertEquals(expected.getClass().getName(), proxy.getClassName(), "Exception class name not equal for element " + i);
                assertEquals(expected.getLocalizedMessage(), proxy.getMessage(), "Exception message not equal for element " + i);
            }

        }
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
    public void close() throws IOException {
        logger.detachAndStopAllAppenders();
    }

    public static class SimplifiedLogEvent {
        public final Level level;
        public final String message;
        public final Optional<? extends Throwable> throwable;

        public SimplifiedLogEvent(Level level, String message, @Nullable Throwable throwable) {
            this.level = level;
            this.message = message;
            this.throwable = Optional.ofNullable(throwable);
        }
    }

}
