package emissary.util.grpc.exceptions;

/**
 * Used when the server is down and not responding. The connection is most likely bad and needs to be replaced.
 */
public class ServiceNotLiveException extends ServiceNotAvailableException {

    private static final long serialVersionUID = 238125357403869467L;

    public ServiceNotLiveException(String errorMessage) {
        super(errorMessage);
    }

    public ServiceNotLiveException(String errorMessage, Throwable err) {
        super(errorMessage, err);
    }
}
