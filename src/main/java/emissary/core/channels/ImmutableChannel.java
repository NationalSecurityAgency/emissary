package emissary.core.channels;

import org.apache.commons.lang3.Validate;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;

/**
 * Decorator class that wraps an existing {@link SeekableByteChannel} instance and makes it immutable
 * 
 * @param <T> Type of SeekableByteChannel to wrap
 */
public final class ImmutableChannel<T extends SeekableByteChannel> implements SeekableByteChannel {

    /**
     * The SeekableByteChannel to be made immutable.
     */
    private final T channel;

    /**
     * Creates a new SeekableByteChannel that provides immutable access to another existing channel
     *
     * @param channel channel for which immutable access is to be provided
     */
    ImmutableChannel(final T channel) {
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
    public T position(final long newPosition) throws IOException {
        channel.position(newPosition);
        return channel;
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
    public T truncate(final long size) throws IOException {
        throw new NonWritableChannelException();
    }
}
