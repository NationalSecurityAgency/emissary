package emissary.core;

import java.io.Serializable;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import emissary.directory.DirectoryEntry;
import emissary.directory.DirectoryPlace;
import emissary.directory.KeyManipulator;
import emissary.log.MDCConstants;
import emissary.place.CoordinationPlace;
import emissary.place.EmptyFormPlace;
import emissary.place.IServiceProviderPlace;
import emissary.pool.AgentPool;
import emissary.pool.AgentThreadGroup;
import emissary.util.JMXUtil;
import emissary.util.PayloadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * An autonomous hunk of software
 */
public abstract class MobileAgent implements Serializable, IMobileAgent, MobileAgentMBean, Runnable {

    // Serializable
    static final long serialVersionUID = 2656898442450171891L;

    // Our logger
    protected static final Logger logger = LoggerFactory.getLogger(MobileAgent.class);

    // Probe logger
    protected static final Logger probeLogger = LoggerFactory.getLogger(MobileAgent.class.getPackage().toString() + ".PROBE");

    // The thread we plan to run on (we are autonomous, in a limited sense)
    protected transient Thread thread = null;

    // Name for our threads
    public static final String AGENT_THREAD = "MobileAgent-".intern();
    private static int AGENT_COUNTER = 0;

    // For tracking errors
    protected int MAX_MOVE_ERRORS = 3;

    // For stopping infinite loops
    protected int MAX_ITINERARY_STEPS = 100;

    // Stages of processing
    protected static final Stage STAGES = new Stage();
    protected static final String ERROR_FORM = Form.ERROR.intern();
    protected static final String DONE_FORM = Form.DONE.intern();

    // What we carry around with us
    protected IBaseDataObject payload = null;

    // Place we are at now
    protected transient IServiceProviderPlace arrivalPlace = null;
    protected boolean processFirstPlace = false;
    protected String lastPlaceProcessed = null;

    // ID string for this agent
    protected static final String NO_AGENT_ID = "No_AgentID_Set".intern();
    protected transient String agentID = NO_AGENT_ID;
    private static final String TG_ID = "Agent Threads".intern();

    // This might not be needed anymore, not carried with agent on a move...
    final Set<String> visitedPlaces = new HashSet<>();

    // To externally control the runnable loop
    protected transient volatile boolean timeToQuit = false;

    // Queue of DirectoryEntry keys to be processed
    protected LinkedList<DirectoryEntry> nextKeyQueue = new LinkedList<>();

    // Track moveErrors on all parts of a given payload
    protected int moveErrorsOccurred = 0;

    /**
     * Still have an uncaught exception handler but not really in a true ThreadGroup with other agents
     */
    public MobileAgent() {
        this(new AgentThreadGroup(TG_ID), AGENT_THREAD + (AGENT_COUNTER++));
    }

    /**
     * Create a new reusable Agent
     *
     * @param threadGroup group we operate it
     * @param threadName symbolic name for this agent thread
     */
    public MobileAgent(final ThreadGroup threadGroup, final String threadName) {
        logger.debug("Constructing agent {}", threadName);
        this.thread = new Thread(threadGroup, this, threadName);
        this.thread.setPriority(Thread.NORM_PRIORITY);
        this.thread.setDaemon(true);
        this.thread.start();

        JMXUtil.registerMBean(this);
    }

    /**
     * Report this agents name for logging purposes
     */
    @Override
    public String getName() {
        return this.thread.getName();
    }

    /**
     * Runnable interface, starts this agent running on it's own thread. It will wait unless it has a payload and a place to
     * start with. You can set both of these items at once using the <em>go</em> method, which will then notify us to come
     * out of the wait state and process the payload
     */
    @Override
    public void run() {
        logger.debug("Starting the 'run' loop");
        synchronized (this) {
            while (!this.timeToQuit) {

                if (!isInUse()) {
                    try {
                        // MAX time in case we miss a notify
                        // we bail out every 60 seconds just
                        // as a last resort
                        wait(60000);
                    } catch (InterruptedException e) {
                        // empty catch block
                    }
                }

                // Allow communications threads to take priority
                // Thread.yield();

                if (isInUse()) {
                    logger.debug("Starting work for {}", agentID());
                    MDC.put(MDCConstants.SHORT_NAME, getPayload().shortName());
                    try {
                        agentControl(this.arrivalPlace);
                    } catch (Throwable throwable) {
                        logger.error("Problem with agent", throwable);
                    } finally {
                        // prevent an interrupted thread from returning the agent
                        if (!this.timeToQuit) {
                            agentReturn();
                            MDC.clear(); // clear all MDC context
                        }
                    }
                }
            }
        }
    }

