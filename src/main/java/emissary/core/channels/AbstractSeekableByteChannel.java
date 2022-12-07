package emissary.core.channels;

import org.apache.commons.lang3.Validate;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;

/**
 * Core implementation of the {@link SeekableByteChannel} interface
 */
public abstract class AbstractSeekableByteChannel implements SeekableByteChannel {
    /**
     * Boolean describing whether the SeekableByteChannel is open or closed.
     */
    private boolean open = true;
    /**
     * The current position of the SeekableByteChannel.
     */
    private long position = 0;

    /**
     * Create a new SBC
     */
    protected AbstractSeekableByteChannel() {}

    /**
     * Real close implementation
     * 
     * @throws IOException if an error occurs
     */
    protected abstract void closeImpl() throws IOException;

    /**
     * Real read implementation
     * 
     * @param byteBuffer to read from the SBC into.
     * @return the number of bytes read
     * @throws IOException if an error occurs
     */
    protected abstract int readImpl(ByteBuffer byteBuffer) throws IOException;

    /**
     * Real size implementation
     * 
     * @return the size of the channel
     * @throws IOException if an error occurs
     */
    protected abstract long sizeImpl() throws IOException;

    /**
     * Close the channel and mark as such
     */
    @Override
    public final void close() throws IOException {
        if (open) {
            open = false;
            closeImpl();
        }
    }

    /**
     * Determine whether the channel is marked as open/closed
     * 
     * @return if the channel is open
     */
    @Override
    public final boolean isOpen() {
        return open;
    }

    /**
     * If the channel is open, return the current position
     * 
     * @return the current position if the channel is still open
     */
    @Override
    public final long position() throws IOException {
        checkOpen(open);
        return position;
    }

    /**
     * Set the position of the channel. Must be greater than -1, can be beyond the length of the channel.
     * 
     * @param position to set within the channel
     */
    @Override
    public final SeekableByteChannel position(final long position) throws IOException {
        checkOpen(open);
        Validate.isTrue(position >= 0, "Required: position >= 0");
        this.position = position;
        return this;
    }

    /**
     * Read bytes from the channel into the provided buffer, if the channel is still open.
     * 
     * Relies on the implementation provided by the extending class to actually carry out the read.
     * 
     * @param byteBuffer to read into
     * @throws IOException if an error occurs
     */
    @Override
    public final int read(final ByteBuffer byteBuffer) throws IOException {
        checkOpen(open);
        Validate.notNull(byteBuffer, "Required: byteBuffer != null");
        // If we're at the end of the file, early return
        if (position() >= size()) {
            return -1;
        }
        return readImpl(byteBuffer);
    }

    /**
     * Return the size of the channel if the channel is still open.
     * 
     * This adheres to the {@link SeekableByteChannel} specification.
     * 
     * @throws IOException if an error occurs
     */
    @Override
    public final long size() throws IOException {
        checkOpen(open);
        return sizeImpl();
    }

    /**
     * Block truncation of the channel, keep it immutable. Will throw {@link NonWritableChannelException}
     * 
     * @param size to set the channel to
     * @throws IOException if an error occurs
     */
    @Override
    public final SeekableByteChannel truncate(final long size) throws IOException {
        throw new NonWritableChannelException();
    }

    /**
     * Block writing of the channel, keep it immutable. Will throw {@link NonWritableChannelException}
     * 
     * @param byteBuffer to write from
     */
    @Override
    public final int write(final ByteBuffer byteBuffer) throws IOException {
        throw new NonWritableChannelException();
    }

    /**
     * Validate the channel is still open, otherwise throw a {@link ClosedChannelException}
     * 
     * @param open if the channel is open or not
     * @throws IOException if an error occurs
     */
    private static void checkOpen(final boolean open) throws IOException {
        if (!open) {
            throw new ClosedChannelException();
        }
    }
}
