package emissary.core;

/**
 * This exception is used for resource management within the emissary framework
 */
public class ResourceException extends EmissaryException {

    /**
     * provide uid for serialization
     */
    private static final long serialVersionUID = 4707276767249682961L;

    /**
     * Create an emissary exception
     * 
     * @param message a string to go along with the exception
     */
    public ResourceException(String message) {
        super(message);
    }

    /**
     * Create an emissary exception
     * 
     * @param message a string to go along with the exception
     * @param cause the wrapped exception
     */
    public ResourceException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Create an emissary exception
     * 
     * @param cause the wrapped exception
     */
    public ResourceException(Throwable cause) {
        super("Exception: " + cause.getMessage(), cause);
    }
}