    /**
     * Call this method to permanently stop the running thread when we finish what we are doing
     */
    @Override
    public void killAgent() {
        logger.debug("killAgent called on {}", getName());
        synchronized (this) {
            this.timeToQuit = true;
            notifyAll();
        }
    }

    /**
     * Kill asynchronously
     */
    @Override
    public void killAgentAsync() {
        logger.debug("killAgentAsync called on {}", getName());
        this.timeToQuit = true;
        try {
            this.thread.setPriority(Thread.MIN_PRIORITY);
            this.thread.interrupt();
        } catch (Exception ignored) {
            // empty catch block
        }
    }

    /**
     * Report whether we are busy or not
     */
    @Override
    public boolean isInUse() {
        // This is deliberately non-synchronized access
        // to a normally synchronized instance variable
        return (this.payload != null) && (this.arrivalPlace != null);
    }

    /**
     * Set the current place we should kick off with
     */
    protected synchronized void setArrivalPlace(final IServiceProviderPlace p) {
        this.arrivalPlace = p;
    }

    /**
     * Set the payload
     */
    protected synchronized void setPayload(final IBaseDataObject p) {
        this.payload = p;
    }

    /**
     * Return the ID
     */
    @Override
    public String agentID() {
        return this.agentID;
    }

    /**
     * Clear out the payload an other private stuff
     */
    protected synchronized void clear() {
        logger.debug("Clearing payload");
        setPayload(null);
        setAgentID(NO_AGENT_ID);
        this.moveErrorsOccurred = 0;
        this.nextKeyQueue.clear();
        clearParallelTrackingInfo();
    }

    protected void clearParallelTrackingInfo() {
        this.visitedPlaces.clear();
    }

    protected void addParallelTrackingInfo(final String t) {
        this.visitedPlaces.add(t);
    }

    protected boolean checkParallelTrackingFor(final String type) {
        return this.visitedPlaces.contains(type);
    }

    /**
     * Return a reference to the payload of this agent
     */
    @Override
    public synchronized IBaseDataObject getPayload() {
        return this.payload;
    }

    /**
     * The main control loop to determine and go through an itinerary until the payload is finished (no where else to go)
     *
     * @param currentPlaceArg where we are now
     */
    protected void agentControl(final IServiceProviderPlace currentPlaceArg) {
        logger.debug("In agentControl {} for {}", currentPlaceArg, this.agentID);
        DirectoryEntry newEntry = currentPlaceArg.getDirectoryEntry();

        IServiceProviderPlace currentPlace = currentPlaceArg;
        final IBaseDataObject mypayload = getPayload();
        int loopCount = 0;
        boolean controlError = false;

        while (currentPlace != null && newEntry != null && mypayload != null && !this.timeToQuit) {
            // One based counter
            loopCount++;

            // First time in, we just have the pickup place where we started
            // our mission. We dont process there, just use it to call through
            // to the directory, so skip the processing. See the difference
            // between the go() and arrive() methods for details
            if ((loopCount > 1 || getProcessFirstPlace()) && !controlError) {
                atPlace(currentPlace, mypayload);
            }

            // Choose next place
            controlError = false;
            newEntry = getNextKey(currentPlace, mypayload);

            // Nothing to do, bail out,
            // normal processing termination
            if (newEntry == null) {
                break;
            }

            // Record what we are doing in the history log
            if (loopCount == 1 && !getProcessFirstPlace()) {
                // Use arrivalPlace for MobileAgent.send()
                recordHistory(currentPlace, this.payload);
                recordHistory(newEntry, this.payload);
            } else {
                recordHistory(newEntry, this.payload);
            }

            // A local place, around the loop and hit it
            if (newEntry.isLocal()) {
                logger.debug("Choosing local place {}", newEntry.getFullKey());
                currentPlace = newEntry.getLocalPlace();
                continue;
            }

            // A remote place, move there
            logger.debug("Moving to {}", newEntry.getFullKey());
            if (moveHandler(mypayload, currentPlace, newEntry)) {
                // We moved, clean it out
                newEntry = null;
                break; // Moved or dead
            }
            logger.error("MoveTo failed, giving up.");
            controlError = true;
            if (++this.moveErrorsOccurred > this.MAX_MOVE_ERRORS || this.payload.transformHistory().size() > this.MAX_ITINERARY_STEPS) {
                logger.error("Too many move errors, giving up");
                newEntry = null;
                break;
            }

            if (!KeyManipulator.isKeyComplete(mypayload.currentForm())) {
                // Let it try going to the ERROR place, if any
                purgeNonFinalForms(mypayload);
                mypayload.replaceCurrentForm(ERROR_FORM);
            } else {
                // It was a full key, just pop it and try what
                // is next on the list. People who ask for full
                // key moves should be made aware of this somehow
                mypayload.popCurrentForm();
                if (mypayload.currentFormSize() == 0) {
                    mypayload.replaceCurrentForm(ERROR_FORM);
                }
            }
        }

        logger.debug("Out of the control loop");

        // If null we are completely finished, or the payload
        // is just moving to another machine to continue
        // Either way, log it and let this agent go back to
        // the pool
        if (newEntry == null) {
            logAgentCompletion(mypayload);
        }
    }

