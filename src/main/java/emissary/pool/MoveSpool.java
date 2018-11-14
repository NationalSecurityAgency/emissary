package emissary.pool;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import emissary.core.IMobileAgent;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.directory.DirectoryEntry;
import emissary.directory.IDirectoryPlace;
import emissary.place.IServiceProviderPlace;
import emissary.util.PayloadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provide a storage area for incoming "moveTo(here)" payloads so that the http transfer can become more asnychronous.
 * This class provides a FIFO for payloads that are arriving and a thread that will put them into agents from the pool
 * as agents become available
 */

public class MoveSpool implements Runnable {

    // Our logger
    private static final Logger logger = LoggerFactory.getLogger(MoveSpool.class);

    // The payload FIFO
    protected final LinkedList<SpoolItem> spool = new LinkedList<SpoolItem>();

    // Reference to the agent pool
    protected AgentPool pool;

    // The thread that stuffs payloads into pool agents
    Thread watcher;

    // thread quit control
    boolean timeToQuit = false;

    // How we want to be registerd in the namespace
    public static final String NAMESPACE_NAME = "ArrivalSpool";

    // Stats on how many moves and for what types arrived here
    public final Map<String, Integer> moveCountMap = new HashMap<String, Integer>();

    // Stats collection
    private int highWaterMark = 0;
    private static long lookupCount = 0;
    private long enqueCount = 0;
    private long dequeCount = 0;

    // Cached ref to my local directory
    IDirectoryPlace localDirectory = null;

    // Methods for using the spool to dispatch
    public static enum Method {
        ARRIVE, GO
    }

    /**
     * Make one and bind it in the namespace
     */
    public MoveSpool() {
        configure();

        // register this pool in the namespace
        Namespace.bind(NAMESPACE_NAME, this);
    }

    /**
     * Configure stuff
     */
    private void configure() {
        // Get the agent pool
        resetPool();

        // start the watcher thread
        watcher = new Thread(this, "MoveSpool");
        watcher.setPriority(Thread.MAX_PRIORITY - 2);
        watcher.setDaemon(true);
        watcher.start();
    }

    public void resetPool() {
        // grab the default pool
        try {
            pool = AgentPool.lookup();
            logger.debug("Found the AgentPool on MoveSpool#resetPool");
        } catch (NamespaceException nex) {
            logger.error("Unable to find agent pool, " + "please create the agent pool before creating the MoveSpool");
        }
    }

    /**
     * Shut down the spooling thread and clear out any remaining payloads.
     */
    public void quit() {
        logger.warn("Purging the spool...");
        synchronized (spool) {
            if (spool.size() > 0) {
                spool.clear();
            }
            spool.notifyAll();
        }
        timeToQuit = true;
        Namespace.unbind(NAMESPACE_NAME);
        logger.info("Done stopping the move spool");
    }

    /**
     * Get a reference to the local directory on this machine
     *
     * @return a reference to the local directory from the namespace
     */
    private IDirectoryPlace getLocalDirectory() {
        if (localDirectory == null) {
            for (Iterator<String> i = Namespace.keySet().iterator(); i.hasNext();) {
                String key = i.next();
                try {
                    Object value = Namespace.lookup(key);
                    if (value instanceof IDirectoryPlace) {
                        localDirectory = (IDirectoryPlace) value;
                        break;
                    }
                } catch (NamespaceException ex) {
                    logger.info("Problem in namespace", ex);
                }
            }
        }
        return localDirectory;
    }

