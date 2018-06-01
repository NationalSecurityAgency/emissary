package emissary.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detailed session information as it is parsed This class just records offsets and length of various things using lists
 * of PositionRecord. If you want to actually produce the bytes of a session see SessionParser.decomposeSession and
 * emissary.parser.DecomposedSession
 *
 * No assertions are made about the order of the header, footer, and data sections within the original byte array, it
 * can be constructed from position records in any order, possibly overlapping and repeated, We only assert that the
 * sections asked for are not out of bounds with respect to the overall array boundaries.
 *
 * The validation scheme allows the overall bounds to be set at either the beginning or at the end of sesion parsing
 * without performance penalty either way.
 */
public class InputSession {

    // Logger
    private static final Logger logger = LoggerFactory.getLogger(InputSession.class);

    // Overall start/length of the session
    protected PositionRecord overall = null;

    // ordered list of PositionRecord for the header within the session
    protected List<PositionRecord> header = new ArrayList<>();

    // ordered list of PositionRecord for the footer within the session
    protected List<PositionRecord> footer = new ArrayList<>();

    // ordered list of PositionRecord for the data within the session
    protected List<PositionRecord> data = new ArrayList<>();

    // unordered collected metadata during parsing
    protected Map<String, Object> metaData = new HashMap<>();

    // Indicator of validness
    protected boolean valid = true;

    /**
     * Record details of an input session
     */
    public InputSession() {}

    /**
     * Record details of an input session
     * 
     * @param o the overall position record
     */
    public InputSession(PositionRecord o) {
        this.overall = o;
    }

    /**
     * Record details of an input session
     * 
     * @param o the overall position record
     * @param d position records for the data
     */
    public InputSession(PositionRecord o, List<PositionRecord> d) throws ParserException {
        this(o);
        addDataRecs(d);
    }

    /**
     * Record details of an input session
     * 
     * @param o the overall position record
     * @param d position record for the data
     */
    public InputSession(PositionRecord o, PositionRecord d) throws ParserException {
        this(o);
        addDataRec(d);
    }

    /**
     * Record details of an input session
     * 
     * @param o the overall position record
     * @param h position records for the header
     * @param f position records for the footer
     * @param d position records for the data
     */
    public InputSession(PositionRecord o, List<PositionRecord> h, List<PositionRecord> f, List<PositionRecord> d) throws ParserException {
        this(o, d);
        addHeaderRecs(h);
        addFooterRecs(f);
    }

    /**
     * Record details of an input session
     * 
     * @param o the overall position record
     * @param h position record for the header
     * @param f position record for the footer
     * @param d position record for the data
     * @param m map of collected metadata
     */
    public InputSession(PositionRecord o, List<PositionRecord> h, List<PositionRecord> f, List<PositionRecord> d, Map<String, Object> m)
            throws ParserException {
        this(o, h, f, d);
        addMetaData(m);
    }

    /**
     * Set the overall position record
     * 
     * @param rec the PositionRecord for the overall session range
     */
    public void setOverall(PositionRecord rec) throws ParserException {
        this.overall = rec;
        validateAll();
    }

    /**
     * Set the overall position record from the data
     * 
     * @param start for the position record
     * @param length for the position record
     */
    public void setOverall(int start, int length) throws ParserException {
        setOverall(new PositionRecord(start, length));
    }

    /**
     * Add a map of metadata
     * 
     * @param m map of String key and String or PositionRecord values
     */
    public void addMetaData(Map<String, Object> m) throws ParserException {
        for (Map.Entry<String, Object> entry : m.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            if (val instanceof PositionRecord) {
                validateRecord((PositionRecord) val);
                metaData.put(key, val);
            } else if (val instanceof String) {
                metaData.put(key, val);
            } else {
                logger.warn("Ignoring metadata record named " + key + " with type of " + val.getClass().getName()
                        + " - it is not a PositionRecord or a String");
            }
        }
    }

    /**
     * Set the session validity
     * 
     * @param b true if session is valid
     */
    public void setValid(boolean b) {
        this.valid = b;
    }

    /**
     * Get session validity
     * 
     * @return true if session is valid
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * Get overall start position
     * 
     * @return overall start
     */
    public long getStart() {
        if (overall != null) {
            return overall.getPosition();
        }
        return 0;
    }

    /**
     * Get overall length
     * 
     * @return overall length
     */
    public long getLength() {
        if (overall != null) {
            return overall.getLength();
        }
        return 0;
    }

    /**
     * Get overall position record for the session
     * 
     * @return overall position record
     */
    public PositionRecord getOverall() {
        return overall;
    }

    /**
     * Add and validate a list of header records
     * 
     * @param h list of PositionRecord
     * @throws ParserException when a record is out of bounds
     */
    public void addHeaderRecs(List<PositionRecord> h) throws ParserException {
        if (h != null && h.size() > 0) {
            validateList(h);
            header.addAll(h);
        }
    }

    /**
     * Add and validate a list of data records
     * 
     * @param d list of PositionRecord
     * @throws ParserException when a record is out of bounds
     */
    public void addDataRecs(List<PositionRecord> d) throws ParserException {
        if (d != null && d.size() > 0) {
            validateList(d);
            data.addAll(d);
        }
    }

    /**
     * Add and validate a list of footer records
     * 
     * @param f list of PositionRecord
     * @throws ParserException when a record is out of bounds
     */
    public void addFooterRecs(List<PositionRecord> f) throws ParserException {
        if (f != null && f.size() > 0) {
            validateList(f);
            footer.addAll(f);
        }
    }

