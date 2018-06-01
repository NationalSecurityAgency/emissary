package emissary.directory;

import java.util.Set;

/**
 * This interface can be implemented by any class wishing to subscribe to peer group changes in an emissary
 * DirectoryPlace.
 */
public interface PeerObserver extends DirectoryObserver {
    /**
     * Called when the peer group changes
     * 
     * @param observableKey the key of the directory being observed
     * @param peers the list of peers currently in the group not including the observable being monitored
     */
    void peerUpdate(String observableKey, Set<DirectoryEntry> peers);
}
