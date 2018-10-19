package emissary.roll;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import emissary.test.core.UnitTest;
import org.junit.Test;

/**
 *
 */
public class RollerTest extends UnitTest {
    @Test
    public void testRoller() throws InterruptedException {
        final RollableTest tr = new RollableTest();
        final Roller r = new Roller(1, TimeUnit.DAYS, 1, tr);
        r.addObserver(tr);
        r.incrementProgress();

        r.run();
        assertTrue(tr.wasRolled);
        assertTrue(r.getProgress() == 0);
        assertTrue(tr.getUpdateCount() == 1);
    }

    @Test
    public void testShouldRoll() {
        RollableTest tr = new RollableTest();
        Roller r = new Roller(1, TimeUnit.MILLISECONDS, 1, tr);
        r.addObserver(tr);
        r.incrementProgress();

        r.run();
        assertTrue(tr.wasRolled);
        assertTrue(r.getProgress() == 0);
        assertTrue(tr.getUpdateCount() == 1);

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
