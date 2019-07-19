package emissary.core.blob;

import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;

final class LegacyContainerWrapper implements IDataContainer {
    /**
     *
     */
    private static final long serialVersionUID = 2857054283156106372L;
    private IOriginalDataContainer legacyContainer;

    LegacyContainerWrapper(IOriginalDataContainer iLegacyDataContainer) {
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
        wrapped.setWriteAction(o -> {
            wrapped.setWriteAction(null);
            wrapped.setCloseAction(x -> {
                int size = (int) x.size();
                byte[] b = new byte[size];
                System.arraycopy(x.array(), 0, b, 0, size);
                setData(b);
            });
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

    @Override
    public IFileProvider getFileProvider() {
        return IFileProvider.tempFileProvider(this);
    }
}
