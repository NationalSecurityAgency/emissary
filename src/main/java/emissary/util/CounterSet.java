package emissary.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * A set of named counters that can be easily incremented
 */
public class CounterSet extends HashMap<String, Integer> {
    private static final long serialVersionUID = 3741872528399600810L;
    // Controls whether unknown keys will be counted, no if false
    protected boolean flexentry = false;

    /**
     * Create a new set of counters
     */
    public CounterSet() {}

    /**
     * Create a new set of counters
     * 
     * @param flexentry whether to allow non initialized keys to be incremented
     */
    public CounterSet(boolean flexentry) {
        super();
        this.flexentry = flexentry;
    }

    /**
     * Create a new set of counters
     * 
     * @param initialCapacity hash map initial capacity
     */
    public CounterSet(int initialCapacity) {
        super(initialCapacity);
    }

    /**
     * Create a new set of counters
     * 
     * @param initialCapacity hash map initial capacity
     * @param loadFactor hash map load factor
     */
    public CounterSet(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
    }

    /**
     * Change the flexentry setting
     * 
     * @param val the new value
     */
    public void setFlexEntry(boolean val) {
        this.flexentry = val;
    }

    /**
     * Determine flex entry status
     * 
     * @return true if unknown keys are allowed in add/increment
     */
    public boolean isFlexEntry() {
        return flexentry;
    }

    /**
     * Get the names of the current counters
     */
    public Set<String> getKeys() {
        return new HashSet<String>(this.keySet());
    }

    /**
     * Add a counter
     * 
     * @param key the name of the new Counter
     */
    public void addKey(String key) {
        put(key, 0);
    }

    /**
     * Add a collection of counters
     * 
     * @param keys the names of the new counters
     */
    public void addKeys(Collection<String> keys) {
        for (String key : keys)
            addKey(key);
    }

    /**
     * Add value to a counter
     * 
     * @param key the name of the counter
     * @param val how much to add
     * @return the value of the counter, or -1 if it is not allowed
     */
    public int add(String key, int val) {
        if (containsKey(key)) {
            put(key, get(key) + val);
        } else if (flexentry) {
            put(key, val);
        }
        return containsKey(key) ? get(key) : -1;
    }

    /**
     * Increment the named Counter
     * 
     * @param key the name of the Counter
     * @return the value of the counter, or -1 if it is not allowed
     */
    public int increment(String key) {
        return add(key, 1);
    }

    /**
     * Decrement the named Counter
     * 
     * @param key the name of the Counter
     * @return the value of the counter, or -1 if it is not allowed
     */
    public int decrement(String key) {
        return add(key, -1);
    }
}
