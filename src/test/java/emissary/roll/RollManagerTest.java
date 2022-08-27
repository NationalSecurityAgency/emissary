package emissary.roll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import emissary.test.core.junit5.UnitTest;
import emissary.util.EmissaryIsolatedClassLoaderExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

class RollManagerTest extends UnitTest {

    @Test
    void testAddRoller() {
        RollManager rm = new RollManager();
        Roller r = new Roller(TimeUnit.SECONDS, 1, new RollableTest());
        rm.addRoller(r);
        assertTrue(rm.rollers.contains(r), "Roller successfully registered");
        rm.exec.shutdown();
    }

    @Test
    void testObserve() {
        RollManager rm = new RollManager();
        Roller r = new Roller(1, TimeUnit.DAYS, 1, new RollableTest());
        RollTestObserver o = new RollTestObserver();
        r.addObserver(o);
        rm.addRoller(r);
        r.incrementProgress();
        Assertions.assertNotNull(o.o, "Roller notified");
        rm.exec.shutdown();
    }

    @Test
    @ExtendWith(EmissaryIsolatedClassLoaderExtension.class)
    void testAutoConfig() {
        RollManager rm = RollManager.getManager();
        assertEquals(1, rm.rollers.size(), "One test Roller configured");
        Roller r = rm.rollers.iterator().next();
        assertEquals(TimeUnit.MINUTES, r.getTimeUnit());
        assertEquals(10L, r.getPeriod());
        assertEquals(100L, r.getMax());
        assertEquals(RollableTest.class, r.getRollable().getClass());
        RollManager.shutdown();
    }

    @Test
    void testFailedRoller() throws Exception {
        RollManager rm = new RollManager();
        CountDownLatch latch = new CountDownLatch(1);
        Roller r = new Roller(TimeUnit.MILLISECONDS, 250, new Rollable() {
            int i = 0;

            @Override
            public void roll() {
                if (i++ == 2) {
                    latch.countDown();
                    throw new RuntimeException("Not supported yet.");
                }
            }

            @Override
            public boolean isRolling() {
                return false;
            }

            @Override
            public void close() {
                // noop
            }
        });
        rm.addRoller(r);
        latch.await();
        // should have been unscheduled
        assertFalse(rm.exec.getQueue().contains(r));
    }

    static class RollTestObserver implements Observer {
        Observable o;
        Object arg;

        @Override
        public void update(Observable o, Object arg) {
            this.o = o;
            this.arg = arg;
        }

    }

}
