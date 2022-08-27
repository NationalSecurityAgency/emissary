package emissary.roll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;

import emissary.test.core.junit5.UnitTest;
import org.junit.jupiter.api.Test;

class RollerTest extends UnitTest {
    @Test
    void testRoller() {
        final RollableTest tr = new RollableTest();
        final Roller r = new Roller(1, TimeUnit.DAYS, 1, tr);
        r.addObserver(tr);
        r.incrementProgress();

        r.run();
        assertTrue(tr.wasRolled);
        assertEquals(0, r.getProgress());
        assertEquals(1, tr.getUpdateCount());
    }

    @Test
    void testShouldRoll() {
        RollableTest tr = new RollableTest();
        Roller r = new Roller(1, TimeUnit.MILLISECONDS, 1, tr);
        r.addObserver(tr);
        r.incrementProgress();

        r.run();
        assertTrue(tr.wasRolled);
        assertEquals(0, r.getProgress());
        assertEquals(1, tr.getUpdateCount());

        tr = new RollableTest();
        r = new Roller(100, TimeUnit.HOURS, 1, tr);
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
