package emissary.grpc;

import emissary.config.ConfigEntry;
import emissary.config.Configurator;
import emissary.core.constants.Configurations;
import emissary.grpc.exceptions.ServiceException;
import emissary.grpc.exceptions.ServiceNotAvailableException;
import emissary.grpc.pool.ConnectionFactory;
import emissary.grpc.retry.Policy;
import emissary.test.core.junit5.UnitTest;
import emissary.test.util.ConfiguredPlaceFactory;

import com.google.common.base.Ascii;
import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
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
    private final ConfiguredPlaceFactory<TestGrpcConnectionPlace> placeFactory = new ConfiguredPlaceFactory<>(
            TestGrpcConnectionPlace.class,
            new ConfigEntry(Configurations.SERVICE_KEY, "*.GRPC.ANALYZE.http://foo.bar:1234/GrpcConnectionPlace$5050"),
            new ConfigEntry(GrpcConnectionPlace.GRPC_HOST, "localhost"),
            new ConfigEntry(GrpcConnectionPlace.GRPC_PORT, "2222"),
            new ConfigEntry(Policy.GRPC_RETRY_MAX_ATTEMPTS, "2"));

    private TestGrpcConnectionPlace place;
    private Server server;

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
        place = placeFactory.buildPlace();
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
    void testValidateConnectionIsCalled() {
        assertFalse(place.getIsConnectionValidated());
        ConnectionFactory.acquireChannel(place.channelPool);
        assertTrue(place.getIsConnectionValidated());
    }

    @Test
    void testPassivateConnectionIsCalled() {
        ManagedChannel channel = ConnectionFactory.acquireChannel(place.channelPool);
        assertFalse(place.getIsConnectionPassivated());
        ConnectionFactory.returnChannel(channel, place.channelPool);
        assertTrue(place.getIsConnectionPassivated());
    }

    @Test
    void testGrpcSuccess() {
        TestRequest payload = TestRequest.newBuilder()
                .setQuery("hello world")
                .build();

        TestResponse response = place.invokeGrpc(
                TestServiceGrpc::newBlockingStub,
                TestServiceGrpc.TestServiceBlockingStub::uppercase,
                payload);

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
                        TestServiceGrpc::newBlockingStub,
                        (stub, request) -> {
                            throw new StatusRuntimeException(status);
                        },
                        payload));

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
                        TestServiceGrpc::newBlockingStub,
                        (stub, request) -> {
                            throw new StatusRuntimeException(status);
                        },
                        payload));

        assertTrue(e.getMessage().startsWith("Encountered gRPC runtime status error " + status.getCode().name()));
    }

    @Test
    void testGrpcRuntimeException() {
        TestRequest payload = TestRequest.newBuilder()
                .setQuery("hello")
                .build();

        TestResponse response = place.invokeGrpc(
                TestServiceGrpc::newBlockingStub,
                (stub, request) -> {
                    throw new IllegalStateException("Random error");
                },
                payload);

        assertNull(response);
    }

    @Test
    void testGrpcSuccessFirstTry() {
        TestRequest payload = TestRequest.newBuilder()
                .setQuery("hello world")
                .build();

        TestResponse response = place.invokeGrpcWithRetry(
                TestServiceGrpc::newBlockingStub,
                TestServiceGrpc.TestServiceBlockingStub::uppercase,
                payload);

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
                TestServiceGrpc::newBlockingStub,
                (stub, request) -> {
                    if (!hasAttempted.getAndSet(true)) {
                        throw new StatusRuntimeException(status);
                    }
                    return stub.uppercase(request);
                },
                payload);

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
                TestServiceGrpc::newBlockingStub,
                (stub, request) -> {
                    if (!hasAttempted.getAndSet(true)) {
                        throw new StatusRuntimeException(status);
                    }
                    return stub.uppercase(request);
                },
                payload));

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
                TestServiceGrpc::newBlockingStub,
                (stub, request) -> {
                    throw new StatusRuntimeException(status);
                },
                payload);

        assertNull(response);
    }

    public static class TestGrpcConnectionPlace extends GrpcConnectionPlace {
        private boolean isConnectionValidated = false;
        private boolean isConnectionPassivated = false;

        public TestGrpcConnectionPlace(Configurator cfg) throws IOException {
            super(cfg);
        }

        public boolean getIsConnectionValidated() {
            return isConnectionValidated;
        }

        public boolean getIsConnectionPassivated() {
            return isConnectionPassivated;
        }

        @Override
        protected boolean validateConnection(ManagedChannel managedChannel) {
            TestServiceGrpc.TestServiceBlockingStub stub = TestServiceGrpc.newBlockingStub(managedChannel);
            isConnectionValidated = stub.checkHealth(Empty.getDefaultInstance()).getOk();
            return isConnectionValidated;
        }

        @Override
        protected void passivateConnection(ManagedChannel managedChannel) {
            managedChannel.shutdownNow();
            isConnectionPassivated = managedChannel.isShutdown();
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

        @Override
        public void checkHealth(Empty request, StreamObserver<HealthStatus> responseObserver) {
            HealthStatus status = HealthStatus.newBuilder().setOk(true).build();
            responseObserver.onNext(status);
            responseObserver.onCompleted();
        }
    }
}
