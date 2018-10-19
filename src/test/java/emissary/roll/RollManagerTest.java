package emissary.roll;

import java.io.IOException;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import emissary.test.core.UnitTest;
import emissary.util.EmissaryIsolatedClassloaderRunner;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(EmissaryIsolatedClassloaderRunner.class)
public class RollManagerTest extends UnitTest {

    @Test
    public void testAddRoller() {
        RollManager rm = new RollManager();
        Roller r = new Roller(TimeUnit.SECONDS, 1, new RollableTest());
        rm.addRoller(r);
        Assert.assertTrue("Roller successfully registered", rm.rollers.contains(r));
        rm.exec.shutdown();
    }

    @Test
    public void testObserve() {
        RollManager rm = new RollManager();
        Roller r = new Roller(1, TimeUnit.DAYS, 1, new RollableTest());
        RollTestObserver o = new RollTestObserver();
        r.addObserver(o);
        rm.addRoller(r);
        r.incrementProgress();
        Assert.assertNotNull("Roller notified", o.o);
        rm.exec.shutdown();
    }

    @Test
    public void testAutoConfig() {
        RollManager rm = RollManager.getManager();
        Assert.assertEquals("One test Roller configured", 1, rm.rollers.size());
        Roller r = rm.rollers.iterator().next();
        Assert.assertEquals(TimeUnit.MINUTES, r.getTimeUnit());
        Assert.assertEquals(10L, r.getPeriod());
        Assert.assertEquals(100L, r.getMax());
        Assert.assertEquals(RollableTest.class, r.getRollable().getClass());
        RollManager.shutdown();
    }

    @Test
    public void testFailedoller() throws Exception {
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
            public void close() throws IOException {
                // noop
            }
        });
        rm.addRoller(r);
        latch.await();
        // should have been unscheduled
        Assert.assertFalse(rm.exec.getQueue().contains(r));
    }


    class RollTestObserver implements Observer {
        Observable o;
        Object arg;

        @Override
        public void update(Observable o, Object arg) {
            this.o = o;
            this.arg = arg;
        }

    }

}
