package emissary.core.channels;

import org.apache.commons.lang3.Validate;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

/**
 * This class creates a SeekableByteChannelFactory which represents a segment of another SeekableByteChannelFactory.
 */
public final class SegmentChannelFactory {
    private SegmentChannelFactory() {}

    /**
     * Creates a SeekableByteChannelFactory which represents a segment of another SeekableByteChannelFactory. NOTE: start +
     * length &lt;= sbcf.size()
     * 
     * @param seekableByteChannelFactory that the segment is created from
     * @param start of the segment in the SeekableByteChannelFactory (start &gt;= 0).
     * @param length of the segment in the SeekableByteChannelFactory (length &gt;= 0).
     * @return the segment SeekableByteChannelFactory.
     */
    public static SeekableByteChannelFactory create(final SeekableByteChannelFactory seekableByteChannelFactory,
            final long start, final long length) {
        return new SegmentChannelFactoryImpl(seekableByteChannelFactory, start, length);
    }

    /**
     * The SeekableByteChannelFactory implementation that returns a "segment" SeekableByteChannel.
     */
    private static class SegmentChannelFactoryImpl implements SeekableByteChannelFactory {
        /**
         * The original SeekableByteChannelFactory that the segment is going to be referencing.
         */
        private final SeekableByteChannelFactory seekableByteChannelFactory;
        /**
         * The start of the segment in the original SeekableByteChannelFactory.
         */
        private final long start;
        /**
         * The length of the segment in the original SeekableByteChannelFactory.
         */
        private final long length;

        private SegmentChannelFactoryImpl(final SeekableByteChannelFactory seekableByteChannelFactory, final long start,
                final long length) {
            Validate.isTrue(seekableByteChannelFactory != null, "Required: seekableByteChannelFactory != null!");
            Validate.isTrue(start >= 0, "Required: start >= 0!");
            Validate.isTrue(length >= 0, "Required: length >= 0!");
            try (SeekableByteChannel sbc = seekableByteChannelFactory.create()) {
                Validate.isTrue(start + length <= sbc.size(), "Required: start + length <= Size!");
            } catch (IOException e) {
                throw new IllegalArgumentException("Could not get seekableByteChannel size!", e);
            }

            this.seekableByteChannelFactory = seekableByteChannelFactory;
            this.start = start;
            this.length = length;
        }

        @Override
        public SeekableByteChannel create() {
            return new SegmentSeekableByteChannel(seekableByteChannelFactory.create(), start, length);
        }
    }

    /**
     * The "segment" SeekableByteChannel implementation.
     */
    private static class SegmentSeekableByteChannel extends AbstractSeekableByteChannel {
        /**
         * The original SeekableByteChannelFactory that the segment is going to be referencing.
         */
        private final SeekableByteChannel seekableByteChannel;
        /**
         * The start of the segment in the original SeekableByteChannelFactory.
         */
        private final long start;
        /**
         * The length of the segment in the original SeekableByteChannelFactory.
         */
        private final long length;

        private SegmentSeekableByteChannel(final SeekableByteChannel seekableByteChannel, final long start,
                final long length) {
            this.seekableByteChannel = seekableByteChannel;
            this.start = start;
            this.length = length;
        }

        @Override
        protected void closeImpl() throws IOException {
            seekableByteChannel.close();
        }

        @Override
        protected int readImpl(final ByteBuffer byteBuffer) throws IOException {
            seekableByteChannel.position(start + position());

            return seekableByteChannel.read(byteBuffer);
        }

        @Override
        protected long sizeImpl() {
            return length;
        }
    }
}
