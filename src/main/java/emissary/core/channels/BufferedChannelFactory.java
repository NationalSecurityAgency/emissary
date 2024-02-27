package emissary.core.channels;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Validate;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

/**
 * Creates a SeekableByteChannel cache of a defined size analogous to BufferedInputStream.
 */
public final class BufferedChannelFactory {
    private BufferedChannelFactory() {}

    /**
     * Creates a SeekableByteChannelFactory that caches the bytes of the passed in SeekableByteChannelFactory.
     * 
     * @param seekableByteChannelFactory to be cached.
     * @param maxBufferSize maximum size of the buffer of bytes (BufferSize = Math.min(sbcf.size(), maxBufferSize)).
     * @return the caching SeekableByteChannelFactory.
     */
    public static SeekableByteChannelFactory create(final SeekableByteChannelFactory seekableByteChannelFactory,
            final int maxBufferSize) {
        return new BufferedChannelFactoryImpl(seekableByteChannelFactory, maxBufferSize);
    }

    /**
     * A SeekableByteChannelFactory that caches the bytes of the passed in SeekableByteChannelFactory.
     */
    private static class BufferedChannelFactoryImpl implements SeekableByteChannelFactory {
        /**
         * The SeekableByteChannel to cache.
         */
        private final SeekableByteChannelFactory seekableByteChannelFactory;
        /**
         * The size of the buffer of bytes.
         */
        private final int bufferSize;

        /**
         * Creates a SeekableByteChannelFactory that caches the bytes of the passed in SeekableByteChannelFactory.
         * 
         * @param seekableByteChannelFactory to be cached.
         * @param maxBufferSize of the buffer of bytes.
         */
        public BufferedChannelFactoryImpl(final SeekableByteChannelFactory seekableByteChannelFactory,
                final int maxBufferSize) {
            Validate.notNull(seekableByteChannelFactory, "Required: seekableByteChannelFactory not null!");
            Validate.isTrue(maxBufferSize > 0, "Required: maxBufferSize > 0");

            this.seekableByteChannelFactory = seekableByteChannelFactory;

            int b = maxBufferSize;
            try (SeekableByteChannel sbc = seekableByteChannelFactory.create()) {
                if (sbc.size() < maxBufferSize) {
                    b = (int) sbc.size();
                }
            } catch (IOException e) {
                // Leave b as maxBufferSize.
            }

            this.bufferSize = b;
        }

        @Override
        public SeekableByteChannel create() {
            return new BufferedSeekableByteChannel(seekableByteChannelFactory.create(), bufferSize);
        }
    }

    /**
     * SeekableByteChannel that caches the bytes of the passed in SeekableByteChannel
     */
    private static class BufferedSeekableByteChannel extends AbstractSeekableByteChannel {
        /**
         * The SeekableByteChannel to cache.
         */
        private final SeekableByteChannel seekableByteChannel;
        /**
         * The size of the buffer of bytes.
         */
        private final int bufferSize;
        /**
         * The buffer of bytes.
         */
        private final ByteBuffer buffer;

        /**
         * The starting offset of the current buffer.
         */
        private long bufferStart = -1;
        /**
         * The number of valid bytes in the current buffer.
         */
        private int bufferValidBytes = -1;

        /**
         * Creates a SeekableByteChannel cache where there is a single buffer of bytes aligned on bufferSize boundaries.
         * 
         * @param seekableByteChannel to be cached.
         * @param bufferSize of the buffer of bytes.
         */
        public BufferedSeekableByteChannel(final SeekableByteChannel seekableByteChannel, final int bufferSize) {
            this.seekableByteChannel = seekableByteChannel;
            this.bufferSize = bufferSize;
            this.buffer = ByteBuffer.allocate(bufferSize);
        }

        @Override
        protected void closeImpl() throws IOException {
            seekableByteChannel.close();
        }

        @Override
        protected int readImpl(final ByteBuffer byteBuffer) throws IOException {
            // Determines the start of the buffer that contains the current position.
            final long bufferStartFromPosition = position() / bufferSize * bufferSize;

            if (bufferStartFromPosition != bufferStart) {
                buffer.position(0);
                seekableByteChannel.position(bufferStartFromPosition);

                bufferValidBytes = IOUtils.read(seekableByteChannel, buffer);
                bufferStart = bufferStartFromPosition;
            }

            final int bufferStartOffset = (int) (position() % bufferSize);
            final int bytesToReturn = Math.min(byteBuffer.remaining(), bufferValidBytes - bufferStartOffset);

            byteBuffer.put(buffer.array(), bufferStartOffset, bytesToReturn);

            return bytesToReturn;
        }

        @Override
        protected long sizeImpl() throws IOException {
            return seekableByteChannel.size();
        }
    }
}
