package emissary.core.channels;

import org.apache.commons.lang3.Validate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

/**
 * {@link SeekableByteChannel} implementation the provides a channel interface to a backing InputStream instance
 * 
 * @param <T> Type of {@link InputStream} exposed by this channel
 */
public class InputStreamChannel<T extends InputStream> extends AbstractSeekableByteChannel {
    /**
     * The InputStreamFactory used to get InputStream instances.
     */
    private final InputStreamFactory<T> inputStreamFactory;

    /**
     * The current InputStream instance.
     */
    private InputStream inputStream;

    /**
     * The current position in the current InputStream instance.
     */
    private long streamPosition;

    private long size;

    /**
     * Create a new InputStreamChannel instance with a fixed size and data source
     *
     * @param size of the InputStreamChannel
     * @param inputStreamFactory data source
     */
    public InputStreamChannel(final long size, final InputStreamFactory<T> inputStreamFactory) {
        Validate.notNull(inputStreamFactory, "Required: inputStreamFactory not null!");
        this.size = size;
        this.inputStreamFactory = inputStreamFactory;
    }

    @Override
    protected final int readImpl(final ByteBuffer byteBuffer, final int maxBytesToRead) throws IOException {
        if (position() < streamPosition) {
            streamPosition = 0;
            inputStream.close();
            inputStream = null;
        }

        if (inputStream == null) {
            inputStream = inputStreamFactory.create();
        }

        // Actually perform the read
        final int bytesRead = SeekableByteChannelHelper.getFromInputStream(inputStream, byteBuffer,
                position() - streamPosition, maxBytesToRead);

        // Update positioning
        position(position() + bytesRead);
        streamPosition = position();

        return bytesRead;
    }


    @Override
    protected long sizeImpl() throws IOException {
        if (size < 0) {
            try (final InputStream is = inputStreamFactory.create()) {
                size = SeekableByteChannelHelper.available(is);
            }
        }
        return size;
    }

    @Override
    protected final void closeImpl() throws IOException {
        if (inputStream != null) {
            inputStream.close();
            inputStream = null;
        }
    }
}
