package emissary.parser;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A very simple minded parser implementation that assumes each input channel is one session. This parser has no idea
 * about headers and footers, just the basic session and not much of an idea about that.
 */
public class SimpleNioParser extends NIOSessionParser {

    private final static Logger logger = LoggerFactory.getLogger(SimpleNioParser.class);

    protected int currentSessionIndex = 0;

    /**
     * Create a session parser on the data
     */
    public SimpleNioParser(SeekableByteChannel channel) throws ParserException {
        super(channel);
    }

    /**
     * Creates a hashtable of elements from the session: header, footer, body, and other meta data values extracted from the
     * session data.
     *
     * @param session The session to be decomposed into separate elements.
     * @return A map of session elements
     */
    protected DecomposedSession decomposeSession(InputSession session) throws ParserException {
        try {
            DecomposedSession d = new DecomposedSession();
            if (session != null) {

                d.setHeader(makeDataSlice(session.getHeader()));

                d.setFooter(makeDataSlice(session.getFooter()));

                d.setData(makeDataSlice(session.getData()));
                long length = d.getData() == null ? -1L : d.getData().length;

                // Cook the raw metadata and transfer to DecomposedSession
                Map<String, Object> md = session.getMetaData();
                if (md != null) {
                    Map<String, String> cooked = cookMetaRecords(md);
                    d.setMetaData(new HashMap<String, Object>()); // clear
                    for (Map.Entry<String, String> entry : cooked.entrySet()) {
                        d.addMetaData(entry.getKey(), entry.getValue());
                    }
                }

                // Use session length if no data length
                if (length < 0) {
                    length = session.getLength();
                }
                d.addMetaData(ORIG_DOC_SIZE_KEY, Long.toString(length));
            }
            return d;
        } catch (IOException ex) {
            throw new ParserException("Error while building DecomposedSession", ex);
        }
    }

    /**
     * Turn the metadata PositionRecord elements into real data
     *
     * @param raw map of PositionRecord objects
     * @return map of metadata
     */
    protected Map<String, String> cookMetaRecords(Map<String, Object> raw) throws IOException {

        Map<String, String> cooked = new HashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String key = entry.getKey();
            Object tmp = entry.getValue();
            if (tmp != null) {
                String value;
                if (tmp instanceof PositionRecord) {
                    value = new String(makeDataSlice((PositionRecord) tmp)).trim();
                } else {
                    value = tmp.toString();
                }
                String name = renameMetadataRecord(key);
                cooked.put(name, value);
            }
        }
        return cooked;
    }

    /**
     * Allow subclasses to arbitrarily rename metadata fields This is a do nothing function in the base class.
     *
     * @param s the name of the field to consider renaming
     * @return the renamed field or the original name if no change
     */
    protected String renameMetadataRecord(String s) {
        return s;
    }

    /**
     * Possible help to debug this factory mess
     * 
     * @return string representation
     */
    @Override
    public String toString() {
        return SimpleNioParser.class.getName() + " isa" + this.getClass().getName();
    }

    /**
     * Creates a hashtable of elements from the session: header, footer, body, and other meta data values extracted from the
     * session data for the next session in the data. This Simple base implementation only treats the whole file as one
     * session
     * 
     * @return next session
     */
    @Override
    public DecomposedSession getNextSession() throws ParserException, ParserEOFException {
        try {
            if (isFullyParsed()) {
                throw new ParserEOFException("Past end of data");
            }

            long csize = channel.size();

            InputSession i = new InputSession(new PositionRecord(0, csize), // overall record
                    new PositionRecord(0, csize)); // data record
            i.setValid(true);
            setFullyParsed(true);
            return decomposeSession(i);
        } catch (IOException ex) {
            throw new ParserException("Exception occurred reading channel", ex);
        }
    }

    /**
     * Slice data from a buffer based on a single position record
     *
     * @param r the position record indicating absolute offsets
     */
    byte[] makeDataSlice(PositionRecord r) throws IOException {
        if (r.getLength() > MAX_ARRAY_SIZE_LONG) {
            throw new IllegalStateException("Implementation currently only handles up to Intger.MAX_VALUE lengths");
        }
        int len = (int) r.getLength();
        ByteBuffer n = ByteBuffer.allocate(len);

        try {
            channel.position(r.getPosition());
            while (n.hasRemaining()) {
                if (channel.read(n) == -1) {
                    channel.close();
                    break;
                }
            }
        } catch (BufferUnderflowException ex) {
            logger.warn("Underflow getting {} bytes at {}", n.capacity(), r.getPosition());
        }
        return n.array();
    }

    /**
     * Slice data from a buffer based on a single position record
     *
     * @param records the list of position records indicating absolute offsets
     */
    byte[] makeDataSlice(List<PositionRecord> records) throws IOException {
        if (records == null || records.isEmpty()) {
            return null;
        }
        if (records.size() == 1) {
            return makeDataSlice(records.get(0));
        }

        int total = 0;
        for (PositionRecord r : records) {
            total += (int) r.getLength();
            if (total > MAX_ARRAY_SIZE || total < 0) {
                throw new IllegalStateException("This implementation cannot create data larger than " + MAX_ARRAY_SIZE);
            }
        }

        ByteBuffer n = ByteBuffer.allocate(total);
        int limit = 0;
        for (PositionRecord r : records) {
            channel.position(r.getPosition());
            limit += (int) r.getLength();
            n.limit(limit);
            while (n.hasRemaining()) {
                if (channel.read(n) == -1) {
                    channel.close();
                    break;
                }
            }
        }

        return n.array();
    }

}
