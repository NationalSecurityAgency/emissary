package emissary.parser;

import java.io.RandomAccessFile;
import java.nio.channels.SeekableByteChannel;

/**
 * Encapsulate the behavior necessary to slide a window through a channel and parse sessions from it.
 */
public abstract class FillingNIOParser extends NIOSessionParser {

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
        byte[] b = null;
        try {
            chunkStart += sessionStart;
            b = loadNextRegion(data);
        } catch (OutOfMemoryError oom) {
            // This can happen even there is plenty of memory due to
            // trying to read too large a chunk size from the underlying
            // java.io.RandomAccessFile. Try reading it in smaller chunks
            try {
                b = fillNextRegion(data);
            } catch (OutOfMemoryError oom2) {
                logger.error("Tried to fill next region but failed", oom2);
                throw oom2;
            }
        }

        if (b == null) {
            setFullyParsed(true);
            throw new ParserEOFException("Sessions completed");
        }

        logger.debug("Got new data at " + chunkStart + " length " + b.length);
        sessionStart = 0;
        return b;
    }
}
