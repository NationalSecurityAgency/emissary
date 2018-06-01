package emissary.pickup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import emissary.test.core.UnitTest;
import org.junit.Before;
import org.junit.Test;

public class PickupQueueTest extends UnitTest {
    @Override
    @Before
    public void setUp() throws Exception {

    }

    @Test
    public void testQueueing() {
        PickupQueue p = new PickupQueue(3);
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

        assertTrue("Can hold 1", p.canHold(1));
        assertTrue("Can hold 2", p.canHold(2));
        assertTrue("Can hold 3", p.canHold(3));
        assertTrue("Can hold 4", !p.canHold(4));
        assertNull("Dequeue on empty queue", p.deque());
        assertTrue("Enqueue item", p.enque(w1));
        assertEquals("Size of queue", 1, p.size());
        assertTrue("Enqueue item", p.enque(w2));
        assertEquals("Size of queue", 2, p.size());
        assertTrue("Enqueue item", p.enque(w3));
        assertEquals("Size of queue", 3, p.size());
        assertTrue("Enqueue item", p.enque(w4));
        assertEquals("Size of queue", 4, p.size());
        WorkBundle dq1 = p.deque();
        assertEquals("Dequeue item", dq1.getBundleId(), w1.getBundleId());
        assertEquals("Size of queue", 3, p.size());

        assertTrue("Enqueue null item", p.enque(null));
        assertEquals("Size of queue after null item", 3, p.size());

        WorkBundle wb5 = new WorkBundle("/output/root", "/eat/prefix");
        assertTrue("Enqueu of item with no files", p.enque(wb5));
        assertEquals("Size of queue after no file item", 3, p.size());
        assertTrue("Can hold when full", !p.canHold(1));
    }

    @Test
    public void testDefaultSize() {
        PickupQueue p = new PickupQueue();
        assertTrue("Can hold default", p.canHold(1));
        assertTrue("Can hold default", p.canHold(19));
    }

    @Test
    public void testNotify() {
        PickupQueue p = new PickupQueue(3);
        PQTester pqt = new PQTester(p);
        Thread t = new Thread(pqt, "PickupQueue tester");
        t.setDaemon(true);
        t.start();
        try {
            Thread.sleep(100);
        } catch (InterruptedException ex) {
        }
        WorkBundle wb = new WorkBundle("/output/root", "/eat/prefix");
        wb.addFileName("file1.txt");
        p.enque(wb);
        try {
            Thread.sleep(100);
        } catch (InterruptedException ex) {
        }
        int hitCount = pqt.getHitCount();
        assertTrue("Waiter was notified " + hitCount + " times", hitCount > 0);
        pqt.timeToQuit = true;
    }

    // Wait for a notify from the PickupQueue
    static class PQTester implements Runnable {
        final PickupQueue pq;
        int hitcount = 0;
        public boolean timeToQuit = false;

        public PQTester(PickupQueue p) {
            this.pq = p;
        }

        public int getHitCount() {
            return hitcount;
        }

        @Override
        public void run() {
            while (!timeToQuit) {
                synchronized (pq) {
                    try {
                        pq.wait(0);
                        hitcount++;
                    } catch (InterruptedException ex) {
                    }
                }
            }
        }
    }
}
