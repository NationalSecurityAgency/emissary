package emissary.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import emissary.directory.DirectoryEntry;
import emissary.directory.KeyManipulator;
import emissary.log.MDCConstants;
import emissary.place.EmptyFormPlace;
import emissary.place.IServiceProviderPlace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * This mobile agent carries around an ArrayList of payload that can be added onto instead of sprouting. The agent is
 * responsible for getting all payloads on the list processed to completion before going idle
 */
public class HDMobileAgent extends MobileAgent {

    // Our logger, shadow the superclass for our own name in the log
    protected static final Logger logger = LoggerFactory.getLogger(HDMobileAgent.class);

    // Serializability
    static final long serialVersionUID = 786319119844306571L;

    // What we carry around with us
    protected List<IBaseDataObject> payloadList = Collections.synchronizedList(new ArrayList<IBaseDataObject>());

    /**
     * Still have the uncaught exception handler but not really in a true ThreadGroup
     */
    public HDMobileAgent() {
        super();
    }

    /**
     * Constructor for the factory, a reusable HD Agent
     */
    public HDMobileAgent(final ThreadGroup threadGroup, final String threadName) {
        super(threadGroup, threadName);
        logger.debug("Constructed HD agent " + threadName);
    }

    /**
     * Override getPayload to just return the first on list or null
     */
    @Override
    public synchronized IBaseDataObject getPayload() {
        return getPayload(0);
    }

    /**
     * New method to get a payload from the list
     * 
     * @param num the specified payload
     */
    public synchronized IBaseDataObject getPayload(final int num) {
        if (this.payloadList == null || this.payloadList.size() <= num) {
            return null;
        }
        return this.payloadList.get(num);
    }

    /**
     * Add payload to the list, warn if not empty
     * 
     * @param p the payload, clear list if null to retain previous behavior
     */
    @Override
    protected synchronized void setPayload(final IBaseDataObject p) {
        if (p == null) {
            this.payloadList.clear();
            super.setPayload(null);
            return;
        }

        if (payloadCount() != 0) {
            logger.warn("Unanticipated call to psetPayload when payloadList is not empty.");
        }
        addPayload(p);
    }

    /**
     * Add a new payload (i.e. instead of sprouting a new agent for it)
     * 
     * @param p the new payload
     * @return true
     */
    public synchronized boolean addPayload(final IBaseDataObject p) {
        return this.payloadList.add(p);
    }

    /**
     * Add a collection of new payload objects
     * 
     * @param c the collection to add
     * @return true
     */
    public synchronized boolean addPayload(final Collection<IBaseDataObject> c) {
        return this.payloadList.addAll(c);
    }

    /**
     * Get number of payload objects on list
     */
    public synchronized int payloadCount() {
        return this.payloadList.size();
    }

    /**
     * Clear the payloadList and all other state info
     */
    @Override
    protected void clear() {
        super.clear();
        this.payloadList.clear();
    }

