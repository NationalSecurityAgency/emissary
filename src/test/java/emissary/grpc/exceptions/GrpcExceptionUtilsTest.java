package emissary.grpc.exceptions;

import emissary.test.core.junit5.UnitTest;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class GrpcExceptionUtilsTest extends UnitTest {
    private static RuntimeException newContextualRuntimeException(Status status) {
        return newContextualRuntimeException(status, null);
    }

    private static RuntimeException newContextualRuntimeException(Status status, @Nullable String description) {
        return GrpcExceptionUtils.toContextualRuntimeException(new StatusRuntimeException(status.withDescription(description)));
    }

    @Test
    void testCancelledStatusRuntimeExceptionCode() {
        RuntimeException noDesc = newContextualRuntimeException(Status.CANCELLED);
        RuntimeException withDesc = newContextualRuntimeException(Status.CANCELLED, "Test");

        assertEquals("CANCELLED: It's likely a client side interrupt occurred", noDesc.getMessage());
        assertEquals("CANCELLED: Test (It's likely a client side interrupt occurred)", withDesc.getMessage());
    }

    @Test
    void testDeadlineExceededStatusRuntimeExceptionCode() {
        RuntimeException noDesc = newContextualRuntimeException(Status.DEADLINE_EXCEEDED);
        RuntimeException withDesc = newContextualRuntimeException(Status.DEADLINE_EXCEEDED, "Test");

        assertEquals("DEADLINE_EXCEEDED: gRPC client connection has timed out", noDesc.getMessage());
        assertEquals("DEADLINE_EXCEEDED: Test (gRPC client connection has timed out)", withDesc.getMessage());
    }

    @Test
    void testResourceExhaustedStatusRuntimeExceptionCode() {
        RuntimeException noDesc = newContextualRuntimeException(Status.RESOURCE_EXHAUSTED);
        RuntimeException withDesc = newContextualRuntimeException(Status.RESOURCE_EXHAUSTED, "Test");

        assertEquals("RESOURCE_EXHAUSTED: It's likely we've exceeded the maximum number of requests", noDesc.getMessage());
        assertEquals("RESOURCE_EXHAUSTED: Test (It's likely we've exceeded the maximum number of requests)", withDesc.getMessage());
    }

    @Test
    void testInternalStatusRuntimeExceptionCode() {
        RuntimeException noDesc = newContextualRuntimeException(Status.INTERNAL);
        RuntimeException withDesc = newContextualRuntimeException(Status.INTERNAL, "Test");

        assertEquals("INTERNAL: It's likely a GPU OOM error or other resource error has caused server to kill itself", noDesc.getMessage());
        assertEquals("INTERNAL: Test (It's likely a GPU OOM error or other resource error has caused server to kill itself)", withDesc.getMessage());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "Network closed for unknown reason, It's possible service crashed due to a misbehaving file",
            "Test, It's likely service crashed"})
    void testUnavailableStatusRuntimeExceptionCode(String statusDescription, String errorMessage) {
        RuntimeException noDesc = newContextualRuntimeException(Status.UNAVAILABLE);
        RuntimeException withDesc = newContextualRuntimeException(Status.UNAVAILABLE, statusDescription);

        assertEquals("UNAVAILABLE: It's likely service crashed", noDesc.getMessage());
        assertEquals(String.format("UNAVAILABLE: %s (%s)", statusDescription, errorMessage), withDesc.getMessage());
    }

    @ParameterizedTest
    @EnumSource(
            value = Status.Code.class,
            names = {"CANCELLED", "DEADLINE_EXCEEDED", "RESOURCE_EXHAUSTED", "INTERNAL", "UNAVAILABLE"},
            mode = EnumSource.Mode.EXCLUDE)
    void testRemainingStatusRuntimeExceptionCodes(Status.Code code) {
        Status status = code.toStatus();
        RuntimeException noDesc = newContextualRuntimeException(status);
        RuntimeException withDesc = newContextualRuntimeException(status, "Test");

        assertEquals(code.name(), noDesc.getMessage());
        assertEquals(String.format("%s: Test", code.name()), withDesc.getMessage());
    }

    @Test
    void testNonStatusRuntimeExceptionType() {
        RuntimeException base = new RuntimeException("Test");
        RuntimeException contextual = GrpcExceptionUtils.toContextualRuntimeException(base);

        assertSame(base, contextual);
    }

    @Test
    void testNonRuntimeExceptionType() {
        Exception base = new Exception("Test");
        RuntimeException contextual = GrpcExceptionUtils.toContextualRuntimeException(base);

        assertNotSame(base, contextual);
        assertInstanceOf(IllegalStateException.class, contextual);
        assertSame(base, contextual.getCause());
    }
}
