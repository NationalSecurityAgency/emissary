package emissary.directory;

import java.util.List;
import java.util.Set;

import emissary.core.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interface for inter-directory operations over http These methods are designed to only be called by the remote
 * inter-directory communications layer
 */
public interface IRemoteDirectory extends IDirectoryPlace {
    /**
     * Register a set of entries. This signature only meant to be called from within
     * emissary.server.mvc.adapters.DirectoryAdapter.
     * 
     * @param entryList the new entries to add
     * @param propagating true if going down the line
     */
    void irdAddPlaces(List<DirectoryEntry> entryList, boolean propagating);

    /**
     * Deregister places. Removes all keys for the specified places. Should only be called from
     * emissary.server.mvc.adapters.DirectoryAdapter
     * 
     * @param keys four-tuple key for the place
     * @param propagating true if going down the line
     * @return count of keys removes
     */
    int irdRemovePlaces(List<String> keys, boolean propagating);

    /**
     * Add a child directory to this one
     * 
     * @param key string key for child directory
     */
    void irdAddChildDirectory(String key);

    /**
     * Remove places for a failed remote machine
     * 
     * @param key string key of failed directory
     * @param propagating true if headed back down the directory chain
     * @return count of how many places were removed locally
     */
    int irdFailRemoteDirectory(String key, boolean propagating);

    /**
     * Add a Set of peer directory to this one
     * 
     * @param keys set of string key for peer directories
     */
    void irdAddPeerDirectories(Set<String> keys);

    /**
     * Get the requested directory entry list excluding all entries that actually proxy for a place local to the requester
     * 
     * @param dataId the key to the entry Map set of DirectoryEntryList objects
     * @param requester key of the place requesting the list
     * @return a DirectoryEntryList object for the key or null if none
     */
    DirectoryEntryList irdGetEntryListExcl(String dataId, String requester);

    /**
     * Helper class to lookup an instance of the local directory cast to this interface if possible
     */
    public class Lookup {
        private final Logger logger = LoggerFactory.getLogger(Lookup.class);

        /**
         * Look up the local directory using one of two methods. The easier method almost always works, the case where it
         * doesn't in when there are multilpe configured Emissary nodes on the same local JVM through a single jetty with
         * multiple Listeners. This is a testing scenario but it is helpful to keep supporting it so we have good test coverage.
         * 
         * @param name name of the local directory or null for default
         */
        public IRemoteDirectory getLocalDirectory(final String name) {
            IDirectoryPlace dir = null;
            try {
                if (name != null) {
                    dir = (IDirectoryPlace) Namespace.lookup(name);
                } else {
                    dir = DirectoryPlace.lookup();
                }
            } catch (emissary.core.EmissaryException ex) {
                this.logger.debug("Could not find local directory " + name);
            }

            IRemoteDirectory remoteDirectory = null;
            if (dir != null) {
                if (dir instanceof IRemoteDirectory) {
                    remoteDirectory = (IRemoteDirectory) dir;
                } else {
                    this.logger.error("Directory is not an IRemoteDirectory!");
                }
            }
            return remoteDirectory;
        }
    }
}
