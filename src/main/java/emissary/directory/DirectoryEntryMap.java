package emissary.directory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keep a map of DataID to DirectoryEntryList for the Directory Extensible to use other things for the key if desired,
 * just override the methods that figure out the key automatically from the DirectoryEntry or DirectoryEntryList.
 */
public class DirectoryEntryMap extends ConcurrentHashMap<String, DirectoryEntryList> {

    // Serializable
    static final long serialVersionUID = 9097156614421373808L;

    private static final Logger logger = LoggerFactory.getLogger(DirectoryEntryMap.class);

    /** Value of DEEP_COPY flag */
    public static final boolean DEEP_COPY = true;

    /** Value of SHALLOW_COPY flag */
    public static final boolean SHALLOW_COPY = false;

    /**
     * No arg ctor supplies our tuned defaults to the super ctor
     */
    public DirectoryEntryMap() {
        super(1024, 0.8f, 3);
    }

    /**
     * Capacity ctor
     * 
     * @param initialCapacity initial capacity for map
     */
    public DirectoryEntryMap(final int initialCapacity) {
        super(initialCapacity, 0.8f, 3);
    }

    /**
     * Full ctor
     * 
     * @param initialCapacity initial capacity for map
     * @param loadFactor how loaded before rehash required
     * @param concurrencyLevel how many threads can update at once
     */
    public DirectoryEntryMap(final int initialCapacity, final float loadFactor, final int concurrencyLevel) {
        super(initialCapacity, loadFactor, concurrencyLevel);
    }

    /**
     * Create a new map from an old one. Makes a shallow copy
     * 
     * @param map the map to copy
     */
    public DirectoryEntryMap(final DirectoryEntryMap map) {
        this(map, SHALLOW_COPY);
    }

