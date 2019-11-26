package emissary.core;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.rmi.Remote;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import com.google.common.collect.LinkedListMultimap;
import emissary.directory.DirectoryEntry;
import emissary.directory.KeyManipulator;
import emissary.pickup.Priority;
import emissary.place.IServiceProviderPlace;
import emissary.util.ByteUtil;
import emissary.util.PayloadUtil;
import org.apache.commons.lang.StringUtils;

/**
 * Class to hold byte array of data, header, footer, and attributes
 */
public class BaseDataObject implements Serializable, Cloneable, Remote, IBaseDataObject {

    /* Including this here make serialization of this object faster. */
    private static final long serialVersionUID = 7362181964652092657L;

    /* Our payload */
    protected byte[] theData;

    /**
     * Original name of the input data. Can only be set in the constructor of the DataObject. returned via the
     * {@link #getFilename()} method. Also used in constructing the {@link #shortName()} of the document.
     */
    protected String theFileName;

    /**
     * Terminal portion of theFileName
     */
    protected String shortName;

    /**
     * The internal identifier, generated for each constructed object
     */
    protected UUID internalId = UUID.randomUUID();

    /**
     * The currentForm is a stack of the itinerary items. The contents of the list are {@link String} and map to the
     * dataType portion of the keys in the emissary.DirectoryPlace.
     */
    protected List<String> currentForm = new ArrayList<>();

    /**
     * History of processing errors. Lines of text are accumulated from String and returned in-toto as a String.
     */
    protected StringBuilder procError;

    /**
     * A travelogue built up as the agent moves about. Appended to by the agent as it goes from place to place.
     */
    protected List<String> history = new ArrayList<>();

    /**
     * The last determined language(characterset) of the data.
     */
    protected String fontEncoding = null;

    /**
     * Dynamic facets or metadata attributes of the data
     */
    protected LinkedListMultimap<String, Object> parameters = LinkedListMultimap.create(100);

    /**
     * If this file caused other agents to be sprouted, indicate how many
     */
    protected int numChildren = 0;

    /**
     * If this file has siblings that were sprouted at the same time, this will indicate how many total siblings there are.
     * This can be used to navigate among siblings without needing to refer to the parent.
     */
    protected int numSiblings = 0;

    /**
     * What child is this in the family order
     */
    protected int birthOrder = 0;

    /**
     * Hash of alternate views of the data {@link String} current form is the key, byte[] is the value
     */
    protected Map<String, byte[]> multipartAlternative = new TreeMap<>();

    /**
     * Any header that goes along with the data
     */
    protected byte[] header = null;

    /**
     * Any footer that goes along with the data
     */
    protected byte[] footer = null;

    /**
     * If the header has some encoding scheme record it
     */
    protected String headerEncoding = null;

    /**
     * Record the classification scheme for the document
     */
    protected String classification = null;

    /**
     * Keep track of if and how the document is broken so we can report on it later
     */
    protected StringBuilder brokenDocument = null;

    // Filetypes that we think are equivalent to no file type at all
    protected String[] FILETYPE_EMPTY = {Form.UNKNOWN};

    // Filetypes with this suffix are equivalent to no file type at all
    protected String FILETYPE_ENDSWITH = "-UNWRAPPED";

    /**
     * The integer priority of the data object. A lower number is higher priority.
     */
    protected int priority = Priority.DEFAULT;

    /**
     * The timestamp for when the BaseDataObject was created. Used in data provenance tracking.
     */
    protected Date creationTimestamp;

    /**
     * The extracted records, if any
     */
    protected List<IBaseDataObject> extractedRecords;

    /**
     * Check to see if this tree is able to be written out.
     */
    protected boolean outputable = true;

    /**
     * The unique identifier of this object
     */
    protected String id;

    /**
     * The identifier of the {@link emissary.pickup.WorkBundle}
     */
    protected String workBundleId;

    /**
     * The identifier used to track the object through the system
     */
    protected String transactionId;

    /**
     * Create an empty BaseDataObject.
     */
    public BaseDataObject() {
        this.theData = null;
        setCreationTimestamp(new Date(System.currentTimeMillis()));
    }