    /**
     * Run the thread to watch the spool
     */
    @Override
    public void run() {
        int consecutiveSendCounter = 0;

        // Run until we are told to quit
        while (!timeToQuit) {
            // Check the spool for work to be done
            int sz = 0;
            synchronized (spool) {
                sz = spool.size();
            }

            if (sz == 0) {
                // No payloads to look at. Sleep a while
                consecutiveSendCounter = 0;
                try {
                    logger.debug("Nothing in spool, time to wait...");
                    Thread.yield();
                    synchronized (spool) {
                        if (spool.size() == 0) {
                            spool.wait(60000);
                        }
                    }
                } catch (InterruptedException ignore) {
                    // empty catch block
                }
                continue;
            }

            // Get an agent and a sool item
            IMobileAgent agent = null;
            SpoolItem item = null;
            String itemName = null;

            try {
                // This may block for the max time the
                // pool is configured to use if no
                // agents available
                agent = pool.borrowAgent();
                if (agent == null) {
                    logger.debug("Got a null agent from pool!");
                    continue;
                }

                // Get the oldest payload from the spool
                item = removeFirstPayload();
                if (item == null) {
                    logger.debug("Got a null item from move spool!");
                    pool.returnAgent(agent);
                    continue;
                }

                // We have both an agent and a spool item
                // so hook em up and send it on the way
                itemName = PayloadUtil.getName(item.getPayload());

                logger.debug("Handing over " + itemName + " to an agent, method=" + item.getMethod());

                if (item.getMethod() == Method.GO) {
                    IServiceProviderPlace place = item.getPlace();
                    if (place == null) {
                        place = getLocalDirectory();
                    }

                    Object payload = item.getPayload();
                    agent.go(payload, place);
                    consecutiveSendCounter++;
                } else if (item.getMethod() == Method.ARRIVE) {
                    agent.arrive(item.getPayload(), item.getPlace(), item.getErrorCount(), item.getItineraryItems());
                    consecutiveSendCounter++;
                } else {
                    logger.error("Illegal spooler method specified " + item.getMethod() + ", payload=" + item.getPayload()
                            + " will be irretreivably lost");
                }

                if (consecutiveSendCounter % 10 == 0) {
                    logger.debug("Sent 10 consecutive entries, " + "time to yield the MoveSpool");
                    Thread.yield();
                }
            } catch (Throwable t) {
                if (agent != null) {
                    logger.error("Unable to start agent, payload " + itemName + " is irretrievably lost", t);
                    try {
                        pool.returnAgent(agent);
                    } catch (Exception ex) {
                        logger.error("Unable to return agent to the pool", ex);
                    }
                } else {
                    logger.debug("Cannot get agent from pool, trying again", t);
                }
            } finally {
                // hold no references to this stuff
                agent = null;
                item = null;
            }
        }
    }

    /**
     * Remove the oldest payload item on the spool
     *
     * @return SpoolItem from the spool
     */
    protected SpoolItem removeFirstPayload() {
        SpoolItem s = null;
        synchronized (spool) {
            // Do some stats
            if (spool.size() > highWaterMark) {
                highWaterMark = spool.size();
            }

            s = spool.removeFirst();
            dequeCount++;
        }
        return s;
    }

    /**
     * Add an item to the spool for sending. Can be the result of a sprout or a new item being ingested into the system. The
     * arrivalPlace is null so we call MobileAgent.go rather than MobileAgent.arrive
     *
     * @param payload the dataObject or Collection to save
     * @return number of items on the queue
     */
    public int send(Object payload) {
        return enqueue(Method.GO, payload, null, 0, (List<DirectoryEntry>) null);
    }

    /**
     * Add an item to the spool for sending. Can be the result of a sprout or a new item being ingested into the system. We
     * call MobileAgent.go rather than MobileAgent.arrive
     *
     * @param payload the dataObject or Collection to save
     * @param place the sending or sprouting place reference
     * @return number of items on the queue
     */
    public int send(Object payload, IServiceProviderPlace place) {
        return enqueue(Method.GO, payload, place, 0, (List<DirectoryEntry>) null);
    }

    /**
     * Add an arriving payload and associated state transfer info to the spool Calls MobileAgent.arrive in this case
     *
     * @param payload the data object or Collection to save
     * @param place IServiceProviderPlace ref for the agent to visit
     * @param errorCount state from the transferred MobileAgent
     * @param itineraryItems state from the transferred MobileAgent
     * @return number of items in the queue
     */
    public int arrive(Object payload, IServiceProviderPlace place, int errorCount, List<DirectoryEntry> itineraryItems) {
        return enqueue(Method.ARRIVE, payload, place, errorCount, itineraryItems);
    }

