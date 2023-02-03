package emissary.core.channels;

import org.apache.commons.lang3.Validate;

import java.io.IOException;
import java.math.BigInteger;
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
     * Used during {@link #read(ByteBuffer)} to calculate resizing a ByteBuffer
     */
    private static final BigInteger bigIntMaxValue = BigInteger.valueOf(Integer.MAX_VALUE);

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
     * Real read implementation. The provided byteBuffer will be properly sized (limited) on the way in.
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
        if (!hasRemaining()) {
            return -1;
        }

        // Remaining bytes in this channel
        final long channelRemaining = remaining();
        // Remaining bytes in the provided buffer
        final int byteBufferRemaining = byteBuffer.remaining();
        // Store off the current limit in case we need to update it
        final int byteBufferLimit = byteBuffer.limit();

        // If the byte buffer has more bytes left than the channel, we want to right-size it for
        // implementations to be able to 'simply' just read into the byteBuffer.
        if (byteBufferRemaining > channelRemaining) {
            // Get the new limit in a safe way to avoid arithmetic exception issues
            final int newLimit = BigInteger.valueOf(channelRemaining).add(BigInteger.valueOf(byteBuffer.position()))
                    .min(bigIntMaxValue).intValue();
            // Update the limit of the byteBuffer temporarily whilst we carry out the read
            // This will be reset to the original limit before returning
            byteBuffer.limit(newLimit);
        }

        try {
            // Actually carry out the read, and keep how many bytes read for later return
            final int bytesRead = readImpl(byteBuffer);
            // Update position of channel
            position(position() + bytesRead);
            // Return the amount of bytes read from the channel
            return bytesRead;
        } finally {
            // Update limit of byteBuffer, which may have been reduced to ensure a safe read
            byteBuffer.limit(byteBufferLimit);
        }
    }

    /**
     * Whether this channel has any bytes remaining.
     * 
     * @return true if there are bytes remaining
     * @throws IOException if an error occurs
     */
    public final boolean hasRemaining() throws IOException {
        return remaining() > 0;
    }

    /**
     * The amount of bytes remaining in this channel (size - current position).
     * 
     * @return amount of bytes remaining
     * @throws IOException if an error occurs
     */
    public final long remaining() throws IOException {
        return size() - position();
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