    /**
     * Create a new BaseDataObject with byte array and name passed in. WARNING: this implementation uses the passed in array
     * directly, no copy is made so the caller should not reuse the array.
     *
     * @param newData the bytes to hold
     * @param name the name of the data item
     */
    public BaseDataObject(final byte[] newData, final String name) {
        setData(newData);
        setFilename(name);
        setCreationTimestamp(new Date(System.currentTimeMillis()));
    }

    /**
     * Create a new BaseDataObject with byte array, name, and initial form WARNING: this implementation uses the passed in
     * array directly, no copy is made so the caller should not reuse the array.
     *
     * @param newData the bytes to hold
     * @param name the name of the data item
     * @param form the initial form of the data
     */
    public BaseDataObject(final byte[] newData, final String name, final String form) {
        this(newData, name);
        if (form != null) {
            pushCurrentForm(form);
        }
    }

    public BaseDataObject(final byte[] newData, final String name, final String form, final String fileType) {
        this(newData, name, form);
        if (fileType != null) {
            this.setFileType(fileType);
        }
    }

    /**
     * Set the header byte array WARNING: this implementation uses the passed in array directly, no copy is made so the
     * caller should not reuse the array.
     *
     * @param header the byte array of header data
     */
    @Override
    public void setHeader(final byte[] header) {
        this.header = header;
    }

    /**
     * Get the value of headerEncoding. Tells how to interpret the header information.
     *
     * @return Value of headerEncoding.
     */
    @Override
    public String getHeaderEncoding() {
        return this.headerEncoding;
    }

    /**
     * Set the value of headerEncoding for proper interpretation and processing later
     *
     * @param v Value to assign to headerEncoding.
     */
    @Override
    public void setHeaderEncoding(final String v) {
        this.headerEncoding = v;
    }

    /**
     * Set the footer byte array WARNING: this implementation uses the passed in array directly, no copy is made so the
     * caller should not reuse the array.
     *
     * @param footer byte array of footer data
     */
    @Override
    public void setFooter(final byte[] footer) {
        this.footer = footer;
    }

    /**
     * Set the filename
     *
     * @param f the new name of the data including path
     */
    @Override
    public void setFilename(final String f) {
        this.theFileName = f;
        this.shortName = makeShortName();
    }

    /**
     * Return BaseDataObjects byte array. WARNING: this implementation returns the actual array directly, no copy is made so
     * the caller must be aware that modifications to the returned array are live.
     *
     * @return byte array of the data
     */
    @Override
    public byte[] data() {
        return this.theData;
    }

    /**
     * Set BaseDataObjects data to byte array passed in. WARNING: this implementation uses the passed in array directly, no
     * copy is made so the caller should not reuse the array.
     *
     * @param newData byte array to set replacing any existing data
     */
    @Override
    public void setData(final byte[] newData) {
        if (newData == null) {
            this.theData = new byte[0];
        } else {
            this.theData = newData;
        }
    }

    @Override
    public void setData(final byte[] newData, final int offset, final int length) {
        if (length <= 0 || newData == null) {
            this.theData = new byte[0];
        } else {
            this.theData = new byte[length];
            System.arraycopy(newData, offset, this.theData, 0, length);
        }
    }

    @Override
    public int dataLength() {
        return this.theData == null ? 0 : this.theData.length;
    }

    @Override
    public String shortName() {
        return this.shortName;
    }

    /**
     * Construct the shortname
     */
    private String makeShortName() {
        /*
         * using the file object works for most cases. It works on windows with a valid unix path. However, it fails on the unix
         * side if it is given a valid Windows path.
         */
        // File file = new File( theFileName );
        // return file.getName();
        // so..... we'll have to perform the check ourselves ARRRRRRRRRGH!!!!
        final int unixPathIndex = this.theFileName.lastIndexOf("/");
        if (unixPathIndex >= 0) {
            return this.theFileName.substring(unixPathIndex + 1);
        }
        // check for windows path
        final int windowsPathIndex = this.theFileName.lastIndexOf("\\");
        if (windowsPathIndex >= 0) {
            return this.theFileName.substring(windowsPathIndex + 1);
        }

        return this.theFileName;
    }

    @Override
    public String getFilename() {
        return this.theFileName;
    }

    @Override
    public String currentForm() {
        return currentFormAt(0);
    }

