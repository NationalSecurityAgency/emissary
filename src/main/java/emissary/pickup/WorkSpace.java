package emissary.pickup;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import emissary.client.EmissaryResponse;
import emissary.command.FeedCommand;
import emissary.command.ServerCommand;
import emissary.core.EmissaryException;
import emissary.core.NamespaceException;
import emissary.directory.DirectoryAdapter;
import emissary.directory.DirectoryEntry;
import emissary.directory.DirectoryPlace;
import emissary.directory.EmissaryNode;
import emissary.directory.IDirectoryPlace;
import emissary.directory.KeyManipulator;
import emissary.pool.AgentPool;
import emissary.server.EmissaryServer;
import emissary.server.mvc.adapters.WorkSpaceAdapter;
import emissary.util.io.FileFind;
import org.apache.http.HttpStatus;
import org.eclipse.jetty.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Recursively process input and distribute files to one or more remote PickUp client instances when they ask for a
 * bundle of work to do.
 *
 * The work bundles, emissary.pickup.WorkBundle objects, are placed on a queue for sending to consumers by the producer
 * here. Once a WorkBundle has been requested and sent to a consumer (i.e. FilePickUpClient) it is placed on a pending
 * queue tagged with the consumer id until the consumer notifies this WorkSpace that the work has been completed. If the
 * consumer goes away without notification, then the work is moved back to the outbound queue and given to another
 * consumer.
 */
public class WorkSpace implements Runnable {
    /** Our logger */
    protected static final Logger logger = LoggerFactory.getLogger(WorkSpace.class);

    protected FeedCommand feedCommand;

    /**
     * Pickup places we will send to, loaded and modified by directory observation during runtime
     */
    protected List<String> pups = new CopyOnWriteArrayList<String>();

    /**
     * Initial pattern for finding pickup places
     */
    protected String pattern = System.getProperty(CLZ + ".clientPattern", "*.FILE_PICK_UP_CLIENT.INPUT.*");

    /** The directory observer for the pattern */
    protected WorkSpaceDirectoryWatcher watcher;

    // Process control
    protected static final String CLZ = WorkSpace.class.getName();
    protected boolean WANT_DIRECTORIES = Boolean.getBoolean(CLZ + ".includeDirectories");
    protected boolean debug = Boolean.getBoolean(CLZ + ".debug");
    protected boolean simpleMode = false;
    protected String outputRootPath = System.getProperty("outputRoot", null);
    protected String eatPrefix = System.getProperty("eatPrefix", null);
    protected int numberOfBundlesToSkip = Integer.getInteger(CLZ + ".skip", 0).intValue();
    protected boolean skipDotFiles = Boolean.getBoolean(CLZ + ".skipDotFiles");
    protected boolean loop = false;
    protected boolean useRetryStrategy = false;
    protected int MAX_BUNDLE_RETRIES = 5;
    protected PriorityQueue<PriorityDirectory> myDirectories = new PriorityQueue<PriorityDirectory>();

    // Stats tracking, map of stats per remote pick up place
    protected WorkSpaceStats stats = new WorkSpaceStats();

    // Thread to notify clients that there is work to do
    protected ClientNotifier notifier = null;

    // Process control for collector thread
    protected boolean timeToQuit = false;
    protected boolean collectorThreadHasQuit = false;
    protected boolean jettyStartedHere = false;
    protected float MEM_THRESHOLD = 0.80f;
    protected long LOOP_PAUSE_TIME = 60000L;
    protected long PENDING_HANG_TIME = 600000L;
    protected long NOTIFIER_PAUSE_TIME = 1000L;
    protected int retryCount = 0;
    protected boolean useFileTimestamps = false;
    protected String PROJECT_BASE = null;

    /**
     * How many file names to send per remote message, should be 10% or less of the size of the PickUpPlace.MAX_QUE Helps
     * prevent blocking if it's not a factor of the PickUpPlace.MAX_QUE size
     */
    protected int FILES_PER_MESSAGE = Integer.getInteger(CLZ + ".filesPerBundle", 5);

    protected long MAX_BUNDLE_SIZE = Long.getLong(CLZ + ".maxSizePerBundle", -1);

    // Metrics collection
    protected long filesProcessed = 0;
    protected long bundlesProcessed = 0;
    protected long bytesProcessed = 0;

    // Data tracking
    protected String dataCaseId = System.getProperty("caseId", null);
    protected boolean caseClosed = false;

    // List of WorkBundle objects we are going to distribute
    protected PriorityQueue<WorkBundle> outbound = new PriorityQueue<WorkBundle>();

    // List of WorkBundle objects that are pending completion notice
    // Keyed by bundleId to quicky remove items that are processed
    // normally (the expected case)
    protected Map<String, WorkBundle> pending = new HashMap<String, WorkBundle>();

    // Keep track of files we have seen that are either outbound or pending
    // so that we can avoid using file timestamps in the collector loop
    protected Map<String, Long> filesSeen = new HashMap<String, Long>();
    protected Map<String, Long> filesDone = new HashMap<String, Long>();

    // Used to synchronize access to the pending and outbound queues
    // One lock to rule them all
    protected final Object QLOCK = new Object();

    // How we register in the namespace and advertise ourself
    protected static final String DEFAULT_WORK_SPACE_NAME = "WorkSpace";
    protected String WORK_SPACE_NAME = DEFAULT_WORK_SPACE_NAME;

    protected String workSpaceUrl;
    protected String workSpaceKey;

    /**
     * Command line entry point for sending files to a list of remote TreePickUpPlaces
     */
    public static void main(final String[] args) {
        try {
            final WorkSpace ws = new WorkSpace(FeedCommand.parse(FeedCommand.class, args));
            ws.run();
            logger.info("Workspace has completed the mission [ +1 health ].");
            ws.shutDown();
        } catch (Exception e) {
            logger.error("Bad commandline arguments, check the FeedCommand help", e);
        }
        System.exit(0);
    }

    /**
     * Construct the space
     */
    public WorkSpace() throws Exception {

    }