    /**
     * Move an agent to a remote with retries and logging
     *
     * @param payloadArg data object we are holding at the top, log errors into it
     * @param currentPlace in order to look up directory retries
     * @param newEntryArg where we want to move to
     * @return true if the move was successful, false otherwise
     */
    protected boolean moveHandler(final IBaseDataObject payloadArg, final IServiceProviderPlace currentPlace, final DirectoryEntry newEntryArg) {
        int moveErrors = 0;

        DirectoryEntry newEntry = newEntryArg;
        while (!moveTo(newEntry)) {
            // Track this fact as data may have become
            // multipathed here...
            this.moveErrorsOccurred++;
            logger.info("moveErrorCount bumped to {} while trying {}", this.moveErrorsOccurred, newEntry.getServiceLocation());

            // Dont allow too many move errors
            if (++moveErrors > this.MAX_MOVE_ERRORS) {
                payloadArg.addProcessingError("FATAL.ERROR.MOVETO." + newEntry.getServiceLocation() + "$1002");
                return false;
            }

            // try again at a possibly different place
            final DirectoryEntry nextEntry = getNextKey(currentPlace, payloadArg);
            final String pseudoKey =
                    newEntry.getDataType() + ".MOVE-ERROR." + newEntry.getServiceType() + "." + newEntry.getServiceLocation() + "$102";
            payloadArg.addProcessingError(pseudoKey);
            logger.warn("Could not move to {} retrying at {}", newEntry, nextEntry);
            newEntry = nextEntry;
            if (newEntry == null) {
                return false;
            }
        }

        // It must have worked
        return true;
    }

    /**
     * Do work now that we have arrived a the specified place
     *
     * @param place the place we are asking to work for us
     * @param payloadArg the data for the place to operate on
     */
    protected void atPlace(final IServiceProviderPlace place, final IBaseDataObject payloadArg) {
        logger.debug("In atPlace {} with {}", place, payloadArg.shortName());

        try (TimedResource timer = resourceWatcherStart(place)) {
            this.lastPlaceProcessed = place.getDirectoryEntry().getKey();
            if (this.moveErrorsOccurred > 0) {
                payloadArg.setParameter("AGENT_MOVE_ERRORS", Integer.toString(this.moveErrorsOccurred));
            }

            place.agentProcessCall(payloadArg);

            if (this.moveErrorsOccurred > 0) {
                payloadArg.deleteParameter("AGENT_MOVE_ERRORS");
            }
            logger.debug("done with agentProcessCall for {}", place);
        } catch (Throwable problem) {
            logger.warn("** {} place caught problem:", place, problem);
            payloadArg.addProcessingError("atPlace(" + place + "): " + problem);
            payloadArg.replaceCurrentForm(ERROR_FORM);
        } finally {
            if (!(place instanceof EmptyFormPlace) && payloadArg.currentFormSize() == 0) {
                logger.error("Place {} left an empty form stack, changing it to ERROR", place);
                payloadArg.addProcessingError(place + " left an empty form stack");
                payloadArg.pushCurrentForm(ERROR_FORM);
            }
            checkInterrupt(place);
        }
    }

