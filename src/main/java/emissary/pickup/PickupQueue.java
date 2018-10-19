package emissary.pickup;

import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A size limited queue for holding data to process.
 */
public class PickupQueue {
    // Data structure for the bundles
    protected final LinkedList<WorkBundle> queue = new LinkedList<WorkBundle>();

    // Our logger
    private static final Logger logger = LoggerFactory.getLogger(PickupQueue.class);

    /**
     * These parameters determine the enqueing behavior. The desire is to minimize the number of remote calls from main to
     * and instance of this class with the getQueSize method, and at the same keep all of the places busy. We do this by
     * making the MAX_QUE_SIZE large enough to hold enough files to be processed in SLEEP_TIME. BUT we don't just make the
     * MAX_QUE_SIZE huge because then we use too much memory. Some feeds put stuff on the Que in blocks. If our que is a
     * prime numbered size they cannot fill it completely, which will help prevent blocking maybe.
     */
    private int MAX_QUE_SIZE = 19;

    /**
     * Normal pickup queue creation
     */
    public PickupQueue() {}

    /**
     * Create a queue with the given max size
     *
     * @param maxSize the maximum size the queue can grow to
     */
    public PickupQueue(int maxSize) {
        MAX_QUE_SIZE = maxSize;
    }

    /**
     * Add incoming information to the queue of file names to process and notify anyone waiting on the queue
     *
     * @param paths the WorkBundle object containing files to queue up
     * @return true if it was enqueued, false if we are too busy to handle it
     */
    public boolean enque(WorkBundle paths) {
        if (paths == null || paths.size() == 0) {
            logger.warn("Enque of a null or empty WorkBundle structure!");
            return true;
        }

        synchronized (queue) {
            // Add it to the queue
            queue.add(0, paths);
            queue.notifyAll();
        }

        synchronized (this) {
            // notify waiters every time paths added
            this.notifyAll();
        }

        return true;
    }

    /**
     * Return size of queue
     */
    public int size() {
        return getQueSize();
    }

    /**
     * Getter for the size of the queue
     *
     * @return the size of the queue
     */
    public int getQueSize() {
        int size = -1;
        synchronized (queue) {
            size = queue.size();
        }
        return size;
    }

    /**
     * Get one data object from the queue.
     *
     * @return the dequeued WorkBundle or null if none
     */
    public synchronized WorkBundle deque() {
        WorkBundle nextFile = null;
        int size = -1;
        synchronized (queue) {
            size = queue.size();
            if (size > 0) {
                nextFile = queue.removeLast();
            }
        }
        return nextFile;
    }

    /**
     * Tell caller if we can hold this many more items
     *
     * @return true iff there is room for num items
     */
    public boolean canHold(int num) {
        return getQueSize() + num <= MAX_QUE_SIZE;
    }
}
