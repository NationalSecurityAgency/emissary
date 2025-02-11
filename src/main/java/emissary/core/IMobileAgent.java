package emissary.core;

import emissary.directory.DirectoryEntry;
import emissary.place.IServiceProviderPlace;

import java.io.Serializable;
import java.util.List;

/**
 * Interface to the MobileAgent
 */
public interface IMobileAgent extends Serializable, Runnable {
    /**
     * Return the unique agent id
     */
    String agentId();

    /**
     * Get a reference to the payload this agent is responsible for
     */
    IBaseDataObject getPayload();

    /**
     * Send an agent on its way with the specified payload The payload is not processed at sourcePlace, source is only used
     * to get directory access to figure out where to go next.
     * 
     * @param payload the payload IBaseDataObject or list thereof
     * @param sourcePlace the place sending the payload the key of this place will be added to the transform history but the
     *        payload will not be processed here
     */
    void go(Object payload, IServiceProviderPlace sourcePlace);

    /**
     * Arriving payload assigned to an agent for process at arrivalPlace
     * 
     * @param payload the payload IBaseDataObject or list thereof
     * @param arrivalPlace the place to begin processing the payload has already been added to the transform history by the
     *        sender
     * @param mec the move error count to update the agent's state
     * @param iq the list of DirectoryEntry stored itinerary steps if any
     */
    void arrive(Object payload, IServiceProviderPlace arrivalPlace, int mec, List<DirectoryEntry> iq)
            throws Exception;

    /**
     * Retrieve the current move error count
     */
    int getMoveErrorCount();

    /**
     * Retreive the current list of stored itinerary steps
     * 
     * @return array of DirectoryEntry
     */
    @SuppressWarnings("AvoidObjectArrays")
    DirectoryEntry[] getItineraryQueueItems();

    /**
     * Return true if agent is working on a payload
     */
    boolean isInUse();

    /**
     * Get the payload as an object for serialization during transport.
     *
     */
    Object getPayloadForTransport();

    /**
     * Get the name of the current payload
     */
    String getName();

    /**
     * get the name of the last place processed.
     */
    String getLastPlaceProcessed();

    /**
     * Call this method to permanently stop the running thread when we finish what we are doing
     */
    void killAgent();

    /**
     * Call this method to permanently stop the running thread eventually Returns immediately
     */
    void killAgentAsync();

    /**
     * Determine if this agent is walking un-dead
     */
    boolean isZombie();

    /**
     * Interrupte the agent's thread
     */
    void interrupt();

    /**
     * Get the number of move errors
     */
    int getMaxMoveErrors();

    /**
     * Set the maximum number of move attempts that can error out before this instance will quit trying and set the workflow
     * to be an ERROR condition
     * 
     * @param value the maximum number of move failures
     */
    void setMaxMoveErrors(int value);

    /**
     * Get the maximum number of itinerary steps
     */
    int getMaxItinerarySteps();

    /**
     * Set the maximum number of itinerary steps before this instance will turn the workflow into an ERROR condition
     * 
     * @param value the new maximum number of steps
     */
    void setMaxItinerarySteps(int value);

    /**
     * Returns the current payload count of the agent.
     * 
     * @return default value of 1
     */
    default int payloadCount() {
        return 1;
    }

}
