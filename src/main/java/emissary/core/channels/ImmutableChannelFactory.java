package emissary.core.channels;

import org.apache.commons.lang3.Validate;

import java.nio.channels.SeekableByteChannel;

/**
 * Helper {@link SeekableByteChannelFactory} used to provide immutable access to channels created by an existing channel
 * factory
 * 
 * @param <T> Type of {@link SeekableByteChannel} instances for which immutable access will be provided
 */
public final class ImmutableChannelFactory<T extends SeekableByteChannel> implements SeekableByteChannelFactory<ImmutableChannel<T>> {
    private final SeekableByteChannelFactory<T> sbcf;


    /**
     * <p>
     * Wrap a provided channel factory with immutability i.e. write, truncate etc are all disabled.
     * </p>
     *
     * <p>
     * Position *can* be changed on individual channels
     * </p>
     *
     * @param sbcf channel factory instance to wrap
     * @param <T> Type of {@link SeekableByteChannel} instances for which immutable access will be provided
     * @return the wrapped channel factory
     */
    public static <T extends SeekableByteChannel> ImmutableChannelFactory<T> createFactory(final SeekableByteChannelFactory<T> sbcf) {
        return new ImmutableChannelFactory<>(sbcf);
    }

    private ImmutableChannelFactory(final SeekableByteChannelFactory<T> sbcf) {
        Validate.notNull(sbcf, "Required: sbcf not null");

        this.sbcf = sbcf;
    }

    /**
     * Creates an immutable channel instance upon invocation.
     *
     * @return an immutable channel instance
     */
    @Override
    public ImmutableChannel<T> create() {
        return new ImmutableChannel<>(sbcf.create());
    }

}
