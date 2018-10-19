package emissary.directory;

import java.util.List;
import java.util.Set;

import emissary.place.IServiceProviderPlace;

/**
 * Interface for normal directory operations
 */
public interface IDirectoryPlace extends IServiceProviderPlace {

    int REMOTE_COST_OVERHEAD = 1000;
    int REMOTE_EXPENSE_OVERHEAD = 100000;

    /**
     * Register a place with all of its full keys
     *
     * @param keys list of keys with expense
     */
    void addPlaces(List<String> keys);

    /**
     * Deregister places. Removes all keys for the specified places
     *
     * @param keys four-tuple key for the place
     * @return count of keys removed
     */
    int removePlaces(List<String> keys);

    /**
     * Make directory contents available for debug or display and analysis
     *
     * @return List of DirectoryEntry (copies)
     */
    List<DirectoryEntry> getEntries();

    /**
     * Get list of DirectoryEntry that match the key pattern
     *
     * @param pattern a key pattern to match
     * @return List of DirectoryEntry (copies)
     */
    List<DirectoryEntry> getMatchingEntries(String pattern);

    /**
     * Make relay contents available for debug or display and analysis
     *
     * @return List of relay DirectoryEntries (copies)
     */
    List<DirectoryEntry> getRelayEntries();

    /**
     * Get list of relay DirectoryEntry that match the key pattern
     *
     * @param pattern a key pattern to match
     * @return List of DirectoryEntry (copies)
     */
    List<DirectoryEntry> getMatchingRelayEntries(String pattern);

    /**
     * Make directory contents entry keys available for display and transfer
     *
     * @return Iterator of String in the DataID format DATATYPE::SERVICETYPE
     */
    Set<String> getEntryKeys();

    /**
     * Get the requested directory entry
     *
     * @param dataId the key to the entry Map set of DirectoryEntryList objects
     * @return a DirectoryEntryList object for the key or null if none
     */
    DirectoryEntryList getEntryList(String dataId);

    /**
     * Get a list of the keys of all the peer directories known here
     *
     * @return set of string keys for peer directories
     */
    Set<String> getPeerDirectories();

    /**
     * Get a list of the keys of all children directories known here
     *
     * @return set of string keys for children
     */
    Set<String> getRegisteredChildren();

    /**
     * Get the key of the relay (parent) directory or null if none
     *
     * @return string key of parent directory
     */
    String getRelayDirectory();

    /**
     * Add an observer for one of the observable activities in the directory The runtime class of the observer determines
     * what is being observed
     *
     * @param observer the new DirectoryObserver to add
     */
    void addObserver(DirectoryObserver observer);

    /**
     * Remove an observer previously registered with this directory
     *
     * @param observer the object to remove
     * @return true if it was found on the list
     */
    boolean deleteObserver(DirectoryObserver observer);


    /**
     * Get the heartbeat status of a remote directory as seen from this directory.
     *
     * @param key the key of the remote directory
     * @return true if remote is up, false otherwise
     */
    boolean isRemoteDirectoryAvailable(String key);

    /**
     * Trigger a heartbeat message to the remote directory
     *
     * @param key the key of the remote directory
     * @return true if remote is up, false otherwise
     */
    boolean heartbeatRemoteDirectory(String key);

    /**
     * Indicate if directory is running
     *
     * @return true if running
     */
    boolean isRunning();

    /**
     * Indicate whether shutdown has been initiated
     *
     * @return true if shutdown initiated
     */
    boolean isShutdownInitiated();

    /**
     * Add a Routing acceptor function for the specified directory entry
     *
     * @param de the directory entry to associate this function with
     * @param script the javascript accept function
     * @param isDefault is a default rule for SERVICE_NAME
     * @return true if the function is added
     * @throws Exception if script does not compile
     */
    boolean addRoutingFunction(DirectoryEntry de, String script, boolean isDefault) throws Exception;

    /**
     * Remove a routing function
     *
     * @param de the directory entry's function to remove
     * @param isDefault if true remove the default routing rule
     * @return true if a function was removed
     */
    boolean removeRoutingFunction(DirectoryEntry de, boolean isDefault);
}
