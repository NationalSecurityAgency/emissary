package emissary.parser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

/**
 * Representation of a fully built session with metadata, header, footer, classification, initial forms, and data
 */
public class DecomposedSession {

    // Serializable
    static final long serialVersionUID = 8714610712515636160L;

    // Keys for the decomposed session map
    static final String HEADER_KEY = "HEADER";
    static final String FOOTER_KEY = "FOOTER";
    static final String DATA_KEY = "DATA";
    static final String CLASSIFICATION_KEY = "CLASSIFICATION";
    static final String METADATA_KEY = "METADATA";
    static final String INITIAL_FORMS = "INITIAL_FORMS";

    protected byte[] header = null;
    protected byte[] footer = null;
    protected byte[] data = null;
    protected String classification = null;
    protected List<String> initialForms = new ArrayList<String>();
    protected ArrayListMultimap<String, Object> metadata = ArrayListMultimap.create(100, 1);

    /**
     * Set the header. This implementation does NOT make a copy of the byte array Previously existing header data is lost
     *
     * @param h byte array of header data to set
     */
    public void setHeader(byte[] h) {
        header = h;
    }

    /**
     * Get the header
     *
     * @return byte array of header data or null if none
     */
    public byte[] getHeader() {
        return header;
    }

    /**
     * Set the footer This implementation does NOT make a copy of the byte array Previously existing footer data is lost
     *
     * @param f byte array of data to set as footer
     */
    public void setFooter(byte[] f) {
        footer = f;
    }

    /**
     * Get the footer
     *
     * @return byte array of footer or null if none
     */
    public byte[] getFooter() {
        return footer;
    }

    /**
     * Set the data entry using the passed in byte array This implementation does NOT make a copy of the byte array
     * Previously existing data is lost
     *
     * @param d bytes to set
     */
    public void setData(byte[] d) {
        setData(d, false);
    }

    /**
     * Set the data entry using the passed in byte array or a copy Previously existing data is lost
     *
     * @param d bytes to set
     * @param copy make a copy when true
     */
    public void setData(byte[] d, boolean copy) {
        if (d == null || !copy) {
            data = d;
        } else {
            setData(d, 0, d.length);
        }
    }

    /**
     * Set the data from the specified portion of the array Always makes a copy. Previously existing data is lost.
     *
     * @param d bytes to set
     * @param start start offset
     * @param end ending offset
     */
    public void setData(byte[] d, int start, int end) {
        data = new byte[end - start];
        System.arraycopy(d, start, data, 0, data.length);
    }

    /**
     * Get the data entry
     *
     * @return the data bytes or null if none
     */
    public byte[] getData() {
        return data;
    }

    /**
     * Set the classification
     *
     * @param s the value to set
     */
    public void setClassification(String s) {
        classification = s;
    }

    /**
     * @return the classification entry or null if none
     */
    public String getClassification() {
        return classification;
    }

    /**
     * Add metadata from map, any existing is lost
     *
     * @param m the map of metadata to set
     */
    public void setMetaData(Map<String, ? extends Object> m) {
        metadata.clear();
        addMetaData(m);
    }

    /**
     * Return the metadata map, creating one if it doesnt exist already
     *
     * @return the map of MetaData
     */
    public Map<String, Collection<Object>> getMetaData() {
        return metadata.asMap();
    }

    /**
     * Return the entire multimap of metadata for use during the construction process
     */
    public Multimap<String, Object> getMultimap() {
        return metadata;
    }

    /**
     * @return true if there is a header entry
     */
    public boolean hasHeader() {
        return header != null;
    }

    /**
     * Test for footer presence
     *
     * @return true if there is a footer entry
     */
    public boolean hasFooter() {
        return footer != null;
    }

    /**
     * Test for classification presence
     *
     * @return true if there is a classification entry
     */
    public boolean hasClassification() {
        return classification != null;
    }

    /**
     * Test for metadata presence
     *
     * @return true if there is a metadata entry
     */
    public boolean hasMetaData() {
        return metadata.size() > 0;
    }

    /**
     * Test for data presence
     *
     * @return true if there is a data entry
     */
    public boolean hasData() {
        return data != null;
    }

    /**
     * Add a record to the nested MetaData map Metadata map will be created if not existing
     *
     * @param name the name of the meta record to add
     * @param value the value to add
     */
    public void addMetaData(String name, Object value) {
        if (name != null && value != null) {
            metadata.put(name, value);
        }
    }

    /**
     * Add a map of metadata to the existing map MetaData map will be created if not existing
     *
     * @param m map of items to add
     */
    public void addMetaData(Map<String, ? extends Object> m) {
        if (m != null && m.size() > 0) {
            for (Map.Entry<String, ? extends Object> entry : m.entrySet()) {
                String key = entry.getKey();
                Object v = entry.getValue();
                if (v instanceof Iterable) {
                    metadata.putAll(key, (Iterable<?>) v);
                } else {
                    metadata.put(key, v);
                }
            }
        }
    }

    /**
     * Return a single metadata item
     *
     * @param key the name
     */
    public List<Object> getMetaDataItem(String key) {
        return metadata.get(key);
    }

    /**
     * Get a simplified form of a single metadata item using semi-colon as the separator
     *
     * @param key the name
     */
    public String getStringMetadataItem(String key) {
        return getStringMetadataItem(key, emissary.core.IBaseDataObject.DEFAULT_PARAM_SEPARATOR);
    }

    /**
     * Get a simplified form of a single metadata item
     *
     * @param key the name
     * @param sep the separator
     */
    public String getStringMetadataItem(String key, String sep) {
        List<Object> o = metadata.get(key);
        if (o.size() == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Object item : o) {
            if (sb.length() > 0) {
                sb.append(sep);
            }
            sb.append(item);
        }
        return sb.toString();
    }

    /**
     * Add an initial form to the list of existing forms. Initial forms will be created if not existing
     *
     * @param form initial form to add
     */
    public void addInitialForm(String form) {
        if (form != null) {
            initialForms.add(form);
        }
    }

    /**
     * Set the list of initial forms to use This will overwrite existing initial forms
     */
    public void setInitialForms(List<String> forms) {
        if (forms != null) {
            initialForms = new ArrayList<String>(forms);
        }
    }

    /**
     * Get the list of initial forms. Returns empty list if no initial forms have been set
     */
    public List<String> getInitialForms() {
        return initialForms;
    }

    /**
     * Check validity of session
     */
    public boolean isValid() {
        return hasData() || hasHeader() || hasFooter();
    }
}
