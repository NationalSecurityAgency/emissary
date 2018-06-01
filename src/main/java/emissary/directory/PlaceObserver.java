package emissary.directory;

/**
 * This interface can be implemented by any class wishing to subscribe to place changes in an emissary DirectoryPlace.
 * These methods are called for the appropriate actions that match the pattern supplied to the observable.
 */
public interface PlaceObserver extends DirectoryObserver {
    /**
     * Called when a place matching the subscription is registered
     * 
     * @param observableKey key of the directory reporting the registration
     * @param placeKey key of the place that is being registered
     */
    void placeRegistered(String observableKey, String placeKey);

    /**
     * Called when a place matching the subscription is deregistered
     * 
     * @param observableKey key of the directory reporting the deregistration
     * @param placeKey key of the place that is being deregistered
     */
    void placeDeregistered(String observableKey, String placeKey);

    /**
     * Called when the cost of a place matching the subscription is changed
     * 
     * @param observableKey key of the directory reporting the change
     * @param placeKey key of the place that is being changed
     */
    void placeCostChanged(String observableKey, String placeKey);

    /**
     * The pattern for this observers subscription,
     * 
     * @see emissary.directory.KeyManipulator#gmatch
     */
    String getPattern();
}
