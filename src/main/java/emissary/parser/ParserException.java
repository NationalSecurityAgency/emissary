package emissary.parser;

/**
 * Exceptions thrown during input parsing
 */
public class ParserException extends emissary.core.EmissaryException {

    // Serializable
    static final long serialVersionUID = 2829172862630282553L;

    /**
     * An exception with a message
     * 
     * @param msg the message
     */
    public ParserException(String msg) {
        super(msg);
    }

    /**
     * An exception with a message and a cause
     * 
     * @param msg the message
     * @param cause the cause of the exception
     */
    public ParserException(String msg, Throwable cause) {
        super(msg, cause);
    }

}
