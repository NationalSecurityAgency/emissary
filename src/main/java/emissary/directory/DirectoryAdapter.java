package emissary.directory;

import java.util.Set;

import emissary.core.EmissaryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class implements all of the emissary.directory.DirectoryPlace observer interfaces with null implementations so
 * that it is easy to extend and just provide behaviour for the methods of interest.
 */
public class DirectoryAdapter implements PeerObserver, PlaceObserver, RelayObserver {

    // Our logger
    protected static final Logger logger = LoggerFactory.getLogger(DirectoryAdapter.class);

    /**
     * Pattern for place observer subscriptions
     * 
     * @see emissary.directory.KeyManipulator#gmatch
     */
    protected String pattern = "*.*.*.*";

    /**
     * Default constructor
     */
    public DirectoryAdapter() {}

    /**
     * Constructor with place pattern
     */
    public DirectoryAdapter(final String pattern) {
        this.pattern = pattern;
    }

    /**
     * Called when a place matching the subscription is registered
     * 
     * @param observableKey key of the directory reporting the registration
     * @param placeKey key of the place that is being registered
     */
    @Override
    public void placeRegistered(final String observableKey, final String placeKey) {
        logger.debug("Place registered " + placeKey + " in directory " + observableKey);
    }

    /**
     * Called when a place matching the subscription is deregistered
     * 
     * @param observableKey key of the directory reporting the deregistration
     * @param placeKey key of the place that is being deregistered
     */
    @Override
    public void placeDeregistered(final String observableKey, final String placeKey) {
        logger.debug("Place deregistered " + placeKey + " in directory " + observableKey);
    }

    /**
     * Called when the cost of a place matching the subscription is changed
     * 
     * @param observableKey key of the directory reporting the change
     * @param placeKey key of the place that is being changed
     */
    @Override
    public void placeCostChanged(final String observableKey, final String placeKey) {
        logger.debug("Place cost change " + placeKey + " in directory " + observableKey);
    }

    /**
     * The pattern for this observers subscription,
     * 
     * @see emissary.directory.KeyManipulator#gmatch
     */
    @Override
    public String getPattern() {
        return this.pattern;
    }

    /**
     * Called when the peer group changes
     * 
     * @param observableKey the key of the directory being observed
     * @param peers the list of peers currently in the group
     */
    @Override
    public void peerUpdate(final String observableKey, final Set<DirectoryEntry> peers) {
        logger.debug("Peer group updates from " + observableKey + " set is now " + peers);
    }

    /**
     * Called when a child is added to the relay poing
     * 
     * @param observableKey the key of the directory being observed
     * @param child the child added to the relay point
     */
    @Override
    public void childAdded(final String observableKey, final DirectoryEntry child) {
        logger.debug("Relay group updates from " + observableKey + " added " + child.getKey());
    }

    /**
     * Called when a child is removed from the relay poing
     * 
     * @param observableKey the key of the directory being observed
     * @param child the child removed from the relay point
     */
    @Override
    public void childRemoved(final String observableKey, final DirectoryEntry child) {
        logger.debug("Relay group updates from " + observableKey + " remove " + child.getKey());
    }

    /**
     * Static method to register on the local default named directory
     * 
     * @param observer the DirectoryObserver to register
     */
    public static void register(final DirectoryObserver observer) throws EmissaryException {
        final IDirectoryPlace directory = DirectoryPlace.lookup();
        if (directory != null) {
            directory.addObserver(observer);
        }
    }

    /**
     * Static method to remove from the local default named directory
     * 
     * @param observer the DirectoryObserver to remove
     */
    public static void remove(final DirectoryObserver observer) throws EmissaryException {
        final IDirectoryPlace directory = DirectoryPlace.lookup();
        if (directory != null) {
            directory.deleteObserver(observer);
        }
    }
}
