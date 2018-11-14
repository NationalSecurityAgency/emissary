package emissary.pickup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import emissary.command.FeedCommand;
import emissary.config.ConfigUtil;
import emissary.test.core.UnitTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class WorkSpaceTest extends UnitTest {
    MyWorkSpace mws;

    String CFGDIR = System.getProperty(ConfigUtil.CONFIG_DIR_PROPERTY);

    @Override
    @Before
    public void setUp() throws Exception {

        try {
            mws = new MyWorkSpace();
        } catch (Exception ex) {
            fail("Cannot start workspace: " + ex.getMessage());
        }
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        mws.shutDown();
        pause(100L);
        mws = null;
    }

    @Test
    public void testPriorityQueueing() {
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
        assertEquals("Highest priority work must be taken first", 1, taken.getPriority());
    }

    /**
     * Test the WorkBundle sorting of the "oldest first" sorting when the priorities are even (and hence, don't override the
     * time based ordering)
     */
    @Test
    public void testOldestFirstQueueing() throws Exception {
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
        WorkBundle taken = null;
        long age = 0L;
        assertEquals("Outbound Queue Size", 11, mws.getOutboundQueueSize());
        while ((taken = mws.take(C1)) != null) {
            if (taken.size() != 0) {
                assertEquals("Oldest work must be taken first", age, taken.getOldestFileModificationTime());
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
    public void testOldestFirstPriorityOverrideQueueing() throws Exception {
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
        WorkBundle taken = null;
        int pri = 0;
        assertEquals("Outbound Queue Size", 11, mws.getOutboundQueueSize());
        while ((taken = mws.take(C1)) != null) {
            if (taken.size() != 0) {
                assertEquals("Ignoring time, prioritized work must be taken first", pri, taken.getPriority());
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
    public void testYoungestFirstQueueing() throws Exception {
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
        WorkBundle taken = null;
        long age = 10L;
        assertEquals("Outbound Queue Size", 11, mws.getOutboundQueueSize());
        while ((taken = mws.take(C1)) != null) {
            if (taken.size() != 0) {
                assertEquals("Youngest work must be taken first", age, taken.getYoungestFileModificationTime());
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
    public void testYoungestFirstPriorityOverrideQueueing() throws Exception {
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

        WorkBundle taken = null;
        int pri = 0;
        assertEquals("Outbound Queue Size", 11, mws.getOutboundQueueSize());
        while ((taken = mws.take(C1)) != null) {
            if (taken.size() != 0) {
                assertEquals("Ignoring time, priority work must be taken first", pri, taken.getPriority());
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
    public void testSmallestFirstQueueing() throws Exception {
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
        WorkBundle taken = null;
        long sz = 0L;
        assertEquals("Outbound Queue Size", 11, mws.getOutboundQueueSize());
        while ((taken = mws.take(C1)) != null) {
            if (taken.size() != 0) {
                assertEquals("Smallest bundle must be taken first", sz, taken.getTotalFileSize());
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
    public void testSmallestFirstPriorityOverrideQueueing() throws Exception {
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

        WorkBundle taken = null;
        int pri = 0;
        assertEquals("Outbound Queue Size", 11, mws.getOutboundQueueSize());
        while ((taken = mws.take(C1)) != null) {
            if (taken.size() != 0) {
                assertEquals("Ignoring size, priority work must be taken first", pri, taken.getPriority());
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
    public void testLargestFirstQueueing() throws Exception {
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
        WorkBundle taken = null;
        long sz = 10L;
        assertEquals("Outbound Queue Size", 11, mws.getOutboundQueueSize());
        while ((taken = mws.take(C1)) != null) {
            if (taken.size() != 0) {
                assertEquals("Largest bundle must be taken first", sz, taken.getTotalFileSize());
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
    public void testLargestFirstPriorityOverrideQueueing() throws Exception {
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

        WorkBundle taken = null;
        int pri = 0;
        assertEquals("Outbound Queue Size", 11, mws.getOutboundQueueSize());
        while ((taken = mws.take(C1)) != null) {
            if (taken.size() != 0) {
                assertEquals("Ignoring size, priority work must be taken first", pri, taken.getPriority());
            } else {
                break; // size = 0 means its the last
            }
            pri++;
        }
    }

    @Test
    public void testFailurePutsPendingBundleBackToOutboundQueue() {
        String C1 = "INITIAL.INPUT.A.http://otherhost:7001/FilePickUpClient";
        String C2 = "INITIAL.INPUT.A.http://otherhost:8001/FilePickUpClient";
        String C3 = "INITIAL.INPUT.A.http://otherhost:9001/FilePickUpClient";

        // Set up some fake clients
        mws.addPickUp_(C1);
        mws.addPickUp_(C2);
        mws.addPickUp_(C3);
        assertEquals("Pickups added must count", 3, mws.getPickUpPlaceCount());

        // Set up some fake work
        WorkBundle wb = new WorkBundle("/fake/root", "/fake/eat");
        wb.addFileName("faker.txt");

        // Give the fake work to the space
        mws.addOutboundBundle_(wb);
        assertEquals("Adding bundle processes it", 1, mws.getBundlesProcessed());
        assertEquals("Budnle is outbound", 1, mws.getOutboundQueueSize());
        assertEquals("No retries yet", 0, mws.getRetriedCount());
        assertEquals("No pending items yet", 0, mws.getPendingQueueSize());

        // Take the fake work
        WorkBundle taken = mws.take(C2);
        assertEquals("wb taken must be the one put in", wb.getBundleId(), taken.getBundleId());

        // Check the counts after taking the work
        assertEquals("Taking bundle doesn't change  processes count", 1, mws.getBundlesProcessed());
        assertEquals("No bundles outbound", 0, mws.getOutboundQueueSize());
        assertEquals("No retries yet", 0, mws.getRetriedCount());
        assertEquals("Taken item is pending", 1, mws.getPendingQueueSize());

        // Fail the fake client that took the work
        mws.removePickUp_(C2);
        assertEquals("Pickups removed must count", 2, mws.getPickUpPlaceCount());

        // Make sure the bundle went back to outbound queue
        assertEquals("Failed bundle back to  outbound", 1, mws.getOutboundQueueSize());
        assertEquals("Retry counter bumped on failure", 1, mws.getRetriedCount());
        assertEquals("Failed item no longer pending", 0, mws.getPendingQueueSize());
    }

    @Test
    public void testArgumentParsing() throws Exception {
        List<String> args = new ArrayList<String>();
        args.add("--retry");
        args.add("--loop");
        args.add("--simple");
        args.add("-i");
        args.add("/tmp/foo:20,/tmp/bar,/tmp/quuz:1");
        FeedCommand command = FeedCommand.parse(FeedCommand.class, args);
        WorkSpace mws = new WorkSpace(command);
        assertEquals("Three priority directory args must be present", 3, mws.getDirectories().size());
        assertTrue("Highest priority directory must be first", mws.getDirectories().get(0).indexOf("quuz") > -1);
        assertEquals("Simple argument must cause flag to be set", true, mws.getSimpleMode());
    }

    private static final class MyWorkSpace extends WorkSpace {
        public MyWorkSpace() throws Exception {}

        public MyWorkSpace(FeedCommand command) throws Exception {
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