    /**
     * Create a new map from an old one.
     * 
     * @param map the map to copy
     * @param deepCopy true if should be a deep copy
     */
    public DirectoryEntryMap(final DirectoryEntryMap map, final boolean deepCopy) {
        this();
        if (map != null) {
            for (final Map.Entry<String, DirectoryEntryList> entry : map.entrySet()) {
                if (deepCopy) {
                    final DirectoryEntryList dl = new DirectoryEntryList(entry.getValue(), DirectoryEntryList.DEEP_COPY);
                    this.put(entry.getKey(), dl);
                } else {
                    this.put(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    /**
     * Add a directory entry to the appropriate DirectoryEntryList If it is a duplicate entry in all parts except cost, only
     * the lowest cost entry is kept. Either this entry or the one already in the list will be discarded.
     *
     * @param d the entry to add
     */
    public void addEntry(final DirectoryEntry d) {
        final String dataId = KeyManipulator.getDataID(d.getKey());
        addEntry(dataId, d);
    }

    /**
     * Add the directory entry to the specified list
     * 
     * @param key the key to this map
     * @param d the entry to add
     */
    protected void addEntry(final String key, final DirectoryEntry d) {
        DirectoryEntryList list = get(key);

        if (list == null) {
            list = new DirectoryEntryList();
            put(key, list);
        }
        final int beforeSize = list.size();
        list.add(d);
        final int afterSize = list.size();

        if (logger.isDebugEnabled()) {
            // This check could be wrong since nothing is synchronized.
            // Its understood and ok since it's just debug stuff
            if (afterSize > beforeSize) {
                logger.debug("Appears to have added entry " + d.getKey() + " list is now size " + afterSize);
            } else {
                logger.debug("Appear to have dropped or replaced entry " + d.getKey() + " list is still size " + afterSize);
            }
        }
    }

    /**
     * Builds a super list of all the entries currently in all buckets of the map
     * 
     * @return list of all directory entries
     */
    public List<DirectoryEntry> allEntries() {
        final List<DirectoryEntry> list = new ArrayList<DirectoryEntry>();

        for (final DirectoryEntryList d : values()) {
            list.addAll(d);
        }
        return list;
    }

    /**
     * Builds a super list of all the entry keys currently in all buckets of the map
     * 
     * @return list of all directory entry keys
     */
    public List<String> allEntryKeys() {
        final List<String> list = new ArrayList<String>();

        for (final DirectoryEntryList d : values()) {
            for (final DirectoryEntry e : d) {
                list.add(e.getFullKey());
            }
        }
        return list;
    }

    /**
     * Perform a super count of all the DirectoryEntry objects on all of the lists
     * 
     * @return count of all DirectoryEntry contained in map
     */
    public int entryCount() {
        int count = 0;
        for (final DirectoryEntryList d : values()) {
            count += d.size();
        }
        return count;
    }

    /**
     * Remove a directory entry from the map
     * 
     * @param entryKey for the entry to remove, not wildcarded
     * @return the removed entry or null if not found
     */
    public DirectoryEntry removeEntry(final String entryKey) {
        final String dataId = KeyManipulator.getDataID(entryKey);
        return removeEntry(dataId, entryKey);
    }

    /**
     * Remove the directory entry specified by entry key from the list specified by key
     * 
     * @param key the key to this map
     * @param entryKey the key of the DirectoryEntry to remove
     * @return the removed object
     */
    protected DirectoryEntry removeEntry(final String key, final String entryKey) {
        DirectoryEntry removed = null;
        final DirectoryEntryList list = get(key);
        if (list != null) {
            // NB: cannot remove from DirectoryEntryList through iterator
            for (int i = 0; i < list.size(); i++) {
                final DirectoryEntry entry = list.get(i);
                if (entry.getKey().equals(entryKey)) {
                    removed = entry;
                    list.remove(i);
                    break;
                }
            }

            // Remove the mapping if it is empty
            if (list.size() == 0) {
                this.remove(key);
            }
        }

        return removed;
    }

    /**
     * Remove all entries that match the key returning them as a list
     * 
     * @param key the key that must be matched (can be wildcarded)
     * @return list of entries that were removed from the map
     */
    public List<DirectoryEntry> removeAllMatching(final String key) {
        return removeAllMatching(key, Long.MAX_VALUE);
    }

    /**
     * Remove all entries that match the key returning them as a list
     * 
     * @param key the key that must be matched (can be wildcarded)
     * @param checkpoint only remove entries older than this value, use Long.MAX_VALUE to have all matching entries removed
     *        without regard to their age
     * @return list of entries that were removed from the map
     */
    public List<DirectoryEntry> removeAllMatching(final String key, final long checkpoint) {
        final List<DirectoryEntry> removed = new ArrayList<DirectoryEntry>();

        for (final DirectoryEntryList list : values()) {
            // NB: cannot remove from DirectoryEntryList through iterator
            // Need to mark and sweep
            for (int i = 0; i < list.size(); i++) {
                final DirectoryEntry entry = list.get(i);
                if (KeyManipulator.gmatch(entry.getKey(), key) && entry.getAge() < checkpoint) {
                    removed.add(entry);
                }
            }
        }

        for (final DirectoryEntry d : removed) {
            removeEntry(d.getKey());
        }

        return removed;
    }

    /**
     * Find and remove all entries on a directory
     * 
     * @param key the key of the directory
     * @return list of all entries removed
     */
    public List<DirectoryEntry> removeAllOnDirectory(final String key) {
        // Wildcard the key so we can just use gmatch
        final String wckey = KeyManipulator.getHostMatchKey(key);
        return removeAllMatching(wckey);
    }

    /**
     * Find and remove all entries on a directory
     * 
     * @param key the key of the directory
     * @param checkpoint only remove entries older than this
     * @return list of all entries removed
     */
    public List<DirectoryEntry> removeAllOnDirectory(final String key, final long checkpoint) {
        // Wildcard the key so we can just use gmatch
        final String wckey = KeyManipulator.getHostMatchKey(key);
        return removeAllMatching(wckey, checkpoint);
    }

    /**
     * Collect all entries that match the key returning them as a list
     * 
     * @param key the key that must be matched (can be wildcarded)
     * @return list of entries that were matches, still live in the directory map
     */
    public List<DirectoryEntry> collectAllMatching(final String key) {
        final List<DirectoryEntry> match = new ArrayList<DirectoryEntry>();

        final String dataId = KeyManipulator.getDataID(key);
        if (dataId.contains("*") || dataId.contains("?")) {
            for (final DirectoryEntryList list : values()) {
                for (final DirectoryEntry entry : list) {
                    if (KeyManipulator.gmatch(entry.getKey(), key)) {
                        match.add(entry);
                    }
                }
            }
        } else {
            final DirectoryEntryList list = this.get(dataId);
            if (list != null) {
                for (final DirectoryEntry entry : list) {
                    if (KeyManipulator.gmatch(entry.getKey(), key)) {
                        match.add(entry);
                    }
                }
            }
        }
        return match;
    }

    /**
     * Find all entries on a directory
     * 
     * @param key the key of the directory
     * @return list of all entries matched, still live in directory map
     */
    public List<DirectoryEntry> collectAllOnDirectory(final String key) {
        // Wildcard the key so we can just use gmatch
        final String wckey = KeyManipulator.getHostMatchKey(key);
        return collectAllMatching(wckey);
    }

    /**
     * Count all entries that match the key
     * 
     * @param key the key that must be matched (can be wildcarded)
     * @return count of entries that were matches
     */
    public int countAllMatching(final String key) {
        int count = 0;

        for (final DirectoryEntryList list : values()) {
            for (final Iterator<DirectoryEntry> i = list.iterator(); i.hasNext();) {
                final DirectoryEntry entry = i.next();
                if (KeyManipulator.gmatch(entry.getKey(), key)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Count all entries on a directory
     * 
     * @param key the key of the directory
     * @return count of all entries matched
     */
    public int countAllOnDirectory(final String key) {
        // Wildcard the key so we can just use gmatch
        final String wckey = KeyManipulator.getHostMatchKey(key);
        return countAllMatching(wckey);
    }

    /**
     * Merge new entries in our local entry map
     *
     * @param entryList the list of entries to merge
     */
    public void addEntries(final List<DirectoryEntry> entryList) {
        if (entryList != null) {
            for (final DirectoryEntry d : entryList) {
                addEntry(d);
            }
        }
    }

    /**
     * Merge new non-local peer entries into our local entry map.
     * 
     * @param that the new entries
     */
    public void addEntries(final DirectoryEntryMap that) {
        if (that != null) {
            for (final Map.Entry<String, DirectoryEntryList> entry : that.entrySet()) {
                // Optimized add since already grouped by same key
                DirectoryEntryList list = this.get(entry.getKey());
                if (list == null) {
                    list = new DirectoryEntryList();
                    put(entry.getKey(), list);
                }
                list.addAll(entry.getValue());
            }
        }
    }

    /**
     * Change cost on matching entries
     * 
     * @param key the key to match
     * @param increment cost increment
     * @return List of string entry keys changed
     */
    public List<String> addCostToMatching(final String key, final int increment) {
        final List<DirectoryEntry> list = collectAllMatching(key);
        final List<String> ret = new ArrayList<String>();
        for (final DirectoryEntry e : list) {
            e.addCost(increment);
            ret.add(e.getFullKey());
        }

        // Put them back in order if something changed
        if (ret.size() > 0) {
            sort();
        }

        return ret;
    }

    /**
     * Force a sort on all the directory entry lists due to some external factors
     */
    public void sort() {
        for (final DirectoryEntryList list : values()) {
            list.sort();
        }
    }
}
