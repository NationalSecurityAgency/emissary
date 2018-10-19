package emissary.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import emissary.directory.DirectoryEntry;
import emissary.place.IServiceProviderPlace;
import emissary.place.sample.DevNullPlace;
import emissary.test.core.UnitTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ResourceWatcherTest extends UnitTest {

    public ResourceWatcher resourceWatcher = null;
    public IServiceProviderPlace place = null;

    @Override
    @Before
    public void setUp() throws Exception {}

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testResourceWatcherWithMultipleThreads() throws Exception {
        this.resourceWatcher = new ResourceWatcher();
        this.place = new DevNullPlace();
        int threadCount = 10;
        int iterations = 100;

        CountDownLatch cdl = new CountDownLatch(threadCount);
        ThreadGroup tg = new ThreadGroup("ResourceWatcherTest");
        final Thread[] pokers = new Thread[threadCount];

        // Start up threads to poke resources into the watcher
        for (int i = 0; i < threadCount; i++) {
            ResourceConsumer rc = new ResourceConsumer(iterations, 100, cdl);
            pokers[i] = new Thread(tg, rc);
        }

        for (Thread poker : pokers) {
            poker.start();
        }
        // Wait for them to be done
        cdl.await();

        // Add them up
        final Map<String, com.codahale.metrics.Timer> stats = this.resourceWatcher.getStats();
        assertTrue("Stats were not collected", stats.size() > 0);

        final com.codahale.metrics.Timer s = stats.get("DevNullPlace");
        assertNotNull("Events must be measured", s);

        assertEquals("Events must not be lost", (long) (threadCount * iterations), s.getCount());

        this.resourceWatcher.resetStats();
        assertEquals("Stats must be cleared", 0, this.resourceWatcher.getStats().size());

        this.resourceWatcher.quit();
    }

    // I was not able to get this to work by extending the current agent implementations
    // due to an uspecified issue where the thread is started during object construction
    // given the refactor forces us to operate on MobileAgent object, this was a necessity
    public final class ResourceConsumer implements IMobileAgent {
        private static final long serialVersionUID = 1L;
        final int times;
        final int duration;
        final Random rand = new Random();
        final CountDownLatch cdl;

        public ResourceConsumer(final int times, final int duration, CountDownLatch cdl) {

            this.times = times;
            this.duration = duration;
            this.cdl = cdl;
        }

        @Override
        public void run() {
            try {
                for (int i = 0; i < this.times; i++) {
                    try (TimedResource tr = ResourceWatcherTest.this.resourceWatcher.starting(this, ResourceWatcherTest.this.place)) {
                        Thread.sleep(rand.nextInt(100));
                    } catch (InterruptedException ex) {
                        // empty catch block
                    }
                }
            } finally {
                cdl.countDown();
            }
        }

        @Override
        public String agentID() {
            return "";
        }

        @Override
        public IBaseDataObject getPayload() {
            return null;
        }

        @Override
        public void go(Object payload, IServiceProviderPlace sourcePlace) {

        }

        @Override
        public void arrive(Object payload, IServiceProviderPlace arrivalPlace, int mec, List<DirectoryEntry> iq) throws Exception {

        }

        @Override
        public int getMoveErrorCount() {
            return 0;
        }

        @Override
        public DirectoryEntry[] getItineraryQueueItems() {
            return null;
        }

        @Override
        public boolean isInUse() {
            return false;
        }

        @Override
        public Object getPayloadForTransport() {
            return null;
        }

        @Override
        public String getName() {
            return "";
        }

        @Override
        public String getLastPlaceProcessed() {
            return "";
        }

        @Override
        public void killAgent() {

        }

        @Override
        public void killAgentAsync() {

        }

        @Override
        public boolean isZombie() {
            return true;
        }

        @Override
        public void interrupt() {

        }

        @Override
        public int getMaxMoveErrors() {
            return 0;
        }

        @Override
        public void setMaxMoveErrors(int value) {

        }

        @Override
        public int getMaxItinerarySteps() {
            return 10;
        }

        @Override
        public void setMaxItinerarySteps(int value) {

        }
    }
}