    protected final void checkInterrupt(final IServiceProviderPlace place) {
        if (Thread.interrupted()) {
            // this should NEVER happen. if it does, we've done something bad
            if (this.thread != Thread.currentThread()) {
                logger.error("MobileAgent thread instance is not the current thread. Instance thread: {} \tCurrent thread: {}", this.thread,
                        Thread.currentThread());
            }
            // Log only when interrupted by the ResourceWatcher, not during shutdown.
            if (!this.timeToQuit) {
                logger.warn("Place {} was interrupted during execution. Adjust place time out or modify code accordingly.", place);
            }
        }
    }

    protected TimedResource resourceWatcherStart(final IServiceProviderPlace place) {
        TimedResource tr = TimedResource.EMPTY;
        // CoordinationPlaces are tracked individually
        if (!(place instanceof CoordinationPlace)) {
            try {
                tr = ResourceWatcher.lookup().starting(this, place);
            } catch (EmissaryException ex) {
                logger.debug("No resource monitoring enabled");
            }
        }
        return tr;
    }

    /**
     * Clean up, idle, and return agent to pool
     */
    protected synchronized void agentReturn() {
        clear();
        setArrivalPlace(null);
        this.lastPlaceProcessed = null;
        AgentPool pool = null;
        try {
            pool = AgentPool.lookup();
            pool.returnAgent(this);
        } catch (Exception e) {
            logger.error("Cannot get return agent to pool", e);
        }
    }

