package emissary.core;

/**
 * This is the top of the exception hierarchy for Emissary All emissary exceptions extend from here
 */
public class EmissaryRuntimeException extends RuntimeException {

    // Serializable
    private static final long serialVersionUID = -1590114207784953305L;

    /**
     * Create an emissary exception
     *
     * @param message a string to go along with the exception
     */
    public EmissaryRuntimeException(final String message) {
        super(message);
    }

    /**
     * Create an emissary exception
     *
     * @param message a string to go along with the exception
     * @param cause the wrapped exception
     */
    public EmissaryRuntimeException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /**
     * Create an emissary exception
     *
     * @param cause the wrapped exception
     */
    public EmissaryRuntimeException(final Throwable cause) {
        super("Exception: " + cause.getMessage(), cause);
    }
}
