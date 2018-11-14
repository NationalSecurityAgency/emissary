package emissary.parser;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encapsulate the behavior necessary to slide a window through a channel and parse sessions from it. nextChunkOrDie
 * will load the next region.
 */
public abstract class FillingNIOParser extends NIOSessionParser {

    private final static Logger logger = LoggerFactory.getLogger(FillingNIOParser.class);

    /** position of the session start relative to the start of the current chunk */
    protected int sessionStart = 0;

    public FillingNIOParser(SeekableByteChannel channel) {
        super(channel);
    }

    @Deprecated
    public FillingNIOParser(RandomAccessFile raf) {
        this(raf.getChannel());
    }

    /**
     * Get and set all stats for loading the next chunk This is specified to the RAF parser families
     * 
     * @param data the allocated data block to fill or null
     * @return ref to the incoming block now filled or a new one if incoming was null or size was changed
     * @throws ParserEOFException when there is no more data
     */
    protected byte[] nextChunkOrDie(byte[] data) throws ParserException {
        if (sessionStart > 0) {
            // the sessionStart is somewhere other than the beginning of the buffer and
            // we've discovered that we need to load more data. Compact the buffer so that
            // the sessionStart is at the beginning of the buffer. chunkStart is incremented
            // by sessionStart, the write offset is updated.
            final ByteBuffer b = ByteBuffer.wrap(data);
            b.position(sessionStart);
            b.limit(writeOffset);
            b.compact(); // does not re-allocate the byte array, only manipulates the current buffer.

            chunkStart += sessionStart;
            sessionStart = 0;
            writeOffset = b.position();
            logger.debug("Compacted buffer: sessionStart/chunkStart/writeOffset = {}/{}/{}", sessionStart, chunkStart, writeOffset);
        }

        try {
            byte[] b = loadNextRegion(data);
            logger.debug("Got new data at {} length {}", chunkStart, b.length);
            return b;

        } catch (ParserEOFException eof) {
            if (writeOffset - sessionStart > 1) {
                // there's data left in the buffer that's more than a newline, meaning
                // there's data we were unable to parse, but there was no additional
                // data to read - so the session is truncated
                throw new ParserException("Unexpectedly malformed data at " + chunkStart);
            } else {
                // end of file and the last session was complete.
                setFullyParsed(true);
                throw eof;
            }
        }
    }
}
