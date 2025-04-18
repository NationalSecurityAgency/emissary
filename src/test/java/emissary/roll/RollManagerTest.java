package emissary.roll;

import emissary.config.ConfigUtil;
import emissary.core.EmissaryRuntimeException;
import emissary.test.core.junit5.UnitTest;
import emissary.test.core.junit5.extensions.EmissaryIsolatedClassLoaderExtension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RollManagerTest extends UnitTest {

    @Test
    void testAddRoller() throws IOException {
        RollManager rm = new RollManager(ConfigUtil.getConfigInfo(this.getClass()));
        Roller r = new Roller(TimeUnit.SECONDS, 1, new RollableTest());
        rm.addRoller(r);
        assertTrue(rm.rollers.contains(r), "Roller successfully registered");
        rm.exec.shutdown();
    }

    @Test
    void testObserve() throws IOException {
        RollManager rm = new RollManager(ConfigUtil.getConfigInfo(this.getClass()));
        Roller r = new Roller(1, TimeUnit.DAYS, 1, new RollableTest());
        RollTestObserver o = new RollTestObserver();
        r.addPropertyChangeListener(o);
        rm.addRoller(r);
        r.incrementProgress();
        assertNotNull(o.o, "Roller notified");
        rm.exec.shutdown();
    }

    @Test
    @ExtendWith(EmissaryIsolatedClassLoaderExtension.class)
    void testAutoConfig() throws IOException {
        RollManager rm = RollManager.getManager(ConfigUtil.getConfigInfo(this.getClass()));
        assertEquals(1, rm.rollers.size(), "One test Roller configured");
        Roller r = rm.rollers.iterator().next();
        assertEquals(TimeUnit.MINUTES, r.getTimeUnit());
        assertEquals(10L, r.getPeriod());
        assertEquals(100L, r.getMax());
        assertEquals(RollableTest.class, r.getRollable().getClass());
        RollManager.shutdown();
    }

    @Test
    void testFailedRoller() throws IOException, InterruptedException {
        RollManager rm = new RollManager(ConfigUtil.getConfigInfo(this.getClass()));
        CountDownLatch latch = new CountDownLatch(1);
        Roller r = new Roller(TimeUnit.MILLISECONDS, 250, new Rollable() {
            int i = 0;

            @Override
            public void roll() {
                if (i++ == 2) {
                    latch.countDown();
                    throw new EmissaryRuntimeException("Not supported yet.");
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

    static class RollTestObserver implements PropertyChangeListener {
        Object o;
        String prop;

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            this.prop = evt.getPropertyName();
            this.o = evt.getNewValue();
        }

    }

}
