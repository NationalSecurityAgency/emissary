package emissary.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.RandomAccessFile;
import java.nio.channels.SeekableByteChannel;

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
        if (writeOffset == 0) {
            // processing a new session, advance the chunkStart and reset sessionStart
            chunkStart += sessionStart;
            sessionStart = 0;
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
            }
            else {
                // end of file and the last session was complete.
                setFullyParsed(true);
                throw eof;
            }
        }
    }
}
