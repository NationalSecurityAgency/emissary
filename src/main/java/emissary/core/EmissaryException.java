package emissary.core;

/**
 * This is the top of the exception hierarchy for Emissary All emissary exceptions extend from here
 */
public class EmissaryException extends Exception {

    // Serializable
    static final long serialVersionUID = 4180091209631811809L;

    /**
     * Create an emissary exception
     * 
     * @param message a string to go along with the exception
     */
    public EmissaryException(String message) {
        super(message);
    }

    /**
     * Create an emissary exception
     * 
     * @param message a string to go along with the exception
     * @param cause the wrapped exception
     */
    public EmissaryException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Create an emissary exception
     * 
     * @param cause the wrapped exception
     */
    public EmissaryException(Throwable cause) {
        super("Exception: " + cause.getMessage(), cause);
    }
}
