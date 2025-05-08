package emissary.place;

import emissary.place.grpc.TestRequest;
import emissary.place.grpc.TestResponse;
import emissary.place.grpc.TestServiceGrpc;
import emissary.test.core.junit5.UnitTest;
import emissary.util.grpc.exceptions.ServiceException;
import emissary.util.grpc.exceptions.ServiceNotAvailableException;
import emissary.util.grpc.pool.ConnectionFactory;
import emissary.util.io.ResourceReader;

import com.google.common.base.Ascii;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.apache.commons.pool2.PooledObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrpcConnectionPlaceTest extends UnitTest {
    private TestGrcpConnectionPlace place;
    private Server server;

    static class TestGrcpConnectionPlace extends GrpcConnectionPlace {
        private boolean isValidated = false;

        protected TestGrcpConnectionPlace(InputStream is) throws IOException {
            super(is);
        }

        public boolean getIsValidated() {
            return isValidated;
        }

        @Override
        boolean validatePooledObject(PooledObject<ManagedChannel> pooledObject) {
            isValidated = true;
            return true;
        }
    }

    static class TestServiceImpl extends TestServiceGrpc.TestServiceImplBase {
        @Override
        public void uppercase(TestRequest request, StreamObserver<TestResponse> responseObserver) {
            TestResponse resp = TestResponse.newBuilder()
                    .setResult(Ascii.toUpperCase(request.getQuery()))
                    .build();
            responseObserver.onNext(resp);
            responseObserver.onCompleted();
        }
    }

    static Stream<Integer> serviceExceptionCodes() {
        Set<Integer> exclude = serviceNotAvailableExceptionCodes().collect(Collectors.toSet());
        return IntStream.rangeClosed(0, 16).filter(i -> !exclude.contains(i)).boxed();
    }

    static Stream<Integer> serviceNotAvailableExceptionCodes() {
        return IntStream.of(8, 14).boxed();
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        ResourceReader rr = new ResourceReader();
        InputStream is = rr.getConfigDataAsStream(this.getClass());
        place = new TestGrcpConnectionPlace(is);
        server = ServerBuilder.forPort(place.getPort())
                .addService(new TestServiceImpl())
                .build()
                .start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.shutdownNow();
        }
    }

    @Test
    void testValidatePooledObjectIsCalled() {
        assertFalse(place.getIsValidated());
        ConnectionFactory.acquireChannel(place.channelPool);
        assertTrue(place.getIsValidated());
    }

    @Test
    void testGrpcSuccess() {
        TestRequest payload = TestRequest.newBuilder()
                .setQuery("hello world")
                .build();

        TestResponse response = place.invokeGrpc(
                TestServiceGrpc::newBlockingStub, TestServiceGrpc.TestServiceBlockingStub::uppercase, payload);

        assertEquals("HELLO WORLD", response.getResult());
    }

    @ParameterizedTest
    @MethodSource("serviceExceptionCodes")
    void testGrpcServiceException(int code) {
        Status status = Status.fromCodeValue(code);

        TestRequest payload = TestRequest.newBuilder()
                .setQuery("hello world")
                .build();

        ServiceException e = assertThrows(
                ServiceException.class, () -> place.invokeGrpc(
                        TestServiceGrpc::newBlockingStub, (stub, request) -> {
                            throw new StatusRuntimeException(status);
                        }, payload));

        assertTrue(e.getMessage().startsWith("Encountered gRPC runtime status error " + status.getCode().name()));
    }

    @ParameterizedTest
    @MethodSource("serviceNotAvailableExceptionCodes")
    void testGrpcServiceNotAvailableException(int code) {
        Status status = Status.fromCodeValue(code);

        TestRequest payload = TestRequest.newBuilder()
                .setQuery("hello world")
                .build();

        ServiceNotAvailableException e = assertThrows(
                ServiceNotAvailableException.class, () -> place.invokeGrpc(
                        TestServiceGrpc::newBlockingStub, (stub, request) -> {
                            throw new StatusRuntimeException(status);
                        }, payload));

        assertTrue(e.getMessage().startsWith("Encountered gRPC runtime status error " + status.getCode().name()));
    }

    @Test
    void testGrpcRuntimeException() {
        TestRequest payload = TestRequest.newBuilder()
                .setQuery("hello")
                .build();

        TestResponse response = place.invokeGrpc(
                TestServiceGrpc::newBlockingStub, (stub, request) -> {
                    throw new IllegalStateException("Random error");
                }, payload);

        assertNull(response);
    }

    @Test
    void testGrpcSuccessFirstTry() {
        TestRequest payload = TestRequest.newBuilder()
                .setQuery("hello world")
                .build();

        TestResponse response = place.invokeGrpcWithRetry(
                TestServiceGrpc::newBlockingStub, TestServiceGrpc.TestServiceBlockingStub::uppercase, payload);

        assertEquals("HELLO WORLD", response.getResult());
    }

    @ParameterizedTest
    @MethodSource("serviceNotAvailableExceptionCodes")
    void testGrpcSuccessMultipleTries(int code) {
        Status status = Status.fromCodeValue(code);

        TestRequest payload = TestRequest.newBuilder()
                .setQuery("hello world")
                .build();

        AtomicBoolean hasAttempted = new AtomicBoolean(false);
        TestResponse response = place.invokeGrpcWithRetry(
                TestServiceGrpc::newBlockingStub, (stub, request) -> {
                    if (!hasAttempted.getAndSet(true)) {
                        throw new StatusRuntimeException(status);
                    }
                    return stub.uppercase(request);
                }, payload);

        assertEquals("HELLO WORLD", response.getResult());
    }

    @ParameterizedTest
    @MethodSource("serviceExceptionCodes")
    void testGrpcFailureNoRetries(int code) {
        Status status = Status.fromCodeValue(code);

        TestRequest payload = TestRequest.newBuilder()
                .setQuery("hello world")
                .build();

        AtomicBoolean hasAttempted = new AtomicBoolean(false);
        ServiceException e = assertThrows(ServiceException.class, () -> place.invokeGrpcWithRetry(
                TestServiceGrpc::newBlockingStub, (stub, request) -> {
                    if (!hasAttempted.getAndSet(true)) {
                        throw new StatusRuntimeException(status);
                    }
                    return stub.uppercase(request);
                }, payload));

        assertTrue(e.getMessage().startsWith("Encountered gRPC runtime status error " + status.getCode().name()));
    }

    @ParameterizedTest
    @MethodSource("serviceNotAvailableExceptionCodes")
    void testGrpcFailureMaxRetries(int code) {
        Status status = Status.fromCodeValue(code);

        TestRequest payload = TestRequest.newBuilder()
                .setQuery("hello world")
                .build();

        TestResponse response = place.invokeGrpcWithRetry(
                TestServiceGrpc::newBlockingStub, (stub, request) -> {
                    throw new StatusRuntimeException(status);
                }, payload);

        assertNull(response);
    }
}
