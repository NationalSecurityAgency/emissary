package emissary.pickup;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PickupQueueTest extends UnitTest {
    @Override
    @BeforeEach
    public void setUp() throws Exception {

    }

    @Test
    void testQueueing() {
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

        assertTrue(p.canHold(1), "Can hold 1");
        assertTrue(p.canHold(2), "Can hold 2");
        assertTrue(p.canHold(3), "Can hold 3");
        assertFalse(p.canHold(4), "Can hold 4");
        assertNull(p.deque(), "Dequeue on empty queue");
        assertTrue(p.enque(w1), "Enqueue item");
        assertEquals(1, p.size(), "Size of queue");
        assertTrue(p.enque(w2), "Enqueue item");
        assertEquals(2, p.size(), "Size of queue");
        assertTrue(p.enque(w3), "Enqueue item");
        assertEquals(3, p.size(), "Size of queue");
        assertTrue(p.enque(w4), "Enqueue item");
        assertEquals(4, p.size(), "Size of queue");
        WorkBundle dq1 = p.deque();
        assertEquals(dq1.getBundleId(), w1.getBundleId(), "Dequeue item");
        assertEquals(3, p.size(), "Size of queue");

        assertTrue(p.enque(null), "Enqueue null item");
        assertEquals(3, p.size(), "Size of queue after null item");

        WorkBundle wb5 = new WorkBundle("/output/root", "/eat/prefix");
        assertTrue(p.enque(wb5), "Enqueu of item with no files");
        assertEquals(3, p.size(), "Size of queue after no file item");
        assertFalse(p.canHold(1), "Can hold when full");
    }

    @Test
    void testDefaultSize() {
        PickupQueue p = new PickupQueue();
        assertTrue(p.canHold(1), "Can hold default");
        assertTrue(p.canHold(19), "Can hold default");
    }

    @Test
    void testNotify() {
        PickupQueue p = new PickupQueue(3);
        PQTester pqt = new PQTester(p);
        Thread t = new Thread(pqt, "PickupQueue tester");
        t.setDaemon(true);
        t.start();
        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {
        }
        WorkBundle wb = new WorkBundle("/output/root", "/eat/prefix");
        wb.addFileName("file1.txt");
        p.enque(wb);
        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {
        }
        int hitCount = pqt.getHitCount();
        assertTrue(hitCount > 0, "Waiter was notified " + hitCount + " times");
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
                    } catch (InterruptedException ignored) {
                        // Ignore
                    }
                }
            }
        }
    }
}
