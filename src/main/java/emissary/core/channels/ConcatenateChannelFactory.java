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
     * @param leftSeekableByteChannelFactory is the left SeekableByteChannelFactory to be concatenated.
     * @param rightSeekableByteChannelFactory is the right SeekableByteChannelFactory to be concatenated.
     * @return the concatenated SeekableByteChannelFactory.
     */
    public static SeekableByteChannelFactory create(final SeekableByteChannelFactory leftSeekableByteChannelFactory,
            final SeekableByteChannelFactory rightSeekableByteChannelFactory) {
        return new ConcatenateChannelFactoryImpl(leftSeekableByteChannelFactory, rightSeekableByteChannelFactory);
    }

    /**
     * The SeekableByteChannelFactory implementation that returns a "concatenation" of two SeekableByteChannels.
     */
    private static class ConcatenateChannelFactoryImpl implements SeekableByteChannelFactory {
        /**
         * The left SeekableByteChannelFactory to be concatenated
         */
        private final SeekableByteChannelFactory leftSeekableByteChannelFactory;
        /**
         * The right SeekableByteChannelFactory to be concatenated
         */
        private final SeekableByteChannelFactory rightSeekableByteChannelFactory;

        private ConcatenateChannelFactoryImpl(final SeekableByteChannelFactory leftSeekableByteChannelFactory,
                final SeekableByteChannelFactory rightSeekableByteChannelFactory) {
            Validate.isTrue(leftSeekableByteChannelFactory != null, "Required: leftSeekableByteChannelFactory != null!");
            Validate.isTrue(rightSeekableByteChannelFactory != null, "Required: rightSeekableByteChannelFactory != null!");

            this.leftSeekableByteChannelFactory = leftSeekableByteChannelFactory;
            this.rightSeekableByteChannelFactory = rightSeekableByteChannelFactory;
        }

        @Override
        public SeekableByteChannel create() {
            return new ConcatenateSeekableByteChannel(leftSeekableByteChannelFactory.create(), rightSeekableByteChannelFactory.create());
        }
    }

    /**
     * The "concatenation" SeekableByteChannel implementation.
     */
    private static class ConcatenateSeekableByteChannel extends AbstractSeekableByteChannel {
        private static final BigInteger LONG_MAX_VALUE = BigInteger.valueOf(Long.MAX_VALUE);

        /**
         * The left SeekableByteChannel to be concatenated.
         */
        private final SeekableByteChannel leftSeekableByteChannel;
        /**
         * The right SeekableByteChannel to be concatenated.
         */
        private final SeekableByteChannel rightSeekableByteChannel;

        /**
         * the total size of leftSbcf.size() + rightSbcf.size() or -1 if not valid.
         */
        private long totalSize = -1;

        private ConcatenateSeekableByteChannel(final SeekableByteChannel leftSeekableByteChannel,
                final SeekableByteChannel rightSeekableByteChannel) {
            this.leftSeekableByteChannel = leftSeekableByteChannel;
            this.rightSeekableByteChannel = rightSeekableByteChannel;
        }

        @Override
        protected void closeImpl() throws IOException {
            leftSeekableByteChannel.close();
            rightSeekableByteChannel.close();
        }

        @Override
        protected int readImpl(final ByteBuffer byteBuffer) throws IOException {
            size();

            final long start = position();
            final int length = byteBuffer.remaining();
            final long leftSbcSize = leftSeekableByteChannel.size();
            final long leftSbcLastIndex = leftSbcSize - 1;

            if (start + length <= leftSbcLastIndex) {
                leftSeekableByteChannel.position(start);
                leftSeekableByteChannel.read(byteBuffer);
            } else if (start > leftSbcLastIndex) {
                rightSeekableByteChannel.position(start - leftSbcSize);
                rightSeekableByteChannel.read(byteBuffer);
            } else {
                final int leftAmount = (int) (leftSbcSize - start);
                final int originalLimit = byteBuffer.limit();

                byteBuffer.limit(leftAmount);
                leftSeekableByteChannel.position(start);
                leftSeekableByteChannel.read(byteBuffer);
                byteBuffer.limit(originalLimit);
                rightSeekableByteChannel.position(0);
                rightSeekableByteChannel.read(byteBuffer);
            }

            return length;
        }

        @Override
        protected long sizeImpl() throws IOException {
            if (totalSize <= -1) {
                final BigInteger leftSize = BigInteger.valueOf(leftSeekableByteChannel.size());
                final BigInteger rightSize = BigInteger.valueOf(rightSeekableByteChannel.size());
                final BigInteger leftPlusRightSize = leftSize.add(rightSize);

                if (leftPlusRightSize.compareTo(LONG_MAX_VALUE) <= 0) {
                    totalSize = leftPlusRightSize.longValue();
                } else {
                    throw new IOException("leftSbcf.size() + rightSbcf.size() > Long.MAX_VALUE not allowed!");
                }
            }

            return totalSize;
        }
    }
}
