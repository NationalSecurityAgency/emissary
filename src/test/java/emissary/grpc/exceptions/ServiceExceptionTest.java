package emissary.grpc.exceptions;

import emissary.test.core.junit5.UnitTest;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceExceptionTest extends UnitTest {
    @Test
    void testHandleGrpcStatusRuntimeExceptionWithNullCode() {
        StatusRuntimeException exception = new StatusRuntimeException(Status.UNKNOWN.withDescription("test"));
        exception = new StatusRuntimeException(Status.fromCodeValue(Status.UNKNOWN.getCode().value()), exception.getTrailers());
        StatusRuntimeException finalException = exception;
        ServiceException se =
                assertThrows(ServiceException.class, () -> ServiceException.handleGrpcStatusRuntimeException(finalException));
        assertEquals(ServiceException.GRPC_ERROR_PREFIX + "This is an unhandled code type. Please add it to the list of gRPC exceptions: UNKNOWN",
                se.getMessage());
    }

    @Test
    void testHandleGrpcStatusRuntimeExceptionWithDeadlineExceeded() {
        StatusRuntimeException exception = new StatusRuntimeException(Status.DEADLINE_EXCEEDED.withDescription("test"));
        ServiceException se = assertThrows(ServiceException.class, () -> ServiceException.handleGrpcStatusRuntimeException(exception));
        assertEquals(ServiceException.GRPC_ERROR_PREFIX + "gRPC client connection has timed out: DEADLINE_EXCEEDED: test",
                se.getMessage());
    }

    @Test
    void testHandleGrpcStatusRuntimeExceptionWithUnavailableAndNetworkClosedForUnknownReason() {
        StatusRuntimeException exception =
                new StatusRuntimeException(Status.UNAVAILABLE.withDescription("Network closed for unknown reason"));
        ServiceException se = assertThrows(ServiceException.class, () -> ServiceException.handleGrpcStatusRuntimeException(exception));
        assertEquals(
                ServiceException.GRPC_ERROR_PREFIX
                        + "It's possible service crashed due to a misbehaving file: UNAVAILABLE: Network closed for unknown reason",
                se.getMessage());
    }

    @Test
    void testHandleGrpcStatusRuntimeExceptionWithUnavailable() {
        StatusRuntimeException exception = new StatusRuntimeException(Status.UNAVAILABLE.withDescription("test"));
        ServiceNotLiveException serviceNotLiveException =
                assertThrows(ServiceNotLiveException.class, () -> ServiceException.handleGrpcStatusRuntimeException(exception));
        assertEquals(ServiceException.GRPC_ERROR_PREFIX + "It's likely service crashed: UNAVAILABLE: test", serviceNotLiveException.getMessage());
    }

    @Test
    void testHandleGrpcStatusRuntimeExceptionWithCancelled() {
        StatusRuntimeException exception = new StatusRuntimeException(Status.CANCELLED.withDescription("test"));
        ServiceException se = assertThrows(ServiceException.class, () -> ServiceException.handleGrpcStatusRuntimeException(exception));
        assertEquals(ServiceException.GRPC_ERROR_PREFIX + "It's likely a client side interrupt occurred: CANCELLED: test", se.getMessage());
    }

    @Test
    void testHandleGrpcStatusRuntimeExceptionWithResourceExhausted() {
        StatusRuntimeException exception = new StatusRuntimeException(Status.RESOURCE_EXHAUSTED.withDescription("test"));
        ServiceNotReadyException serviceNotReadyException =
                assertThrows(ServiceNotReadyException.class, () -> ServiceException.handleGrpcStatusRuntimeException(exception));
        assertEquals(ServiceException.GRPC_ERROR_PREFIX + "It's likely we've exceeded the maximum number of requests: RESOURCE_EXHAUSTED: test",
                serviceNotReadyException.getMessage());
    }

    @Test
    void testHandleGrpcStatusRuntimeExceptionWithInternal() {
        StatusRuntimeException exception = new StatusRuntimeException(Status.INTERNAL.withDescription("test"));
        ServiceException se = assertThrows(ServiceException.class, () -> ServiceException.handleGrpcStatusRuntimeException(exception));
        assertEquals(ServiceException.GRPC_ERROR_PREFIX + "It's likely a gpu OOM error or other resource error has occurred: INTERNAL: test",
                se.getMessage());
    }

    @Test
    void testHandleGrpcStatusRuntimeExceptionWithDefault() {
        StatusRuntimeException exception = new StatusRuntimeException(Status.UNKNOWN.withDescription("test"));
        ServiceException se = assertThrows(ServiceException.class, () -> ServiceException.handleGrpcStatusRuntimeException(exception));
        assertEquals(
                ServiceException.GRPC_ERROR_PREFIX + "This is an unhandled code type. Please add it to the list of gRPC exceptions: UNKNOWN: test",
                se.getMessage());
    }

}
