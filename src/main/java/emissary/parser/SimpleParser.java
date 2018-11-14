package emissary.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A very simple minded parser implementation that assumes each input set of data bytes is one session This parser has
 * no idea about headers and footers, just the basic session, and not much of an idea about that.
 * 
 * This class is deprecated, use {@link SimpleNioParser}
 */
@Deprecated
public class SimpleParser extends SessionParser {
    // Logger
    @SuppressWarnings("unused")
    private Logger logger = LoggerFactory.getLogger(SimpleParser.class);

    protected static final Logger slogger = LoggerFactory.getLogger(SimpleParser.class);

    // The input data to parse
    protected byte[] data;

    // List of InputSession objects that have been parsed
    protected List<InputSession> sessions = null;

    /**
     * Create a session parser on the data
     */
    public SimpleParser(byte[] data) {
        this.data = data;
        sessions = new ArrayList<>();

        // Set logger for actual implementing class
        logger = LoggerFactory.getLogger(this.getClass());
    }

    /**
     * Creates a hashtable of elements from the session: header, footer, body, and other meta data values extracted from the
     * session data.
     * 
     * @param session The session to be decomposed into separate elements.
     * @return A map of session elements.
     */
    protected DecomposedSession decomposeSession(InputSession session) {
        DecomposedSession d = new DecomposedSession();
        if (session != null) {
            List<PositionRecord> header = session.getHeader();
            if (header != null && header.size() > 0) {
                d.setHeader(DataByteArraySlicer.makeDataSlice(data, header));
            }

            List<PositionRecord> footer = session.getFooter();
            if (footer != null && footer.size() > 0) {
                d.setFooter(DataByteArraySlicer.makeDataSlice(data, footer));
            }

            List<PositionRecord> sdata = session.getData();
            int length = -1;
            if (sdata != null && sdata.size() > 0) {
                byte[] databytes = DataByteArraySlicer.makeDataSlice(data, sdata);
                d.setData(databytes);
                length = databytes.length;
            }

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
                length = (int) session.getLength();
            }
            d.addMetaData(ORIG_DOC_SIZE_KEY, Integer.toString(length));
        }
        return d;
    }

    /**
     * Turn the metadata PositionRecord elements into real data
     * 
     * @param raw map of PositionRecord objects
     * @return map of metadata
     */
    protected Map<String, String> cookMetaRecords(Map<String, Object> raw) {

        Map<String, String> cooked = new HashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String key = entry.getKey();
            Object tmp = entry.getValue();
            if (tmp != null) {
                String value = null;
                if (tmp instanceof PositionRecord) {
                    value = new String(DataByteArraySlicer.makeDataSlice(data, (PositionRecord) tmp)).trim();
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
     */
    @Override
    public String toString() {
        return SimpleParser.class.getName() + " isa" + this.getClass().getName();
    }

    /**
     * Creates a hashtable of elements from the session: header, footer, body, and other meta data values extracted from the
     * session data for the next session in the data. This Simple base implementation only treats the whole file as one
     * session
     * 
     * @return the DecomposedSession
     * @throws ParserException when parsing cannot be completed
     * @throws ParserEOFException when the last session has already been parsed
     */
    @Override
    public DecomposedSession getNextSession() throws ParserException, ParserEOFException {
        if (isFullyParsed()) {
            throw new ParserEOFException("Sessions completed");
        }

        InputSession i = new InputSession(new PositionRecord(0, data.length), new PositionRecord(0, data.length));
        i.setValid(true);
        setFullyParsed(true);

        return decomposeSession(i);
    }
}