    /**
     * Get the next key from the directory with error handling Can return null if there is no place to handle the form
     *
     * @param place the place we will use to access the directory
     * @param payloadArg the current payload we care about
     * @return the SDE answer from the directory
     */
    protected DirectoryEntry getNextKey(final IServiceProviderPlace place, final IBaseDataObject payloadArg) {

        logger.debug("start getNextKey");

        // Check for bad preconditions.
        if (payloadArg == null || place == null) {
            logger.warn("Null payload or placein getNextKey");
            return null;
        }

        // Stop looping from occuring
        if (payloadArg.transformHistory().size() > this.MAX_ITINERARY_STEPS &&
                payloadArg.currentForm() != ERROR_FORM) {
            payloadArg.replaceCurrentForm(ERROR_FORM);
            payloadArg.addProcessingError("Agent stopped due to larger than max transform history size (looping?)");
        }

        // Perhaps we already have additional keys to process
        // from the last time we asked the directory. If so,
        // choose the next one and spit it out
        if (!this.nextKeyQueue.isEmpty()) {
            logger.debug("Returning next key from stack size={}", this.nextKeyQueue.size());
            return this.nextKeyQueue.removeFirst();
        }

        // We would need a current form of the payload to continue
        if (payloadArg.currentFormSize() < 1) {
            logger.debug("No current forms on payload {}", payloadArg.shortName());
            return null;
        }

        // Maybe we are done, if so quit now
        if (payloadArg.currentForm().startsWith(DONE_FORM)) {
            return null;
        }

        // If we are in the error condition,
        // clean up and try for error drop off
        final String curKey = payloadArg.currentForm();
        if (ERROR_FORM.equals(curKey)) {
            if (payloadArg.currentFormSize() > 1 && ERROR_FORM.equals(payloadArg.currentFormAt(1))) {
                logger.error("ERROR handling place produced an error, purging all current forms");
                while (payloadArg.currentFormSize() > 0) {
                    payloadArg.popCurrentForm();
                }
                payloadArg.appendTransformHistory("ERROR.SKIP.*.http://Previous_Error_Bypass$99999");
            } else {
                if (payloadArg.currentFormSize() > 1) {
                    logger.warn("Got current form of ERROR, clearing form stack on {}: {}", payloadArg.shortName(), payloadArg.getAllCurrentForms());
                }
                purgeNonFinalForms(payloadArg);
            }
        }

        // If we have a fully specified key as current form
        // just go there and process
        if (KeyManipulator.isKeyComplete(curKey)) {
            logger.debug("Got current full key form of {}", curKey);
            return new DirectoryEntry(curKey);
        }

        /* Get the last entry from the payload */
        DirectoryEntry lastEntry = payloadArg.getLastPlaceVisited();

        final List<String> dataForms = payloadArg.getAllCurrentForms();
        logger.debug(">>> Current forms for {} are {}", payloadArg.shortName(), dataForms);

        /* Get the last service type from the last key */
        String lastServiceType = STAGES.getStageName(0);
        if (lastEntry != null) {
            // lastServiceType = KeyManipulator.serviceType(lastEntry.key());
            lastServiceType = lastEntry.getServiceType();
        }

        logger.debug("Payload reports lastEntry is {} with serviceType {}", lastEntry, lastServiceType);

        // For Analyze type, don't allow it to go back to ID
        // *.INPUT.* and *.<SPROUT>.* are not in the list so will
        // both start at 0
        int startType = typeLookup(lastServiceType);

        // If we came from transform we can start at the beginning again
        if (lastEntry != null && startType != 0) {
            // String key = lastEntry.key();
            // if (key.indexOf(".TRANSFORM.") > -1) {
            if ("TRANSFORM".equals(lastEntry.getServiceType())) {
                startType = 0;
            }
        }

        DirectoryEntry curEntry = null;
        for (int curType = startType; curType < STAGES.size(); curType++) {
            // Search the form stack starting with the top.
            for (int curForm = 0; curForm < dataForms.size(); curForm++) {
                final String cform = dataForms.get(curForm);
                final String tform = STAGES.getStageName(curType);

                // Test a full key form to see if it is the correct
                // stage to be chosen
                if (KeyManipulator.isKeyComplete(cform)) {
                    if (KeyManipulator.getServiceType(cform).equals(tform)) {
                        logger.debug("Choosing cur form {} in stage {}", cform, tform);
                        payloadArg.pullFormToTop(cform);
                        return new DirectoryEntry(cform);
                    }
                }

                String formID = cform + KeyManipulator.DATAIDSEPARATOR + tform;
                curEntry = nextKeyFromDirectory(formID, place, lastEntry, payloadArg);

                // Process through the parallel service type once per place max
                // no matter how many forms would route there
                if (curEntry != null && isParallelServiceType(curType)) {
                    boolean parallelEntryRejected = false;
                    do {
                        parallelEntryRejected = false;
                        logger.debug(
                                "curEntry isParallel with curType={}, curEntry={}, visitedPlace={}, serviceName={}, lastServiceType={}, curTypeName={}",
                                curType, curEntry.getFullKey(), this.visitedPlaces, curEntry.getServiceName(), lastServiceType,
                                STAGES.getStageName(curType));
                        if (this.visitedPlaces.isEmpty() || STAGES.getStageName(curType).equals(lastServiceType)) {
                            if (checkParallelTrackingFor(curEntry.getServiceName())) {
                                lastEntry = new DirectoryEntry(curEntry);
                                lastEntry.setDataType(cform);
                                formID = lastEntry.getDataID();
                                parallelEntryRejected = true;
                                logger.debug("rejecting curEntry due to parallel check in {}, new formID={}, new lastEntry={}", this.visitedPlaces,
                                        formID, lastEntry.getFullKey());
                                curEntry = nextKeyFromDirectory(formID, place, lastEntry, payloadArg);
                            } else {
                                addParallelTrackingInfo(curEntry.getServiceName());
                                logger.debug("Added parallel tracking = {}", this.visitedPlaces);
                            }
                        } else {
                            clearParallelTrackingInfo();
                            logger.debug("Cleared parallel tracking info");
                        }
                    } while (parallelEntryRejected && curEntry != null);
                }

                if (curEntry != null) {
                    logger.debug("===== --- *** Doing {}.{}--{}", STAGES.getStageName(curType), formID, curEntry.getServiceName());
                    payloadArg.pullFormToTop(cform);
                    return curEntry;
                }
            }
        }
        return null;
    }