    /**
     * Add an item to the spool. When an agent becomes available it is assigned to this payload in turn. We call
     * MobileAgent.go or MobileAgent.arrive depending on the Method requested
     *
     * @param method ARRIVE or GO
     * @param payload the data object or Collection to save
     * @param place IServiceProviderPlace ref for the agent to visit, possibly null
     * @param errorCount state from the transferred MobileAgent or null for GO
     * @param itineraryItems state from the transferred MobileAgent or empty for GO
     * @return number of items on the queue
     */
    protected int enqueue(Method method, Object payload, IServiceProviderPlace place, int errorCount, List<DirectoryEntry> itineraryItems) {

        String itemName = PayloadUtil.getName(payload);
        logger.debug("Enqueue item " + itemName + " for place " + place + ", method=" + method);
        SpoolItem s = new SpoolItem(method, payload, place, errorCount, itineraryItems);
        int size = 0;

        synchronized (spool) {
            spool.addLast(s);
            enqueCount++;
            size = spool.size();
            spool.notifyAll();
        }

        // Collect the stats
        synchronized (moveCountMap) {
            String serviceName = s.getServiceName();
            if (moveCountMap.containsKey(serviceName)) {
                Integer count = moveCountMap.get(serviceName);
                moveCountMap.put(serviceName, Integer.valueOf(count.intValue() + 1));
            } else {
                moveCountMap.put(serviceName, Integer.valueOf(1));
            }
        }

        logger.debug("Done enqueue of " + itemName + ", size=" + size);
        return size;
    }


    /**
     * Look up the instance in the namespace
     */
    public static MoveSpool lookup() throws NamespaceException {
        lookupCount++;
        return (MoveSpool) Namespace.lookup(NAMESPACE_NAME);
    }

    /**
     * Provide a copy of the map for stats gathering applications. This map shows how many items of each type have arrived
     * on this node
     */
    public Map<String, Integer> getMoveCountMap() {
        synchronized (moveCountMap) {
            return new HashMap<String, Integer>(moveCountMap);
        }
    }

    /**
     * Provide statistics in string form
     *
     * @return list of types and counts that have spooled here
     */
    public String getStatPairs() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        synchronized (moveCountMap) {
            for (String key : moveCountMap.keySet()) {
                if (sb.length() > 1) {
                    sb.append(",");
                }
                sb.append(key).append("=").append(moveCountMap.get(key));
            }
        }
        sb.append("}");
        return sb.toString();
    }


    /**
     * To String for the namespace display
     */
    @Override
    public String toString() {
        // Spool size is deliberately not synchronized
        return "MoveSpool current/high " + spool.size() + "/" + highWaterMark + ", en/dequeue " + enqueCount + "/" + dequeCount + ", serviceNames="
                + getStatPairs();
    }

    /**
     * Non-public encapsulation of what we need to hold on the spool
     */
    protected static class SpoolItem {

        final Method method;
        final Object payload;
        final IServiceProviderPlace place;
        final int errorCount;
        final List<DirectoryEntry> itineraryItems;

        public SpoolItem(Method method, Object payload, IServiceProviderPlace place, int errorCount, List<DirectoryEntry> itineraryItems) {
            this.method = method;
            this.payload = payload;
            this.place = place;
            this.errorCount = errorCount;
            this.itineraryItems = itineraryItems;
        }

        /**
         * Get the payload
         */
        public Object getPayload() {
            return payload;
        }

        /**
         * Get the place
         */
        public IServiceProviderPlace getPlace() {
            return place;
        }

        /**
         * Get the error count
         */
        public int getErrorCount() {
            return errorCount;
        }

        /**
         * Get serviceName from place key
         *
         * @return string service name from key
         */
        public String getServiceName() {
            if (place != null) {
                return emissary.directory.KeyManipulator.getServiceName(place.getKey());
            }
            return "sprout";
        }

        /**
         * Get the list of itinerary items
         *
         * @return List of DirectoryEntry
         */
        public List<DirectoryEntry> getItineraryItems() {
            return itineraryItems;
        }

        /**
         * Get the spool method
         */
        public Method getMethod() {
            return method;
        }
    }

    /**
     * @return the lookupCount
     */
    public static long getLookupCount() {
        return lookupCount;
    }

    /**
     * @return the dequeCount
     */
    public long getDequeCount() {
        return dequeCount;
    }

    /**
     * @return the enqueCount
     */
    public long getEnqueCount() {
        return enqueCount;
    }

    /**
     * @return the highWaterMark
     */
    public int getHighWaterMark() {
        return highWaterMark;
    }

    public int getCurrentSpoolSize() {
        return spool.size();
    }
}
