package emissary.core.blob;

import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;

import emissary.core.IBaseDataObject;

/**
 * Container for binary data, with capability additional to the original {@link IBaseDataObject}.
 *
 * @author adyoun2
 *
 */
public interface IDataContainer extends IOriginalDataContainer, Cloneable, Serializable {

    static final class LegacyContainerWrapper implements IDataContainer {
        /**
         *
         */
        private static final long serialVersionUID = 2857054283156106372L;
        private IOriginalDataContainer legacyContainer;

        public LegacyContainerWrapper(IOriginalDataContainer iLegacyDataContainer) {
            this.legacyContainer = iLegacyDataContainer;
        }

        @Override
        public void setData(byte[] newData, int offset, int length) {
            legacyContainer.setData(newData, offset, length);
        }

        @Override
        public void setData(byte[] newData) {
            legacyContainer.setData(newData);
        }

        @Override
        public ByteBuffer dataBuffer() {
            return legacyContainer.dataBuffer();
        }

        @Override
        public byte[] data() {
            return legacyContainer.data();
        }

        @Override
        public LegacyContainerWrapper clone() throws CloneNotSupportedException {
            LegacyContainerWrapper c = (LegacyContainerWrapper) super.clone();
            c.legacyContainer = legacyContainer.clone();
            return c;
        }

        @Override
        public SeekableByteChannel channel() {
            SeekableInMemoryByteChannel baseChannel = new SeekableInMemoryByteChannel(data());
            WrappedSeekableByteChannel<SeekableInMemoryByteChannel> wrapped = new WrappedSeekableByteChannel<>(baseChannel);
            wrapped.setCloseAction(x -> {
                int size = (int) x.size();
                byte[] b = new byte[size];
                System.arraycopy(x.array(), 0, b, 0, size);
                setData(b);
            });
            return wrapped;
        }

        @Override
        public SeekableByteChannel newChannel(long estimatedSize) {
            SeekableInMemoryByteChannel baseChannel = new SeekableInMemoryByteChannel();
            WrappedSeekableByteChannel<SeekableInMemoryByteChannel> wrapped = new WrappedSeekableByteChannel<>(baseChannel);
            wrapped.setCloseAction(x -> {
                int size = (int) x.size();
                byte[] b = new byte[size];
                System.arraycopy(x.array(), 0, b, 0, size);
                setData(b);
            });
            return wrapped;
        }

        @SuppressWarnings("deprecation")
        @Override
        public long length() {
            return legacyContainer.dataLength();
        }
    }

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
     * Writes to this channel <strong>will</strong> be persisted into the underlying data, so long as the channel is
     * closed normally.
     * </p>
     *
     * @return A channel on the data, or null if no data exists.
     * @throws IOException
     */
    SeekableByteChannel channel() throws IOException;

    /**
     * Get a channel that can be used to write new data. Existing data will be lost if this is used.
     *
     * @param estimatedSize An estimate of the size of the data that will be written, to allow an appropriate data store
     *        to be used.
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

    @Deprecated
    @Override
    default int dataLength() {
        long len = length();
        if (len > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) len;
    }
}
