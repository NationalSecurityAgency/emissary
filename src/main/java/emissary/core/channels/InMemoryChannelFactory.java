package emissary.core.channels;

import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.apache.commons.lang3.Validate;

import java.nio.channels.SeekableByteChannel;

/**
 * Provide an in-memory backed implementation for streaming data to a consumer
 */
public final class InMemoryChannelFactory {
    private InMemoryChannelFactory() {}

    /**
     * Create a new instance of the factory using the provided byte array
     * 
     * @param bytes containing the data to provide to consumers in an immutable manner
     * @return a new instance
     */
    public static SeekableByteChannelFactory create(final byte[] bytes) {
        return ImmutableChannelFactory.create(new InMemoryChannelFactoryImpl(bytes));
    }

    /**
     * Private class to hide implementation details from callers
     */
    private static final class InMemoryChannelFactoryImpl implements SeekableByteChannelFactory {
        /**
         * The byte array this SeekableByteChannel is to represent.
         */
        private final byte[] bytes;

        private InMemoryChannelFactoryImpl(final byte[] bytes) {
            Validate.notNull(bytes, "Required: bytes not null");

            this.bytes = bytes;
        }

        /**
         * Create an immutable byte channel to the existing byte array (no copy in/out regardless of how many channels are
         * created)
         * 
         * @return the new channel instance
         */
        @Override
        public SeekableByteChannel create() {
            return new SeekableInMemoryByteChannel(bytes);
        }
    }
}
