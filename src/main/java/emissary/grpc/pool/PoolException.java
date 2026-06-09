package emissary.grpc.pool;

import java.io.Serial;

/**
 * Exception type for failures with handling the gRPC connection pool, such as failed borrows.
 */
public class PoolException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1495483102825486040L;

    public PoolException(String errorMessage) {
        super(errorMessage);
    }

    public PoolException(String errorMessage, Throwable err) {
        super(errorMessage, err);
    }
}
