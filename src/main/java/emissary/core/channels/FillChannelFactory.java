package emissary.core.channels;

import org.apache.commons.lang3.Validate;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.Arrays;

/**
 * This class creates a SeekableByteChannel of the designated size with each element having the designated value.
 */
public class FillChannelFactory {
    private FillChannelFactory() {}

    /**
     * Creates an instance of the factory with the designated size and value.
     * 
     * @param size of the SeekableByteChannel.
     * @param value of each element in the seekable byte channel.
     * @return an immutable version of the seekable byte channel with the designated value.
     */
    public static SeekableByteChannelFactory create(final long size, final byte value) {
        return SeekableByteChannelHelper.immutable(new FillChannelFactoryImpl(size, value));
    }

    /**
     * The SeekableByteChannelFactory implementation that returns a "fill" SeekableByteChannel.
     */
    private static final class FillChannelFactoryImpl implements SeekableByteChannelFactory {

        /**
         * The size of the SeekableByteChannel that is returned.
         */
        private final long size;

        /**
         * The value of each element in the SeekableByteChannel that is returned.
         */
        private final byte value;

        private FillChannelFactoryImpl(final long size, final byte value) {
            Validate.isTrue(size >= 0, "Required: size >= 0");

            this.size = size;
            this.value = value;
        }

        @Override
        public SeekableByteChannel create() {
            return new FillChannel(size, value);
        }
    }

    /**
     * The implementation of the "fill" SeekableByteChannel.
     */
    private static final class FillChannel extends AbstractSeekableByteChannel {

        /**
         * The value of each element in the SeekableByteChannel.
         */
        private final byte value;

        private final long size;

        private FillChannel(final long size, final byte value) {
            this.size = size;
            this.value = value;
        }

        @Override
        protected long sizeImpl() throws IOException {
            return size;
        }

        @Override
        protected void closeImpl() throws IOException {
            // Nothing to do.
        }

        @Override
        protected int readImpl(final ByteBuffer byteBuffer, final int maxBytesToRead) throws IOException {
            final int bytesToFill = Math.min(maxBytesToRead, Integer.MAX_VALUE);

            if (byteBuffer.hasArray()) {
                Arrays.fill(byteBuffer.array(), byteBuffer.position(), byteBuffer.position() + bytesToFill, value);
            } else {
                for (int i = 0; i < bytesToFill; i++) {
                    byteBuffer.put(value);
                }
            }

            position(position() + bytesToFill);

            return bytesToFill;
        }
    }
}
