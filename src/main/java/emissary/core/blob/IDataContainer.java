package emissary.core.blob;

import java.io.IOException;
import java.io.Serializable;
import java.nio.channels.SeekableByteChannel;

import emissary.core.IBaseDataObject;

/**
 * Container for binary data, with capability additional to the original {@link IBaseDataObject}.
 *
 */
public interface IDataContainer extends IOriginalDataContainer, Cloneable, Serializable {

    /**
     * Up-convert an {@link IOriginalDataContainer} into an {@link IDataContainer}.
     *
     * @param iLegacyDataContainer the old style implementation to up-convert.
     * @return An instance of the provided object meeting the {@link IDataContainer} interface.
     */
    public static IDataContainer wrap(IOriginalDataContainer iLegacyDataContainer) {
        return new LegacyContainerWrapper(iLegacyDataContainer);
    }

    @Override
    IDataContainer clone() throws CloneNotSupportedException;

    /**
     * <p>
     * Get the data as a channel.
     * </p>
     * <p>
     * Writes to this channel <strong>will</strong> be persisted into the underlying data, so long as the channel is closed
     * normally.
     * </p>
     *
     * @return A channel on the data, or null if no data exists.
     * @throws IOException
     */
    SeekableByteChannel channel() throws IOException;

    /**
     * Get a channel that can be used to write new data. Existing data will be lost if this is used.
     *
     * @param estimatedSize An estimate of the size of the data that will be written, to allow an appropriate data store to
     *        be used.
     * @return A channel that can be used to write new data.
     * @throws IOException
     */
    SeekableByteChannel newChannel(long estimatedSize) throws IOException;

    /**
     * Get the 64-bit length of the data.
     *
     * @return The length of the data as a long.
     */
    long length();

    /**
     * Invalidate any local caches
     */
    default void invalidateCache() {}

    /**
     * {@inheritDoc}
     * 
     * @deprecated Use {@link #length()} instead.
     */
    @Deprecated
    @Override
    default int dataLength() {
        long len = length();
        if (len > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) len;
    }

    /**
     * <p>
     * Get direct access to the data in file form.
     * </p>
     * <p>
     * This method is provided to allow the client to interact directly with APIs that expect data to be provided in File
     * form, where the implementation may be capable of providing a more efficient mechanism than the client writing a
     * temporary file itself.
     * </p>
     * <p>
     * <strong>This should only be used where appropriate.</strong>
     * </p>
     *
     * @return the data in file form, or null if the operation is not possible.
     */
    IFileProvider getFileProvider();

    default boolean supportsDirectMutationViaBuffer() {
        return true;
    }
}
