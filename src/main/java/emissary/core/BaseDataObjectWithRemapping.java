package emissary.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.LinkedListMultimap;

/**
 * Class to hold byte array of data, header, footer, and attributes
 */
public class BaseDataObjectWithRemapping extends BaseDataObject {

    /* Including this here make serialization of this object faster. */
    private static final long serialVersionUID = 7362181964652092657L;

    /**
     * Create an empty BaseDataObject.
     */
    public BaseDataObjectWithRemapping() {
        super();
    }

    /**
     * Create a new BaseDataObject with byte array and name passed in. WARNING: this implementation uses the passed in array
     * directly, no copy is made so the caller should not reuse the array.
     *
     * @param newData the bytes to hold
     * @param name the name of the data item
     */
    public BaseDataObjectWithRemapping(final byte[] newData, final String name) {
        super(newData, name);
    }

    /**
     * Create a new BaseDataObject with byte array, name, and initial form WARNING: this implementation uses the passed in
     * array directly, no copy is made so the caller should not reuse the array.
     *
     * @param newData the bytes to hold
     * @param name the name of the data item
     * @param form the initial form of the data
     */
    public BaseDataObjectWithRemapping(final byte[] newData, final String name, final String form) {
        super(newData, name, form);
    }

    public BaseDataObjectWithRemapping(final byte[] newData, final String name, final String form, final String fileType) {
        super(newData, name, form, fileType);
    }

    @Override
    public boolean hasParameter(final String key) {
        // Try remapping
        try {
            final MetadataDictionary dict = MetadataDictionary.lookup();
            return this.parameters.containsKey(dict.map(key));
        } catch (NamespaceException ex) {
            // Remapping not enabled
            return this.parameters.containsKey(key);
        }
    }

    @Override
    public void putParameter(final String key, final Object val) {
        // Try remapping
        MetadataDictionary dict = null;
        try {
            dict = MetadataDictionary.lookup();
        } catch (NamespaceException ex) {
            // Remapping not enabled
        }

        final String n = dict != null ? dict.map(key) : key;

        this.parameters.removeAll(n);

        if (val instanceof Iterable) {
            this.parameters.putAll(n, (Iterable<?>) val);
        } else {
            this.parameters.put(n, val);
        }
    }

    /**
     * Merge in new parameters using the specified policy to determine whether to keep all values, unique values, or prefer
     * existing values
     *
     * @param m map of new parameters
     * @param policy the merge policy
     */
    @Override
    public void putParameters(final Map<? extends String, ? extends Object> m, final MergePolicy policy) {
        // Try remapping
        MetadataDictionary dict = null;
        try {
            dict = MetadataDictionary.lookup();
        } catch (NamespaceException ex) {
            // Remapping not enabled
        }

        for (final Map.Entry<? extends String, ? extends Object> entry : m.entrySet()) {
            final String name = dict != null ? dict.map(entry.getKey()) : entry.getKey();

            if ((policy == MergePolicy.KEEP_EXISTING) && this.parameters.containsKey(name)) {
                continue;
            }

            final Object value = entry.getValue();
            if (value instanceof Iterable) {
                for (final Object v : (Iterable<?>) value) {
                    if (policy == MergePolicy.KEEP_ALL || policy == MergePolicy.KEEP_EXISTING) {
                        this.parameters.put(name, v);
                    } else if (policy == MergePolicy.DISTINCT) {
                        if (!this.parameters.containsEntry(name, v)) {
                            this.parameters.put(name, v);
                        }
                    } else {
                        throw new RuntimeException("Unhandled parameter merge policy " + policy + " for " + name);
                    }
                }
            } else {
                if (policy == MergePolicy.KEEP_ALL || policy == MergePolicy.KEEP_EXISTING) {
                    this.parameters.put(name, value);
                } else if (policy == MergePolicy.DISTINCT) {
                    if (!this.parameters.containsEntry(name, value)) {
                        this.parameters.put(name, value);
                    }
                } else {
                    throw new RuntimeException("Unhandled parameter merge policy " + policy + " for " + name);
                }
            }
        }
    }

    @Override
    public List<Object> getParameter(final String key) {
        // Try remapping
        List<Object> v = null;
        try {
            final MetadataDictionary dict = MetadataDictionary.lookup();
            v = this.parameters.get(dict.map(key));
        } catch (NamespaceException ex) {
            // Remapping not enabled
            v = this.parameters.get(key);
        }
        if ((v == null) || v.isEmpty()) {
            return null;
        }
        return v;
    }