    @Override
    public String currentFormAt(final int i) {
        if (i < this.currentForm.size()) {
            return this.currentForm.get(i);
        }
        return "";
    }

    @Override
    public int searchCurrentForm(final String value) {
        return this.currentForm.indexOf(value);
    }

    @Override
    public String searchCurrentForm(final Collection<String> values) {
        for (final String value : values) {
            if (this.currentForm.contains(value)) {
                return value;
            }
        }
        return null;
    }

    @Override
    public int currentFormSize() {
        return this.currentForm.size();
    }

    @Override
    public void replaceCurrentForm(final String form) {
        this.currentForm.clear();
        if (form != null) {
            pushCurrentForm(form);
        }
    }

    /**
     * Remove a form from the head of the list
     *
     * @return The value that was removed, or {@code null} if the list was empty.
     */
    @Override
    public String popCurrentForm() {
        if (this.currentForm.isEmpty()) {
            return null;
        } else {
            return this.currentForm.remove(0);
        }
    }

    @Override
    public int deleteCurrentForm(final String form) {
        int count = 0;

        if (this.currentForm == null || this.currentForm.isEmpty()) {
            return count;
        }

        // Remove all matching
        for (final Iterator<String> i = this.currentForm.iterator(); i.hasNext();) {
            final String val = i.next();
            if (val.equals(form)) {
                i.remove();
                count++;
            }
        }
        return count;
    }

    @Override
    public int deleteCurrentFormAt(final int i) {
        // Make sure its a legal position.
        if ((i >= 0) && (i < this.currentForm.size())) {
            this.currentForm.remove(i);
        }
        return this.currentForm.size();
    }

    @Override
    public int addCurrentFormAt(final int i, final String newForm) {
        if (newForm == null) {
            throw new IllegalArgumentException("caller attempted to add a null form value at position " + i);
        }

        if (i < this.currentForm.size()) {
            this.currentForm.add(i, newForm);
        } else {
            this.currentForm.add(newForm);
        }
        return this.currentForm.size();
    }

    @Override
    public int enqueueCurrentForm(final String newForm) {
        if (newForm == null) {
            throw new IllegalArgumentException("caller attempted to enqueue a null form value");
        }

        this.currentForm.add(newForm);
        return this.currentForm.size();
    }

    @Override
    public int pushCurrentForm(final String newForm) {
        if (newForm == null) {
            throw new IllegalArgumentException("caller attempted to push a null form value");
        }

        return addCurrentFormAt(0, newForm);
    }

    @Override
    public void setCurrentForm(final String newForm) {
        setCurrentForm(newForm, false);
    }

    @Override
    public void setCurrentForm(final String newForm, final boolean clearAllForms) {
        if (StringUtils.isBlank(newForm)) {
            throw new IllegalArgumentException("caller attempted to set the current form to a null value");
        }

        if (clearAllForms) {
            replaceCurrentForm(newForm);
        } else {
            popCurrentForm();
            pushCurrentForm(newForm);
        }
    }


    @Override
    public List<String> getAllCurrentForms() {
        return new ArrayList<>(this.currentForm);
    }

    @Override
    public void pullFormToTop(final String curForm) {
        if (this.currentForm.size() > 1) {
            // Delete it
            final int count = deleteCurrentForm(curForm);

            // If deleted, add it back on top
            if (count > 0) {
                this.currentForm.add(0, curForm);
            }
        }
    }

    @Override
    public String toString() {
        final StringBuilder myOutput = new StringBuilder();
        final String ls = System.getProperty("line.separator");

        myOutput.append(ls);
        myOutput.append("   currentForms: ").append(getAllCurrentForms()).append(ls);
        myOutput.append("   transform history (").append(this.history.size()).append(") :").append(ls);

        for (final String historyValue : this.history) {
            myOutput.append("        -> ").append(historyValue).append(ls);
        }

        return myOutput.toString();
    }

    @Override
    public String printMeta() {
        return PayloadUtil.printFormattedMetadata(this);
    }

    @Override
    public void addProcessingError(final String err) {
        if (this.procError == null) {
            this.procError = new StringBuilder();
        }
        this.procError.append(err).append("\n");
    }

