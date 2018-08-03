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
    private final static Logger logger = LoggerFactory.getLogger(NIOSessionParser.class);

    // the input channel we will read from
    protected SeekableByteChannel channel;

    // Track where we are in the overall file and in the current chunk
    protected int chunkStart = 0;
    protected byte[] data = null;

    // Max chunk to read at a time, we will never be able to read a file with a session larger than this.
    protected static final int MAP_MAX_DEFAULT = 40 * 1024 * 1024; // 40Mb
    protected int chunkSize = MAP_MAX_DEFAULT;

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
    public int getChunkSize() {
        return chunkSize;
    }

    /**
     * Set the chunking size
     */
    public void setChunkSize(int value) {
        if (value > 0) {
            chunkSize = value;
        }
    }

    /**
     * Read more data, starting where the last read left off. Read in <code>chunksize</code> bytes.
     *
     * @param data the byte array to (re)load or null if one should be created
     * @return the byte array of data
     */
    protected byte[] loadNextRegion(byte[] data) {
        logger.debug("loadOrFillNextRegion: data.length = {}, chunkSize = {}, chunkStart = {}",
                data == null ? -1 : data.length, chunkSize, chunkStart);

        // Position before checking remaining
        try {
            channel.position(chunkStart);
        } catch (IOException iox) {
            logger.error("Unable to seek to {}", chunkStart, iox);
            return null;
        }

        // Optionally create the array or recreate if old is too small
        if (data == null || data.length < chunkSize) {
            data = new byte[chunkSize];
        }

        final ByteBuffer b = ByteBuffer.wrap(data);
        b.limit(chunkSize);

        try {
            readFully(b);
        } catch (EOFException ex) {
            logger.warn("End of channel reached at {} instead of expected {}", chunkSize - b.remaining(), chunkSize, ex);
        } catch (IOException ex) {
            logger.error("Count not read {} bytes into array", chunkSize, ex);
            return null;
        } finally {
            logger.debug("After loadOrFillNextRegion, buffer state = {}, data length = {}", b, data.length);
            int amountRead = chunkSize - b.remaining();
            if (amountRead < data.length) {
                data = Arrays.copyOfRange(data, 0, amountRead);
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
