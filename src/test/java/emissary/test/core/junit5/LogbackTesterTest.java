package emissary.test.core.junit5;

import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LogbackTesterTest extends UnitTest {
    protected Logger logger = LoggerFactory.getLogger("TESTY");

    @Test
    void canCreateTester() {
        LogbackTester logBackTester = new LogbackTester(logger.getName());
        assertNotNull(logBackTester);
    }

    @Test
    void canCheckSingleEvent() {
        LogbackTester logBackTester = new LogbackTester(logger.getName());
        assertNotNull(logBackTester);
        List<LogbackTester.SimplifiedLogEvent> events = new ArrayList<>();
        LogbackTester.SimplifiedLogEvent event2 = new LogbackTester.SimplifiedLogEvent(Level.WARN, "Not good news.", new Exception("worse news"));
        events.add(event2);
        logger.warn("Not good news.", new Exception("worse news"));
        assertDoesNotThrow(() -> logBackTester.checkLogList(events));
    }

    @Test
    void canDetectWrongMessage() {
        LogbackTester logBackTester = new LogbackTester(logger.getName());
        assertNotNull(logBackTester, "Expected tester to not be null.");
        List<LogbackTester.SimplifiedLogEvent> events = new ArrayList<>();
        LogbackTester.SimplifiedLogEvent event2 = new LogbackTester.SimplifiedLogEvent(Level.WARN, "Wrong Message.", new Exception("worse news"));
        events.add(event2);
        logger.warn("Not good news.", new Exception("worse news"));
        AssertionFailedError thrownException =
                assertThrows(AssertionFailedError.class, () -> logBackTester.checkLogList(events), "Expected an exception.");
        assertTrue(thrownException.getMessage().startsWith("Messages not equal"));
    }

    @Test
    void canDetectWrongLevel() {
        LogbackTester logBackTester = new LogbackTester(logger.getName());
        assertNotNull(logBackTester, "Expected tester to not be null.");
        List<LogbackTester.SimplifiedLogEvent> events = new ArrayList<>();
        LogbackTester.SimplifiedLogEvent event3 = new LogbackTester.SimplifiedLogEvent(Level.INFO, "Not good news.", new Exception("worse news"));
        events.add(event3);
        logger.warn("Not good news.", new Exception("worse news"));
        AssertionFailedError thrownException =
                assertThrows(AssertionFailedError.class, () -> logBackTester.checkLogList(events), "Expected an exception.");
        assertTrue(thrownException.getMessage().startsWith("Levels not equal"));
    }

    @Test
    void canDetectWrongThrowable() {
        LogbackTester logBackTester = new LogbackTester(logger.getName());
        assertNotNull(logBackTester, "Expected tester to not be null.");
        List<LogbackTester.SimplifiedLogEvent> events = new ArrayList<>();
        LogbackTester.SimplifiedLogEvent event4 = new LogbackTester.SimplifiedLogEvent(Level.WARN, "Not good news.", new Exception("something"));
        events.add(event4);
        logger.warn("Not good news.", new Throwable("worse news"));
        AssertionFailedError thrownException =
                assertThrows(AssertionFailedError.class, () -> logBackTester.checkLogList(events), "Expected an exception.");
        assertTrue(thrownException.getMessage().startsWith("Exception class name not"));
    }

    @Test
    void canDetectWrongThrowableMessage() {
        LogbackTester logBackTester = new LogbackTester(logger.getName());
        assertNotNull(logBackTester, "Expected tester to not be null.");
        List<LogbackTester.SimplifiedLogEvent> events = new ArrayList<>();
        LogbackTester.SimplifiedLogEvent event4 = new LogbackTester.SimplifiedLogEvent(Level.WARN, "Not good news.", new Exception("something"));
        events.add(event4);
        logger.warn("Not good news.", new Exception("worse news"));
        AssertionFailedError thrownException =
                assertThrows(AssertionFailedError.class, () -> logBackTester.checkLogList(events), "Expected an exception.");
        assertTrue(thrownException.getMessage().startsWith("Exception message not equal for"), thrownException.getMessage());
    }

    @Test
    void canCheckOneEvent() {
        LogbackTester logBackTester = new LogbackTester(logger.getName());
        assertNotNull(logBackTester);
        logBackTester.checkLogList(Collections.emptyList());
    }
}
