package emissary.config;

/**
 * This exceptionis used for tracking internal configuration errors
 */
public class ConfigSyntaxException extends emissary.core.EmissaryException {

    /**
     * provide uid for serialization
     */
    private static final long serialVersionUID = -6742020817447824759L;

    /**
     * Create an emissary exception
     * 
     * @param message a string to go along with the exception
     */
    public ConfigSyntaxException(final String message) {
        super(message);
    }

    /**
     * Create an emissary exception
     * 
     * @param message a string to go along with the exception
     * @param cause the wrapped exception
     */
    public ConfigSyntaxException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /**
     * Create an emissary exception
     * 
     * @param cause the wrapped exception
     */
    public ConfigSyntaxException(final Throwable cause) {
        super("Exception: " + cause.getMessage(), cause);
    }
}
