package emissary.test.core.junit5;

import emissary.core.IBaseDataObjectXmlCodecs;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.Validate;
import org.jdom2.Element;
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


        /**
         * The XML Element name for the log events.
         */
        public static final String LOG_NAME = "log";
        /**
         * The XML Element name for the SimplifiedLogEvent level attribute.
         */
        public static final String LEVEL_NAME = "level";
        /**
         * the XML Element name for the SimplifiedLogEvent message attribute.
         */
        public static final String MESSAGE_NAME = "message";
        /**
         * The XML Element name for the SimplifiedLogEvent throwableClassName attribute.
         */
        public static final String THROWABLE_CLASS_NAME = "throwableClassName";
        /**
         * The XML Element name for the SimplifiedLogEvent throwableMessage attribute.
         */
        public static final String THROWABLE_MESSAGE_NAME = "throwableMessage";

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

        /**
         * This method returns any log events from the given XML element.
         *
         * @param answersElement the "answers" XML element that should contain any log events.
         * @return the list of log events.
         */
        public static List<SimplifiedLogEvent> fromXml(final Element answersElement) {
            final List<SimplifiedLogEvent> simplifiedLogEvents = new ArrayList<>();

            final List<Element> answerChildren = answersElement.getChildren();

            for (final Element answerChild : answerChildren) {
                final String childName = answerChild.getName();

                if (childName.equals(LOG_NAME)) {
                    final Level level = Level.valueOf(answerChild.getChild(LEVEL_NAME).getValue());
                    final String message = answerChild.getChild(MESSAGE_NAME).getValue();
                    final Element throwableClassNameElement = answerChild.getChild(THROWABLE_CLASS_NAME);
                    final Element throwableMessageElement = answerChild.getChild(THROWABLE_MESSAGE_NAME);
                    final String throwableClassName = throwableClassNameElement == null ? null : throwableClassNameElement.getValue();
                    final String throwableMessage = throwableMessageElement == null ? null : throwableMessageElement.getValue();

                    simplifiedLogEvents.add(new SimplifiedLogEvent(level, message, throwableClassName, throwableMessage));
                }
            }

            return simplifiedLogEvents;
        }

        public static List<Element> toXml(final List<SimplifiedLogEvent> logEvents) {
            final List<Element> logElements = new ArrayList<>();

            for (SimplifiedLogEvent e : logEvents) {
                final Element logElement = new Element(LOG_NAME);

                logElement.addContent(IBaseDataObjectXmlCodecs.preserve(IBaseDataObjectXmlCodecs.protectedElement(LEVEL_NAME, e.level.toString())));
                logElement.addContent(IBaseDataObjectXmlCodecs.preserve(IBaseDataObjectXmlCodecs.protectedElement(MESSAGE_NAME, e.message)));
                if (e.throwableClassName != null) {
                    logElement.addContent(
                            IBaseDataObjectXmlCodecs.preserve(IBaseDataObjectXmlCodecs.protectedElement(THROWABLE_CLASS_NAME, e.throwableClassName)));
                }
                if (e.throwableMessage != null) {
                    logElement.addContent(
                            IBaseDataObjectXmlCodecs.preserve(IBaseDataObjectXmlCodecs.protectedElement(THROWABLE_MESSAGE_NAME, e.throwableMessage)));
                }
                logElements.add(logElement);
            }

            return logElements;
        }

    }
}
