package emissary.core.view;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;

import emissary.core.IBaseDataObject;
import emissary.core.blob.IDataContainer;

/**
 * Container of alternate views of data, matching only those methods on the original {@link IBaseDataObject}. New method
 * signatures should be added to {@link IViewManager}.
 *
 */
public interface IOriginalViewManager extends Cloneable, Serializable {

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
     * @deprecated Interaction via {@link IDataContainer}s is preferred.
     */
    @Deprecated
    byte[] getAlternateView(String arg1);

    /**
     * Return a specified multipart alternative view of the data in a buffer
     *
     * @param arg1 the name of the view to retrieve
     * @return buffer of alternate view data
     * @deprecated Interaction via {@link IDataContainer}s is preferred.
     */
    @Deprecated
    ByteBuffer getAlternateViewBuffer(String arg1);

    /**
     * Add a multipart alternative view of the data
     *
     * @param name the name of the new view
     * @param data the byte array of data for the view
     * @deprecated Interaction via {@link IDataContainer}s is preferred.
     */
    @Deprecated
    void addAlternateView(String name, byte[] data);

    /**
     * Add a multipart alternative view of the data
     *
     * @param name the name of the new view
     * @param data the byte array conatining data for the view
     * @param offset index of the first byte to use
     * @param length number of bytes to use
     * @deprecated Interaction via {@link IDataContainer}s is preferred.
     */
    @Deprecated
    void addAlternateView(String name, byte[] data, int offset, int length);

    /**
     * Append the specified data to the alternate view
     *
     * @param name the name of the new view
     * @param data the byte array of data for the view
     * @deprecated Interaction via {@link IDataContainer}s is preferred.
     */
    @Deprecated
    void appendAlternateView(String name, byte[] data);

    /**
     * Append to a multipart alternative view of the data
     *
     * @param name the name of the view
     * @param data the byte array conatining data for the view
     * @param offset index of the first byte to use
     * @param length number of bytes to use
     * @deprecated Interaction via {@link IDataContainer}s is preferred.
     */
    @Deprecated
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
     * @deprecated Interaction via {@link IDataContainer}s is preferred.
     */
    @Deprecated
    Map<String, byte[]> getAlternateViews();

}
