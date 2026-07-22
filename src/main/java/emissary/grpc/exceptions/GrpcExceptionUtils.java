package emissary.grpc.exceptions;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public class GrpcExceptionUtils {
    private GrpcExceptionUtils() {}

    /**
     * Adds additional context to exception messages. If a throwable is not an instance of a {@link RuntimeException},
     * returns it wrapped with an {@code IllegalStateException} so that it can be immediately thrown unchecked.
     *
     * @param throwable some throwable
     * @return the throwable as a {@link RuntimeException}
     */
    public static RuntimeException toContextualRuntimeException(Throwable throwable) {
        if (throwable instanceof StatusRuntimeException exception) {
            return addStatusRuntimeExceptionMessage(exception);
        }
        if (throwable instanceof RuntimeException exception1) {
            return exception1;
        }
        return new IllegalStateException(throwable);
    }

    private static StatusRuntimeException addStatusRuntimeExceptionMessage(StatusRuntimeException e) {
        Status status = e.getStatus();
        return new StatusRuntimeException(status.withDescription(getStatusCodeDescription(status)), e.getTrailers());
    }

    private static String getStatusCodeDescription(Status status) {
        Status.Code code = status.getCode();
        return switch (code) {
            case CANCELLED -> getStatusCodeDescription(status, "It's likely a client side interrupt occurred");
            case DEADLINE_EXCEEDED -> getStatusCodeDescription(status, "gRPC client connection has timed out");
            case RESOURCE_EXHAUSTED -> getStatusCodeDescription(status, "It's likely we've exceeded the maximum number of requests");
            case INTERNAL -> getStatusCodeDescription(status, "It's likely a GPU OOM error or other resource error has caused server to kill itself");
            case UNAVAILABLE -> status.getDescription() != null && status.getDescription().contains("Network closed for unknown reason")
                    ? getStatusCodeDescription(status, "It's possible service crashed due to a misbehaving file")
                    : getStatusCodeDescription(status, "It's likely service crashed");
            default -> status.getDescription();
        };
    }

    private static String getStatusCodeDescription(Status status, String message) {
        String description = status.getDescription();
        if (description != null) {
            return String.format("%s (%s)", description, message);
        }
        return message;
    }

    /**
     * Unwraps common async wrapper exceptions, as Java async APIs often wrap the real causes of failure.
     *
     * @param throwable wrapped throwable
     * @return root cause when available, otherwise the original throwable
     */
    public static Throwable unwrapAsyncThrowable(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException || current instanceof ExecutionException) {
            if (current.getCause() == null) {
                break;
            }
            current = current.getCause();
        }
        return current;
    }
}
