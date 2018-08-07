package emissary.core.blob;

import java.nio.ByteBuffer;

import emissary.core.IBaseDataObject;

/**
 * Container of binary data, matching only those methods on the original {@link IBaseDataObject}. New method signatures
 * should be added to {@link IDataContainer}.
 * 
 * @author adyoun2
 *
 */
public interface IOriginalDataContainer {

    /**
     * <p>
     * Return the data as a byte array.
     * </p>
     * <p>
     * Changes to data in the array may or may not alter the underlying data. <strong>It is recommended that any
     * intended changes are persisted using {@link #setData(byte[])}.</strong>
     * </p>
     * 
     * @return byte array of the data
     */
    byte[] data();

    /**
     * <p>
     * Set data to byte array passed in.
     * </p>
     * <p>
     * Subsequent changes to data in the array may or may not alter the underlying data. <strong>It is recommended that
     * any intended changes are persisted by calling this again.</strong>
     * </p>
     * 
     * @param newData byte array to set replacing any existing data
     */
    void setData(byte[] newData);

    /**
     * <p>
     * Set data to the portion of the byte array specified.
     * </p>
     * <p>
     * Subsequent changes to data in the array may or will not alter the underlying data.
     * </p>
     * 
     * @param newData array containing desired data
     * @param offset the index of the first byte to use
     * @param length the number of bytes to use
     */
    void setData(final byte[] newData, int offset, int length);

    /**
     * Return length of binary data.
     * 
     * @return length in bytes of the data
     * @deprecated Use {@link IDataContainer#length()} instead
     */
    @Deprecated
    int dataLength();

    /**
     * <p>
     * Return the Data wrapped in a ByteBuffer class.
     * </p>
     * <p>
     * Mutations on the returned object may or may not alter the underlying data. <strong>It is recommended that any
     * intended changes are persisted using {@link #setData(byte[])}.</strong>
     * </p>
     * 
     * @return buffer required by the HTML Velocity templates.
     */
    ByteBuffer dataBuffer();

    IOriginalDataContainer clone() throws CloneNotSupportedException;

}
