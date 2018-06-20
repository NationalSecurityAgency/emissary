package emissary.util;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * This class provides a seekable channel for a portion, or window, within the provided ReadableByteChannel. The
 * underlying window size is configured on instantiation. If you intend to move the positions for stateful processing,
 * you should provide a buffer size, <code>buffsize</code>, greater than the maximum amount you wish to operate against
 * at any one time.
 * 
 * <p>
 * This implementation should be able to hold a maximum window of ~4GB. This implementation uses on heap buffers so be
 * wary of using Integer.MAX_VALUE as that can cause and OOME.
 */
public class WindowedSeekableByteChannel implements SeekableByteChannel {

    /**
     * The input source
     */
    private final ReadableByteChannel in;

    /**
     * estimated length. We have to estimate because we are buffering into a window and may not be at the end. We read
     * ahead to keep buffers full, but there can be additional data.
     */
    long estimatedLength;

    /**
     * The earliest position we can move to. Essentially, position of the underlying Channel that is at position 0 of
     * buff1
     */
    long minposition;

    /* Maximum amount of data allowed in memory */
    // long maxwindow;

    /** flag if we've reached the end of the underlying channel */
    private boolean endofchannel;

    /**
     * Internal buffers for windowed content
     */
    private static final int INIT_BUFFERS_SIZE = 2;
    private LinkedList<ByteBuffer> buffers = new LinkedList<>();
    private long setPosition = -1;


    /**
     * Creates a new instance and populates buffers with data.
     */
    public WindowedSeekableByteChannel(final ReadableByteChannel in, final int buffsize) throws IOException {
        if ((in == null) || !in.isOpen()) {
            throw new IllegalArgumentException("Channel must be open and not null:");
        }

        this.in = in;
        int capacity = (int) Math.ceil(((double) buffsize) / INIT_BUFFERS_SIZE);

        for (int x = 0; x < INIT_BUFFERS_SIZE; x++) {
            ByteBuffer buffer = ByteBuffer.allocate(capacity);
            readIntoBuffer(buffer);
            buffers.add(buffer);

            if (this.endofchannel) {
                break;
            }
        }
    }

    /**
     * If necessary, will move data in the window to make room for additional data from the channel.
     */
    private void realignBuffers() throws IOException {
        final int qtr = buffers.peekLast().capacity() / 2;
        if (endofchannel || (buffers.peekLast().remaining() > qtr)) {
            return;
        }

        // Shrink buffers back to closer to the initial buffer size, but do not roll off a set position
        while (buffers.size() > INIT_BUFFERS_SIZE && setPosition > (minposition + buffers.peekFirst().limit())) {
            // Remove overflow buffers
            ByteBuffer buffer = buffers.pollFirst();
            minposition += buffer.limit();
        }

        final int offset = buffers.stream().mapToInt(buffer -> buffer.position()).sum();

        // Do not roll off a set position in the buffer
        if (setPosition != -1 && minposition + qtr > setPosition) {
            // Extend the buffer to fit request
            ByteBuffer buffer = ByteBuffer.allocate(buffers.peek().capacity());
            buffers.add(buffer);
            readIntoBuffer(buffer);
            return;
        }

        Iterator<ByteBuffer> bufferIterator = buffers.iterator();

        // Position the first byte buffer
        ByteBuffer prev = bufferIterator.next();
        prev.position(qtr);
        prev.compact();

        while (bufferIterator.hasNext()) {
            ByteBuffer next = bufferIterator.next();
            next.rewind();
            filldst(next, prev);
            next.compact();
            prev = next;
        }

        // Read into last buffer
        readIntoBuffer(prev);

        // update the offset
        this.minposition += qtr;
        // reset our location
        setOffset(offset - qtr);
    }

    /**
     * Determine if there are bytes available to be read.
     * 
     * @return true if either buffer has data remaining or we have not reached the end of channel.
     */
    private boolean bytesAvailable() {
        return (!endofchannel || remaining() > 0);
    }

    /**
     * Calculate the remaining bytes available in buffers
     *
     * @return bytes remaining in buffers
     */
    private int remaining() {
        return buffers.stream().mapToInt(buffer -> buffer.remaining()).sum();
    }

    /**
     * Attempt to read data from the open channel into the buffer provided.
     * <p/>
     * After this call completes, we have either filled the buffer -or- have reached the end of data in the input
     * channel. The buffer will have its position set to 0, and limit set to the end of the data read, which may be
     * equal to the size of the buffer.
     * <p/>
     * Has the side effect of raising the endofchannel flag if we have exhausted the bytes in the input channel. Updates
     * the estimatedLength with the number of bytes read.
     * <p/>
     *
     * @param buf the destination buffer.
     * @return the number of bytes read into the buffer.
     */
    private int readIntoBuffer(final ByteBuffer buf) throws IOException {
        final int rem = buf.remaining();
        int read = 0;
        while ((read != -1) && (buf.remaining() > 0)) {
            read = this.in.read(buf);
        }
        this.endofchannel = (read == -1);
        // total amount read in case we hit EOS
        final int totalRead = rem - buf.remaining();
        this.estimatedLength += totalRead;
        buf.flip();
        return totalRead;
    }

    /**
     * {@inheritDoc}
     *
     * @see java.nio.channels.Channel#isOpen()
     */
    @Override
    public boolean isOpen() {
        return this.in.isOpen();
    }

