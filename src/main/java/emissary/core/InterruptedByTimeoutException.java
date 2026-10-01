package emissary.core;

import java.io.Serial;

public class InterruptedByTimeoutException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 9219052337699630060L;

    /**
     * Create an runtime exception
     *
     * @param message a string to go along with the exception
     */
    public InterruptedByTimeoutException(final String message) {
        super(message);
    }

    /**
     * Create an runtime exception
     *
     * @param message a string to go along with the exception
     * @param cause the wrapped exception
     */
    public InterruptedByTimeoutException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /**
     * Create an runtime exception
     *
     * @param cause the wrapped exception
     */
    public InterruptedByTimeoutException(final Throwable cause) {
        super("Exception: " + cause.getMessage(), cause);
    }
}
