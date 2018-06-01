package emissary.parser;

/**
 * Exception thrown when parsing has fully completed
 */
public class ParserEOFException extends ParserException {

    static final long serialVersionUID = -5773911956597083703L;

    /**
     * An exception with a message
     * 
     * @param msg the message
     */
    public ParserEOFException(String msg) {
        super(msg);
    }
}
