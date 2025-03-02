package emissary.place;

import emissary.core.IBaseDataObject;
import emissary.core.MobileAgent;
import emissary.core.NamespaceException;
import emissary.core.ResourceException;
import emissary.directory.DirectoryEntry;

import java.util.List;
import java.util.Set;

/**
 * IServiceProviderPlace. IServiceProviderPlaces can be created by the emissary.admin.PlaceStarter and registered with
 * the emissary.directory.IDirectoryPlace to make their respective services available and a specified cost and quality
 * throughout the system.
 */
public interface IServiceProviderPlace {

    /**
     * Used as a marker on the transofrm history of a payload when we sprout it, between the parent's history and the new
     * history of the sprout
     */
    String SPROUT_KEY = "<SPROUT>";

    /**
     * Return list of next places to go with data. Delegation call through to our IDirectoryPlace
     * 
     * @param dataId the SERVICE_NAME::SERVICE_TYPE
     * @param lastPlace last place visited
     * @return list of DirectoryEntry
     */
    List<DirectoryEntry> nextKeys(String dataId, IBaseDataObject payload, DirectoryEntry lastPlace);

    /**
     * Add a service proxy to a running place. Duplicates are ignored.
     * 
     * @param serviceProxy the new proxy string to add
     */
    void addServiceProxy(String serviceProxy);

    /**
     * Add a full key to the running place.
     * 
     * @param key the new key
     */
    void addKey(String key);

    /**
     * Remove key
     * 
     * @param key the key to remove
     */
    void removeKey(String key);

    /**
     * Remove a service proxy from the running place. Proxy strings not found registered will be ignored
     * 
     * @param serviceProxy the proxy string to remove
     */
    void removeServiceProxy(String serviceProxy);

    /**
     * Shutdown the place, freeing any resources
     */
    void shutDown();

    /**
     * Visiting agents call here to have a payload processed
     * 
     * @param payload the payload to be processed
     */
    void agentProcessCall(IBaseDataObject payload) throws ResourceException;

    /**
     * Return an entry representing this place in the directory
     */
    DirectoryEntry getDirectoryEntry();

    /**
     * Method called by the HD Agents to process a payload
     * 
     * @param payload the payload to be processed
     * @return list of IBaseDataObject result attachments
     */
    List<IBaseDataObject> agentProcessHeavyDuty(IBaseDataObject payload) throws Exception;

    /**
     * Method called by the HD Agents for bulk processing of payloads
     * 
     * @param payloadList list of payloads to be processed
     * @return list of IBaseDataObject result attachments
     */
    List<IBaseDataObject> agentProcessHeavyDuty(List<IBaseDataObject> payloadList) throws Exception;

    /**
     * Override point for HD Agent calls
     * 
     * @param payload the payload to be processed
     * @return list of IBaseDataObject result attachments
     */
    List<IBaseDataObject> processHeavyDuty(IBaseDataObject payload) throws ResourceException;

    /**
     * Override point for non-HD agent calls
     * 
     * @param payload the payload to be processed
     */
    void process(IBaseDataObject payload) throws ResourceException;

    /**
     * Get key for place, first one on list with '*' as service proxy if there are multiple entries
     * 
     * @return the key of this place in the directory
     */
    String getKey();

    /**
     * List the keys for this place registration
     * 
     * @return list of string complete keys
     */
    Set<String> getKeys();

    /**
     * Return a set of the service proxies
     * 
     * @return set of string values
     */
    Set<String> getProxies();

    /**
     * Return the first service proxy on the list
     * 
     * @return SERVICE_PROXY value or empty string if none
     */
    String getPrimaryProxy();

    /**
     * Get place name
     * 
     * @return string name of the place
     */
    String getPlaceName();

    /**
     * Get custom resource limitation in millis if specified
     * 
     * @return -2 if not specified, or long millis if specified
     */
    long getResourceLimitMillis();

    /**
     * Get max resource limit in millis if specified
     *
     * @return -2 if not specified, or long millis if specified
     */
    long getResourceLimitMillisMax();

    /**
     * Get the action to take if resource hits the max timeout
     *
     * @return "Notify" not specified, or the action if specified
     */
    String getMaxTimeoutAction();

    /**
     * Get the agent that is currently responsible for this thread
     */
    MobileAgent getAgent() throws NamespaceException;

    /**
     * Returns whether form is denied
     */
    boolean isDenied(String s);
}