    public WorkSpace(FeedCommand feedCommand) {
        this.feedCommand = feedCommand;
        // TODO make setting of all parameters use setters
        this.loop = this.feedCommand.isLoop();
        this.setRetryStrategy(this.feedCommand.isRetry());
        this.setFileTimestampUsage(this.feedCommand.isFileTimestamp());
        this.WORK_SPACE_NAME = this.feedCommand.getWorkspaceName();
        this.simpleMode = this.feedCommand.isSimple();
        this.PROJECT_BASE = this.feedCommand.getProjectBase().toAbsolutePath().toString();
        this.pattern = this.feedCommand.getClientPattern();
        this.outputRootPath = this.feedCommand.getOutputRoot();
        this.eatPrefix = this.feedCommand.getEatPrefix();
        this.FILES_PER_MESSAGE = this.feedCommand.getBundleSize();
        this.dataCaseId = this.feedCommand.getCaseId();
        this.setSkipDotFiles(this.feedCommand.isSkipDotFile());
        this.WANT_DIRECTORIES = this.feedCommand.isIncludeDirs();
        this.setSimpleMode(this.feedCommand.isSimple());
        this.myDirectories.addAll(this.feedCommand.getPriorityDirectories());

        if (null != this.feedCommand.getSort()) {
            this.outbound = new PriorityQueue<>(11, this.feedCommand.getSort());
        }

        configure();
        startJetty();
        initializeService();
    }

    protected void startJetty() {
        if (!emissary.server.EmissaryServer.isStarted()) {
            // TODO investigate passing the feedCommand object directly to the serverCommand
            List<String> args = new ArrayList<String>();
            args.add("-b");
            args.add(PROJECT_BASE);
            args.add("--agents");
            args.add("1"); // feed don't need agents
            args.add("-h");
            args.add(this.feedCommand.getHost());
            args.add("-p");
            args.add(String.valueOf(this.feedCommand.getPort()));
            // feed doesn't make sense in standalone
            args.add("-m");
            args.add("cluster");
            args.add("--flavor");
            args.add(this.feedCommand.getFlavor());
            if (this.feedCommand.isSslEnabled()) {
                args.add("--ssl");
            }
            try {
                // To ensure the feed command starts correctly, depends on a node-{feedCommand.getPort}.cfg file
                ServerCommand cmd = ServerCommand.parse(ServerCommand.class, args);
                Server server = new EmissaryServer(cmd).startServer();
                final boolean jettyStatus = server.isStarted();
                if (!jettyStatus) {
                    logger.error("Cannot start the Workspace due to EmissaryServer not starting!");
                    // throw new Exception("Cannot start embedded jetty server");
                } else {
                    logger.info("Workspace is up and running");
                    this.jettyStartedHere = true;
                }
            } catch (EmissaryException | InstantiationException | IllegalAccessException | ClassNotFoundException e) {
                logger.error("Error starting EmissaryServer! WorkSpace will not start!", e);
            }
        } else {
            logger.info("EmissaryServer is already running, Workspace should be up.");
        }
    }

    protected void initializeService() {
        // Load existing pickup client list
        try {
            this.pups.addAll(getPickUpClients(this.pattern));
            logger.info("Found " + this.pups.size() + " initial clients using " + this.pattern + " in " + getKey());
            logger.debug("Initial pickups : " + this.pups);
        } catch (EmissaryException ex) {
            logger.error("Cannot lookup pickup places using pattern " + this.pattern + " in " + getKey(), ex);
        }

        // Hook our observer onto the local directory
        // so we keep in sync with any changes to the clients
        this.watcher = new WorkSpaceDirectoryWatcher(this.pattern);
        try {
            DirectoryAdapter.register(this.watcher);
        } catch (EmissaryException ex) {
            logger.error("Cannot register directory observer", ex);
        }

        // Must be before we start processing files and directories
        initializeCase();
    }

    /**
     * Start collection of files and monitoring system progress
     */
    @Override
    public void run() {
        // Start the collection of files
        startCollector();

        // Start client notifier
        startNotifier();

        // Start monitoring the system until all work is done
        monitorProgress();

        logger.debug("Ending the WorkSpace run method");
    }

    /**
     * Stop the work space
     */
    public void stop() {
        this.timeToQuit = true;
    }

    /**
     * Shut down services that were started here
     */
    public void shutDown() {
        stop();
        if (this.jettyStartedHere) {
            final EmissaryNode node = new EmissaryNode();
            if (node.isValid()) {
                try {
                    final EmissaryServer s = EmissaryServer.lookup();
                    s.getServer().stop();
                } catch (NamespaceException ex) {
                    logger.error("Cannot find jetty server", ex);
                } catch (Exception ex) {
                    logger.error("Jetty cannot be shutdown", ex);
                }
            }
            try {
                AgentPool.lookup().close();
            } catch (NamespaceException ex) {
                logger.debug("Agent pool namespace lookup failed", ex);
            }
        }
    }

    /**
     * Set the pending hang time, how long to wait after outbound queue is empty
     *
     * @param pendingHangTime in millis
     */
    public void setPendingHangTime(final long pendingHangTime) {
        this.PENDING_HANG_TIME = pendingHangTime;
    }

    /**
     * Set the loop pause time when loop is true
     *
     * @param pauseTimeMillis pause interval in millis
     */
    public void setPauseTime(final long pauseTimeMillis) {
        this.LOOP_PAUSE_TIME = pauseTimeMillis;
    }

    /**
     * Set or unset looping
     */
    public void setLoop(final boolean on) {
        this.loop = on;
    }

    /**
     * Get the value of the loop indicator
     */
    public boolean getLoop() {
        return this.loop;
    }

    /**
     * Set the use of file timestamps to control whether a file is new enough to be added to the queue
     */
    public void setFileTimestampUsage(final boolean value) {
        this.useFileTimestamps = value;
    }

    /**
     * Return whether fileTimestamps can be used for collector queue control
     */
    public boolean getFileTimestampUsage() {
        return this.useFileTimestamps;
    }

