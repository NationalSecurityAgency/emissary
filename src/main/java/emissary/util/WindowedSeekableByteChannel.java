package emissary.util;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger logger = LoggerFactory.getLogger(WindowedSeekableByteChannel.class);

    /**
     * The input source
     */
    private final ReadableByteChannel in;

    /**
     * estimated length. We have to estimate because we are buffering into a window and may not be at the end. We read ahead
     * to keep buffers full, but there can be additional data.
     */
    long estimatedLength;

    /**
     * The earliest position we can move to. Essentially, position of the underlying Channel that is at position 0 of buff1
     */
    long minposition;

    /* Maximum amount of data allowed in memory */
    // long maxwindow;

    /** flag if we've reached the end of the underlying channel */
    private boolean endofchannel;

    /**
     * Internal buffers for windowed content
     */
    private ByteBuffer buff1;
    private ByteBuffer buff2;

    /**
     * Creates a new instance and populates buffers with data.
     */
    public WindowedSeekableByteChannel(final ReadableByteChannel in, final int buffsize) throws IOException {

        logger.debug("WindowSeekableByteChannel created with buffer size = {}", buffsize);

        if ((in == null) || !in.isOpen()) {
            throw new IllegalArgumentException("Channel must be open and not null:");
        }

        this.in = in;
        int capacity = buffsize / 2;
        if ((buffsize % 2) == 1) {
            capacity++;
        }

        this.buff1 = ByteBuffer.allocate(capacity);
        readIntoBuffer(this.buff1);
        // only fill buff2 if there's more to read. otherwise save heap
        if (!this.endofchannel) {
            this.buff2 = ByteBuffer.allocate(capacity);
            readIntoBuffer(this.buff2);
        } else {
            this.buff2 = ByteBuffer.allocate(0);
        }
    }

    /**
     * If necessary, will move data in the window to make room for additional data from the channel.
     */
    private void realignBuffers() throws IOException {
        logger.debug("realignBuffers() called: buf1 = {}, buf2 = {}", buff1, buff2);

        final int qtr = this.buff1.capacity() / 2;
        if (this.endofchannel || (this.buff2.remaining() > qtr)) {
            logger.debug("after early return from realignBuffers(): buf1 = {}, buf2 = {}", buff1, buff2);
            return;
        }
        // keep track of our position
        final int offset = this.buff1.position() + this.buff2.position();
        this.buff1.position(qtr);
        // push them forward
        this.buff1.compact();

        // read from the beginning of the buffer
        this.buff2.rewind();

        logger.debug("realignBuffers() called prior to fillDst: buf1 = {}, buf2 = {}", buff1, buff2);

        filldst(this.buff2, this.buff1);
        // chuck the bytes read into buff1

        logger.debug("realignBuffers() called prior prior to buff2 compact: buf1 = {}, buf2 = {}", buff1, buff2);

        this.buff2.compact();

        logger.debug("realignBuffers() called prior to readIntoBuffer: buf1 = {}, buf2 = {}", buff1, buff2);
        readIntoBuffer(this.buff2);
        // update the offset
        this.minposition += qtr;
        // reset our location
        setOffset(offset - qtr);

        logger.debug("after realignBuffers(): buf1 = {}, buf2 = {}", buff1, buff2);

    }

    /**
     * Determine if there are bytes available to be read.
     * 
     * @return true if either buffer has data remaining or we have not reached the end of channel.
     */
    private boolean bytesAvailable() {
        return this.buff1.remaining() > 0 || this.buff2.remaining() > 0 || !this.endofchannel;
    }

    /**
     * Attempt to read data from the open channel into the buffer provided.
     * <p>
     * After this call completes, we have either filled the buffer -or- have reached the end of data in the input channel.
     * The buffer will have its position set to 0, and limit set to the end of the data read, which may be equal to the size
     * of the buffer.
     * <p>
     * Has the side effect of raising the endofchannel flag if we have exhausted the bytes in the input channel. Updates the
     * estimatedLength with the number of bytes read.
     *
     * @param buf the destination buffer.
     * @return the number of bytes read into the buffer.
     */
    private int readIntoBuffer(final ByteBuffer buf) throws IOException {
        logger.debug("readIntoBuffer() called: {}", buf);

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
     * Closes underlying Channel and releases buffers. Further calls to this instance will result in unspecified behavior.
     * 
     * @see java.nio.channels.Channel#close()
     */
    @Override
    public void close() throws IOException {
        this.in.close();
        this.buff1 = null;
        this.buff2 = null;
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
        if (this.endofchannel && (this.buff1.remaining() + this.buff2.remaining() == 0)) {
            return -1;
        }

        // no more room in the target buffer, but we might have more to read.
        // - do we want to possibly throw an exception here?
        if (dst.remaining() == 0) {
            return 0;
        }

        final int maxWrite = dst.remaining();

        while (dst.hasRemaining() && bytesAvailable()) {
            logger.debug("filling buffers");
            realignBuffers();
            filldst(this.buff1, dst);
            filldst(this.buff2, dst);
        }
        final int bytesRead = maxWrite - dst.remaining();
        return (this.endofchannel && bytesRead == 0) ? -1 : bytesRead;
    }

    /*
     * Safely fill a destination buffer avoiding a buffer overflow if necessary. <p> Copies byte from src to dest. The
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
        return this.minposition + this.buff1.position() + this.buff2.position();
    }

    /**
     * {@inheritDoc}
     *
     * @see java.nio.channels.SeekableByteChannel#position(long)
     */
    @Override
    public SeekableByteChannel position(final long newPosition) throws IOException {
        // if data hasn't been read in, we'll have to do it below and see if it's available
        if (this.endofchannel && (newPosition > this.estimatedLength)) {
            throw new EOFException("Position is beyond EOF");
        }

        long tgtPosition = newPosition - this.minposition;
        // see if we can move there
        if (tgtPosition < 0) {
            throw new IllegalStateException("Cannot move to " + newPosition + " in the stream. Minimum position is " + this.minposition);
        }

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
        logger.debug("setOffset() called tgtOffset = {}, buff1 = {}, buff2 = {}", tgtOffset, buff1, buff2);

        if (tgtOffset <= this.buff1.limit()) {
            this.buff1.position((int) tgtOffset);
            this.buff2.position(0);
        } else if (tgtOffset <= (this.buff1.limit() + this.buff2.limit())) {
            this.buff1.position(this.buff1.limit());
            this.buff2.position((int) (tgtOffset - this.buff1.capacity()));
        } else {
            this.buff1.position(this.buff1.limit());
            this.buff2.position(this.buff2.limit());
            return false;
        }
        return true;
    }

    /**
     * Returns the minimum position we can go to in the backing Channel. This is based on the current window mapped into the
     * backing store.
     * 
     * @return the minimum allowed position in the channel
     */
    public long getMinPosition() {
        return this.minposition;
    }

    /**
     * Returns the maximum position we can go to in the backing Channel. This is based on the current window mapped into the
     * backing store.
     * 
     * @return the maximum allowed position in the channel
     */
    public long getMaxPosition() {
        return this.minposition + this.buff1.limit() + this.buff2.limit();
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
