package emissary.grpc.exceptions;

/**
 * Parent exception for failures based on the current state of the service. E.g. the service is down, the service has
 * run out of a hardware resource, the service is misconfigured, etc.
 */
public class ServiceNotAvailableException extends RuntimeException {

    private static final long serialVersionUID = 5692975291196373963L;

    public ServiceNotAvailableException(String errorMessage) {
        super(errorMessage);
    }

    public ServiceNotAvailableException(String errorMessage, Throwable err) {
        super(errorMessage, err);
    }
}
