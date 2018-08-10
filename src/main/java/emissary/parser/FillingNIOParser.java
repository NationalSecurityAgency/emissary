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
    protected byte[] nextChunkOrDie(byte[] data) throws ParserEOFException {
        chunkStart += sessionStart;
        byte[] b = loadNextRegion(data);

        if (b == null) {
            setFullyParsed(true);
            throw new ParserEOFException("Sessions completed");
        }

        logger.debug("Got new data at " + chunkStart + " length " + b.length);
        sessionStart = 0;
        return b;
    }
}
