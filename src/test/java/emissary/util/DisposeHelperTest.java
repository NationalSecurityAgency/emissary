package emissary.util;

import emissary.core.BaseDataObject;
import emissary.core.EmissaryRuntimeException;
import emissary.core.IBaseDataObject;
import emissary.test.core.junit5.UnitTest;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisposeHelperTest extends UnitTest {

    private static final Runnable FIRST = () -> LoggerFactory.getLogger("DisposeHelperRunnable").warn("DisposeHelperTestFirstRunnable");
    private static final Runnable SECOND = () -> LoggerFactory.getLogger("DisposeHelperRunnable").warn("DisposeHelperTestSecondRunnable");
    private static final Runnable THIRD = () -> LoggerFactory.getLogger("DisposeHelperRunnable").warn("DisposeHelperTestThirdRunnable");

    private static final Runnable THROWS = () -> {
        throw new EmissaryRuntimeException("DisposeHelperTest");
    };

    @Nullable
    private ListAppender<ILoggingEvent> appender = null;
    private final Logger logger = (Logger) LoggerFactory.getLogger(DisposeHelper.class);
    private final Logger rLogger = (Logger) LoggerFactory.getLogger("DisposeHelperRunnable");

    private IBaseDataObject bdo;
    private static final String TEST_BDO_NAME = "DisposeHelperTestBdo";

    @BeforeEach
    void setup() {
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        rLogger.addAppender(appender);
        bdo = new BaseDataObject(new byte[0], TEST_BDO_NAME);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
        rLogger.detachAppender(appender);
    }

    @Test
    void testInvalidRunnable() {
        final List<Object> objs = new ArrayList<>();
        objs.add(FIRST);
        objs.add("InvalidRunnable");
        bdo.setParameter(DisposeHelper.KEY, objs);

        DisposeHelper.execute(bdo);
        assertTrue(appender.list.stream()
                .anyMatch(i -> i.getLevel().equals(Level.WARN) &&
                        i.getFormattedMessage().contains("Not a valid Runnable on object DisposeHelperTestBdo")));
    }

    @Test
    void testExecuteDisposable() {
        DisposeHelper.set(bdo, FIRST);
        DisposeHelper.execute(bdo);
        assertTrue(appender.list.stream()
                .anyMatch(i -> i.getFormattedMessage().contains("DisposeHelperTestFirstRunnable")));
    }

    @Test
    void testThrowsDisposable() {
        DisposeHelper.set(bdo, THROWS);
        DisposeHelper.execute(bdo);
        assertTrue(appender.list.stream()
                .anyMatch(i -> i.getLevel().equals(Level.WARN) &&
                        i.getFormattedMessage().contains("Exception while executing Runnable for " + TEST_BDO_NAME)));
    }

    @Test
    void testSetNullDisposable() {
        assertThrows(NullPointerException.class, () -> DisposeHelper.set(bdo, null));
    }

    @Test
    void testSetDisposable() {
        assertEquals(0, DisposeHelper.get(bdo).size());
        DisposeHelper.set(bdo, FIRST);
        assertEquals(1, DisposeHelper.get(bdo).size());
        assertEquals(FIRST, DisposeHelper.get(bdo).get(0));
        DisposeHelper.set(bdo, SECOND);
        assertEquals(1, DisposeHelper.get(bdo).size());
        assertEquals(SECOND, DisposeHelper.get(bdo).get(0));
    }

    @Test
    void testAddDisposable() {
        DisposeHelper.add(bdo, FIRST);
        List<Runnable> lr = DisposeHelper.get(bdo);
        assertEquals(1, lr.size());
        assertEquals(FIRST, lr.get(0));

        DisposeHelper.add(bdo, SECOND);
        lr = DisposeHelper.get(bdo);
        assertEquals(2, lr.size());
        assertEquals(SECOND, lr.get(1));
    }

    @Test
    void testAddListOfDisposable() {
        final List<Runnable> runnableList = new ArrayList<>();
        runnableList.add(FIRST);
        runnableList.add(SECOND);
        DisposeHelper.add(bdo, runnableList);
        final List<Runnable> lr = DisposeHelper.get(bdo);
        assertEquals(2, lr.size());
        assertEquals(FIRST, lr.get(0));
        assertEquals(SECOND, lr.get(1));
    }

    @Test
    void testAddToListOfDisposable() {
        final List<Runnable> runnableList = new ArrayList<>();
        runnableList.add(FIRST);
        runnableList.add(SECOND);
        DisposeHelper.add(bdo, runnableList);
        List<Runnable> lr = DisposeHelper.get(bdo);
        assertEquals(2, lr.size());
        assertEquals(FIRST, lr.get(0));
        assertEquals(SECOND, lr.get(1));

        DisposeHelper.add(bdo, THIRD);
        lr = DisposeHelper.get(bdo);
        assertEquals(3, DisposeHelper.get(bdo).size());
        assertEquals(FIRST, lr.get(0));
        assertEquals(SECOND, lr.get(1));
        assertEquals(THIRD, lr.get(2));
    }

    @Test
    void testAddListToListOfDisposable() {
        final List<Runnable> runnableList = new ArrayList<>();
        runnableList.add(FIRST);
        runnableList.add(SECOND);
        DisposeHelper.add(bdo, runnableList);
        List<Runnable> lr = DisposeHelper.get(bdo);
        assertEquals(2, lr.size());
        assertEquals(FIRST, lr.get(0));
        assertEquals(SECOND, lr.get(1));

        DisposeHelper.add(bdo, Arrays.asList(THIRD));
        lr = DisposeHelper.get(bdo);
        assertEquals(3, lr.size());
        assertEquals(FIRST, lr.get(0));
        assertEquals(SECOND, lr.get(1));
        assertEquals(THIRD, lr.get(2));
    }
}