    @Override
    public String getProcessingError() {
        String s = null;
        if (this.procError != null) {
            s = this.procError.toString();
        }
        return s;
    }

    @Override
    public List<String> transformHistory() {
        return new ArrayList<>(this.history);
    }

    @Override
    public void clearTransformHistory() {
        this.history.clear();
    }

    @Override
    public void appendTransformHistory(final String key) {
        this.history.add(key);
    }

    /**
     * Replace history with the new history. Is this historic revisionism? Maybe, but it is needed to support sprouting
     *
     * @param newHistory list of new history strings to use
     */
    @Override
    public void setHistory(final List<String> newHistory) {
        this.history.clear();
        this.history.addAll(newHistory);
    }

    @Override
    public String whereAmI() {
        String host = null;
        try {
            host = InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException e) {
            host = "FAILED";
        }
        return host;
    }

    @Override
    public DirectoryEntry getLastPlaceVisited() {
        final int sz = this.history.size();
        if (sz == 0) {
            return null;
        }
        return new DirectoryEntry(this.history.get(sz - 1));
    }

    @Override
    public DirectoryEntry getPenultimatePlaceVisited() {
        final int sz = this.history.size();
        if (sz < 2) {
            return null;
        }
        return new DirectoryEntry(this.history.get(sz - 2));
    }

