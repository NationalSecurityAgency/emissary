package emissary.core.channels;

import org.apache.commons.lang3.Validate;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;

/**
 * Wrap {@link SeekableByteChannelFactory} objects to make them immutable
 */
public final class ImmutableChannelFactory {
    private ImmutableChannelFactory() {}

    /**
     * <p>
     * Wrap a provided channel factory with immutability i.e. write, truncate etc are all disabled.
     * </p>
     * 
     * <p>
     * Position *can* be changed on individual channels
     * </p>
     * 
     * @param sbcf to wrap
     * @return the wrapped channel factory
     */
    public static SeekableByteChannelFactory create(final SeekableByteChannelFactory sbcf) {
        return new ImmutableChannelFactoryImpl(sbcf);
    }

    /**
     * Wraps an existing channel factory in immutability.
     */
    private static final class ImmutableChannelFactoryImpl implements SeekableByteChannelFactory {
        /**
         * The SeekableByteChannelFactory to be made immutable.
         */
        private final SeekableByteChannelFactory sbcf;

        /**
         * Configure an immutable factory instance with the provided factory
         * 
         * @param sbcf to wrap
         */
        private ImmutableChannelFactoryImpl(final SeekableByteChannelFactory sbcf) {
            Validate.notNull(sbcf, "Required: sbcf not null");

            this.sbcf = sbcf;
        }

        /**
         * Creates an immutable channel instance upon invocation.
         * 
         * @return an immutable channel instance
         */
        @Override
        public SeekableByteChannel create() {
            return new ImmutableSeekableByteChannel(sbcf.create());
        }

    }

    /**
     * Immutable overrides for the actual implementation.
     */
    private static final class ImmutableSeekableByteChannel implements SeekableByteChannel {
        /**
         * The SeekableByteChannel to be made immutable.
         */
        private final SeekableByteChannel channel;

        private ImmutableSeekableByteChannel(final SeekableByteChannel channel) {
            Validate.notNull(channel, "Required: channel not null");
            this.channel = channel;
        }

        @Override
        public boolean isOpen() {
            return channel.isOpen();
        }

        @Override
        public void close() throws IOException {
            channel.close();
        }

        @Override
        public int read(final ByteBuffer dst) throws IOException {
            return channel.read(dst);
        }

        @Override
        public long position() throws IOException {
            return channel.position();
        }

        @Override
        public SeekableByteChannel position(final long newPosition) throws IOException {
            return channel.position(newPosition);
        }

        @Override
        public long size() throws IOException {
            return channel.size();
        }

        @Override
        public int write(final ByteBuffer src) throws IOException {
            throw new NonWritableChannelException();
        }

        @Override
        public SeekableByteChannel truncate(final long size) throws IOException {
            throw new NonWritableChannelException();
        }
    }
}