    /**
     * Evaluate parallel attribute of specified type index
     */
    protected boolean isParallelServiceType(final int typeSetPosition) {
        return STAGES.isParallelStage(typeSetPosition);
    }

    /**
     * Get index in typeSet for specified string, 0 if not found
     */
    public static int typeLookup(final String s) {
        int idx = STAGES.getStageIndex(s);
        if (idx < 0) {
            idx = 0; // failsafe
        }
        return idx;
    }

    /**
     * Communicate with the directory through the current place to get the next place to go. These are all local calls since
     * all the local directories have all the information
     *
     * This call may cause several key entries to be returned from the directory. All will be put on an internal queue and
     * the first one will be returned to the caller. Caller knows to look on the internal queue for additional entries
     * before calling this method again.
     */
    protected DirectoryEntry nextKeyFromDirectory(final String dataID, final IServiceProviderPlace place, final DirectoryEntry lastEntry,
            final IBaseDataObject payloadArg) {

        try {
            logger.debug("Trying nextKey for {} with last={}, atPlace={}", dataID, lastEntry, place);

            // Query the directory
            final List<DirectoryEntry> entries = place.nextKeys(dataID, payloadArg, lastEntry);

            // Add the entries returned to the queue
            if ((entries != null) && !entries.isEmpty()) {
                this.nextKeyQueue.addAll(entries);
                logger.debug("Added {} new key entries from the directory for {}", entries.size(), dataID);
            }

        } catch (Exception e) {
            logger.warn("cannot get key, I was working on: {}", payloadArg.shortName(), e);
            // Returning instead of throwing will allow
            // the next form to be tried.
        }

        // Dequeue first item and return it to the caller
        DirectoryEntry tmpEntry = null;
        if (!this.nextKeyQueue.isEmpty()) {
            tmpEntry = this.nextKeyQueue.removeFirst();
        }
        logger.debug("nextKeyFromDirectory found {}", tmpEntry);
        return tmpEntry;
    }

    /**
     * Wrapper around the http moveto posting code
     *
     * @param entry where we are going
     */
    protected boolean moveTo(final DirectoryEntry entry) {

        long elapsedTime = System.currentTimeMillis();
        // String place = entry.remotePlace();
        final String place = entry.getKey();
        // String url = KeyManipulator.serviceHostURL(place);
        final String url = entry.getServiceHostURL();

        logger.debug("in MobileAgent.moveTo(): place = {}, url={}", place, url);
        try {
            // TODO This should be uncommented when redoing the moveTo functionality
            // final EmissaryClient emissaryClient = EmissaryClient.getInstance();
            // final boolean status = emissaryClient.moveTo(place, this);
            final boolean status = false;
            if (!status) {
                logger.error("Unable to move to {} with {}", place, getPayload().getFilename());
                return false;
            }
        } catch (Throwable e) {
            logger.error("moveTo Problem for {}: {}: elapsedTime: {}s {}", this.agentID, getPayload().currentForm(),
                    ((System.currentTimeMillis() - elapsedTime) / 1000.0), getPayload().getFilename(), e);
            return false;
        }

        // Yeah! it worked
        if (logger.isDebugEnabled()) {
            elapsedTime += System.currentTimeMillis();
            logger.debug("MoveTo {} for {} took {}s", place, this.agentID, elapsedTime / 1000.0);
        }
        return true;
    }

    /**
     * Build the unique agent ID for carrying this payload around mostly used in error reporting
     *
     * @param theID usually comes from the shortName of the payload
     */
    protected void setAgentID(final String theID) {
        final long t = (System.currentTimeMillis() % 10000);
        final String id = "Agent-" + t;
        this.agentID = id + "-" + ((theID != null) ? theID : "blah");
    }

    /**
     * A little more than the name implies, this method sets the things required for an idle agent to get moving again. This
     * is to be used when starting the agent from a pick up place because although we start with an initial 'place' we don't
     * use it for processing, just to get the nextKey from the directory there.
     *
     * @param payloadArg the real payload, existing if any will be cleared
     * @param arrivalPlaceArg the place we start at
     */
    @Override
    public synchronized void go(final Object payloadArg, final IServiceProviderPlace arrivalPlaceArg) {
        clear();
        go(payloadArg, arrivalPlaceArg, false);
    }