    /**
     * Set Retry strategy on or off
     */
    public void setRetryStrategy(final boolean on) {
        this.useRetryStrategy = on;
    }

    /**
     * Get value of the retry strategy indicator
     *
     * @return true if retry strategy in use
     */
    public boolean getRetryStrategy() {
        return this.useRetryStrategy;
    }

    /**
     * Set the directory to monitor at the default priority
     *
     * @deprecated see addDirectory
     */
    @Deprecated
    public void setDirectory(final String dir) {
        final String dirWithTrailingSlash;
        if (dir.endsWith("/")) {
            dirWithTrailingSlash = dir;
        } else {
            dirWithTrailingSlash = dir + "/";
        }
        addDirectory(new PriorityDirectory(dirWithTrailingSlash, Priority.DEFAULT));
    }

    /**
     * Add directory at specified priority to be monitored
     */
    public void addDirectory(final String dir, final int priority) {
        addDirectory(new PriorityDirectory(dir, priority));
    }

    /**
     * Add specified PriorityDirectory object to be monitored
     */
    public void addDirectory(final PriorityDirectory dir) {
        this.myDirectories.add(dir);
        logger.debug("Adding input directory " + dir);
    }

    /**
     * Get the name of the directory being monitored
     *
     * @deprecated see getDirectories
     */
    @Deprecated
    public String getDirectory() {
        return getDirectories().toString();
    }

    public List<String> getDirectories() {
        final List<String> l = new ArrayList<String>();
        final PriorityDirectory[] pds = this.myDirectories.toArray(new PriorityDirectory[0]);
        Arrays.sort(pds);
        for (final PriorityDirectory pd : pds) {
            l.add(pd.toString());
        }
        return l;
    }

    /**
     * Set directory processing flag. When true directory entries are retrieved from the input area just like normal files.
     *
     * @see emissary.util.io.FileFind
     * @param on the new value for directory retrieval
     */
    public void setDirectoryProcessing(final boolean on) {
        this.WANT_DIRECTORIES = on;
    }

    /**
     * Reset the eatprefix for this workspace
     *
     * @param prefix the new prefix
     */
    public void setEatPrefix(final String prefix) {
        logger.debug("Reset eatPrefix to " + prefix);
        this.eatPrefix = prefix;
        normalizeEatPrefix();
    }

    /**
     * Make sure the eatPrefix is in canonical form
     */
    protected void normalizeEatPrefix() {
        if (this.eatPrefix != null && this.eatPrefix.contains("//")) {
            this.eatPrefix = this.eatPrefix.replaceAll("/+", "/");
        }
    }

    /**
     * Reset the outputRoot
     *
     * @param value the new outputRoot value
     */
    public void setOutputRoot(final String value) {
        logger.debug("Reset outputRoot to " + value);
        this.outputRootPath = value;
    }

    /**
     * Get the value of the configured outputRoot
     */
    public String getOutputRoot() {
        return this.outputRootPath;
    }

    /**
     * Reset the case id
     *
     * @param value the new value for caseId
     */
    public void setCaseId(final String value) {
        logger.debug("Reset caseId to " + value);
        this.dataCaseId = value;
    }

    /**
     * Reset the skipDotFiles flag
     *
     * @param value the new value for the skipDotFiles flag
     */
    public void setSkipDotFiles(final boolean value) {
        this.skipDotFiles = value;
    }

    /**
     * Set the debug flag
     *
     * @param value the new value for the debug flag
     */
    public void setDebugFlag(final boolean value) {
        this.debug = value;
    }

    /**
     * Set the simple mode flag
     *
     * @param value the new value for the flag
     */
    public void setSimpleMode(final boolean value) {
        this.simpleMode = value;
    }

    /**
     * Get the value of the simple mode flag
     */
    public boolean getSimpleMode() {
        return this.simpleMode;
    }

    /**
     * Set the pattern for finding pickup clients
     *
     * @param thePattern the new pattern
     * @see emissary.directory.KeyManipulator#gmatch(String,String)
     */
    public void setPattern(final String thePattern) throws Exception {

        if ((this.pattern != null) && (thePattern != null) && this.pattern.equals(thePattern)) {
            logger.debug("The pattern is already set to " + thePattern);
            return;
        }

        this.pattern = thePattern;

        // Clear out old pick up clients
        logger.warn("Clearing client list so we can look for new pattern " + thePattern + " in " + getKey());
        this.pups.clear();

        // Find new ones
        this.pups.addAll(getPickUpClients(this.pattern));

        // Set up a new observer on the directory
        if (this.watcher != null) {
            DirectoryAdapter.remove(this.watcher);
        }
        this.watcher = new WorkSpaceDirectoryWatcher(this.pattern);
        DirectoryAdapter.register(this.watcher);
    }

    /**
     * Configure the Processor. The *.cfg file is optional
     */
    protected void configure() {
        final EmissaryNode node = new EmissaryNode();
        if (node.isValid()) {
            this.workSpaceUrl = node.getNodeScheme() + "://" + node.getNodeName() + ":" + node.getNodePort() + "/" + this.WORK_SPACE_NAME;
        } else {
            this.workSpaceUrl = "http://localhost:8001/" + this.WORK_SPACE_NAME;
            logger.warn("WorkSpace is not running in a valid emissary node." + " Using URL " + this.workSpaceUrl);
        }
        this.workSpaceKey = "WORKSPACE.WORK_SPACE.INPUT." + this.workSpaceUrl;

        normalizeEatPrefix();

        // Need to bind so WorkSpaceTakeWorker can find us on the callback
        // The url we use to bind is in the advertisement to clients
        emissary.core.Namespace.bind(this.workSpaceUrl, this);
    }


