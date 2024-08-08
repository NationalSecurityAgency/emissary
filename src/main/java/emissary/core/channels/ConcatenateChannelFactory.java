package emissary.core.channels;

import org.apache.commons.lang3.Validate;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

/**
 * This class creates a SeekableByteChannelFactory that concatenates two other SeekableByteChannelFactories.
 */
public final class ConcatenateChannelFactory {
    private ConcatenateChannelFactory() {}

    /**
     * Creates a SeekableByteChannelFactory which represents the concatenation of two other SeekableByteChannelFactories
     * 
     * @param first is the first SeekableByteChannelFactory to be concatenated.
     * @param second is the second SeekableByteChannelFactory to be concatenated.
     * @return the concatenated SeekableByteChannelFactory.
     */
    public static SeekableByteChannelFactory create(final SeekableByteChannelFactory first,
            final SeekableByteChannelFactory second) {
        return new ConcatenateChannelFactoryImpl(first, second);
    }

    /**
     * The SeekableByteChannelFactory implementation that returns a "concatenation" of two SeekableByteChannels.
     */
    private static class ConcatenateChannelFactoryImpl implements SeekableByteChannelFactory {
        /**
         * The first SeekableByteChannelFactory to be concatenated
         */
        private final SeekableByteChannelFactory first;
        /**
         * The second SeekableByteChannelFactory to be concatenated
         */
        private final SeekableByteChannelFactory second;

        private ConcatenateChannelFactoryImpl(final SeekableByteChannelFactory first,
                final SeekableByteChannelFactory second) {
            Validate.isTrue(first != null, "Required: first != null!");
            Validate.isTrue(second != null, "Required: second != null!");

            this.first = first;
            this.second = second;
        }

        @Override
        public SeekableByteChannel create() {
            return new ConcatenateSeekableByteChannel(first.create(), second.create());
        }
    }

    /**
     * The "concatenation" SeekableByteChannel implementation.
     */
    private static class ConcatenateSeekableByteChannel extends AbstractSeekableByteChannel {
        private static final BigInteger LONG_MAX_VALUE = BigInteger.valueOf(Long.MAX_VALUE);

        /**
         * The first SeekableByteChannel to be concatenated.
         */
        private final SeekableByteChannel first;
        /**
         * The second SeekableByteChannel to be concatenated.
         */
        private final SeekableByteChannel second;

        /**
         * the total size of first.size() + second.size() or -1 if not valid.
         */
        private long totalSize = -1;

        private ConcatenateSeekableByteChannel(final SeekableByteChannel first,
                final SeekableByteChannel second) {
            this.first = first;
            this.second = second;
        }

        @Override
        protected void closeImpl() throws IOException {
            first.close();
            second.close();
        }

        @Override
        protected int readImpl(final ByteBuffer byteBuffer) throws IOException {
            size();

            final long start = position();
            final int length = byteBuffer.remaining();
            final long firstSbcSize = first.size();
            final long firstSbcLastIndex = firstSbcSize - 1;

            if (start + length <= firstSbcLastIndex) {
                first.position(start);
                first.read(byteBuffer);
            } else if (start > firstSbcLastIndex) {
                second.position(start - firstSbcSize);
                second.read(byteBuffer);
            } else {
                final int firstAmount = (int) (firstSbcSize - start);
                final int originalLimit = byteBuffer.limit();

                byteBuffer.limit(firstAmount);
                first.position(start);
                first.read(byteBuffer);
                byteBuffer.limit(originalLimit);
                second.position(0);
                second.read(byteBuffer);
            }

            return length;
        }

        @Override
        protected long sizeImpl() throws IOException {
            if (totalSize <= -1) {
                final BigInteger firstSize = BigInteger.valueOf(first.size());
                final BigInteger secondSize = BigInteger.valueOf(second.size());
                final BigInteger firstPlusSecondSize = firstSize.add(secondSize);

                if (firstPlusSecondSize.compareTo(LONG_MAX_VALUE) <= 0) {
                    totalSize = firstPlusSecondSize.longValue();
                } else {
                    throw new IOException("firstSbcf.size() + secondSbcf.size() > Long.MAX_VALUE not allowed!");
                }
            }

            return totalSize;
        }
    }
}