    /**
     * Provide access to the move-error counter for the MoveAdapter
     */
    @Override
    public int getMoveErrorCount() {
        return this.moveErrorsOccurred;
    }

    /**
     * Provide access to the itinerary queue for the MoveAdapter
     */
    @Override
    public DirectoryEntry[] getItineraryQueueItems() {
        return this.nextKeyQueue.toArray(new DirectoryEntry[0]);
    }

    /**
     * This is for an already in process agent arriving at a new place from a "moveTo". This is different than the above
     * method because we presume we have arrived at this place in order to do some processing here, not just because we got
     * picked up by it. So we don't need to get a key first, just start processing.
     *
     * @param dataObject the real payload, exisitng if any will be cleared
     * @param arrivalPlaceArg the place we start at
     * @param moveErrorCount transported move error counter
     * @param queuedItineraryItems transported itinerary items list of DirectoryEntry
     */
    @Override
    public synchronized void arrive(final Object dataObject, final IServiceProviderPlace arrivalPlaceArg, final int moveErrorCount,
            final List<DirectoryEntry> queuedItineraryItems) throws Exception {

        if (dataObject instanceof IBaseDataObject) {
            clear();
            this.moveErrorsOccurred = moveErrorCount;
            this.nextKeyQueue.addAll(queuedItineraryItems);
            go(dataObject, arrivalPlaceArg, true);
        } else {
            throw new Exception("Illegal payload sent to MobileAgent, cannot handle " + dataObject.getClass().getName());
        }
    }

    /**
     * Private implementation for both of the above arrive and go methods, uses the setProcessFirstPlace to communicate on
     * which path we entered to the agent's thread
     *
     * @param dataObject the real payload
     * @param arrivalPlaceArg the place we start at
     * @param processAtFirstPlace true if we should call process on arrivalPlaceArg
     */
    protected synchronized void go(final Object dataObject, final IServiceProviderPlace arrivalPlaceArg, final boolean processAtFirstPlace) {
        // Check conditions
        if (dataObject != null && !(dataObject instanceof IBaseDataObject)) {
            throw new IllegalArgumentException("Illegal payload sent to MobileAgent, " + "cannot handle " + dataObject.getClass().getName());
        }

        setProcessFirstPlace(processAtFirstPlace);

        // Allow this to be null to that derived classes
        // can handle the setting of their payload and still
        // be able to use this method
        if (dataObject != null) {
            final IBaseDataObject d = (IBaseDataObject) dataObject;
            logger.debug("Setting payload {}", d.shortName());
            setPayload(d);
            setAgentID(d.shortName());
        }

        // Likewise...
        if (arrivalPlaceArg != null) {
            setArrivalPlace(arrivalPlaceArg);

            // If a "go" rather an an "arrive", log the arrivalPlaceArg
            // on the transform history of the payload
            if (!processAtFirstPlace) {
                logger.debug("Adding history for arrival place {}", arrivalPlaceArg.getKey());
                recordHistory(arrivalPlaceArg, getPayload());
            }
        }

        // the run() loop now takes over on the agent's thread and we return
        // control of the currentThread to the caller of this method
        notifyAll();
    }

    /**
     * Delete all forms on the stack that are not final. This is called in error conditions to try and break out of loops or
     * terminate other badness and zip to the end
     *
     * @param payloadArg the dataobject to work on
     */
    protected static void purgeNonFinalForms(final IBaseDataObject payloadArg) {
        int i = 0;
        while (i < payloadArg.currentFormSize()) {
            final String form = payloadArg.getAllCurrentForms().get(i);
            if (form.equals(ERROR_FORM)) {
                i++;
                continue;
            }
            final String pseudoKey = payloadArg.currentFormAt(i) + ".SKIP.*.http://Previous_Error_Bypass$100";

            logger.debug("Removed {} because of ERROR.SKIP", payloadArg.currentFormAt(i));
            payloadArg.appendTransformHistory(pseudoKey);
            payloadArg.deleteCurrentFormAt(i);
        }
    }