    /**
     * Get the initial list of pick up client places from the local directory. Our observer will keep us in sync after this
     * initial pull. This method does not cause clients to be notified.
     *
     * @param thePattern the key pattern to match for places of interest
     */
    protected Set<String> getPickUpClients(final String thePattern) throws EmissaryException {
        final Set<String> thePups = new HashSet<String>();
        final IDirectoryPlace dir = DirectoryPlace.lookup();
        final List<DirectoryEntry> list = dir.getMatchingEntries(thePattern);
        for (final DirectoryEntry d : list) {
            thePups.add(d.getKey());
            logger.info("Adding pickup client " + d.getKey());
        }
        logger.debug("Found " + thePups.size() + " initial pickup client entries");
        return thePups;
    }

    /**
     * Start the file collector threads, one per directory
     */
    public void startCollector() {
        for (final PriorityDirectory pd : this.myDirectories) {
            final WorkSpaceCollector collector = new WorkSpaceCollector(pd);
            final Thread collectorThread = new Thread(collector, "WorkSpace Collector " + pd);
            collectorThread.setDaemon(true);
            collectorThread.start();
            logger.debug("Started WorkSpace Collector thread on " + pd);
        }
    }

    /**
     * Start the client notification Thread*
     */
    public void startNotifier() {
        this.notifier = new ClientNotifier();
        final Thread notifierThread = new Thread(this.notifier, "WorkSpace Client Notifier");
        notifierThread.setDaemon(true);
        notifierThread.start();
        logger.debug("Started Client Notifier thread");
    }

    /**
     * Rotate the list of pickups so that the same old one isn't always first on the list.
     */
    protected void rotatePickUps() {
        // Move element(0) to the tail and shift all to the left
        Collections.rotate(this.pups, -1);
    }

    /**
     * Notify pick up place that data is available
     *
     * @return number of successful notices
     */
    protected int notifyPickUps() {
        int successCount = 0;
        for (final String pup : this.pups) {
            final boolean status = notifyPickUp(pup);
            if (status) {
                successCount++;
            }
            if (getOutboundQueueSize() == 0) {
                break;
            }
        }
        logger.debug("Notified " + successCount + " of " + this.pups.size() + " pickup places");
        return successCount;
    }

    /**
     * Add one pickup and notify of work to be done
     */
    protected void addPickUp(final String pup) {
        if (!this.pups.contains(pup)) {
            this.pups.add(pup);
            if (logger.isDebugEnabled()) {
                logger.debug("Adding pickup " + pup + ", new size=" + this.pups.size() + ": " + this.pups);
            }
        } else {
            logger.debug("Not adding " + pup + " already on list size " + this.pups.size());
        }
    }

    /**
     * Notify one pickup
     *
     * @param pup the key of the one to notify
     */
    protected boolean notifyPickUp(final String pup) {
        final WorkSpaceAdapter tpa = new WorkSpaceAdapter();
        logger.debug("Sending notice to " + pup);

        boolean notified = false;
        int tryCount = 0;

        while (!notified && tryCount < 5) {
            final EmissaryResponse status = tpa.outboundOpenWorkSpace(pup, this.workSpaceKey);

            // TODO Consider putting this method in the response
            if (status.getStatus() != HttpStatus.SC_OK) {
                logger.warn("Failed to notify " + pup + " on try " + tryCount + ": " + status.getContentString());
                try {
                    Thread.sleep((tryCount + 1) * 100);
                } catch (InterruptedException ignore) {
                    // empty catch block
                }
            } else {
                notified = true;
            }
            tryCount++;
        }

        if (logger.isInfoEnabled()) {
            logger.info("Notified " + pup + " in " + tryCount + " attempts: " + (notified ? "SUCCESS" : "FAILED"));
        }

        return notified;
    }

    /**
     * Return the registration key for this work space
     */
    public String getKey() {
        return this.workSpaceKey;
    }

    /**
     * Return the workspace name
     */
    public String getNamespaceName() {
        return this.WORK_SPACE_NAME;
    }

    /**
     * Remove a pickup, if it had work bundles pending completion transfer them back to the outbound queue
     *
     * @param remoteKey the directory observer string key that was removed
     */
    protected void removePickUp(final String remoteKey) {
        this.pups.remove(remoteKey);
        if (logger.isDebugEnabled()) {
            logger.debug("Removed pickup " + remoteKey + ", size=" + this.pups.size() + ": " + this.pups);
        }
        int pendCount = 0;
        final String remoteName = KeyManipulator.getServiceHost(remoteKey);
        synchronized (this.QLOCK) {
            // NB: no enhanced for loop with Iterator.remove()
            for (Iterator<String> i = this.pending.keySet().iterator(); i.hasNext();) {
                final String id = i.next();
                final WorkBundle wb = this.pending.get(id);
                if (remoteName.equals(wb.getSentTo())) {
                    i.remove(); // remove from pending
                    wb.setSentTo(null); // clear in progress indicator
                    this.retryCount++;
                    if (wb.incrementErrorCount() <= this.MAX_BUNDLE_RETRIES) {
                        logger.debug("Removing pending bundle " + wb.getBundleId() + " from pending pool, re-adding to outbound with errorCount="
                                + wb.getErrorCount());
                        addOutboundBundle(wb); // send to outbound again
                        pendCount++;

                        // Set overall counts back to normal
                        this.bundlesProcessed--;
                    } else {
                        logger.error("Bundle " + wb + " associated with too " + "many failures, permanently discarding");
                    }
                }
            }
        }
        if (pendCount > 0) {
            logger.info("Moved " + pendCount + " items back to outbound queue from " + remoteName);
        }
    }