    @Override
    public boolean hasVisited(final String pattern) {
        for (final String historyValue : this.history) {
            if (KeyManipulator.gmatch(historyValue, pattern)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean beforeStart() {
        if (this.history.isEmpty()) {
            return true;
        }
        final String s = this.history.get(this.history.size() - 1);
        return s.indexOf(IServiceProviderPlace.SPROUT_KEY) > -1;
    }

    @Override
    public void clearParameters() {
        this.parameters.clear();
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
    public void setParameters(final Map<? extends String, ? extends Object> map) {
        this.parameters.clear();
        putParameters(map);
    }

    @Override
    public void setParameter(final String key, final Object val) {
        deleteParameter(key);
        putParameter(key, val);
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
     * Put a collection of parameters into the metadata map, keeping both old and new values
     *
     * @param m the map of new parameters
     */
    @Override
    public void putParameters(final Map<? extends String, ? extends Object> m) {
        putParameters(m, MergePolicy.KEEP_ALL);
    }

    /**
     * Put a collection of parameters into the metadata map, adding only distinct k/v pairs
     *
     * @param m the map of new parameters
     */
    @Override
    public void putUniqueParameters(final Map<? extends String, ? extends Object> m) {
        putParameters(m, MergePolicy.DISTINCT);
    }

    /**
     * Merge in parameters keeping existing keys unchanged
     *
     * @param m map of new parameters to consider
     */
    @Override
    public void mergeParameters(final Map<? extends String, ? extends Object> m) {
        putParameters(m, MergePolicy.KEEP_EXISTING);
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
    @Deprecated
    public void appendParameter(final String key, final CharSequence value, final String sep) {
        appendParameter(key, value);
    }

    @Override
    public String getStringParameter(final String key) {
        return getStringParameter(key, DEFAULT_PARAM_SEPARATOR);
    }

    @Override
    public String getStringParameter(final String key, final String sep) {
        final List<Object> obj = getParameter(key);
        if (obj == null) {
            return null;
        } else if (obj.isEmpty()) {
            return null;
        } else if ((obj.size() == 1) && (obj.get(0) instanceof String)) {
            return (String) obj.get(0);
        } else if ((obj.size() == 1) && (obj.get(0) == null)) {
            return null;
        } else {
            final StringBuilder sb = new StringBuilder();
            for (final Object item : obj) {
                if (sb.length() > 0) {
                    sb.append(sep);
                }
                sb.append(item);
            }
            return sb.toString();
        }
    }

    /**
     * Retrieve all the metadata elements of this object This method returns possibly mapped metadata element names
     *
     * @return map of metadata elements
     */
    @Override
    public Map<String, Collection<Object>> getParameters() {
        return this.parameters.asMap();
    }

    /**
     * Get a processed represenation of the parameters for external use
     */
    @Override
    public Map<String, String> getCookedParameters() {
        final Map<String, String> ext = new TreeMap<>();
        for (final String key : this.parameters.keySet()) {
            ext.put(key.toString(), getStringParameter(key));
        }
        return ext;
    }

    @Override
    public Set<String> getParameterKeys() {
        return this.parameters.keySet();
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

    @Override
    public void setNumChildren(final int num) {
        this.numChildren = num;
    }

    @Override
    public void setNumSiblings(final int num) {
        this.numSiblings = num;
    }

    @Override
    public void setBirthOrder(final int num) {
        this.birthOrder = num;
    }

    @Override
    public int getNumChildren() {
        return this.numChildren;
    }

    @Override
    public int getNumSiblings() {
        return this.numSiblings;
    }

    @Override
    public int getBirthOrder() {
        return this.birthOrder;
    }

    /**
     * Return a reference to the header byte array. WARNING: this implementation returns the actual array directly, no copy
     * is made so the caller must be aware that modifications to the returned array are live.
     *
     * @return byte array of header information or null if none
     */
    @Override
    public byte[] header() {
        return this.header;
    }

    @Override
    public ByteBuffer headerBuffer() {
        return ByteBuffer.wrap(header());
    }

    /**
     * Return a reference to the footer byte array. WARNING: this implementation returns the actual array directly, no copy
     * is made so the caller must be aware that modifications to the returned array are live.
     *
     * @return byte array of footer data or null if none
     */
    @Override
    public byte[] footer() {
        return this.footer;
    }


    @Override
    public ByteBuffer footerBuffer() {
        return ByteBuffer.wrap(footer());
    }

    @Override
    public ByteBuffer dataBuffer() {
        return ByteBuffer.wrap(data());
    }

    @Override
    public String getFontEncoding() {
        return this.fontEncoding;
    }

    @Override
    public void setFontEncoding(final String fe) {
        this.fontEncoding = fe;
    }

    private static final String FILETYPE = "FILETYPE";

    /**
     * Put the FILETYPE parameter, null to clear
     *
     * @param v the value to store or null
     */
    @Override
    public void setFileType(final String v) {
        deleteParameter(FILETYPE);
        if (v != null) {
            setParameter(FILETYPE, v);
        }
    }

    @Override
    public boolean setFileTypeIfEmpty(final String v, final String[] empties) {
        if (isFileTypeEmpty(empties)) {
            setFileType(v);
            return true;
        }
        return false;
    }

    @Override
    public boolean isFileTypeEmpty() {
        return isFileTypeEmpty(this.FILETYPE_EMPTY);
    }

    /**
     * Return true if the file type is null or in one of the specified set of empties
     *
     * @param empties a list of types that count as empty
     */
    protected boolean isFileTypeEmpty(final String[] empties) {
        final String s = getFileType();

        if (StringUtils.isEmpty(s)) {
            return true;
        }

        if (s.endsWith(FILETYPE_ENDSWITH)) {
            return true;
        }

        for (int i = 0; empties != null && i < empties.length; i++) {
            if (s.equals(empties[i])) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean setFileTypeIfEmpty(final String v) {
        return setFileTypeIfEmpty(v, this.FILETYPE_EMPTY);
    }

    @Override
    public String getFileType() {
        return getStringParameter(FILETYPE);
    }

    @Override
    public int getNumAlternateViews() {
        return this.multipartAlternative.size();
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

    @Override
    public void appendAlternateView(final String name, final byte[] data) {
        appendAlternateView(name, data, 0, data.length);
    }

    @Override
    public void appendAlternateView(final String name, final byte[] data, final int offset, final int length) {
        final byte[] av = getAlternateView(name);
        if (av != null) {
            addAlternateView(name, ByteUtil.glue(av, 0, av.length - 1, data, offset, offset + length - 1));
        } else {
            addAlternateView(name, data, offset, length);
        }
    }

    /**
     * Return a specified multipart alternative view of the data in a buffer
     *
     * @param s the name of the view to retrieve
     * @return buffer of alternate view data or null if none
     */
    @Override
    public ByteBuffer getAlternateViewBuffer(final String s) {
        final byte[] viewdata = getAlternateView(s);
        if (viewdata == null) {
            return null;
        }
        return ByteBuffer.wrap(viewdata);
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
     * {@inheritDoc}
     *
     * @return an ordered set of alternate view names
     */
    @Override
    public Set<String> getAlternateViewNames() {
        return new TreeSet<>(this.multipartAlternative.keySet());
    }

    /**
     * Get the alternate view map. WARNING: this implementation returns the actual map directly, no copy is made so the
     * caller must be aware that modifications to the returned map are live.
     *
     * @return an map of alternate views ordered by name, key = String, value = byte[]
     */
    @Override
    public Map<String, byte[]> getAlternateViews() {
        return this.multipartAlternative;
    }

    @Override
    public boolean isBroken() {
        return (this.brokenDocument != null);
    }

    @Override
    public void setBroken(final String v) {
        if (v == null) {
            this.brokenDocument = null;
            return;
        }

        if (this.brokenDocument == null) {
            this.brokenDocument = new StringBuilder();
            this.brokenDocument.append(v);
        } else {
            this.brokenDocument.append(", ").append(v);
        }
    }

    @Override
    public String getBroken() {
        if (this.brokenDocument == null) {
            return null;
        }
        return this.brokenDocument.toString();
    }

    @Override
    public void setClassification(final String classification) {
        this.classification = classification;
    }

    @Override
    public String getClassification() {
        return this.classification;
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

    @Override
    public void setPriority(final int priority) {
        this.priority = priority;
    }

    /**
     * Clone this payload
     */
    @Override
    public IBaseDataObject clone() throws CloneNotSupportedException {
        final BaseDataObject c = (BaseDataObject) super.clone();
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

    @Override
    public Date getCreationTimestamp() {
        return this.creationTimestamp;
    }

    /**
     * The creation timestamp is part of the provenance of the event represented by this instance. It is normally set from
     * the constructor
     *
     * @param creationTimestamp when this item was created
     */
    @Override
    public void setCreationTimestamp(final Date creationTimestamp) {
        if (creationTimestamp == null) {
            throw new IllegalArgumentException("Timestamp must not be null");
        }

        this.creationTimestamp = creationTimestamp;
    }

    @Override
    public List<IBaseDataObject> getExtractedRecords() {
        return this.extractedRecords;
    }

    @Override
    public void setExtractedRecords(final List<? extends IBaseDataObject> records) {
        if (records == null) {
            throw new IllegalArgumentException("Record list must not be null");
        }

        for (final IBaseDataObject r : records) {
            if (r == null) {
                throw new IllegalArgumentException("No added record may be null");
            }
        }

        this.extractedRecords = new ArrayList<>(records);
    }

    @Override
    public void addExtractedRecord(final IBaseDataObject record) {
        if (record == null) {
            throw new IllegalArgumentException("Added record must not be null");
        }

        if (this.extractedRecords == null) {
            this.extractedRecords = new ArrayList<>();
        }

        this.extractedRecords.add(record);
    }

    @Override
    public void addExtractedRecords(final List<? extends IBaseDataObject> records) {
        if (records == null) {
            throw new IllegalArgumentException("ExtractedRecord list must not be null");
        }

        for (final IBaseDataObject r : records) {
            if (r == null) {
                throw new IllegalArgumentException("No ExctractedRecord item may be null");
            }
        }

        if (this.extractedRecords == null) {
            this.extractedRecords = new ArrayList<>();
        }

        this.extractedRecords.addAll(records);
    }

    @Override
    public boolean hasExtractedRecords() {
        return (this.extractedRecords != null) && !this.extractedRecords.isEmpty();
    }

    @Override
    public void clearExtractedRecords() {
        this.extractedRecords = null;
    }

    @Override
    public int getExtractedRecordCount() {
        return (this.extractedRecords == null) ? 0 : this.extractedRecords.size();
    }

    @Override
    public UUID getInternalId() {
        return this.internalId;
    }

    @Override
    public boolean isOutputable() {
        return outputable;
    }

    @Override
    public void setOutputable(boolean outputable) {
        this.outputable = outputable;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getWorkBundleId() {
        return workBundleId;
    }

    @Override
    public void setWorkBundleId(String workBundleId) {
        this.workBundleId = workBundleId;
    }

    @Override
    public String getTransactionId() {
        return transactionId;
    }

    @Override
    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }
}