    /**
     * Make a nice log message when we are done with the payload
     *
     * @param payloadArg the one we just finished with
     */
    protected void logAgentCompletion(final IBaseDataObject payloadArg) {
        // Keep this at a nice high level, above the debug chatter
        final Object isProbe = payloadArg.getParameter("DIRECTORY_PROBE");
        final Logger dest = (isProbe == null) ? logger : probeLogger;

        if (dest.isInfoEnabled()) {
            dest.info(PayloadUtil.getPayloadDisplayString(payloadArg));
        }
    }

    /**
     * Record the processing history in the data object
     *
     * @param place where the processing is taking place
     * @param payloadArg the dataobject that is being processed
     */
    protected void recordHistory(final IServiceProviderPlace place, final IBaseDataObject payloadArg) {
        recordHistory(place.getDirectoryEntry(), payloadArg);
    }

    /**
     * Record the processing history in the data object
     *
     * @param placeEntry where the processing is taking place
     * @param payloadArg the dataobject that is being processed
     */
    protected void recordHistory(final DirectoryEntry placeEntry, final IBaseDataObject payloadArg) {

        String placeKey = null;
        final String cf = payloadArg.currentForm();
        final DirectoryEntry dnew = new DirectoryEntry(placeEntry);
        if (!KeyManipulator.isKeyComplete(cf)) {
            // Splice this current form into the place key
            // for a proper representation of why we are here
            dnew.setDataType(cf);
            placeKey = dnew.getFullKey();
        } else {
            // We already have a full key in the current form
            // just need to figure out the current cost
            if (!payloadArg.beforeStart()) {
                final DirectoryEntry lpv = payloadArg.getLastPlaceVisited();

                // Subtract one remote overhead if this represents a move
                int exp = lpv.getExpense();
                if (!KeyManipulator.getServiceHostURL(cf).equals(lpv.getServiceHostURL()) && exp > DirectoryPlace.REMOTE_EXPENSE_OVERHEAD) {
                    exp -= DirectoryPlace.REMOTE_EXPENSE_OVERHEAD;
                }

                // Current form cannot perhaps handle the cost, but
                // we need it here for the xform history
                if (exp > 0) {
                    placeKey = cf + KeyManipulator.DOLLAR + exp;
                } else {
                    placeKey = cf;
                }
            } else {
                // Full part key in current form and before start.
                // Must use key from "Sending Place" rather than
                // current form here
                placeKey = dnew.getFullKey();
            }
        }

        payloadArg.appendTransformHistory(placeKey);

        logger.debug("Appended {} to history which now has size {}", placeKey, payloadArg.transformHistory().size());
    }

    /**
     * Setter for processFirstPlace
     *
     * @param arg the new value for processFirstPlace
     */
    protected void setProcessFirstPlace(final boolean arg) {
        this.processFirstPlace = arg;
    }

    /**
     * Getter for processFirstPlace
     *
     * @return the value of processFirstPlace
     */
    protected boolean getProcessFirstPlace() {
        return this.processFirstPlace;
    }

    @Override
    public void dumpPlaceStats() {
        ResourceWatcher rw = null;
        try {
            rw = ResourceWatcher.lookup();
        } catch (NamespaceException ne) {
            logger.error("Exception occurred while trying to lookup resource", ne);
            return;
        }

        logger.info("Dumping All Stats for {}:", AGENT_THREAD);
        logger.info("===============");
        rw.logStats(logger);
        logger.info("===============");
    }

    /**
     * Get the number of move errors
     */
    @Override
    public int getMaxMoveErrors() {
        return this.MAX_MOVE_ERRORS;
    }

    /**
     * Set the maximum number of move attempts that can error out before this instance will quit trying and set the workflow
     * to be an ERROR condition
     *
     * @param value the maximum number of move failures
     */
    @Override
    public void setMaxMoveErrors(final int value) {
        this.MAX_MOVE_ERRORS = value;
    }

    /**
     * Get the maximum number of itinerary steps
     */
    @Override
    public int getMaxItinerarySteps() {
        return this.MAX_ITINERARY_STEPS;
    }

    /**
     * Set the maximum number of itinerary steps before this instance will turn the workflow into an ERROR condition
     *
     * @param value the new maximum number of steps
     */
    @Override
    public void setMaxItinerarySteps(final int value) {
        this.MAX_ITINERARY_STEPS = value;
    }
}