    /**
     * Method called by remote PickUp client instances when they are ready to reteive data from this WorkSpace Access via
     * emissary.comms.http.WorkSpaceApapter
     *
     * @param remoteKey key of the requesting PickUp place
     * @return WorkBundle at the head of the list or null if empty
     */
    public WorkBundle take(final String remoteKey) {
        final String remoteName = KeyManipulator.getServiceHost(remoteKey);
        WorkBundle item = null;
        synchronized (this.QLOCK) {
            if (getOutboundQueueSize() == 0) {
                // Empty WorkBundle will let them know to stop asking us
                logger.info("Sent shutdown msg to " + remoteName);
                this.stats.shutDownSent(remoteName);
                item = new WorkBundle();
            } else {
                // transfer from outbound to pending list and
                // record who the work was given to to track
                // completion status
                this.stats.bump(remoteName);
                item = this.outbound.poll();
                item.setSentTo(remoteName);
                this.pending.put(item.getBundleId(), item);
                logger.info("Gave bundle " + item + " to " + remoteName);
                final WorkBundle nextItem = this.outbound.peek();
                if (nextItem != null) {
                    logger.info("After take: new top differs to prior by [oldest/youngest/size]=["
                            + ((long) nextItem.getOldestFileModificationTime() - item.getOldestFileModificationTime()) + "/"
                            + ((long) nextItem.getYoungestFileModificationTime() - item.getYoungestFileModificationTime()) + "/"
                            + ((long) nextItem.getTotalFileSize() - item.getTotalFileSize()) + "]");
                }
            }
        }
        return item;
    }

    /**
     * Add a new bundle of work to the pending queue
     *
     * @param wb the new bundle
     */
    protected void addOutboundBundle(final WorkBundle wb) {
        int sz = 0;
        synchronized (this.QLOCK) {
            this.bundlesProcessed++;
            sz = this.outbound.size();
            this.outbound.add(wb);
            addFilesSeen(wb.getFileNameList());
        }

        if (logger.isInfoEnabled()) {
            logger.info("Adding workbundle " + wb + " size " + (sz + 1) + " filesSeen " + this.filesSeen.size());
        }
    }

    /**
     * Show items that are pending completion (debug)
     */
    public String[] showPendingItems() {
        final List<String> list = new ArrayList<String>();
        synchronized (this.QLOCK) {
            for (final Map.Entry<String, WorkBundle> entry : this.pending.entrySet()) {
                list.add(entry.getValue().toString());
            }
        }
        return list.toArray(new String[0]);
    }

    /**
     * Clear the pending queue
     *
     * @return number of items removed
     */
    public int clearPendingQueue() {
        final int size = getPendingQueueSize();

        if (size > 0) {
            synchronized (this.QLOCK) {
                logger.debug("Clearing pending queue of " + size + " items");
                for (final Map.Entry<String, WorkBundle> entry : this.pending.entrySet()) {
                    removeFilesSeen(entry.getValue().getFileNameList());
                }
                this.pending.clear();
                logger.debug("Cleared filesSeen leaving " + this.filesSeen.size() + " items");
            }
        }
        return size;
    }

    /**
     * Receive notice that a bundle was completed Normally called from emissary.server.mvc.adapters.WorkSpaceAdapter when a
     * bundle completion message is received from the remote client doing the processing.
     *
     * @param remoteName the name of the place that did the processing
     * @param bundleId the unique id of the bundle that was completed
     * @param itWorked true if processed normally
     * @return true if the item was removed from the pending list
     */
    public boolean workCompleted(final String remoteName, final String bundleId, final boolean itWorked) {
        WorkBundle item = null;

        synchronized (this.QLOCK) {
            item = this.pending.remove(bundleId);
            if (item != null) {
                addFilesDone(item.getFileNameList());
                removeFilesSeen(item.getFileNameList());
                logger.debug("Removed " + item.size() + " from filesSeen leaving " + this.filesSeen.size());
            }
        }
        if (item == null) {
            logger.info("Unknown bundle completed: " + bundleId);
        } else if (!itWorked) {
            item.setSentTo(null); // clear in progress indicator
            if (item.incrementErrorCount() > this.MAX_BUNDLE_RETRIES) {
                logger.error("Bundle " + item + " has too many " + " errors, permanently discarded");
            } else {
                addOutboundBundle(item); // send to outbound again
            }
        }
        logger.debug("Bundle " + bundleId + " completed by " + remoteName
                + (itWorked ? "" : (" but failed for the " + (item != null ? item.getErrorCount() : -1) + " time")));
        return item != null;
    }

    /**
     * begin the case processing, does nothing in this implementation
     */
    protected void initializeCase() {
        // Dont care in this implementation
        logger.debug("In base initializeCase implementation (do nothing)");
    }

    /**
     * end the case processing does nothing in this implementation
     */
    protected void closeCase() {
        // Dont care in this implementation
        this.caseClosed = true;
        logger.debug("In base closeCase implementation (do nothing)");
    }

    /**
     * handle getting a directory in the recursive descent
     *
     * @param dir File for which isDirectory returns true
     */
    protected void processDirectory(final File dir) {
        // We don't care in this implementation
        logger.debug("got a directory processDirectory(" + dir + ")");
    }

    /**
     * Add each fileName and its respective lastModifiedDate to the filesSeen list
     *
     * @param fileNames the collection of file name strings to add
     */
    protected void addFilesSeen(final Collection<String> fileNames) {
        for (final String fn : fileNames) {
            this.filesSeen.put(fn, Long.valueOf(getFileModificationDate(fn)));
        }
    }

    /**
     * Add each fileName and its respective lastModifiedDate to the filesDone list
     *
     * @param fileNames the collection of file name strings to add
     */
    protected void addFilesDone(final Collection<String> fileNames) {
        for (final String fn : fileNames) {
            this.filesDone.put(fn, Long.valueOf(getFileModificationDate(fn)));
        }
    }

    /**
     * Remove each fileName from the filesSeen list without regard to the timestamp
     *
     * @param fileNames the collection of file name strings to remove
     */
    protected void removeFilesSeen(final Collection<String> fileNames) {
        for (final String fn : fileNames) {
            this.filesSeen.remove(fn);
        }
    }

    /**
     * Lookup a lastModified date for a file
     *
     * @param fn the filename
     * @return the long representing the date of last modification or 0L if an error or it does not exist
     */
    protected long getFileModificationDate(final String fn) {
        return new File(fn).lastModified();
    }

    protected long getFileSize(final String fn) {
        return new File(fn).length();
    }