    @Override
    public void appendParameter(final String key, final CharSequence value) {
        // Try remapping
        try {
            final MetadataDictionary dict = MetadataDictionary.lookup();
            this.parameters.put(dict.map(key), value);
        } catch (NamespaceException ex) {
            // Remapping not enabled
            this.parameters.put(key, value);
        }
    }

    @Override
    public void appendParameter(final String key, final Iterable<? extends CharSequence> values) {
        // Try remapping
        String pkey = key;
        try {
            final MetadataDictionary dict = MetadataDictionary.lookup();
            pkey = dict.map(key);
        } catch (NamespaceException ex) {
            // Remapping not enabled
        }

        this.parameters.putAll(pkey, values);
    }

    /**
     * Append data to the specified metadata element if it doesn't already exist If you expect to append a lot if things
     * this way, this method might not have the performance characteristics that you expect. You can build a set and
     * externally and append the values after they are uniqued.
     *
     * @param key name of the metadata element
     * @param value the value to append
     * @return true if the item is added, false if it already exists
     */
    @Override
    public boolean appendUniqueParameter(final String key, final CharSequence value) {
        // Try remapping
        MetadataDictionary dict = null;
        try {
            dict = MetadataDictionary.lookup();
        } catch (NamespaceException ex) {
            // Remapping not enabled
        }

        final String n = dict != null ? dict.map(key) : key;

        if (this.parameters.containsEntry(n, value)) {
            return false;
        }

        this.parameters.put(n, value);
        return true;
    }

    @Override
    public List<Object> deleteParameter(final String key) {
        try {
            final MetadataDictionary dict = MetadataDictionary.lookup();
            return this.parameters.removeAll(dict.map(key));
        } catch (NamespaceException ex) {
            // Renaming not enabled
            return this.parameters.removeAll(key);
        }
    }

    /**
     * Return a specified multipart alternative view of the data WARNING: this implementation returns the actual array
     * directly, no copy is made so the caller must be aware that modifications to the returned array are live.
     *
     * @param s the name of the view to retrieve
     * @return byte array of alternate view data or null if none
     */
    @Override
    public byte[] getAlternateView(final String s) {
        try {
            final MetadataDictionary dict = MetadataDictionary.lookup();
            return this.multipartAlternative.get(dict.map(s));
        } catch (NamespaceException ex) {
            return this.multipartAlternative.get(s);
        }
    }

    /**
     * Add a multipart alternative view of the data WARNING: this implementation returns the actual array directly, no copy
     * is made so the caller must be aware that modifications to the returned array are live.
     *
     * @param name the name of the new view
     * @param data the byte array of data for the view
     */
    @Override
    public void addAlternateView(final String name, final byte[] data) {
        String mappedName = name;
        try {
            final MetadataDictionary dict = MetadataDictionary.lookup();
            mappedName = dict.map(name);
        } catch (NamespaceException ex) {
            // ignore
        }

        if (data == null) {
            this.multipartAlternative.remove(mappedName);
        } else {
            this.multipartAlternative.put(mappedName, data);
        }
    }

    @Override
    public void addAlternateView(final String name, final byte[] data, final int offset, final int length) {
        String mappedName = name;
        try {
            final MetadataDictionary dict = MetadataDictionary.lookup();
            mappedName = dict.map(name);
        } catch (NamespaceException ex) {
            // ignore
        }

        if (data == null || length <= 0) {
            this.multipartAlternative.remove(mappedName);
        } else {
            final byte[] mpa = new byte[length];
            System.arraycopy(data, offset, mpa, 0, length);
            this.multipartAlternative.put(mappedName, mpa);
        }
    }

    /**
     * Clone this payload
     */
    @Override
    public IBaseDataObject clone() throws CloneNotSupportedException {
        final BaseDataObjectWithRemapping c = (BaseDataObjectWithRemapping) super.clone();
        if ((this.theData != null) && (this.theData.length > 0)) {
            c.setData(this.theData, 0, this.theData.length);
        }
        c.currentForm = new ArrayList<>(this.currentForm);
        c.history = new ArrayList<>(this.history);
        c.multipartAlternative = new HashMap<>(this.multipartAlternative);
        c.priority = this.priority;
        c.creationTimestamp = this.creationTimestamp;

        if ((this.extractedRecords != null) && !this.extractedRecords.isEmpty()) {
            c.clearExtractedRecords(); // remove super.clone copy
            for (final IBaseDataObject r : this.extractedRecords) {
                c.addExtractedRecord(r.clone());
            }
        }
        // This creates a deep copy Guava style
        c.parameters = LinkedListMultimap.create(this.parameters);

        return c;
    }
}