    /**
     * The arrive method that takes in a list of payloads arriving on the new machine
     * 
     * @param payload the real payload, exisitng if any will be cleared
     * @param arrivalPlace the place we start at
     * @param moveErrorCount state transfer from sending agent
     * @param queuedItineraryItems state transfer from sending agent
     */
    @Override
    @SuppressWarnings("unchecked")
    public synchronized void arrive(final Object payload, final IServiceProviderPlace arrivalPlace, final int moveErrorCount,
            final List<DirectoryEntry> queuedItineraryItems) throws Exception {

        logger.debug("Arrived at " + arrivalPlace.toString());

        clear();
        moveErrorsOccurred = moveErrorCount;
        nextKeyQueue.addAll(queuedItineraryItems);

        if (payload instanceof IBaseDataObject) {
            go(payload, arrivalPlace, true);
        } else if (payload instanceof Collection) {
            addPayload((Collection<IBaseDataObject>) payload);
            setAgentID(getPayload().shortName());
            go(null, arrivalPlace, true);
        } else {
            throw new Exception("Illegal payload sent to HDMobileAgent, cannot handle " + payload.getClass().getName());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized void go(final Object payload, final IServiceProviderPlace arrivalPlace) {
        if (payload instanceof IBaseDataObject) {
            super.go(payload, arrivalPlace);
        } else if (payload instanceof Collection) {
            addPayload((Collection<IBaseDataObject>) payload);
            setAgentID(getPayload().shortName());
            go(null, arrivalPlace, false);
        } else {
            logger.error("Illegal payload sent to HDMobileAgent, cannot handle " + payload.getClass().getName());
        }
    }

    /**
     * The main control loop to determine and go through an itinerary Since we have a list of payload object that can
     * potentially grow at every place we visit, we need to iterate over them until we get done but we cannot use a normal
     * iterator which will throw an exception if the underlying collection mutates while iterating. So we make the selection
     * of the first payload that needs work, find the place for it, process all other payloads that have the same form and
     * lastPlace, then move on.
     */
    @Override
    protected void agentControl(final IServiceProviderPlace currentPlaceArg) {
        DirectoryEntry newEntry = currentPlaceArg.getDirectoryEntry();
        logger.debug("In agentControlHD " + currentPlaceArg + " for " + agentID);

        // Set into the super classes payload member...
        IBaseDataObject mypayload = getPayload();

        // Go until all the payloads disappear or get done
        IServiceProviderPlace currentPlace = currentPlaceArg;
        int loopCount = 0;
        boolean nextKeyRecorded = true;
        boolean controlError = false;

        while (currentPlace != null && newEntry != null && mypayload != null) {
            // One based loop counter
            loopCount++;

            // Remember the payload's form and last place before
            // doing the processing
            final String primaryCurrentForm = mypayload.currentForm();
            final DirectoryEntry primaryLastEntry = mypayload.getLastPlaceVisited();

            if (logger.isDebugEnabled()) {
                logger.debug("Starting control loop for " + mypayload.shortName() + ", currentPlace=" + currentPlace.getKey() + ", newEntry= "
                        + newEntry.getFullKey() + ", loopCount=" + loopCount);
            }

            // First time in, we just have the pickup place where we started
            // our mission. We dont process there, just use it to call through
            // to the directory, so skip the processing if this is true
            if ((loopCount > 1 || getProcessFirstPlace()) && !controlError) {
                // If we are at IO phase, add them all since the deferrals
                // below should make everyone ready to drop off at the same time
                if ("IO".equals(currentPlace.getDirectoryEntry().getServiceType())) {
                    // Drop off doesn't sprout so ignore return value
                    if (!nextKeyRecorded) {
                        logger.debug("Recording history drop off case");
                        recordHistory(newEntry, this.payloadList);
                        nextKeyRecorded = true;
                    }
                    atPlaceHD(currentPlace, this.payloadList);
                } else {
                    // Add the primary payload object to a list
                    final List<IBaseDataObject> toBeProcessed = new ArrayList<IBaseDataObject>();
                    toBeProcessed.add(mypayload);

                    // Add any other payload that has the same current form
                    // and last place visited as this one while we are here...
                    for (final IBaseDataObject slug : this.payloadList) {
                        final DirectoryEntry slugLastPlaceVisited = slug.getLastPlaceVisited();

                        if (slug != mypayload
                                && slug.searchCurrentForm(primaryCurrentForm) > -1
                                && ((primaryLastEntry == null && slugLastPlaceVisited == null) || (primaryLastEntry != null
                                        && slugLastPlaceVisited != null && slugLastPlaceVisited.getKey().equals(primaryLastEntry.getKey())))) {
                            // We don't need to call getNextKey but do
                            // need to simulate this side effect of it...
                            slug.pullFormToTop(primaryCurrentForm);

                            toBeProcessed.add(slug);

                            if (logger.isDebugEnabled()) {
                                logger.debug("Adding slug " + slug.shortName() + " with key "
                                        + (slugLastPlaceVisited == null ? "null" : slugLastPlaceVisited.getKey()) + " to ride with "
                                        + mypayload.shortName() + " having key " + (primaryLastEntry == null ? "null" : primaryLastEntry.getKey())
                                        + " current form " + primaryCurrentForm);
                            }
                        }
                    }

                    // Process everything on the list
                    if (!nextKeyRecorded) {
                        if (loopCount == 1 && !getProcessFirstPlace()) {
                            // ArrivalPlace for MobileAgent.send()
                            logger.debug("Recording history two normal loop-1 case");
                            recordHistory(currentPlace, toBeProcessed);
                        } else {
                            logger.debug("Recording history two non-loop-1 case");
                            recordHistory(newEntry, toBeProcessed);
                        }
                        nextKeyRecorded = true;
                    }
                    final List<IBaseDataObject> sprouts = atPlaceHD(currentPlace, toBeProcessed);

                    // Add any sprouts collected from the payloads
                    if (sprouts.size() > 0) {
                        addPayload(sprouts);
                    }
                }
            }

            // Where to go next...
            controlError = false;
            newEntry = getNextKey(currentPlace, mypayload);
            nextKeyRecorded = false;

            // Defer IO phase for now if there are attachments to process
            // and we aren't already in the io phase
            if ((newEntry != null) && (payloadCount() > 1) && "IO".equals(newEntry.getServiceType())
                    && !"IO".equals(currentPlace.getDirectoryEntry().getServiceType())) {
                logger.debug("Deferring IO Phase place for " + newEntry);
                newEntry = null;
            } else {
                if (newEntry != null) {
                    logger.debug("Continuing with place " + newEntry);
                }
            }

            // Choose the first place on the list that
            // doesn't have a null nextKey when we run out
            // of key for the one we were working on intitially
            DirectoryEntry dropOffEntry = null;
            int haveDropOffFor = -1;
            if (newEntry == null) {
                logger.debug("Got null newEntry for " + mypayload.shortName() + " looking for a better payload...");
                for (int i = 0; i < payloadCount(); i++) {
                    final IBaseDataObject p = getPayload(i);
                    if (p == mypayload) {
                        continue;
                    }
                    setParallelTrackingInfoFor(p);
                    newEntry = getNextKey(currentPlace, p);
                    if (newEntry != null) {
                        // Defer IO Phase until sure we are all done
                        if ("IO".equals(newEntry.getServiceType())) {
                            logger.debug("Found IO service for part " + i + ", " + p.shortName() + "deferring that and continuing to look");
                            if (haveDropOffFor == -1) {
                                haveDropOffFor = i;
                                dropOffEntry = newEntry;
                            }
                            newEntry = null;
                            continue;
                        }

                        if (logger.isDebugEnabled()) {
                            logger.debug("Found good key " + newEntry + " for new payload " + p.shortName() + ", serviceName="
                                    + newEntry.getServiceName());
                        }

                        // Found a new top dog to process
                        // Pull it to the top of the list in case we have to move
                        if (i != 0) {
                            switchPrimaryPayload(i);
                        }
                        // Remember it for processing
                        mypayload = p;
                        break; // out of the for loop
                    }
                }
            }

            // Reset drop off if we deferred it above and found nothing better
            if (newEntry == null && haveDropOffFor > -1) {
                // Pull entry to top
                if (haveDropOffFor != 0) {
                    switchPrimaryPayload(haveDropOffFor);
                    mypayload = getPayload(0);
                    setParallelTrackingInfoFor(mypayload);
                    logger.debug("Pulling payload " + haveDropOffFor + " to top before IO reinstatement");
                }

                // Set newEntry and go to drop off, deferred as long as possible
                newEntry = dropOffEntry;
                logger.debug("Resetting newEntry to IO phase");
            }

            // Null entry at this point means we are all done
            // with all the payloads, normal processing termination
            if (newEntry == null) {
                break;
            }

            // Local processing, go around the loop and process there
            if (newEntry.isLocal()) {
                logger.debug("Choosing local place " + newEntry.getFullKey());
                currentPlace = newEntry.getLocalPlace();
                continue;
            }

            if (logger.isDebugEnabled()) {
                logger.debug("Recording history one move case loopCount=" + loopCount + " getProcessFirstPlace=" + getProcessFirstPlace()
                        + " currentPlace=" + currentPlace + " newEntry=" + newEntry.getFullKey());
            }

            // Time to move, entry is remote, record the history and go
            recordHistory(newEntry, mypayload);
            nextKeyRecorded = true;

            if (moveHandler(mypayload, currentPlace, newEntry)) {
                // Clear them out
                newEntry = null;
                currentPlace = null;
                break; // Moved or dead
            }
            // Let it try and go the the ERROR place if there is one
            logger.error("MoveTo failed, giving up.");
            controlError = true;
            if (!KeyManipulator.isKeyComplete(mypayload.currentForm())) {
                mypayload.replaceCurrentForm(ERROR_FORM);
            } else {
                mypayload.popCurrentForm();
            }
        }

        // If null we are completely finished, otherwise we
        // should just be moving to another machine
        if (newEntry == null) {
            logAgentCompletion();
        }
    }

    /**
     * Make the payload at the specified index the new primary one and reset the logger context to the new value
     * 
     * @param i the index of the desired payload
     */
    protected void switchPrimaryPayload(final int i) {
        synchronized (this) {
            // Pull them both
            final IBaseDataObject oldTop = getPayload(0);
            final IBaseDataObject p = getPayload(i);
            // Swap them
            this.payloadList.set(0, p);
            this.payloadList.set(i, oldTop);
            // switch logger context
            MDC.put(MDCConstants.SHORT_NAME, p.shortName());
        }
    }

    /**
     * Do work now that we have arrived a the specified place
     * 
     * @param place the place we are asking to work for us
     * @param payloadListArg list of IBaseDataObject for the place to operate on
     * @return list of &quot;sprouted&quot; payloads
     */
    protected List<IBaseDataObject> atPlaceHD(final IServiceProviderPlace place, final List<IBaseDataObject> payloadListArg) {
        MDC.put(MDCConstants.SERVICE_LOCATION, place.toString());
        logger.debug("In atPlaceHD {} with {} payload items", place, payloadListArg.size());

        List<IBaseDataObject> ret = Collections.emptyList();
        try (TimedResource tr = resourceWatcherStart(place)) {
            // Process and get back a list of sprouted payloads
            lastPlaceProcessed = place.getDirectoryEntry().getKey();

            if (moveErrorsOccurred > 0) {
                addMoveErrorCount(payloadListArg);
            }

            ret = place.agentProcessHeavyDuty(payloadListArg);

            for (Iterator<IBaseDataObject> it = ret.iterator(); it.hasNext();) {
                final IBaseDataObject ibdo = it.next();
                if (ibdo == null) {
                    logger.error("{} violated contract and returned null IBaseDataObject. Child counts and IDs may be inconsistent.", place);
                    it.remove();
                }
            }

            if (moveErrorsOccurred > 0) {
                deleteMoveErrorCount(payloadListArg);
            }

            logger.debug("done with agentProcessHD for {} with {} sprouted results along for the ride", place, ret.size());
        } catch (Throwable problem) {
            logger.warn("**{} caught {} with {} payloads", place, problem, payloadListArg.size(), problem);
            // We don't know here which one of the items on the list
            // caused the exception so we are going to error them all
            // If place providers don't catch their own exceptions
            // we are conservative in what we think is safe to do here
            for (final IBaseDataObject p : payloadListArg) {
                p.addProcessingError("agentProcessHeavyDury(" + place + "): " + problem);
                p.replaceCurrentForm(MobileAgent.ERROR_FORM);
            }
        } finally {
            if (!(place instanceof EmptyFormPlace)) {
                for (final IBaseDataObject p : payloadListArg) {
                    if (p.currentFormSize() == 0) {
                        logger.error("Place {} left an empty form stack, changing it to ERROR", place);
                        p.addProcessingError(place + " left an empty form stack");
                        p.pushCurrentForm(ERROR_FORM);
                    }
                }
            }
            MDC.remove(MDCConstants.SERVICE_LOCATION);
            checkInterrupt(place);
        }

        return ret;
    }

    /**
     * Add the move error count to each payload
     */
    protected void addMoveErrorCount(final List<IBaseDataObject> payloadListArg) {
        for (final IBaseDataObject payload : payloadListArg) {
            payload.setParameter("AGENT_MOVE_ERRORS", Integer.toString(moveErrorsOccurred));
        }
    }

    /**
     * Delete the move error count from each payload
     */
    protected void deleteMoveErrorCount(final List<IBaseDataObject> payloadListArg) {
        for (final IBaseDataObject payload : payloadListArg) {
            payload.deleteParameter("AGENT_MOVE_ERRORS");
        }
    }

    /**
     * Record history for a bunch of payload objects (IBaseDataObject)
     */
    protected void recordHistory(final IServiceProviderPlace place, final List<IBaseDataObject> payloadListArg) {
        logger.debug("In recordHistory with " + payloadListArg.size() + " payloads");
        final DirectoryEntry placeEntry = place.getDirectoryEntry();
        for (final IBaseDataObject d : payloadListArg) {
            recordHistory(placeEntry, d);
        }
    }

    /**
     * Record history for a bunch of payload objects (IBaseDataObject)
     */
    protected void recordHistory(final DirectoryEntry placeEntry, final List<IBaseDataObject> payloadListArg) {
        logger.debug("In recordHistory with " + payloadListArg.size() + " payloads");
        for (final IBaseDataObject d : payloadListArg) {
            recordHistory(placeEntry, d);
        }
    }

    /**
     * Make the log message for all the payloads
     */
    protected void logAgentCompletion() {
        for (final IBaseDataObject payload : this.payloadList) {
            logAgentCompletion(payload);
        }
    }

    /**
     * Return whatever we carry as an object for serialization
     */
    @Override
    public Object getPayloadForTransport() {
        return this.payloadList;
    }

    /**
     * Report whether we are busy or not
     */
    @Override
    public boolean isInUse() {
        return (this.payloadList != null) && !this.payloadList.isEmpty() && (arrivalPlace != null);
    }

    /**
     * Setup the parallel type set tracking variable for a possible new payload
     */
    protected void setParallelTrackingInfoFor(final IBaseDataObject d) {

        // Clear out current value
        clearParallelTrackingInfo();

        // Look at history from the tail backwards in time and
        // while looking at the same parallelType serviceType
        // add values to the visitedPlaces tracking variable.
        // We need to do this because this data object may have
        // been processed through some of them as a slug and it
        // will not have filled in the visitedPlace properly.
        final List<String> history = d.transformHistory();
        int lastParallelType = -1;
        for (int i = history.size() - 1; i >= 0; i--) {
            final String key = history.get(i);
            final int typeSet = typeLookup(KeyManipulator.getServiceType(key));
            if (lastParallelType == -1 && isParallelServiceType(typeSet)) {
                lastParallelType = typeSet;
            }
            if (typeSet != lastParallelType) {
                break;
            }
            addParallelTrackingInfo(KeyManipulator.getServiceName(key));
        }
    }

    /**
     * To string method useful from the Namespace when bound there
     */
    @Override
    public String toString() {
        if (isZombie()) {
            return "Closed";
        } else if (!isInUse()) {
            return "Idle";
        }

        String sn = null;
        int sz = 0;
        if (this.payloadList != null && !this.payloadList.isEmpty()) {
            // Avoid synchronizing this [don't call getPayload()]
            try {
                sn = this.payloadList.get(0).shortName();
                sz = this.payloadList.size();
            } catch (Throwable t) {
                // empty catch block
            }
        }
        if (sn == null) {
            sn = "Missing payload";
        }
        return sn + "(" + sz + ") - " + lastPlaceProcessed;
    }

    /**
     * @return the lastPlaceProcessed
     */
    @Override
    public String getLastPlaceProcessed() {
        return this.lastPlaceProcessed;
    }

    /**
     * Interrupt the agent's thread Seems a little weird to be public, but there aren't a lot of choices.
     */
    @Override
    public void interrupt() {
        this.thread.interrupt();
    }

    /**
     * Determine if this agent is walking un-dead
     */
    @Override
    public boolean isZombie() {
        return this.timeToQuit;
    }

}
