package emissary.parser;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.Arrays;

/**
 * Provide a basic NIO-based session parser that reads data in chunks from the underlying channel. A chunk might have
 * zero or more complete sessions within it. The chunk buffer will begin at minChunkSize, but grow as large as
 * maxChunkSize in order to accomodate a complete session. Sessions larger than maxChunkSize will lead to
 * ParserExceptions
 */
public abstract class NIOSessionParser extends SessionParser {
    // Logger
    private final static Logger logger = LoggerFactory.getLogger(NIOSessionParser.class);
    protected static final int MIN_CHUNK_SIZE_DEFAULT = 2 * 1024 * 1024; // 2Mb
    protected static final int MAX_CHUNK_SIZE_DEFAULT = 40 * 1024 * 1024; // 40Mb

    /** The data source for this parser */
    protected SeekableByteChannel channel;

    /** The start position of the current chunk relative to the data source */
    protected int chunkStart = 0;

    /** The current chunk buffer */
    @Nullable
    protected byte[] data = null;

    /** The current write position for the current chunk buffer */
    protected int writeOffset = 0;

    /** Min chunk buffer size. */
    protected int minChunkSize = MIN_CHUNK_SIZE_DEFAULT;

    /** Max chunk to read at a time, we will never be able to read a file with a session larger than this. */
    protected int maxChunkSize = MAX_CHUNK_SIZE_DEFAULT;

    /** When we grow the chunk buffer to accomodate additional data, we will grow the buffer by this increment */
    protected int chunkAllocationIncrement = (10 * 1024 * 1024) + 100;

    /**
     * Create the parser with the supplied data source
     * 
     * @param channel the source of data
     */
    public NIOSessionParser(SeekableByteChannel channel) {
        this.channel = channel;
    }

    /**
     * Get the chunking size
     */
    public int getMaxChunkSize() {
        return maxChunkSize;
    }

    /**
     * Set the chunking size
     */
    public void setMaxChunkSize(int value) {
        if (value > 0) {
            maxChunkSize = value;
        }
    }

    /**
     * Read more data, starting where the last read left off. Read in <code>chunksize</code> bytes.
     *
     * @param data the byte array to (re)load or null if one should be created
     * @return the byte array of data
     * @throws ParserException in cases where a new array can't be read.
     */
    protected byte[] loadNextRegion(@Nullable byte[] data) throws ParserException {
        logger.debug("loadNextRegion(): data.length = {}, maxChunkSize = {}, chunkStart = {}, writeOffset = {}",
                data == null ? -1 : data.length, maxChunkSize, chunkStart, writeOffset);

        if (!channel.isOpen()) {
            throw new ParserEOFException("Channel is closed, likely completely consumed");
        }

        // Optionally create the array or recreate if old is too small
        if (data == null) {
            data = new byte[minChunkSize];
        }

        if (writeOffset >= data.length) {
            // grow the byte buffer to accomodate more data
            int newSize = data.length + chunkAllocationIncrement;
            if (newSize > maxChunkSize) {
                newSize = maxChunkSize;
            }

            if (data.length >= maxChunkSize) {
                // if the byte array is already maxChunkSize or larger, there isn't anything more we can do
                throw new ParserException("buffer size required to read session " + chunkStart + " is larger than maxChunkSize " + maxChunkSize);
            }

            byte[] newData = new byte[newSize];
            System.arraycopy(data, 0, newData, 0, data.length);
            data = newData;
        }

        final ByteBuffer b = ByteBuffer.wrap(data);
        b.position(writeOffset);
        b.limit(data.length);

        try {
            while (b.hasRemaining()) {
                if (channel.read(b) == -1) {
                    channel.close();
                    logger.warn("Closing channel. End of channel reached at {} instead of expected {}", data.length - b.remaining(), data.length);
                    break;
                }
            }
        } catch (IOException ex) {
            throw new ParserException("Exception reading from channel", ex);
        }

        writeOffset = data.length - b.remaining();
        if (writeOffset < data.length) {
            logger.debug("trimming byte[] from {} to size {}", data.length, writeOffset);
            data = Arrays.copyOfRange(data, 0, writeOffset);
        }

        return data;
    }
}
