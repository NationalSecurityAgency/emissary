package emissary.parser;

import java.io.Serial;

/**
 * Exception thrown when parsing needs a new block of data
 */
public class ParserNextChunkException extends ParserException {

    /**
     * provide uid for serialization
     */
    @Serial
    private static final long serialVersionUID = -8521308509244964L;

    /**
     * An exception with a message
     * 
     * @param msg the message
     */
    public ParserNextChunkException(String msg) {
        super(msg);
    }
}
