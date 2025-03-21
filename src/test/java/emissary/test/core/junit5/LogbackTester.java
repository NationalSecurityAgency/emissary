package emissary.test.core.junit5;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.Validate;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
            if (event.throwableClassName == null) {
                assertNull(item.getThrowableProxy(), "Expected no exception for element " + i);
            } else {
                assertNotNull(item.getThrowableProxy(), "Expected an exception for element " + i);
                IThrowableProxy proxy = item.getThrowableProxy();
                assertEquals(event.throwableClassName, proxy.getClassName(), "Exception class name not equal for element " + i);
                assertEquals(event.throwableMessage, proxy.getMessage(), "Exception message not equal for element " + i);
            }
        }
    }

    public List<SimplifiedLogEvent> getSimplifiedLogEvents() {
        final List<SimplifiedLogEvent> simplifiedLogEvents = new ArrayList<>();

        for (int i = 0; i < appender.list.size(); i++) {
            final ILoggingEvent event = appender.list.get(i);

            if (event.getThrowableProxy() == null) {
                simplifiedLogEvents.add(new SimplifiedLogEvent(event.getLevel(), event.getFormattedMessage(),
                        null, null));
            } else {
                simplifiedLogEvents.add(new SimplifiedLogEvent(event.getLevel(), event.getFormattedMessage(),
                        event.getThrowableProxy().getClassName(), event.getThrowableProxy().getMessage()));
            }
        }

        return simplifiedLogEvents;
    }

    @Override
    public void close() throws IOException {
        logger.detachAndStopAllAppenders();
    }

    public static class SimplifiedLogEvent {
        public final Level level;
        public final String message;
        @Nullable
        public final String throwableClassName;
        @Nullable
        public final String throwableMessage;

        public SimplifiedLogEvent(Level level, String message, @Nullable Throwable throwable) {
            this(level, message,
                    throwable == null ? null : throwable.getClass().getName(),
                    throwable == null ? null : throwable.getLocalizedMessage());
        }

        public SimplifiedLogEvent(Level level, String message, @Nullable String throwableClassName, @Nullable String throwableMessage) {
            Validate.notNull(level, "Required: level != null!");
            Validate.notNull(message, "Required: message != null!");

            this.level = level;
            this.message = message;
            this.throwableClassName = throwableClassName;
            this.throwableMessage = throwableMessage;
        }

        @Override
        public int hashCode() {
            return Objects.hash(level, message, throwableClassName, throwableMessage);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }

            if (!(obj instanceof SimplifiedLogEvent)) {
                return false;
            }
            SimplifiedLogEvent other = (SimplifiedLogEvent) obj;

            return Objects.equals(level, other.level) &&
                    Objects.equals(message, other.message) &&
                    Objects.equals(throwableClassName, other.throwableClassName) &&
                    Objects.equals(throwableMessage, other.throwableMessage);
        }

        @Override
        public String toString() {
            return super.toString() + " [level=" + level + ", message=" + message + ", throwableClassName="
                    + throwableClassName + ", throwableMessage=" + throwableMessage + "]";
        }
    }
}
