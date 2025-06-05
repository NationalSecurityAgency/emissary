package emissary.grpc.exceptions;

/**
 * Exception type for failures with handling the gRPC connection pool, such as failed borrows.
 */
public class GrpcPoolException extends RuntimeException {

    private static final long serialVersionUID = 1495483102825486040L;

    public GrpcPoolException(String errorMessage) {
        super(errorMessage);
    }

    public GrpcPoolException(String errorMessage, Throwable err) {
        super(errorMessage, err);
    }
}
