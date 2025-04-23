package emissary.util.grpcpool.exceptions;

/**
 * Used when the service doesn't currently have enough resources to perform the request. The connection is good, just
 * try again later.
 */
public class ServiceNotReadyException extends ServiceNotAvailableException {

    private static final long serialVersionUID = 4069545447000517389L;

    public ServiceNotReadyException(String errorMessage) {
        super(errorMessage);
    }

    public ServiceNotReadyException(String errorMessage, Throwable err) {
        super(errorMessage, err);
    }
}
