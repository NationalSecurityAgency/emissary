package emissary.core;

import emissary.core.channels.SeekableByteChannelFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface IBaseDataObject extends IBaseRecord {

    /**
     * @deprecated As of emissary 8.18.0, this method performs no operations
     */
    @Deprecated(forRemoval = true)
    default void checkForUnsafeDataChanges() {}

    /**
     * Return the data as a byte array. If using a channel to the data, calling this method will only return up to
     * Integer.MAX_VALUE bytes of the original data.
     * 
     * @return byte array of the data
     * @see #getChannelFactory() as the preferred data accessor for larger data
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
     * Clear any data elements
     */
    void clearData();

    /**
     * Checks if the data is defined with a non-zero length.
     * 
     * @return if data is undefined or zero length.
     */
    boolean hasContent() throws IOException;

    /**
     * Set the byte channel factory using whichever implementation is providing access to the data.
     * 
     * @param sbcf the new channel factory to set on this object
     */
    void setChannelFactory(final SeekableByteChannelFactory sbcf);

    /**
     * Returns a new InputStream to the data that this BaseDataObject contains.
     * <p>
     * NOTE 1: Mutating the data elements of this IBaseDataObject while reading from the InputStream will have indeterminate
     * results.
     * <p>
     * NOTE 2: The calling code is responsible for closing the returned InputStream.
     * 
     * @return a new stream that reads the data that this object contains, or null if this object has no data.
     */
    InputStream newInputStream();

    /**
     * Returns the seekable byte channel factory containing a reference to the data
     * 
     * @return the factory containing the data reference
     */
    SeekableByteChannelFactory getChannelFactory();

    /**
     * Get the size of the channel referenced by this object
     * 
     * @return the channel size
     * @throws IOException if an error occurs with the underlying channel
     */
    long getChannelSize() throws IOException;

    /**
     * Return length of the data, up to Integer.MAX_VALUE if the data is in a channel.
     * 
     * Prefer use of {@link #getChannelSize()} going forwards
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
     * Return the header wrapped in a ByteBuffer class.
     * 
     * @return buffer required by the HTML Velocity templates.
     */
    @Deprecated
    ByteBuffer headerBuffer();

    /**
     * Return the footer wrapped in a ByteBuffer class.
     * 
     * @return buffer required by the HTML Velocity templates.
     */
    @Deprecated
    ByteBuffer footerBuffer();

    /**
     * Return theData wrapped in a ByteBuffer class.
     * 
     * @return buffer required by the HTML Velocity templates.
     */
    @Deprecated
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
    @Deprecated
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
     * Support deep copy via clone
     */
    @Deprecated
    IBaseDataObject clone() throws CloneNotSupportedException;

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

}
