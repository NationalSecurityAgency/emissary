package emissary.parser;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provide a basic NIO based session parser.
 */
public abstract class NIOSessionParser extends SessionParser {
    // Logger
    protected final static Logger logger = LoggerFactory.getLogger(NIOSessionParser.class);

    // the input channel
    protected SeekableByteChannel channel = null;

    // Track where we are in the overall file and in the current chunk
    protected long chunkStart = 0L;
    protected byte[] data = null;

    // Max chunk to read at a time
    protected static final long MAP_MAX_DEFAULT = 40L * 1024L * 1024L; // 40Mb
    protected long MAP_MAX = MAP_MAX_DEFAULT;

    /**
     * Create the parser with the supplied data source
     * 
     * @param raf the source of data
     */
    @Deprecated
    public NIOSessionParser(RandomAccessFile raf) {
        this(raf.getChannel());
    }

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
    public long getMapMax() {
        return MAP_MAX;
    }

    /**
     * Set the chunking size
     */
    public void setMapMax(long value) {
        if (value > 0) {
            MAP_MAX = value;
        }
    }

    /**
     * Grab the next MAP_MAX bytes starting where the last session left off
     * 
     * @param data the byte array to (re)load or null if one should be created
     * @return the byte array of data
     */
    protected byte[] loadNextRegion(byte[] data) {
        return loadOrFillNextRegion(data, -1);
    }

    /**
     * Grab the next MAP_MAX bytes starting where the last session left off but read in only 10k segments
     * 
     * @param data the byte array to (re)load or null if one should be created
     * @return the byte array of data
     */
    protected byte[] fillNextRegion(byte[] data) {
        return loadOrFillNextRegion(data, 10240);
    }

    /**
     * Grab the next MAP_MAX bytes starting where the last session left off but read in only blocksize segments or read
     * fully if blocksize <= 0
     * 
     * @param data the byte array to (re)load or null if one should be created
     * @param blocksize how much data to read at once
     * @return the byte array of data
     */
    protected byte[] loadOrFillNextRegion(byte[] data, int blocksize) {
        long chunkSize = MAP_MAX;

        // Position before checking remaining
        try {
            // Leave the choice of if the position is available to the channel, not based on size
            channel.position(chunkStart);
        } catch (IOException iox) {
            logger.error("Unable to seek to {}", chunkStart, iox);
            return null;
        }

        logger.debug("Positioning stream to {} and grabbing next {} bytes", chunkStart, chunkSize);

        // Optionally create the array or recreate if old is wrong size
        if (data == null || data.length != (int) chunkSize) {
            data = new byte[(int) chunkSize];
        }

        ByteBuffer b = ByteBuffer.wrap(data);
        try {
            int readCount = (blocksize <= 0 || blocksize > (data.length)) ? data.length : blocksize;
            b.limit(readCount);
            readFully(b);
        } catch (EOFException ex) {
            logger.error("Could not fill array from input channel with {}", chunkSize, ex);
        } catch (IOException ex) {
            logger.error("Count not read {} bytes into array", chunkSize, ex);
            return null;
        } finally {
            // Trim data to buffers remaining size
            if (b.remaining() > 0) {
                data = Arrays.copyOfRange(data, 0, (data.length - b.remaining()));
            }
        }

        return data;
    }

    protected void readFully(ByteBuffer b) throws IOException {
        while (b.hasRemaining()) {
            if (channel.read(b) == -1) {
                throw new EOFException();
            }
        }
    }
}
