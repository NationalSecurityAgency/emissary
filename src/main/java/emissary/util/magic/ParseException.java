package emissary.util.magic;

/**
 * Exception used within the magic package - external clients never encounter this exception
 */

public class ParseException extends Exception {
    public ParseException(String message) {
        super(message);
    }
}
