package emissary.util.magic;

/**
 * Exception used within the magic package - external clients never encounter this exception
 */

public class ParseException extends Exception {
    private static final long serialVersionUID = -58614520195826109L;

    public ParseException(String message) {
        super(message);
    }
}
