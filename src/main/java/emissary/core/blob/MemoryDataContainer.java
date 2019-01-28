package emissary.core.blob;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;

/**
 * Old school data container storing the data as an on-heap byte array.
 *
 */
public class MemoryDataContainer implements IDataContainer {

    /**
     *
     */
    private static final long serialVersionUID = -4640682765681267112L;
    private byte[] theData = new byte[0];

    public MemoryDataContainer() {
        ContainerMonitor.getInstance().register(this);
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
    public long length() {
        return this.theData.length;
    }

    @Override
    public IFileProvider getFileProvider() {
        return IFileProvider.tempFileProvider(this);
    }

    @Override
    public ByteBuffer dataBuffer() {
        return ByteBuffer.wrap(data());
    }

    @Override
    public SeekableByteChannel channel() throws IOException {
        SeekableInMemoryByteChannel baseChannel = new SeekableInMemoryByteChannel(theData);
        WrappedSeekableByteChannel<SeekableInMemoryByteChannel> wrapped = new WrappedSeekableByteChannel<>(baseChannel);
        wrapped.setCloseAction(this::setDataFromChannel);
        return wrapped;
    }

    @Override
    public SeekableByteChannel newChannel(long estimatedSize) throws IOException {
        SeekableInMemoryByteChannel baseChannel = new SeekableInMemoryByteChannel();
        WrappedSeekableByteChannel<SeekableInMemoryByteChannel> wrapped = new WrappedSeekableByteChannel<>(baseChannel);
        wrapped.setCloseAction(this::setDataFromChannel);
        return wrapped;
    }

    private void setDataFromChannel(SeekableInMemoryByteChannel input) {
        int size = (int) input.size();
        this.theData = new byte[size];
        System.arraycopy(input.array(), 0, theData, 0, size);
    }

    @Override
    public MemoryDataContainer clone() throws CloneNotSupportedException {
        MemoryDataContainer clone = (MemoryDataContainer) super.clone();
        if ((this.theData != null) && (this.theData.length > 0)) {
            clone.setData(this.theData, 0, this.theData.length);
        }
        return clone;
    }

}