    /**
     * Add a validate a header record
     * 
     * @param r the record to add
     * @throws ParserException when a record is out of bounds
     */
    public void addHeaderRec(PositionRecord r) throws ParserException {
        validateRecord(r);
        header.add(r);
    }

    /**
     * Create a position record and add it to the header
     * 
     * @param pos starting position
     * @param len length of data
     * @throws ParserException when out of bounds
     */
    public void addHeaderRec(int pos, int len) throws ParserException {
        addHeaderRec(new PositionRecord(pos, len));
    }

    /**
     * Add a validate a footer record
     * 
     * @param r the record to add
     * @throws ParserException when a record is out of bounds
     */
    public void addFooterRec(PositionRecord r) throws ParserException {
        validateRecord(r);
        footer.add(r);
    }

    /**
     * Create a position record and add it to the footer
     * 
     * @param pos starting position
     * @param len length of data
     * @throws ParserException when out of bounds
     */
    public void addFooterRec(int pos, int len) throws ParserException {
        addFooterRec(new PositionRecord(pos, len));
    }

    /**
     * Add a validate a data record
     * 
     * @param r the record to add
     * @throws ParserException when a record is out of bounds
     */
    public void addDataRec(PositionRecord r) throws ParserException {
        validateRecord(r);
        data.add(r);
    }

    /**
     * Create a position record and add it to the data
     * 
     * @param pos starting position
     * @param len length of data
     * @throws ParserException when out of bounds
     */
    public void addDataRec(int pos, int len) throws ParserException {
        addDataRec(new PositionRecord(pos, len));
    }

    /**
     * Add a metadata position record
     * 
     * @param name name of metadata item
     * @param r position record of data
     * @throws ParserException when out of bounds
     */
    public void addMetaDataRec(String name, PositionRecord r) throws ParserException {
        validateRecord(r);
        metaData.put(name, r);
    }

    /**
     * Add a metadata position record
     * 
     * @param name name of metadata item
     * @param rec string value of metadata item
     */
    public void addMetaDataRec(String name, String rec) {
        metaData.put(name, rec);
    }

    /**
     * Count of data position records
     * 
     * @return count
     */
    public int getDataCount() {
        return data.size();
    }

    /**
     * Count of footer position records
     * 
     * @return count
     */
    public int getFooterCount() {
        return footer.size();
    }

    /**
     * Count of header position records
     * 
     * @return count
     */
    public int getHeaderCount() {
        return header.size();
    }

    /**
     * Count of metadata records
     * 
     * @return count
     */
    public int getMetaDataCount() {
        return metaData.size();
    }

    /**
     * Get footer position records
     * 
     * @return list of PositionRecord
     */
    public List<PositionRecord> getFooter() {
        return footer;
    }

    /**
     * List header records
     * 
     * @return list of PositionRecord
     */
    public List<PositionRecord> getHeader() {
        return header;
    }

    /**
     * Get data position records
     * 
     * @return list of PositionRecord
     */
    public List<PositionRecord> getData() {
        return data;
    }

    /**
     * Return a map of metadata information. Some of the values will be String, others will be PositionRecord
     * 
     * @return metadata
     */
    public Map<String, Object> getMetaData() {
        return metaData;
    }

    /**
     * Info pump for debugging output
     * 
     * @return string representation
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Session is ").append((this.isValid() ? "" : "not")).append("valid").append("\n");
        sb.append("Session overall ").append(this.getOverall()).append("\n");
        sb.append("Header record ").append(this.getHeader()).append("\n");
        sb.append("Data record ").append(this.getData()).append("\n");
        sb.append("Footer record ").append(this.getFooter()).append("\n");
        Map<String, Object> m = this.getMetaData();
        sb.append("Metadata count ").append((m == null ? 0 : m.size())).append("\n");
        return sb.toString();
    }

    /**
     * Validation of one PositionRecord. Does nothing if overall bounds not yet set
     * 
     * @param r the record to check
     * @throws ParserException when out of bounds
     */
    protected void validateRecord(PositionRecord r) throws ParserException {
        if (overall != null && r != null && ((r.getPosition() < overall.getPosition()) || (r.getEnd() > overall.getEnd()))) {
            throw new ParserException("Position record " + r + " is out of bounds for the data " + overall);
        }
    }

    /**
     * Validate a list of PositionRecord. Does nothing if overall bounds not yet set
     * 
     * @param list the list of PositionRecord
     * @throws ParserException when out of bounds
     */
    protected void validateList(List<PositionRecord> list) throws ParserException {

        if (overall == null || list == null) {
            return;
        }

        for (PositionRecord p : list) {
            validateRecord(p);
        }
    }

    /**
     * Validate everything. Does nothing if overall bounds not yet set
     * 
     * @throws ParserException when out of bounds
     */
    protected void validateAll() throws ParserException {
        if (overall == null) {
            return;
        }
        validateList(header);
        validateList(footer);
        validateList(data);
        validateMetaData();
    }

    /**
     * Validate the PositionRecords in the metadata map Does nothing if overall bounds not yet set
     * 
     * @throws ParserException when out of bounds
     */
    protected void validateMetaData() throws ParserException {
        if (overall == null || metaData.isEmpty()) {
            return;
        }

        for (Object obj : metaData.values()) {
            if (obj instanceof PositionRecord) {
                validateRecord((PositionRecord) obj);
            }
        }
    }

}