    /**
     * Closes underlying Channel and releases buffers. Further calls to this instance will result in unspecified
     * behavior.
     * 
     * @see java.nio.channels.Channel#close()
     */
    @Override
    public void close() throws IOException {
        this.in.close();
        buffers.clear();
    }

    /**
     * {@inheritDoc}
     *
     * @see java.nio.channels.SeekableByteChannel#read(ByteBuffer)
     */
    @Override
    public int read(final ByteBuffer dst) throws IOException {
        if (!this.isOpen()) {
            throw new ClosedChannelException();
        }

        if (dst == null) {
            throw new IllegalArgumentException("Destination ByteBuffer cannot be null");
        }

        // we have nothing left to read and have consumed both buffers fully.
        if (endofchannel && (remaining() == 0)) {
            return -1;
        }

        // no more room in the target buffer, but we might have more to read.
        // - do we want to possibly throw an exception here?
        if (dst.remaining() == 0) {
            return 0;
        }

        final int maxWrite = dst.remaining();

        while (dst.hasRemaining() && bytesAvailable()) {
            realignBuffers();
            for (ByteBuffer b : buffers) {
                filldst(b, dst);
            }
        }
        final int bytesRead = maxWrite - dst.remaining();
        return (this.endofchannel && bytesRead == 0) ? -1 : bytesRead;
    }

    /*
     * Safely fill a destination buffer avoiding a buffer overflow if necessary. <p/> Copies byte from src to dest. The
     * number of bytes copied is the minimum of the amount of space remaining in the destination buffer
     */
    private void filldst(final ByteBuffer src, final ByteBuffer dst) {
        while (src.hasRemaining() && dst.hasRemaining()) {
            final int origLimit = src.limit();
            // avoid buffer overflow
            final int limit = (src.remaining() > dst.remaining()) ? (src.position() + dst.remaining()) : origLimit;
            src.limit(limit);
            dst.put(src);
            // set it back
            src.limit(origLimit);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @see java.nio.channels.SeekableByteChannel#position()
     */
    @Override
    public long position() throws IOException {
        return minposition + buffers.stream().mapToInt(buffer -> buffer.position()).sum();
    }

    /**
     * {@inheritDoc}
     *
     * @see java.nio.channels.SeekableByteChannel#position(long)
     */
    @Override
    public SeekableByteChannel position(final long newPosition) throws IOException {
        // if data hasn't been read in, we'll have to do it below and see if it's available
        if (this.endofchannel && (newPosition >= this.estimatedLength)) {
            throw new EOFException("Position is beyond EOF");
        }

        long tgtPosition = newPosition - this.minposition;
        // see if we can move there
        if (tgtPosition < 0) {
            throw new IllegalStateException("Cannot move back this far in the stream. Minimum " + this.minposition);
        }

        setPosition = newPosition;
        while (!setOffset(tgtPosition) && !this.endofchannel) {
            realignBuffers();
            tgtPosition = newPosition - this.minposition;
        }
        if (newPosition > this.estimatedLength) {
            throw new EOFException("Position is beyond EOF");
        }
        return this;
    }

    /**
     * attempts to set position to specified offset in underlying buffers
     */
    private boolean setOffset(final long tgtOffset) {
        int modOffset = 0;
        boolean set = false;
        for (ByteBuffer buffer : buffers) {
            if (set) {
                buffer.position(0);
            } else {
                if (tgtOffset < modOffset + buffer.limit()) {
                    buffer.position((int) (tgtOffset - modOffset));
                    set = true;
                } else {
                    buffer.position(buffer.limit());
                }

                // increment modOffset
                modOffset += buffer.limit();
            }
        }

        return set;
    }

    /**
     * Returns the minimum position we can go to in the backing Channel. This is based on the current window mapped into
     * the backing store.
     * 
     * @return the minimum allowed position in the channel
     */
    public long getMinPosition() {
        return this.minposition;
    }

    /**
     * Returns the maximum position we can go to in the backing Channel. This is based on the current window mapped into
     * the backing store.
     * 
     * @return the maximum allowed position in the channel
     */
    public long getMaxPosition() {
        return this.minposition + buffers.stream().mapToInt(buffer -> buffer.limit()).sum();
    }

    /**
     * A potential size for the underlying Channel. This value can change as we read additional data into the buffer.
     * Eventually, this number should reflect the true size assuming no underlying exceptions.
     * 
     * @return an estimated length of the underlying channel
     */
    @Override
    public long size() throws IOException {
        return this.estimatedLength;
    }

    /**
     * This is a read only implementation.
     * 
     * @param size The truncation size.
     * @return throws ex
     * @throws IOException If there is some I/O problem.
     * @throws UnsupportedOperationException If the operation is not supported.
     * @see java.nio.channels.SeekableByteChannel#truncate(long)
     */
    @Override
    public SeekableByteChannel truncate(final long size) throws IOException {
        throw new UnsupportedOperationException("This implementation does not allow mutations to the underlying channel");
    }

    /**
     * Unsupported in this implementation. This could be modified in the future to allow in memory writes.
     * 
     * @param source The bytes to write.
     * @return throws ex
     * @throws IOException If there is some I/O problem.
     * @throws UnsupportedOperationException If the operation is not supported.
     * @see java.nio.channels.SeekableByteChannel#write(ByteBuffer)
     */
    @Override
    public int write(final ByteBuffer source) throws IOException {
        throw new UnsupportedOperationException("This is a readonly implementation");
    }
}
