package emissary.core;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface IBaseDataObject {

    /**
     * Define the merge policy values for parameter handling
     */
    static enum MergePolicy {
        DISTINCT, KEEP_EXISTING, KEEP_ALL
    }

    /**
     * Default separator when stringing parameter values together
     */
    String DEFAULT_PARAM_SEPARATOR = ";";

    /**
     * Return BaseDataObjects byte array.
     * 
     * @return byte array of the data
     */
    byte[] data();

    /**
     * Set BaseDataObjects data to byte array passed in.
     * 
     * @param newData byte array to set replacing any existing data
     */
    void setData(byte[] newData);

    /**
     * Set BaseDataObjects data to the portion of the byte array specified
     * 
     * @param newData array containing desired data
     * @param offset the index of the first byte to use
     * @param length the number of bytes to use
     */
    void setData(final byte[] newData, int offset, int length);

    /**
     * Return length of BaseDataObjects byte array.
     * 
     * @return length in bytes of the data
     */
    int dataLength();

    /**
     * Set the header byte array
     * 
     * @param arg1 the byte array of header data
     */
    void setHeader(byte[] arg1);

    /**
     * Return a reference to the header byte array.
     * 
     * @return byte array of header information or null if none
     */
    byte[] header();

    /**
     * Set the footer byte array
     * 
     * @param arg1 byte array of footer data
     */
    void setFooter(byte[] arg1);

    /**
     * Return a reference to the footer byte array.
     * 
     * @return byte array of footer data or null if none
     */
    byte[] footer();


    /**
     * Get the value of headerEncoding. Tells how to interpret the header information.
     * 
     * @return Value of headerEncoding.
     */
    String getHeaderEncoding();

    /**
     * Set the value of headerEncoding for proper interpretation and processing later
     * 
     * @param arg1 Value to assign to headerEncoding.
     */
    void setHeaderEncoding(String arg1);

    /**
     * Get the classification string for the data
     * 
     * @return String classification value
     */
    String getClassification();

    /**
     * Set the classification.
     * 
     * @param classification string classification value
     */
    void setClassification(String classification);


    /**
     * Sets the number of children that the current agents spawned.
     * 
     * @param num the number value to set
     */
    void setNumChildren(int num);

    /**
     * Gets the number of children that have this as a parent
     * 
     * @return the number of children that have this parent
     */
    int getNumChildren();

    /**
     * Sets the number of siblings for this data object.
     * 
     * @param num the number of siblings to set
     */
    void setNumSiblings(int num);

    /**
     * Get the number of siblings
     * 
     * @return the number of siblings including this one
     */
    int getNumSiblings();

    /**
     * What number is this sibling in the family
     * 
     * @param num the birthorder number value to set
     */
    void setBirthOrder(int num);

    /**
     * Get this sibling number, count from one.
     * 
     * @return the birth order of this sibling
     */
    int getBirthOrder();

    /**
     * Return the header wrapped in a ByteBuffer class.
     * 
     * @return buffer required by the HTML Velocity templates.
     */
    ByteBuffer headerBuffer();

    /**
     * Return the footer wrapped in a ByteBuffer class.
     * 
     * @return buffer required by the HTML Velocity templates.
     */
    ByteBuffer footerBuffer();

    /**
     * Return theData wrapped in a ByteBuffer class.
     * 
     * @return buffer required by the HTML Velocity templates.
     */
    ByteBuffer dataBuffer();

    /**
     * Get the font encoding string
     * 
     * @return string name of font encoding for the data
     */
    String getFontEncoding();

    /**
     * Set the font encoding string
     * 
     * @param arg1 string name of font encoding for the data
     */
    void setFontEncoding(String arg1);

    /**
     * Clear all metadata elements
     */
    void clearParameters();

    /**
     * Determine if parameter is present
     * 
     * @param key name of metadata element to check
     */
    boolean hasParameter(String key);

    /**
     * Replace all of the metadata elements with a new set
     * 
     * @param map the new set
     */
    void setParameters(Map<? extends String, ? extends Object> map);

    /**
     * Set a new parameter value, deleting an old one
     * 
     * @param key the name of the element
     * @param val the value of the element
     */
    void setParameter(String key, Object val);

    /**
     * Put a new metadata element into the map
     * 
     * @param key the name of the element
     * @param val the value of the element
     */
    void putParameter(String key, Object val);

    /**
     * Put a collection of parameters into the metadata map
     * 
     * @param m the map of new parameters
     */
    void putParameters(Map<? extends String, ? extends Object> m);

    /**
     * Put a collection of parameters into the metadata map using the specified merge policy
     *
     * @param m the map of new parameters
     * @param policy the merge policy
     */
    void putParameters(Map<? extends String, ? extends Object> m, MergePolicy policy);

    /**
     * Merge a collection of parameters into the metadata map
     *
     * @param m the map of new parameters
     */
    void mergeParameters(Map<? extends String, ? extends Object> m);

    /**
     * Put a collection of parameters into the metadata map uniquely
     *
     * @param m the map of new parameters
     */
    void putUniqueParameters(Map<? extends String, ? extends Object> m);

    /**
     * Retrieve a specified metadata element
     * 
     * @param key name of the metadata element
     * @return the value or null if no such element
     */
    List<Object> getParameter(String key);

    /**
     * Append data to the specified metadata element
     * 
     * @param key name of the metadata element
     * @param value the value to append
     */
    void appendParameter(String key, CharSequence value);

    /**
     * Append data values to the specified metadata element
     * 
     * @param key name of the metadata element
     * @param values the values to append
     */
    void appendParameter(String key, Iterable<? extends CharSequence> values);

    /**
     * Append data to the specified metadata element
     *
     * @param key name of the metadata element
     * @param value the value to append
     * @param sep ignored
     * @deprecated use {@link #appendParameter(String, CharSequence)}
     */
    @Deprecated
    void appendParameter(String key, CharSequence value, String sep);

    /**
     * Append data to the specified metadata element if it doesn't exist
     *
     * @param key name of the metadata element
     * @param value the value to append
     * @return true if the item is added, false if it already exists
     */
    boolean appendUniqueParameter(String key, CharSequence value);

    /**
     * Retrieve a specified metadata element as a string value
     * 
     * @param key name of the metadata element
     * @return the string value or null if no such element
     */
    String getStringParameter(String key);

    /**
     * Retrieve a specified metadata element as a string value
     * 
     * @param key name of the metadata element
     * @param sep the separator for multivalued fields
     * @return the string value or null if no such element
     */
    String getStringParameter(String key, String sep);

    /**
     * Retrieve all the metadata elements of this object
     * 
     * @return map of metadata elements
     */
    Map<String, Collection<Object>> getParameters();

    /**
     * Retrieve all the metadata elements of this object in a way that is processed for use external to this instance
     * 
     * @return map of metadata elements
     */
    Map<String, String> getCookedParameters();

    /**
     * Retrieve all of the current metadata keys
     * 
     * @return set of charsequence keys
     */
    Set<String> getParameterKeys();

    /**
     * Delete the specified metadata element named
     * 
     * @param key the name of the metadata item to delete
     * @return the object deleted of null if none
     */
    List<Object> deleteParameter(String key);

    /**
     * Put the FILETYPE parameter
     * 
     * @param arg1 the value to store
     */
    void setFileType(String arg1);

    /**
     * Set FILETYPE parameter iff empty.
     * 
     * @param arg1 the value of the filetype to set
     * @param arg2 the list of things caller considers equal to being empty
     * @return true if it was empty and set
     */
    boolean setFileTypeIfEmpty(String arg1, String[] arg2);

    /**
     * Set FILETYPE parameter iff empty using the built-in definition of empty
     * 
     * @param arg1 the value of the filetype to set
     * @return true if it was empty and set
     */
    boolean setFileTypeIfEmpty(String arg1);

    /**
     * Return true if the file type is null or in one of the "don't care" set
     * 
     * @since 3.3.3
     */
    boolean isFileTypeEmpty();

    /**
     * Get the FILETYPE parameter
     * 
     * @return the string value of the FILETYPE parameter
     */
    String getFileType();

    /**
     * Disclose how many multipart alternative views of the data exist
     * 
     * @return count of alternate views
     */
    int getNumAlternateViews();

    /**
     * Return a specified multipart alternative view of the data
     * 
     * @param arg1 the name of the view to retrieve
     * @return byte array of alternate view data
     */
    byte[] getAlternateView(String arg1);

    /**
     * Return a specified multipart alternative view of the data in a buffer
     * 
     * @param arg1 the name of the view to retrieve
     * @return buffer of alternate view data
     */
    ByteBuffer getAlternateViewBuffer(String arg1);

    /**
     * Add a multipart alternative view of the data
     * 
     * @param name the name of the new view
     * @param data the byte array of data for the view
     */
    void addAlternateView(String name, byte[] data);

    /**
     * Add a multipart alternative view of the data
     * 
     * @param name the name of the new view
     * @param data the byte array conatining data for the view
     * @param offset index of the first byte to use
     * @param length number of bytes to use
     */
    void addAlternateView(String name, byte[] data, int offset, int length);

    /**
     * Append the specified data to the alternate view
     * 
     * @param name the name of the new view
     * @param data the byte array of data for the view
     */
    void appendAlternateView(String name, byte[] data);

    /**
     * Append to a multipart alternative view of the data
     * 
     * @param name the name of the view
     * @param data the byte array conatining data for the view
     * @param offset index of the first byte to use
     * @param length number of bytes to use
     */
    void appendAlternateView(String name, byte[] data, int offset, int length);

    /**
     * Get the set of alt view names for new foreach loops
     * 
     * @return set of alternate view names
     */
    Set<String> getAlternateViewNames();

    /**
     * Get the alternate view map.
     * 
     * @return map of alternate views, key = String, value = byte[]
     */
    Map<String, byte[]> getAlternateViews();

    /**
     * Test for broken document
     * 
     * @return true if broken
     */
    boolean isBroken();

    /**
     * Set brokenness for document
     * 
     * @param arg1 the message to record
     */
    void setBroken(String arg1);

    /**
     * Get brokenness indicator message
     * 
     * @return string message of what is broken
     */
    String getBroken();

    /**
     * Returns the name of the file without the path with which the file will be written.
     * 
     * @return the short name of the file (no path)
     */
    String shortName();

    /**
     * Returns the filename associated with the data.
     * 
     * @return the string name with path
     */
    String getFilename();

    /**
     * Returns the internally generated identifier used to track the object
     * 
     * @return a String representing the internal ID
     */
    UUID getInternalId();

    /**
     * Set the filename
     * 
     * @param f the new name of the data including path
     */
    void setFilename(String f);


    /**
     * Return the current form of the data (top of the stack)
     *
     * @return string value of current form
     */
    String currentForm();

    /**
     * Return the current form at specified position of the list
     * 
     * @param i The specified position
     * @return String containing the form or empty string if illegal position
     */
    String currentFormAt(int i);

    /**
     * Check to see if this value is already on the stack of itinerary items
     * 
     * @param val the string to look for
     * @return the position where it was found or -1
     */
    int searchCurrentForm(String val);

    /**
     * Check to see one of these values is on the stack of itinerary items
     * 
     * @param values the List of strings to look for
     * @return the String that was found out of the list sent in or null
     */
    String searchCurrentForm(Collection<String> values);

    /**
     * Get the size of the itinerary stack
     * 
     * @return size of form stack
     */
    int currentFormSize();

    /**
     * Remove a form from the head of the list
     * 
     * @return the new size of the itinerary stack
     */
    String popCurrentForm();

    /**
     * Replace all current forms with specified
     *
     * @param form the new current form or null if none desired
     */
    void replaceCurrentForm(String form);

    /**
     * Remove a form where ever it appears in the stack
     * 
     * @param form the value to remove
     * @return the number of elements removed from the stack
     */
    int deleteCurrentForm(String form);

    /**
     * Remove a form at the specified location of the itinerary stack
     *
     * @param i the position to delete
     * @return the new size of the itinerary stack
     */
    int deleteCurrentFormAt(int i);

    /**
     * Add current form newForm at idx
     * 
     * @param i the position to do the insert
     * @param val the value to insert
     * @return size of the new stack
     */
    int addCurrentFormAt(int i, String val);

    /**
     * Add a form to the end of the list (the bottom of the stack)
     * 
     * @param val the new value to add to the tail of the stack
     * @return the new size of the itinerary stack
     */
    int enqueueCurrentForm(String val);

    /**
     * Push a form onto the head of the list
     * 
     * @param val the new value to push on the stack
     * @return the new size of the itinerary stack
     */
    int pushCurrentForm(String val);

    /**
     * Replaces the current form of the data with a new form Does a pop() followed by a push(newForm) to simulate what would
     * happen in the old "one form at a time system"
     * 
     * @param val value of the the new form of the data
     */
    void setCurrentForm(String val);

    /**
     * Replaces the current form of the data with a form passed and potentially clears the entire form stack
     *
     * @param val value of the the new form of the data
     * @param clearAllForms whether or not to clear the entire form stack
     */
    void setCurrentForm(String val, boolean clearAllForms);

    /**
     * Return a clone the whole current form list Note this is not a reference to our private store
     * 
     * @return ordered list of current forms
     */
    List<String> getAllCurrentForms();

    /**
     * Move curForm to the top of the stack pushing everything above it down one slot
     * 
     * @param curForm the form to pull to the top
     */
    void pullFormToTop(String curForm);

    /**
     * Return BaseDataObjects info as a String.
     * 
     * @return string value of this object
     */
    @Override
    String toString();


    /**
     * Record a processing error
     * 
     * @param val the new error message to record
     */
    void addProcessingError(String val);

    /**
     * Retrieve the processing error(s)
     * 
     * @return string value of processing errors
     */
    String getProcessingError();

    /**
     * Replace history with the new history
     * 
     * @param list of new history strings to use
     */
    void setHistory(List<String> list);

    /**
     * List of places the data object was carried to.
     * 
     * @return List of strings making up the history
     */
    List<String> transformHistory();

    /**
     * Clear the transformation history
     */
    void clearTransformHistory();

    /**
     * Appends the new key to the transform history. This is called by MobileAgent before moving to the new place. It
     * usually adds the four-tuple of a place's key
     * 
     * @see emissary.core.MobileAgent#agentControl
     * @param key the new value to append
     */
    void appendTransformHistory(String key);

    /**
     * Return what machine we are located on
     * 
     * @return string local host name
     */
    String whereAmI();

    /**
     * Return an SDE based on the last item in the transform history or null if empty
     * 
     * @return last item in history
     */
    emissary.directory.DirectoryEntry getLastPlaceVisited();

    /**
     * Return an SDE based on the penultimate item in the transform history or null if empty
     * 
     * @return penultimate item in history
     */
    emissary.directory.DirectoryEntry getPenultimatePlaceVisited();

    /**
     * Return true if the payload has been to a place matching the key passed in.
     * 
     * @param pattern the key pattern to match
     */
    boolean hasVisited(String pattern);

    /**
     * True if this payload hasn't had any processing yet Does not count parent processing as being for this payload
     * 
     * @return true if not yet started
     */
    boolean beforeStart();

    /**
     * Support deep copy via clone
     */
    IBaseDataObject clone() throws CloneNotSupportedException;

    /**
     * Print the parameters, nicely formatted
     */
    String printMeta();

    /**
     * Get data object's priority.
     * 
     * @return int priority (lower the number, higher the priority).
     */
    int getPriority();

    /**
     * Set the data object's priority, typically based on input dir/file priority.
     * 
     * @param priority int (lower the number, higher the priority).
     */
    void setPriority(int priority);

    /**
     * Get the timestamp for when the object was created. This attribute will be used for data provenance.
     * 
     * @return date - the timestamp the object was created
     */
    Date getCreationTimestamp();

    /**
     * Set the timestamp for when the object was created
     * 
     * @param creationTimestamp - the date the object was created
     */
    void setCreationTimestamp(Date creationTimestamp);

    /**
     * Get the List of extracted records
     */
    List<IBaseDataObject> getExtractedRecords();

    /**
     * Set/replace the list of extracted children
     *
     * @param records the list of extracted children
     */
    void setExtractedRecords(List<? extends IBaseDataObject> records);

    /**
     * Add an extracted child
     *
     * @param record the extracted child to add
     */
    void addExtractedRecord(IBaseDataObject record);

    /**
     * Add extracted children
     *
     * @param records the extracted children to add
     */
    void addExtractedRecords(List<? extends IBaseDataObject> records);

    /**
     * Determine if this object has extracted records.
     * 
     * @return true if this object has extracted records.
     */
    boolean hasExtractedRecords();

    /**
     * Clear the list of extracted records.
     */
    void clearExtractedRecords();

    /**
     * Get count of extracted records
     */
    int getExtractedRecordCount();

    /**
     * @return true if this tree is not able to be output, false otherwise
     */
    boolean isOutputable();

    /**
     * Set whether or not the tree is able to be written out
     *
     * @param outputable true if this tree is not able to be output, false otherwise
     */
    void setOutputable(boolean outputable);

    /**
     * @return the unique identifier of the IBaseDataObject
     */
    String getId();

    /**
     * Set the unique identifier of the IBaseDataObject
     *
     * @param id the unique identifier of the IBaseDataObject
     */
    void setId(String id);

    /**
     * @return the unique identifier of the {@link emissary.pickup.WorkBundle}
     */
    String getWorkBundleId();

    /**
     * Set the unique identifier of the {@link emissary.pickup.WorkBundle}
     *
     * @param workBundleId the unique identifier of the {@link emissary.pickup.WorkBundle}
     */
    void setWorkBundleId(String workBundleId);

    /**
     * @return the unique identifier of the transaction
     */
    String getTransactionId();

    /**
     * Set the unique identifier of the transaction
     * 
     * @param transactionId the unique identifier of the transaction
     */
    void setTransactionId(String transactionId);
}
