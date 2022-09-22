package emissary.pickup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;

import emissary.command.FeedCommand;
import emissary.test.core.junit5.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkSpaceTest extends UnitTest {
    MyWorkSpace mws;

    @Override
    @BeforeEach
    public void setUp() throws Exception {

        try {
            mws = new MyWorkSpace();
        } catch (Exception ex) {
            fail("Cannot start workspace", ex);
        }
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        mws.shutDown();
        pause(100L);
        mws = null;
    }

    @Test
    void testPriorityQueueing() {
        String C1 = "INITIAL.INPUT.A.http://otherhost:7001/FilePickUpClient";
        mws.addPickUp_(C1);

        for (int i = 0; i < 10; i++) {
            // Set up some fake work
            WorkBundle wb = new WorkBundle("/fake/root", "/fake/eat");
            wb.addFileName("faker-" + i + ".txt");
            wb.setPriority(5);
            mws.addOutboundBundle_(wb);
        }

        // Now add a higher priority item
        WorkBundle wb = new WorkBundle("/fake/root", "/fake/eat");
        wb.addFileName("emergency.txt");
        wb.setPriority(1);
        mws.addOutboundBundle_(wb);

        // See that the first thing handed out is the highest priority
        WorkBundle taken = mws.take(C1);
        assertEquals(1, taken.getPriority(), "Highest priority work must be taken first");
    }

    /**
     * Test the WorkBundle sorting of the "oldest first" sorting when the priorities are even (and hence, don't override the
     * time based ordering)
     */
    @Test
    void testOldestFirstQueueing() throws Exception {
        String[] args = {"--sort", "of", "-i", "blah"};
        mws = new MyWorkSpace(FeedCommand.parse(FeedCommand.class, args));

        String C1 = "INITIAL.INPUT.A.http://otherhost:7001/FilePickUpClient";
        mws.addPickUp_(C1);

        long[] times = {4, 5, 3, 1, 2, 7, 0, 8, 9, 10, 6};
        for (int i = 0; i < times.length; i++) {
            // Set up some fake work
            WorkBundle wb = new WorkBundle("/fake/root", "/fake/eat");
            wb.addFileName("faker-" + i + ".txt", times[i], 5L);
            wb.setPriority(1); // uniform priority to ensure times are used, not priority
            mws.addOutboundBundle_(wb);

        }

        // This should be in order of 0,1,2,3,4...10
        WorkBundle taken;
        long age = 0L;
        assertEquals(11, mws.getOutboundQueueSize(), "Outbound Queue Size");
        while ((taken = mws.take(C1)) != null) {
            if (taken.size() != 0) {
                assertEquals(age, taken.getOldestFileModificationTime(), "Oldest work must be taken first");
            } else {
                break; // size = 0 means its the last
            }
            age++;
        }
    }

    /**
     * Test the WorkBundle sorting of the "oldest first" sorting when the priorities are unequal and therefore the times are
     * ignored/defer to the priority order
     */
    @Test
    void testOldestFirstPriorityOverrideQueueing() throws Exception {
        String[] args = {"--sort", "of", "-i", "blah"};
        mws = new MyWorkSpace(FeedCommand.parse(FeedCommand.class, args));

        String C1 = "INITIAL.INPUT.A.http://otherhost:7001/FilePickUpClient";
        mws.addPickUp_(C1);

        long[] times = {4, 5, 3, 1, 2, 7, 0, 8, 9, 10, 6};
        for (int i = 0; i < times.length; i++) {
            // Set up some fake work
            WorkBundle wb = new WorkBundle("/fake/root", "/fake/eat");
            wb.addFileName("faker-" + i + ".txt", times[i], 5L);
            wb.setPriority(i);
            mws.addOutboundBundle_(wb);

        }

        // This should be in order of 0,1,2,3,4...10 _priorities_
        WorkBundle taken;
        int pri = 0;
        assertEquals(11, mws.getOutboundQueueSize(), "Outbound Queue Size");
        while ((taken = mws.take(C1)) != null) {
            if (taken.size() != 0) {
                assertEquals(pri, taken.getPriority(), "Ignoring time, prioritized work must be taken first");
            } else {
                break; // size = 0 means its the last
            }
            pri++;
        }
    }

    /**
     * Test the WorkBundle sorting of the "youngest first" sorting when the priorities are even (and hence, don't override
     * the time based ordering)
     */
    @Test
    void testYoungestFirstQueueing() throws Exception {
        String[] args = {"--sort", "yf", "-i", "blah"};
        mws = new MyWorkSpace(FeedCommand.parse(FeedCommand.class, args));

        String C1 = "INITIAL.INPUT.A.http://otherhost:7001/FilePickUpClient";
        mws.addPickUp_(C1);

        long[] times = {4, 5, 3, 1, 2, 7, 0, 8, 9, 10, 6};
        for (int i = 0; i < times.length; i++) {
            // Set up some fake work
            WorkBundle wb = new WorkBundle("/fake/root", "/fake/eat");
            wb.addFileName("faker-" + i + ".txt", times[i], 2L);
            wb.setPriority(1); // uniform priority to make sure time sorting works
            mws.addOutboundBundle_(wb);

        }

        // This should be in order of 10,9,8,....,0
        WorkBundle taken;
        long age = 10L;
        assertEquals(11, mws.getOutboundQueueSize(), "Outbound Queue Size");
        while ((taken = mws.take(C1)) != null) {
            if (taken.size() != 0) {
                assertEquals(age, taken.getYoungestFileModificationTime(), "Youngest work must be taken first");
            } else {
                break; // size = 0 means its the last
            }
            age--;
        }
    }

    /**
     * Test the WorkBundle sorting of the "youngest first" sorting when the priorities are unequal and therefore the times
     * are ignored/defer to the priority order
     */
    @Test
    void testYoungestFirstPriorityOverrideQueueing() throws Exception {
        String[] args = {"--sort", "yf", "-i", "blah"};
        mws = new MyWorkSpace(FeedCommand.parse(FeedCommand.class, args));

        String C1 = "INITIAL.INPUT.A.http://otherhost:7001/FilePickUpClient";
        mws.addPickUp_(C1);

        long[] times = {4, 5, 3, 1, 2, 7, 0, 8, 9, 10, 6};
        for (int i = 0; i < times.length; i++) {
            // Set up some fake work
            WorkBundle wb = new WorkBundle("/fake/root", "/fake/eat");
            wb.addFileName("faker-" + i + ".txt", times[i], 2L);
            wb.setPriority(i);
            mws.addOutboundBundle_(wb);

        }

        WorkBundle taken;
        int pri = 0;
        assertEquals(11, mws.getOutboundQueueSize(), "Outbound Queue Size");
        while ((taken = mws.take(C1)) != null) {
            if (taken.size() != 0) {
                assertEquals(pri, taken.getPriority(), "Ignoring time, priority work must be taken first");
            } else {
                break; // size = 0 means its the last
            }
            pri++;
        }
    }

    /**
     * Test the WorkBundle sorting of the "smallest first" sorting when the priorities are even (and hence, don't override
     * the time based ordering)
     */
    @Test
    void testSmallestFirstQueueing() throws Exception {
        String[] args = {"--sort", "sf", "-i", "blah"};
        mws = new MyWorkSpace(FeedCommand.parse(FeedCommand.class, args));

        String C1 = "INITIAL.INPUT.A.http://otherhost:7001/FilePickUpClient";
        mws.addPickUp_(C1);

        long[] sizes = {4, 5, 3, 1, 2, 7, 0, 8, 9, 10, 6};
        for (int i = 0; i < sizes.length; i++) {
            // Set up some fake work
            WorkBundle wb = new WorkBundle("/fake/root", "/fake/eat");
            wb.addFileName("faker-" + i + ".txt", 2L, sizes[i]);
            wb.setPriority(1); // uniform priority, so the time ordering is used
            mws.addOutboundBundle_(wb);

        }

        // This should be in order of 0,1,2,3.....10
        WorkBundle taken;
        long sz = 0L;
        assertEquals(11, mws.getOutboundQueueSize(), "Outbound Queue Size");
        while ((taken = mws.take(C1)) != null) {
            if (taken.size() != 0) {
                assertEquals(sz, taken.getTotalFileSize(), "Smallest bundle must be taken first");
            } else {
                break; // size = 0 means its the last
            }
            sz++;
        }
    }

    /**
     * Test the WorkBundle sorting of the "smallest first" sorting when the priorities are unequal and therefore the times
     * are ignored/defer to the priority order
     */
    @Test
    void testSmallestFirstPriorityOverrideQueueing() throws Exception {
        String[] args = {"--sort", "sf", "-i", "blah"};
        mws = new MyWorkSpace(FeedCommand.parse(FeedCommand.class, args));

        String C1 = "INITIAL.INPUT.A.http://otherhost:7001/FilePickUpClient";
        mws.addPickUp_(C1);

        long[] sizes = {4, 5, 3, 1, 2, 7, 0, 8, 9, 10, 6};
        for (int i = 0; i < sizes.length; i++) {
            // Set up some fake work
            WorkBundle wb = new WorkBundle("/fake/root", "/fake/eat");
            wb.addFileName("faker-" + i + ".txt", 2L, sizes[i]);
            wb.setPriority(i);
            mws.addOutboundBundle_(wb);

        }

        WorkBundle taken;
        int pri = 0;
        assertEquals(11, mws.getOutboundQueueSize(), "Outbound Queue Size");
        while ((taken = mws.take(C1)) != null) {
            if (taken.size() != 0) {
                assertEquals(pri, taken.getPriority(), "Ignoring size, priority work must be taken first");
            } else {
                break; // size = 0 means its the last
            }
            pri++;
        }
    }

    /**
     * Test the WorkBundle sorting of the "largest first" sorting when the priorities are even (and hence, don't override
     * the time based ordering)
     */
    @Test
    void testLargestFirstQueueing() throws Exception {
        String[] args = {"--sort", "lf", "-i", "blah"};
        mws = new MyWorkSpace(FeedCommand.parse(FeedCommand.class, args));

        String C1 = "INITIAL.INPUT.A.http://otherhost:7001/FilePickUpClient";
        mws.addPickUp_(C1);

        long[] sizes = {4, 5, 3, 1, 2, 7, 0, 8, 9, 10, 6};
        for (int i = 0; i < sizes.length; i++) {
            // Set up some fake work
            WorkBundle wb = new WorkBundle("/fake/root", "/fake/eat");
            wb.addFileName("faker-" + i + ".txt", 2L, sizes[i]);
            wb.setPriority(1); // uniform priority to sort by size
            mws.addOutboundBundle_(wb);

        }

        // This should be in order of 10,9,8,....0
        WorkBundle taken;
        long sz = 10L;
        assertEquals(11, mws.getOutboundQueueSize(), "Outbound Queue Size");
        while ((taken = mws.take(C1)) != null) {
            if (taken.size() != 0) {
                assertEquals(sz, taken.getTotalFileSize(), "Largest bundle must be taken first");
            } else {
                break; // size = 0 means its the last
            }
            sz--;
        }
    }

    /**
     * Test the WorkBundle sorting of the "largest first" sorting when the priorities are unequal and therefore the times
     * are ignored/defer to the priority order
     */
    @Test
    void testLargestFirstPriorityOverrideQueueing() throws Exception {
        String[] args = {"--sort", "lf", "-i", "blah"};
        mws = new MyWorkSpace(FeedCommand.parse(FeedCommand.class, args));

        String C1 = "INITIAL.INPUT.A.http://otherhost:7001/FilePickUpClient";
        mws.addPickUp_(C1);

        long[] sizes = {4, 5, 3, 1, 2, 7, 0, 8, 9, 10, 6};
        for (int i = 0; i < sizes.length; i++) {
            // Set up some fake work
            WorkBundle wb = new WorkBundle("/fake/root", "/fake/eat");
            wb.addFileName("faker-" + i + ".txt", 2L, sizes[i]);
            wb.setPriority(i);
            mws.addOutboundBundle_(wb);

        }

        WorkBundle taken;
        int pri = 0;
        assertEquals(11, mws.getOutboundQueueSize(), "Outbound Queue Size");
        while ((taken = mws.take(C1)) != null) {
            if (taken.size() != 0) {
                assertEquals(pri, taken.getPriority(), "Ignoring size, priority work must be taken first");
            } else {
                break; // size = 0 means its the last
            }
            pri++;
        }
    }

    @Test
    void testFailurePutsPendingBundleBackToOutboundQueue() {
        String C1 = "INITIAL.INPUT.A.http://otherhost:7001/FilePickUpClient";
        String C2 = "INITIAL.INPUT.A.http://otherhost:8001/FilePickUpClient";
        String C3 = "INITIAL.INPUT.A.http://otherhost:9001/FilePickUpClient";

        // Set up some fake clients
        mws.addPickUp_(C1);
        mws.addPickUp_(C2);
        mws.addPickUp_(C3);
        assertEquals(3, mws.getPickUpPlaceCount(), "Pickups added must count");

        // Set up some fake work
        WorkBundle wb = new WorkBundle("/fake/root", "/fake/eat");
        wb.addFileName("faker.txt");

        // Give the fake work to the space
        mws.addOutboundBundle_(wb);
        assertEquals(1, mws.getBundlesProcessed(), "Adding bundle processes it");
        assertEquals(1, mws.getOutboundQueueSize(), "Budnle is outbound");
        assertEquals(0, mws.getRetriedCount(), "No retries yet");
        assertEquals(0, mws.getPendingQueueSize(), "No pending items yet");

        // Take the fake work
        WorkBundle taken = mws.take(C2);
        assertEquals(wb.getBundleId(), taken.getBundleId(), "wb taken must be the one put in");

        // Check the counts after taking the work
        assertEquals(1, mws.getBundlesProcessed(), "Taking bundle doesn't change  processes count");
        assertEquals(0, mws.getOutboundQueueSize(), "No bundles outbound");
        assertEquals(0, mws.getRetriedCount(), "No retries yet");
        assertEquals(1, mws.getPendingQueueSize(), "Taken item is pending");

        // Fail the fake client that took the work
        mws.removePickUp_(C2);
        assertEquals(2, mws.getPickUpPlaceCount(), "Pickups removed must count");

        // Make sure the bundle went back to outbound queue
        assertEquals(1, mws.getOutboundQueueSize(), "Failed bundle back to  outbound");
        assertEquals(1, mws.getRetriedCount(), "Retry counter bumped on failure");
        assertEquals(0, mws.getPendingQueueSize(), "Failed item no longer pending");
    }

    @Test
    void testArgumentParsing() throws Exception {
        List<String> args = new ArrayList<>();
        args.add("--retry");
        args.add("--loop");
        args.add("--simple");
        args.add("-i");
        args.add("/tmp/foo:20,/tmp/bar,/tmp/quuz:1");
        FeedCommand command = FeedCommand.parse(FeedCommand.class, args);
        WorkSpace mws = new WorkSpace(command);
        assertEquals(3, mws.getDirectories().size(), "Three priority directory args must be present");
        assertTrue(mws.getDirectories().get(0).contains("quuz"), "Highest priority directory must be first");
        assertTrue(mws.getSimpleMode(), "Simple argument must cause flag to be set");
    }

    private static final class MyWorkSpace extends WorkSpace {
        public MyWorkSpace() throws Exception {}

        public MyWorkSpace(FeedCommand command) {
            super(command);
        }

        public void addPickUp_(String pup) {
            super.addPickUp(pup);
        }

        public void removePickUp_(String pup) {
            super.removePickUp(pup);
        }

        public void addOutboundBundle_(WorkBundle wb) {
            super.addOutboundBundle(wb);
        }

        @SuppressWarnings("unused")
        public List<String> getPups() {
            return pups;
        }

        @Override
        protected int notifyPickUps() {
            // do nothing, pretend all notified
            return pups.size();
        }

        @Override
        protected boolean notifyPickUp(String pup) {
            // do nothing, pretend it worked
            return true;

        }

        @Override
        protected void startJetty() {
            // do nothing
        }

        @Override
        protected void initializeService() {
            // do nothing
        }
    }
}