    /**
     * Monitoring progress of the WorkSpace. Indicate some stats once in a while and do not let the foreground thread
     * terminate while there is still work on the outbound queue or the pending lists.
     */
    protected void monitorProgress() {
        long outboundEmptyTimestamp = -1L;

        // Do while outbound or pending work exists or collector is
        // still running
        while (true) {
            final int outboundSize = getOutboundQueueSize();
            int pendingSize = getPendingQueueSize();
            final boolean reallyQuit = this.timeToQuit && (outboundSize == 0) && (pendingSize == 0);

            // Rmember when outbound becomes empty
            if (outboundSize == 0 && outboundEmptyTimestamp == -1L) {
                outboundEmptyTimestamp = System.currentTimeMillis();
            } else if (outboundSize > 0 && outboundEmptyTimestamp > 0L) {
                outboundEmptyTimestamp = -1L;
            }

            // See if it is time to give up on pending items
            if ((outboundSize == 0) && !this.loop && ((outboundEmptyTimestamp + this.PENDING_HANG_TIME) < System.currentTimeMillis())) {
                if (logger.isInfoEnabled()) {
                    logger.info("Giving up on " + pendingSize + " items due to timeout");
                    for (final Map.Entry<String, WorkBundle> entry : this.pending.entrySet()) {
                        logger.info("Pending item " + entry.getKey() + ": " + entry.getValue());
                    }
                }
                clearPendingQueue();
                pendingSize = 0;
            }

            // All work is done and collector has finished
            if (outboundSize + pendingSize == 0) {
                if (reallyQuit) {
                    break;
                }
                publishStats();
            }

            // Else sleep a while
            try {
                for (int si = 0; si < 3000; si++) {
                    Thread.sleep(10L);
                    if (reallyQuit) {
                        break;
                    }
                }
            } catch (InterruptedException ex) {
                // empty catch block
            }

            if (!this.timeToQuit) {
                publishStats();
            }
        }

        // Case closing actions
        closeCase();
    }

    /**
     * Output some information to the logger on what we have been doing lately
     */
    public void publishStats() {
        logger.info(getStatsMessage());
        for (Iterator<String> i = this.stats.machinesUsed(); i.hasNext();) {
            final String machine = i.next();
            logger.info("Machine " + machine + " took " + this.stats.getCountUsed(machine) + " bundles");
        }
    }

    /**
     * Return the current stats to the caller
     */
    public String getStatsMessage() {
        final int outboundSize = getOutboundQueueSize();
        final int pendingSize = getPendingQueueSize();

        return "WorkSpace has outbound=" + outboundSize + ", pending=" + pendingSize + ", total bundles / files / bytes = " + this.bundlesProcessed
                + " / " + this.filesProcessed + " / " + this.bytesProcessed + " , #clients=" + getPickUpPlaceCount();
    }

    /**
     * Return how many files processed so far
     */
    public long getFilesProcessed() {
        return this.filesProcessed;
    }

    /**
     * Return how many bytes processed so far
     */
    public long getBytesProcessed() {
        return this.bytesProcessed;
    }

    /**
     * Return how many pickup places are being fed
     */
    public int getPickUpPlaceCount() {
        return this.pups.size();
    }

    /**
     * Return how many bundles processed so far
     */
    public long getBundlesProcessed() {
        return this.bundlesProcessed;
    }

    /**
     * Return size of outbound queue
     */
    public int getOutboundQueueSize() {
        synchronized (this.QLOCK) {
            return this.outbound.size();
        }
    }

    public int getRetriedCount() {
        return this.retryCount;
    }

    /**
     * Return size of pending completion queue
     */
    public int getPendingQueueSize() {
        synchronized (this.QLOCK) {
            return this.pending.size();
        }
    }

    /**
     * Overridable point to get the version string for output periodically when looping
     *
     * @return the version info
     */
    protected String getVersionString() {
        return "Emissary version: " + new emissary.util.Version();
    }

    public class ClientNotifier implements Runnable {
        /**
         * Create the notifier Runnable
         */
        public ClientNotifier() {}

        @Override
        public void run() {
            while (true) {
                final int qsize = getOutboundQueueSize();
                if (qsize > 0) {
                    final long start = System.currentTimeMillis();
                    if (logger.isDebugEnabled()) {
                        logger.debug("ClientNotification starting with #clients=" + getPickUpPlaceCount() + " outbound=" + qsize);
                    }
                    notifyPickUps();
                    if (logger.isDebugEnabled()) {
                        final long end = System.currentTimeMillis();
                        logger.debug("ClientNotification took " + (end - start) / 1000.0 + "s for #clients=" + getPickUpPlaceCount());
                    }
                }

                try {
                    Thread.sleep(WorkSpace.this.NOTIFIER_PAUSE_TIME);
                    rotatePickUps();
                } catch (InterruptedException ignore) {
                    // empty catch block
                }

                final int outboundSize = getOutboundQueueSize();
                final int pendingSize = getPendingQueueSize();
                if (WorkSpace.this.timeToQuit && (outboundSize == 0) && (pendingSize == 0) && WorkSpace.this.collectorThreadHasQuit) {
                    break;
                }
            }

            logger.debug("Off the end of the ClientNotifier run loop");
        }
    }

    /**
     * A runnable to collect files into WorkBundles and put them on the outbound queue
     */
    public class WorkSpaceCollector implements Runnable {

        protected PriorityDirectory myDirectory;

        /**
         * Create the collector runnable
         */
        public WorkSpaceCollector(final PriorityDirectory myDirectory) {
            this.myDirectory = myDirectory;
        }

