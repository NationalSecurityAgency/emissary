package emissary.util.grpc.exceptions;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.apache.commons.lang3.StringUtils;

/**
 * Encapsulates bad data errors related to an external service. These errors should be treated as unrecoverable and
 * should not be made due to temporal server state.
 */
public class ServiceException extends RuntimeException {

    private static final long serialVersionUID = 1371863576664142288L;

    static final String GRPC_ERROR_MSG_FMT = "Encountered gRPC runtime status error %s. %s.";

    public ServiceException(String errorMessage) {
        super(errorMessage);
    }

    public ServiceException(String errorMessage, Throwable err) {
        super(errorMessage, err);
    }

    public static void handleGrpcStatusRuntimeException(StatusRuntimeException e) {
        if (e == null) {
            throw new ServiceException("Service returned a null exception");
        }
        Status status = e.getStatus();
        if (status == null) {
            throw new ServiceException("Service returned a null status");
        }
        // code shouldn't ever be null, but we check for safety
        Status.Code code = status.getCode();
        if (code == null) {
            throw new ServiceException("Service returned a status with a null code");
        }

        switch (code) {
            case DEADLINE_EXCEEDED:
                throw new ServiceException(String.format(GRPC_ERROR_MSG_FMT, e.getMessage(),
                        "gRPC client connection has timed out"));
            case UNAVAILABLE: {
                // Likely server has gone down. Could be a crash or resources were scaled down
                String desc = status.getDescription();
                if (StringUtils.isNotEmpty(desc) && desc.contains("Network closed for unknown reason")) {
                    // So-called "poison pill" files have resulted in crashes for unknown reasons.
                    // Out of an abundance of caution, we consider these files as failures.
                    throw new ServiceException(String.format(GRPC_ERROR_MSG_FMT, e.getMessage(),
                            "It's possible service crashed due to a misbehaving file"));
                }
                // Otherwise, we indicate the server is not live
                throw new ServiceNotLiveException(String.format(GRPC_ERROR_MSG_FMT, e.getMessage(), "It's likely service crashed"));
            }
            case CANCELLED:
                throw new ServiceException(String.format(GRPC_ERROR_MSG_FMT, e.getMessage(), "It's likely a client side interrupt occurred"));
            case RESOURCE_EXHAUSTED:
                // Likely we've exceeded the maximum number of concurrent requests
                throw new ServiceNotReadyException(String.format(GRPC_ERROR_MSG_FMT, e.getMessage(),
                        "It's likely we've exceeded the maximum number of requests"));
            case INTERNAL:
                // Likely server killed itself due to OOM or other conditions
                throw new ServiceException(String.format(GRPC_ERROR_MSG_FMT, e.getMessage(),
                        "It's likely a gpu OOM error or other resource error has occurred"));
            default:
                throw new ServiceException(String.format(GRPC_ERROR_MSG_FMT, e.getMessage(),
                        "This is an unhandled code type. Please add it to the list of gRPC exceptions"));
        }
    }
}
