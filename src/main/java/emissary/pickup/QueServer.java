package emissary.pickup;

import java.util.Iterator;

import emissary.core.Pausable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Monitor thread for a PickupQueue and return items for processing. Operates in pull mode from a PickupSpace or push
 * mode just monitoring the queue
 */
public abstract class QueServer extends Pausable {
    // Logger
    private static final Logger logger = LoggerFactory.getLogger(QueServer.class);

    // Poll interval in millis
    public static final long DEFAULT_POLLING_INTERVAL = 1000L;
    protected long pollingInterval = DEFAULT_POLLING_INTERVAL;

    // Loop control
    protected boolean timeToShutdown = false;

    // The queue this thread will monitor
    protected final PickupQueue queue;

    // For Pull mode from a PickupSpace
    protected IPickUpSpace space;

    /**
     * Create
     * 
     * @param space the pickupspace controller
     * @param queue the queue this thread monitors
     */
    public QueServer(IPickUpSpace space, PickupQueue queue) {
        this(space, queue, DEFAULT_POLLING_INTERVAL);
    }

    /**
     * Create with polling interval
     * 
     * @param space the pickupspace controller
     * @param queue the queue this thread monitors
     * @param pollingInterval value in millis
     */
    public QueServer(IPickUpSpace space, PickupQueue queue, long pollingInterval) {
        this(space, queue, pollingInterval, "PickupQueServer");
    }

    /**
     * Create with polling interval and thread name
     * 
     * @param space the pickupspace controller
     * @param queue the queue this thread monitors
     * @param pollingInterval value in millis
     * @param name value to supply to Thread name
     */
    public QueServer(IPickUpSpace space, PickupQueue queue, long pollingInterval, String name) {
        super(name);
        this.space = space;
        this.queue = queue;
        this.pollingInterval = pollingInterval;
        this.setPriority(Thread.NORM_PRIORITY + 1);
    }


    /**
     * Processing loop to monitor the queue
     */
    @Override
    public void run() {
        logger.debug("Starting the QueServer run method");
        while (!timeToShutdown) {
            // Process something on the queue
            try {
                checkQue();
            } catch (Exception e) {
                logger.warn("Exception in checkQue():" + e, e);
            }

            if (checkPaused()) {
                // check to see if we want to stop taking work
                continue;
            } else if (space.getSpaceCount() > 0 && queue.canHold(1)) {
                // If pull mode and we have room for one more.
                logger.debug("Que can hold more, trying take()");
                boolean status = space.take();
                if (status) {
                    try {
                        Thread.sleep(pollingInterval);
                    } catch (InterruptedException ignore) {
                        // empty catch block
                    }
                    continue;
                }
            } else {
                // We must be in push mode or the queue is full,
                // just monitor the queue and try again
                logger.debug("Que full or push mode, waiting, space = " + space + " spacenames = " + space.getSpaceNames() + ", queCanHold(1)? = "
                        + queue.canHold(1));
                try {
                    synchronized (queue) {
                        queue.wait(pollingInterval);
                    }
                } catch (InterruptedException e) {
                    logger.debug("Woke me up so lets check the queue!");
                }
            }
        }
        logger.debug("Off the end of the QueServer.run method");
    }

    /**
     * Check the queue for waiting objects and process them
     */
    public void checkQue() {
        WorkBundle paths = queue.deque();
        while (paths != null) {
            logger.debug("checkQue got a work bundle " + paths);

            // We have work so parse it out and wait for the next agent.
            // This will send the work on the agent's thread.
            // Once the agents are sent we notify the
            // workspace of completion of this bundle
            try {
                boolean status = processQueueItem(paths);
                logger.debug("Initiating bundle completed msg for {}, status={}", paths.getBundleId(), status);
                space.bundleCompleted(paths.getBundleId(), status);
            } catch (Exception e) {
                StringBuffer fnb = new StringBuffer();
                // Report filenames on error
                for (Iterator<String> i = paths.getFileNameIterator(); i.hasNext();) {
                    String fn = i.next();
                    fnb.append(fn).append(",");
                }
                logger.warn("Processing exception on {}", fnb.toString(), e);
                logger.debug("Initiating bundle failed msg for {}", paths.getBundleId());
                space.bundleCompleted(paths.getBundleId(), false);
            }

            // Yield but don't go back to sleep if
            // there is still work to do
            Thread.yield();
            paths = queue.deque();
        }
        logger.debug("QueServer.checkQue ran out of data");
    }

    /**
     * Action to take when an item is removed from queue
     * 
     * @param path the bundle from the queue
     * @return true if it worked
     */
    public abstract boolean processQueueItem(WorkBundle path);

    /**
     * Schedule this thread to stop soon
     */
    public void shutdown() {
        this.timeToShutdown = true;
    }

    /**
     * Pass through to get size of injected queue
     * 
     * @return size of the queue
     */
    public int getQueSize() {
        return queue.getQueSize();
    }

    /**
     * Pass through to enqueue a work bundle on the injected queue
     * 
     * @param bundle the work bundle to enqueue
     * @return true if it was enqueued, false if we are too busy to handle it
     */
    public boolean enque(WorkBundle bundle) {
        return queue.enque(bundle);
    }

}