        /**
         * Pull all of the files into bundles, emit some stats, and notify the PickUp client instances to start work. When the
         * list of file bundles is empty we can quit or loop around again.
         */
        @Override
        public void run() {
            long versionOutputTime = System.currentTimeMillis();
            long start = versionOutputTime;
            long stop = start;
            long minFileTime = 0L;

            // Run the processing
            long lastFileCollect = 0L;
            int loopCount = 0;

            logger.info("Running Workspace from " + getVersionString());

            do {
                start = System.currentTimeMillis();
                // every hour
                if (start - versionOutputTime > 3600000) {
                    logger.info("Continuing Workspace from " + getVersionString());
                    versionOutputTime = start;
                }

                final WorkBundle paths = new WorkBundle(WorkSpace.this.outputRootPath, WorkSpace.this.eatPrefix);
                paths.setCaseId(WorkSpace.this.dataCaseId);
                paths.setSimpleMode(getSimpleMode());

                logger.debug("Processing files in " + this.myDirectory.getDirectoryName());

                final int collectCount =
                        collectFiles(this.myDirectory, WorkSpace.this.WANT_DIRECTORIES, paths, WorkSpace.this.numberOfBundlesToSkip, minFileTime,
                                WorkSpace.this.skipDotFiles);

                // Set times so we dont redistribute files next loop
                // if configured to use timestamps
                if (WorkSpace.this.useFileTimestamps) {
                    lastFileCollect = System.currentTimeMillis();
                }
                stop = System.currentTimeMillis();
                loopCount++;

                // We can only skip bundles on the first time through
                WorkSpace.this.numberOfBundlesToSkip = 0;

                logger.info("Collected " + collectCount + " file bundles in " + ((stop - start) / 1000.0) + "s in loop iteration " + loopCount + ", "
                        + WorkSpace.this.outbound.size() + " items in outbound queue");

                if ((collectCount == 0) && WorkSpace.this.loop) {
                    // Wait pause time seconds and try again if looping
                    try {
                        Thread.sleep(WorkSpace.this.LOOP_PAUSE_TIME);
                    } catch (InterruptedException ioex) {
                        // empty catch block
                    }
                    continue;
                }

                // time shift for next loop if configured to use tstamps
                if (WorkSpace.this.useFileTimestamps) {
                    minFileTime = lastFileCollect;
                }

            } while (WorkSpace.this.loop && !WorkSpace.this.timeToQuit);

            logger.debug("Off the end of the WorkSpaceCollector run method");
            WorkSpace.this.collectorThreadHasQuit = true;
        }

        /**
         * Load WorkBundle objects into our linked list of bundles Also process all directories if so instructed
         *
         * @return count of how many bundles collected for outbound queue
         */
        protected int collectFiles(final PriorityDirectory dir, final boolean wantDirectories, final WorkBundle basePath,
                final int numberOfBundlesToSkipArg, final long minFileTime, final boolean skipDotFilesArg) {
            int skipped = 0;
            int collected = 0;
            int fileCount = 0;
            long bytesInBundle = 0;

            try {
                int ff_options = FileFind.FILES_FLAG;
                if (wantDirectories) {
                    ff_options |= FileFind.DIRECTORIES_FLAG;
                }
                final FileFind ff = new FileFind(ff_options);
                final Iterator<?> f = ff.find(dir.getDirectoryName());

                WorkBundle paths = new WorkBundle(basePath);
                paths.setPriority(dir.getPriority());
                paths.setSimpleMode(getSimpleMode());

                while (f.hasNext()) {
                    // If the outbound queue has a lot of stuff pending
                    // and memory is getting tight, just to sleep until
                    // the situation eases
                    pauseCollector();

                    final File next = (File) f.next();
                    final String fileName = next.getPath();

                    // We should only be getting these if we asked for them
                    // We should only use them if we are not resuming a
                    // previous run.
                    if (next.isDirectory() && numberOfBundlesToSkipArg == 0) {
                        logger.debug("Doing directory " + fileName);
                        processDirectory(next);
                        continue;
                    }

                    // Can we read the file?
                    if (!next.isFile() && !next.canRead()) {
                        logger.debug("Cannot access file: " + fileName);
                        continue;
                    }

                    // Skip dot files possibly
                    // TODO Maybe we want to change this to explicitly look for "." instead of isHidden
                    if (skipDotFilesArg && Files.isHidden(Paths.get(fileName))) {
                        logger.debug("Skipping dot file " + fileName);
                        continue;
                    }

                    // Is file too old? (If we aren't configured to use
                    // tstamps minFileTime will always be 0L
                    if (next.lastModified() < minFileTime) {
                        continue;
                    }

                    synchronized (WorkSpace.this.QLOCK) {
                        if (WorkSpace.this.filesDone.containsKey(fileName)) {
                            WorkSpace.this.filesDone.remove(fileName);
                            continue;
                        } else if (WorkSpace.this.filesSeen.containsKey(fileName)
                                && WorkSpace.this.filesSeen.get(fileName).longValue() == next.lastModified()) {
                            logger.debug("Skipping file already seen " + fileName + ", touch file to force add");
                            continue;
                        }
                    }

                    logger.debug("Adding filename to bundle " + fileName);

                    // add file to workbundle (at least 1)
                    if (workbundleHasRoom(paths, bytesInBundle)) {
                        logger.debug("Added file to workbundle: " + fileName);
                        paths.addFileName(fileName, Long.valueOf(getFileModificationDate(fileName)), getFileSize(fileName));
                        bytesInBundle += next.length();
                        WorkSpace.this.filesProcessed++; // overall
                        fileCount++; // this loop
                        WorkSpace.this.bytesProcessed += next.length(); // overall
                    }
                    // if bundle is full, create a new empty and
                    // move it to the outbound queue.
                    if (!workbundleHasRoom(paths, bytesInBundle)) {
                        logger.debug("Workbundle full, adding it to outbound queue");
                        if (skipped < numberOfBundlesToSkipArg) {
                            skipped++;
                        } else {
                            addOutboundBundle(paths);
                            collected++;
                        }
                        // create new empty work bundle
                        paths = new WorkBundle(basePath);
                        paths.setPriority(dir.getPriority());
                        paths.setSimpleMode(getSimpleMode());
                        bytesInBundle = 0;
                    }

                } // end while f.hasNext()

                // Send residual files, not a complete set perhaps
                if (paths.size() > 0) {
                    if (skipped < numberOfBundlesToSkipArg) {
                        logger.info("Skipping last bundle");
                    } else {
                        addOutboundBundle(paths);
                        collected++;
                    }
                }
                // clear the files done list
                synchronized (WorkSpace.this.QLOCK) {
                    WorkSpace.this.filesDone.clear();
                }
            } catch (Exception e) {
                logger.error("System error", e);
                return collected;
            }

            if (WorkSpace.this.outbound.size() > 0) {
                logger.info("Processed " + fileCount + " files into " + collected + " bundles, skipping " + skipped + " bundles.");
            }
            return collected;
        }

