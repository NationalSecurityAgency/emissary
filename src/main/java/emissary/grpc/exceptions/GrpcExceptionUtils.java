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
        if (throwable instanceof StatusRuntimeException) {
            return addStatusRuntimeExceptionMessage((StatusRuntimeException) throwable);
        }
        if (throwable instanceof RuntimeException) {
            return (RuntimeException) throwable;
        }
        return new IllegalStateException(throwable);
    }

    private static StatusRuntimeException addStatusRuntimeExceptionMessage(StatusRuntimeException e) {
        Status status = e.getStatus();
        return new StatusRuntimeException(status.withDescription(getStatusCodeDescription(status)), e.getTrailers());
    }

    private static String getStatusCodeDescription(Status status) {
        Status.Code code = status.getCode();
        switch (code) {
            case CANCELLED:
                return getStatusCodeDescription(status, "It's likely a client side interrupt occurred");
            case DEADLINE_EXCEEDED:
                return getStatusCodeDescription(status, "gRPC client connection has timed out");
            case RESOURCE_EXHAUSTED:
                return getStatusCodeDescription(status, "It's likely we've exceeded the maximum number of requests");
            case INTERNAL:
                return getStatusCodeDescription(status, "It's likely a GPU OOM error or other resource error has caused server to kill itself");
            case UNAVAILABLE: {
                // Likely server has gone down. Could be a crash or resources were scaled down.
                // So-called "poison pill" files have resulted in crashes for unknown reasons.
                // Out of an abundance of caution, we consider these files as failures.
                // Otherwise, we indicate the server is not live.
                return status.getDescription() != null && status.getDescription().contains("Network closed for unknown reason")
                        ? getStatusCodeDescription(status, "It's possible service crashed due to a misbehaving file")
                        : getStatusCodeDescription(status, "It's likely service crashed");
            }
            default:
                return status.getDescription();
        }
    }

    private static String getStatusCodeDescription(Status status, String message) {
        String description = status.getDescription();
        if (description != null && !description.equals(message)) {
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
