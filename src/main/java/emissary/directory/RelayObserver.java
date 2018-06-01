package emissary.directory;

/**
 * This interface can be implemented by any class wishing to subscribe to relay group changes in an emissary
 * DirectoryPlace.
 */
public interface RelayObserver extends DirectoryObserver {
    /**
     * Called when a child is added to the relay poing
     * 
     * @param observableKey the key of the directory being observed
     * @param child the child added to the relay point
     */
    void childAdded(String observableKey, DirectoryEntry child);

    /**
     * Called when a child is removed from the relay poing
     * 
     * @param observableKey the key of the directory being observed
     * @param child the child removed from the relay point
     */
    void childRemoved(String observableKey, DirectoryEntry child);
}
