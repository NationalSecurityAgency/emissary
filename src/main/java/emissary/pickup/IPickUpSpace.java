package emissary.pickup;

import java.util.List;

/**
 * The portions of an interface related to the PickupSpace, that is, dealing with a remote space that would like to feed
 * data to us if only we would take it when there are availalbe processing resources on this node.
 */
public interface IPickUpSpace {
    /**
     * Open a WorkSpace when told and start asking it for data
     * 
     * @param spaceName the remote name of the space to open
     */
    void openSpace(String spaceName);

    /**
     * Close a WorkSpace
     * 
     * @param spaceName the remote name of the space to close
     */
    void closeSpace(String spaceName);

    /**
     * Return name of space at top of list or null if none
     */
    String getSpaceName();

    /**
     * Get count of how many spaces are on the list
     */
    int getSpaceCount();

    /**
     * Get the names of all spaces on the list
     */
    List<String> getSpaceNames();

    /**
     * Take an item from the space
     * 
     * @return true if we got one
     */
    boolean take();

    /**
     * Count consecutive times a WorkSpace.take() made an error
     */
    int getNumConsecutiveTakeErrors(String spaceName);

    /**
     * Put a new WorkBundle on the queue
     * 
     * @param path the newly arrived WorkBundle object
     */
    boolean enque(emissary.pickup.WorkBundle path);

    /**
     * The size of the last WorkBundle successfully received
     */
    int getBundleSize(String spaceName);

    /**
     * Notify controlling space that a bundle is completed
     * 
     * @param bundleId the bundle that was completed
     * @param itWorked true if bundle processed normally
     */
    void bundleCompleted(String bundleId, boolean itWorked);

}
