package emissary.pickup;

import emissary.test.core.junit5.UnitTest;

import jakarta.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueServerTest extends UnitTest {

    @Nullable
    InputStream tepusConfigStream = null;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        String cdata =
                "PLACE_NAME = TestPickUpPlace\n" + "SERVICE_NAME = TEST_PICK_UP\n" + "SERVICE_TYPE = \"INITIAL\"\n"
                        + "SERVICE_DESCRIPTION = \"Test Place\"\n" + "SERVICE_COST = 50\n" + "SERVICE_QUALITY = 50\n"
                        + "INITIAL_FORM = \"UNKNOWN\"\n" + "SERVICE_PROXY = \"TESTJUNK\"\n";
        tepusConfigStream = new ByteArrayInputStream(cdata.getBytes());
    }

    @Test
    void testQueServer() throws Exception {
        new PickupQueue(13);
        WorkBundle w1 = new WorkBundle("/output/root", "/eat/prefix");
        w1.addFileName("file1.txt");
        WorkBundle w2 = new WorkBundle("/output/root", "/eat/prefix");
        w2.addFileName("file2.txt");
        WorkBundle w3 = new WorkBundle("/output/root", "/eat/prefix");
        w3.addFileName("file3.txt");
        WorkBundle w4 = new WorkBundle("/output/root", "/eat/prefix");
        w4.addFileName("file4.txt");
        w4.addFileName("file5.txt");
        w4.addFileName("file6.txt");

        TestErrorPickUpSpace ps = new TestErrorPickUpSpace(3, 5);

        ps.enque(w1);
        ps.enque(w2);
        ps.enque(w3);
        ps.enque(w4);

        try {
            pause(500);
            assertEquals(4, ps.queServer.bundlesReceived, "Good bundle count received");
            assertEquals(6, ps.queServer.filesReceived, "Good file count received");
        } finally {
            ps.shutDown();
        }

        tepusConfigStream.reset();
        ps = new TestErrorPickUpSpace(15, 0);
        pause(500);
        try {
            ps.openSpace("FOO.BAR.BAZ.http://example.com:1234/FooDeBar");
            pause(1000);

            assertEquals(0, ps.queServer.bundlesReceived, "Good bundle count received");
            assertEquals(0, ps.queServer.filesReceived, "Good file count received");
        } finally {
            ps.shutDown();
        }
    }

    static class TestQueueServer extends QueServer {

        public int bundlesReceived = 0;
        public int filesReceived = 0;

        public TestQueueServer(IPickUpSpace space, PickupQueue queue) {
            super(space, queue);
        }

        public TestQueueServer(IPickUpSpace space, PickupQueue queue, long pollingInterval) {
            super(space, queue, pollingInterval);
        }

        public TestQueueServer(IPickUpSpace space, PickupQueue queue, long pollingInterval, String name) {
            super(space, queue, pollingInterval, name);
        }

        @Override
        public boolean processQueueItem(WorkBundle bundle) {
            bundlesReceived++;
            filesReceived += bundle.getFileNameList().size();
            return true;
        }
    }

    class TestErrorPickUpSpace extends PickUpSpace {
        public TestQueueServer queServer;
        public PickupQueue queue;
        public int expectedErrors;
        public int numErrors = 0;
        public int expectedBundles;
        public int numBundles = 0;
        public int bundleCompletedCount = 0;
        public int bundleFailedCount = 0;

        public TestErrorPickUpSpace(int errors, int bundles) throws IOException {
            super(tepusConfigStream, null, "http://localhost:8005/TestErrorPickUpSpace");
            this.expectedErrors = errors;
            this.expectedBundles = bundles;
            queue = new PickupQueue(13);
            queServer = new TestQueueServer(this, queue, 50, "QueServerTest");
            queServer.start();
        }

        @Override
        public boolean enque(WorkBundle path) {
            assertTrue(queue.enque(path), "Bundle enqueued");
            return true;
        }

        @Override
        public int getQueSize() {
            return queue.getQueSize();
        }

        @Override
        public boolean take() {
            if (numErrors++ < expectedErrors || numBundles >= expectedBundles) {
                return false;
            }
            numBundles++;
            WorkBundle w = new WorkBundle("/output/root", "/eat/prefix");
            w.addFileName("file1.txt");
            assertTrue(enque(w), "Bundle enqueueued");
            return true;
        }

        @Override
        public void bundleCompleted(String bundleId, boolean itWorked) {
            if (itWorked) {
                bundleCompletedCount++;
            } else {
                bundleFailedCount++;
            }
        }

        @Override
        public void shutDown() {
            queServer.shutdown();
            super.shutDown();
        }
    }
}
