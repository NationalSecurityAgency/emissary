package emissary.directory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * This class is used by DirectoryPlace to manage the interaction with the different types of observers that need to be
 * called on the various observable change events.
 */
public class DirectoryObserverManager {
    /** Our logger */
    protected static final Logger logger = LoggerFactory.getLogger(DirectoryObserverManager.class);

    /** Directory key that this observer mananger acts for */
    String directoryKey;

    /** The observers that are registered */
    List<DirectoryObserver> observers = new CopyOnWriteArrayList<>();

    /** The types of actions that observers can register for */
    public enum Action {
        PEER_GROUP_CHANGE, PLACE_ADD, PLACE_REMOVE, PLACE_COST_CHANGE
    }

    /**
     * Construct with key
     * 
     * @param key the key of the directory we work on behalf of
     */
    public DirectoryObserverManager(final String key) {
        this.directoryKey = key;
    }

    /**
     * Add an observer
     * 
     * @param observer the new observer to add
     */
    public void addObserver(final DirectoryObserver observer) {
        this.observers.add(observer);
    }

    /**
     * Remove an observer
     * 
     * @param observer the object to remove
     * @return true if it was found on the list
     */
    public boolean deleteObserver(final DirectoryObserver observer) {
        return this.observers.remove(observer);
    }

    /**
     * Count the observers
     * 
     * @return count of how many observers are being managed
     */
    public int getObserverCount() {
        return this.observers.size();
    }

    /**
     * Count how many peer observers are being managed
     * 
     * @return count of observers on the peer list
     */
    public int getPeerObserverCount() {
        int count = 0;
        for (final DirectoryObserver d : this.observers) {
            if (d instanceof PeerObserver) {
                count++;
            }
        }
        return count;
    }

    /**
     * Count how many place observers are being managed
     * 
     * @return count of observers on the place list
     */
    public int getPlaceObserverCount() {
        int count = 0;
        for (final DirectoryObserver d : this.observers) {
            if (d instanceof PlaceObserver) {
                count++;
            }
        }
        return count;
    }

    /**
     * Notify all peer observers of peer list change
     * 
     * @param peers the current list of peers to our directory
     */
    public void peerUpdate(final Set<DirectoryEntry> peers) {
        int count = 0;
        for (final DirectoryObserver d : this.observers) {
            if (d instanceof PeerObserver) {
                ((PeerObserver) d).peerUpdate(this.directoryKey, peers);
                count++;
            }
        }
        logger.debug("Notified {} PeerObserver instances", count);
    }

    /**
     * Notify all matching place observers of place add
     * 
     * @param placeKey the key that was added
     */
    public void placeAdd(final String placeKey) {
        logger.debug("Received notice of {} added", placeKey);
        placeUpdate(Action.PLACE_ADD, placeKey);
    }

    /**
     * Notify all matching place observers of place adds
     * 
     * @param placeKeys the list of keys added
     */
    public void placeAdd(final List<String> placeKeys) {
        logger.debug("Received notice of {} added", placeKeys.size());

        for (final String key : placeKeys) {
            placeUpdate(Action.PLACE_ADD, key);
        }
    }

    /**
     * Notify all matching place observers of place adds
     * 
     * @param placeEntries the list of entries added
     */
    public void placeAddEntries(final List<DirectoryEntry> placeEntries) {
        logger.debug("Received notice of {} added", placeEntries.size());

        for (final DirectoryEntry entry : placeEntries) {
            placeUpdate(Action.PLACE_ADD, entry.getFullKey());
        }
    }

    /**
     * Notify all matching place observers of place remove
     * 
     * @param placeKey the key what was removed
     */
    public void placeRemove(final String placeKey) {
        logger.debug("Received notice of {} removed", placeKey);
        placeUpdate(Action.PLACE_REMOVE, placeKey);
    }

    /**
     * Notify all matching place observers of place removes
     * 
     * @param placeKeys the list of keys removed
     */
    public void placeRemove(final List<String> placeKeys) {
        logger.debug("Received notice of {} removed", placeKeys.size());

        for (final String key : placeKeys) {
            placeUpdate(Action.PLACE_REMOVE, key);
        }
    }

    /**
     * Notify all matching place observers of place removes
     * 
     * @param placeEntries the list of entries removed
     */
    public void placeRemoveEntries(final List<DirectoryEntry> placeEntries) {
        logger.debug("Received notice of {} removed", placeEntries.size());

        for (final DirectoryEntry entry : placeEntries) {
            placeUpdate(Action.PLACE_REMOVE, entry.getFullKey());
        }
    }

    /**
     * Notify all matching place observers of place cost change
     * 
     * @param placeKey the revised key
     */
    public void placeCostChange(final String placeKey) {
        placeUpdate(Action.PLACE_COST_CHANGE, placeKey);
    }

    /**
     * Notify all matching place observers of place cost changes
     * 
     * @param placeKeys the list of keys with changed cost
     */
    public void placeCostChange(final List<String> placeKeys) {
        logger.debug("Received notice of {} cost changes", placeKeys.size());
        for (final String key : placeKeys) {
            placeUpdate(Action.PLACE_COST_CHANGE, key);
        }
    }

    /**
     * Notify all matching place observers of place cost changes
     * 
     * @param placeEntries the list of entries with changed cost
     */
    public void placeCostChangeEntries(final List<DirectoryEntry> placeEntries) {
        logger.debug("Received notice of {} cost changes", placeEntries.size());
        for (final DirectoryEntry entry : placeEntries) {
            placeUpdate(Action.PLACE_COST_CHANGE, entry.getFullKey());
        }
    }

    /**
     * Notify all matching place observers of change
     * 
     * @param action PLACE_ADD or PLACE_REMOVE
     * @param placeKey the key that was added or removed
     */
    protected void placeUpdate(final Action action, final String placeKey) {
        int obcount = 0;
        int matchcount = 0;

        for (final DirectoryObserver d : this.observers) {
            if (d instanceof PlaceObserver) {
                final PlaceObserver p = (PlaceObserver) d;
                obcount++;
                if (KeyManipulator.gmatch(placeKey, p.getPattern())) {
                    matchcount++;
                    logger.debug("Match! Doing {} for {}", action, placeKey);
                    switch (action) {
                        case PLACE_ADD:
                            p.placeRegistered(this.directoryKey, placeKey);
                            break;
                        case PLACE_REMOVE:
                            p.placeDeregistered(this.directoryKey, placeKey);
                            break;
                        case PLACE_COST_CHANGE:
                            p.placeCostChanged(this.directoryKey, placeKey);
                            break;
                    }
                } else {
                    logger.debug("No match for {} using pattern {}", placeKey, p.getPattern());
                }
            }
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Notified {} of {} place observers of place {} -- {} #all={}", matchcount, obcount, placeKey, action, this.observers.size());
        }
    }
}