        /**
         * Convenience method to check if there is room in the work bundle to add more files.
         *
         * @param bundle the bundle to check
         * @param bytesInBundle the current count of bytes in the bundle.
         * @return true if bundle does not exceed max byte size, or max file count.
         */
        private boolean workbundleHasRoom(final WorkBundle bundle, final long bytesInBundle) {
            boolean bReturn = true;

            // must have a min size of 1 file, but cannot be over the
            // max byte size, or max file count
            if ((bundle.size() > 0)
                    && (((WorkSpace.this.MAX_BUNDLE_SIZE > -1) && (bytesInBundle >= WorkSpace.this.MAX_BUNDLE_SIZE))
                            || ((WorkSpace.this.FILES_PER_MESSAGE > -1) && (bundle
                                    .size() >= WorkSpace.this.FILES_PER_MESSAGE)))) {
                bReturn = false;
            }

            logger.debug("workbundle has room = " + bReturn);
            return bReturn;
        }

        /**
         * Check memory (heap) usage and wait for it to go below the threshold. We must be able to collect at least 500 file
         * bundles to trigger this mechanism.
         */
        protected void pauseCollector() {
            final int initialQueueSize = getOutboundQueueSize();
            if (initialQueueSize < 500) {
                return;
            }
            final long intv = 30000;
            final MemoryMXBean mbean = ManagementFactory.getMemoryMXBean();
            MemoryUsage heap = mbean.getHeapMemoryUsage();
            int count = 0;
            while ((((double) heap.getUsed() / (double) heap.getCommitted()) > WorkSpace.this.MEM_THRESHOLD) && (getOutboundQueueSize() > 500)) {
                logger.debug("Collection memory threshold exceeded " + heap);
                try {
                    Thread.sleep(intv);
                } catch (InterruptedException ex) {
                    // empty catch block
                }
                count++;
                heap = mbean.getHeapMemoryUsage();
            }

            if (count > 0 && logger.isDebugEnabled()) {
                logger.debug("Paused collector " + count + " times for " + (intv / 1000) + "s waiting for memory usage to " + "go below threshold "
                        + WorkSpace.this.MEM_THRESHOLD + " resuming at " + heap + ", queueSize was/is=" + initialQueueSize + "/"
                        + getOutboundQueueSize());
            }
        }
    }

    /**
     * Collect per pickup statistics for this run
     */
    public static class WorkSpaceStats {
        final Map<String, Integer> remoteMap = new HashMap<String, Integer>();
        final Set<String> shutDownSent = new HashSet<String>();

        /**
         * Increment the bundle count for the machine when it takes one
         *
         * @param machine the remote pickup
         */
        public void bump(final String machine) {
            Integer count = this.remoteMap.get(machine);
            if (count == null) {
                count = Integer.valueOf(1);
            } else {
                count = Integer.valueOf(count.intValue() + 1);
            }
            this.remoteMap.put(machine, count);
        }

        /**
         * Indicate that shutdown msg was sent to machine
         *
         * @param machine the remote name
         */
        public void shutDownSent(final String machine) {
            this.shutDownSent.add(machine);
        }

        /**
         * Count how many machines got shutdown msg
         */
        public int getShutDownCount() {
            return this.shutDownSent.size();
        }

        /**
         * Iterate over set of machines uese
         */
        public Iterator<String> machinesUsed() {
            return this.remoteMap.keySet().iterator();
        }

        /**
         * Count of machines used
         */
        public int getCountUsed(final String machine) {
            final Integer count = this.remoteMap.get(machine);
            return (count == null) ? 0 : count.intValue();
        }
    }

    /**
     * Watch the directory for changes to pickup up client places
     */
    public class WorkSpaceDirectoryWatcher extends DirectoryAdapter {
        /**
         * Watch the directory for registrations that match pattern
         *
         * @param pattern the pattern to match
         */
        public WorkSpaceDirectoryWatcher(final String pattern) {
            super(pattern);
            logger.debug("PickupClient pattern is " + pattern);
        }

        /**
         * Accept registration notices that match our pattern
         *
         * @param observableKey the reporting directory
         * @param placeKey the key of the matching registered place
         */
        @Override
        public void placeRegistered(final String observableKey, final String placeKey) {
            final String k = KeyManipulator.removeExpense(placeKey);
            logger.debug("Registration message from " + k);
            if (WorkSpace.this.pups.contains(k) && WorkSpace.this.useRetryStrategy) {
                // This covers the case where the pickup dies and restarts
                // before the Heartbeat mechanism figures out there was
                // a problem.
                logger.info("Already known pickup " + k + " must be reinitialized to clear pending work.");
                removePickUp(k);
            }

            if (!WorkSpace.this.pups.contains(k)) {
                logger.info("New pickup place " + k);
            }

            // add to list and maybe send open msg. Dup places
            // will not be added but might be re-notified
            addPickUp(k);

        }

        /**
         * Accept deregistration notices that match our pattern
         *
         * @param observableKey the reporting directory
         * @param placeKey the key of the matching deregistered place
         */
        @Override
        public void placeDeregistered(final String observableKey, final String placeKey) {
            final String k = KeyManipulator.removeExpense(placeKey);
            logger.debug("DeRegistration message from " + k);
            if (!WorkSpace.this.pups.contains(k)) {
                logger.info("Unknown pickup deregistered " + k);
            } else {
                logger.info("Pickup place " + k + " is gone");
                if (WorkSpace.this.useRetryStrategy) {
                    removePickUp(k);
                }
            }
        }
    }
}
