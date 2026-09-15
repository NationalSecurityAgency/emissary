package emissary.roll;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RollerTest extends UnitTest {
    @Test
    void testRoller() {
        final RollableTest tr = new RollableTest();
        final Roller r = new Roller(TimeUnit.DAYS, 1, tr, 1);
        r.addPropertyChangeListener(tr);
        r.incrementProgress();

        r.run();
        assertTrue(tr.wasRolled);
        assertEquals(0, r.getProgress());
        assertEquals(1, tr.getUpdateCount());
    }

    @Test
    void testShouldRoll() {
        RollableTest tr = new RollableTest();
        Roller r = new Roller(TimeUnit.MILLISECONDS, 1, tr, 1);
        r.addPropertyChangeListener(tr);
        r.incrementProgress();

        r.run();
        assertTrue(tr.wasRolled);
        assertEquals(0, r.getProgress());
        assertEquals(1, tr.getUpdateCount());

        tr = new RollableTest();
        r = new Roller(TimeUnit.HOURS, 1, tr, 100);
        r.incrementProgress();
        r.run();
        assertTrue(tr.wasRolled);
        tr.wasRolled = false;
        r.run();
        assertFalse(tr.wasRolled);
        r.incrementProgress(100);
        r.run();
        assertTrue(tr.wasRolled);
    }
}
