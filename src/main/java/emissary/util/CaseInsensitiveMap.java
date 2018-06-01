package emissary.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Be a HashMap that doesn't care about the case of the keys
 */
public class CaseInsensitiveMap<K, V> extends HashMap<K, V> {
    // serializable
    static final long serialVersionUID = -8692638820306464417L;

    // For remapping string keys to through a case-insensitive layer
    private Map<String, String> remap = new HashMap<String, String>();

    /**
     * Create one
     */
    public CaseInsensitiveMap() {
        super();
    }

    /**
     * Create one with capacity
     */
    public CaseInsensitiveMap(int initialCapacity) {
        super(initialCapacity);
    }

    /**
     * Create one with capacity and load factor
     */
    public CaseInsensitiveMap(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
    }

    /**
     * Create one from another map
     */
    public CaseInsensitiveMap(Map<? extends K, ? extends V> map) {
        super(map.size());
        putAll(map);
    }

    /**
     * Put an element into the map If it is a string, put it in and also add it to our case remapping map
     */
    @Override
    // @SuppressWarnings("unchecked")
    public V put(K key, V value) {
        if (key instanceof String) {
            String uckey = ((String) key).toLowerCase();
            if (remap.containsKey(uckey)) {
                this.remove(uckey);
            }
            remap.put(uckey, (String) key);
        }

        return super.put(key, value);
    }


    /**
     * Get an element from the map If the key is a string check our remap first
     */
    @Override
    public V get(Object key) {
        Object realkey = key;

        if (key instanceof String) {
            String strkey = remap.get(((String) key).toLowerCase());
            if (strkey != null) {
                realkey = strkey;
            }
        }

        return super.get(realkey);
    }

    /**
     * Put all
     */
    @Override
    public void putAll(Map<? extends K, ? extends V> t) {
        for (Map.Entry<? extends K, ? extends V> entry : t.entrySet()) {
            this.put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Contains key
     */
    @Override
    public boolean containsKey(Object key) {
        Object realkey = key;

        if (key instanceof String) {
            String strkey = remap.get(((String) key).toLowerCase());
            if (strkey != null) {
                realkey = strkey;
            }
        }

        return super.containsKey(realkey);
    }

    /**
     * Remove a mapping
     */
    @Override
    public V remove(Object key) {
        Object realkey = key;

        if (key instanceof String) {
            String uckey = ((String) key).toLowerCase();
            String strkey = remap.get(uckey);
            if (strkey != null) {
                realkey = strkey;
            }

            remap.remove(uckey);
        }

        return super.remove(realkey);
    }

}
