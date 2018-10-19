package emissary.parser;

/**
 * Base for all session parsers returned from ParserFactory
 */
public abstract class SessionParser {
    public static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 10;
    public static final long MAX_ARRAY_SIZE_LONG = MAX_ARRAY_SIZE;
    public static String ORIG_DOC_SIZE_KEY = "OrigDocumentSize";

    protected boolean fullyParsed = false;

    /**
     * Indicates if the data has been fully parsed or not.
     * 
     * @return boolean, indicating parsing status.
     */
    public boolean isFullyParsed() {
        return fullyParsed;
    }

    /**
     * Set the fully parsed indicator
     * 
     * @param fullyParsed the new value
     */
    public void setFullyParsed(boolean fullyParsed) {
        this.fullyParsed = fullyParsed;
    }

    /**
     * Get session name or null if none can be provided
     * 
     * @param session the decomposed session to get the name for
     * @return session name or null if the parser cannot know
     */
    public String getSessionName(DecomposedSession session) {
        return null;
    }

    /**
     * Creates a hashtable of elements from the session: header, footer, body, and other meta data values extracted from the
     * session data for the next session in the data
     * 
     * @return the next session from the input
     */
    public abstract DecomposedSession getNextSession() throws ParserException;
}
