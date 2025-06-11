package emissary.grpc;

import emissary.config.ConfigEntry;
import emissary.config.Configurator;
import emissary.core.constants.Configurations;
import emissary.grpc.exceptions.ServiceException;
import emissary.grpc.exceptions.ServiceNotAvailableException;
import emissary.grpc.pool.ConnectionFactory;
import emissary.test.core.junit5.UnitTest;
import emissary.test.util.ConfiguredPlaceFactory;

import com.google.common.base.Ascii;
import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrpcConnectionPlaceTest extends UnitTest {
    private static final String DEFAULT_SERVICE_KEY = "*.GRPC.ANALYZE.http://foo.bar:1234/GrpcTestPlace$1234";
    private static final String DEFAULT_GRPC_HOST = "localhost";
    private static final int DEFAULT_GRPC_PORT = 2222;
    private static final String ARBITRARY_RUNTIME_EXCEPTION_MESSAGE = "fail";

    private static final Server server = ServerBuilder.forPort(DEFAULT_GRPC_PORT)
            .addService(new TestServiceImpl())
            .build();

    private final ConfiguredPlaceFactory<TestGrpcConnectionPlace> placeFactory = new ConfiguredPlaceFactory<>(
            TestGrpcConnectionPlace.class,
            new ConfigEntry(Configurations.SERVICE_KEY, DEFAULT_SERVICE_KEY),
            new ConfigEntry(GrpcConnectionPlace.GRPC_HOST, DEFAULT_GRPC_HOST),
            new ConfigEntry(GrpcConnectionPlace.GRPC_PORT, String.valueOf(DEFAULT_GRPC_PORT)));

    private TestGrpcConnectionPlace place;

    static Stream<Integer> grpcCodes() {
        return Arrays.stream(Code.values()).map(Code::value);
    }

    static Stream<Integer> recoverableGrpcCodes() {
        return IntStream.of(Code.RESOURCE_EXHAUSTED.value(), Code.UNAVAILABLE.value()).boxed();
    }

    static Stream<Integer> nonRecoverableGrpcCodes() {
        Set<Integer> exclude = recoverableGrpcCodes().collect(Collectors.toSet());
        return grpcCodes().filter(i -> !exclude.contains(i));
    }

    @BeforeAll
    static void startServer() throws Exception {
        server.start();
    }

    @AfterAll
    static void stopServer() {
        server.shutdownNow();
    }

    @Test
    void testValidateConnectionIsCalled() {
        place = placeFactory.buildPlace();
        assertFalse(place.getIsConnectionValidated());
        ConnectionFactory.acquireChannel(place.channelPool);
        assertTrue(place.getIsConnectionValidated());
    }

    @Test
    void testPassivateConnectionIsCalled() {
        place = placeFactory.buildPlace();
        ManagedChannel channel = ConnectionFactory.acquireChannel(place.channelPool);
        assertFalse(place.getIsConnectionPassivated());
        ConnectionFactory.returnChannel(channel, place.channelPool);
        assertTrue(place.getIsConnectionPassivated());
    }

    @Nested
    class RetryDisabledTests extends UnitTest {
        @BeforeEach
        public void setUpPlace() {
            place = placeFactory.buildPlace(new ConfigEntry(RetryHandler.GRPC_RETRY_MAX_ATTEMPTS, "1"));
        }

        @Test
        void testGrpcSuccess() {
            TestRequest request = TestRequest.newBuilder()
                    .setQuery("hello world")
                    .build();

            TestResponse response = place.invokeGrpc(
                    TestServiceGrpc::newBlockingStub,
                    TestServiceGrpc.TestServiceBlockingStub::uppercase,
                    request);

            assertEquals("HELLO WORLD", response.getResult());
        }

        @ParameterizedTest
        @MethodSource("emissary.grpc.GrpcConnectionPlaceTest#recoverableGrpcCodes")
        void testGrpcRecoverableCodes(int code) {
            Status status = Status.fromCodeValue(code);

            TestRequest request = TestRequest.newBuilder()
                    .setQuery("hello world")
                    .build();

            ServiceNotAvailableException e = assertThrows(ServiceNotAvailableException.class, () -> place.invokeGrpc(
                    TestServiceGrpc::newBlockingStub,
                    (stub, payload) -> {
                        throw new StatusRuntimeException(status);
                    },
                    request));

            assertTrue(e.getMessage().startsWith("Encountered gRPC runtime status error " + status.getCode().name()));
        }

        @ParameterizedTest
        @MethodSource("emissary.grpc.GrpcConnectionPlaceTest#nonRecoverableGrpcCodes")
        void testGrpcNonRecoverableCodes(int code) {
            Status status = Status.fromCodeValue(code);

            TestRequest request = TestRequest.newBuilder()
                    .setQuery("hello world")
                    .build();

            ServiceException e = assertThrows(ServiceException.class, () -> place.invokeGrpc(
                    TestServiceGrpc::newBlockingStub,
                    (stub, payload) -> {
                        throw new StatusRuntimeException(status);
                    },
                    request));

            assertTrue(e.getMessage().startsWith("Encountered gRPC runtime status error " + status.getCode().name()));
        }

        @Test
        void testGrpcRuntimeException() {
            TestRequest request = TestRequest.newBuilder()
                    .setQuery("hello")
                    .build();

            IllegalStateException e = assertThrows(IllegalStateException.class, () -> place.invokeGrpc(
                    TestServiceGrpc::newBlockingStub,
                    (stub, payload) -> {
                        throw new IllegalStateException(ARBITRARY_RUNTIME_EXCEPTION_MESSAGE);
                    },
                    request));

            assertEquals(ARBITRARY_RUNTIME_EXCEPTION_MESSAGE, e.getMessage());
        }
    }

    @Nested
    class RetryEnabledTests extends UnitTest {
        private static final int DEFAULT_GRPC_RETRY_MAX_ATTEMPTS = 5;

        @BeforeEach
        public void setUpPlace() {
            place = placeFactory.buildPlace(
                    new ConfigEntry(RetryHandler.GRPC_RETRY_MAX_ATTEMPTS, String.valueOf(DEFAULT_GRPC_RETRY_MAX_ATTEMPTS)));
        }

        @Test
        void testGrpcSuccessFirstTry() {
            TestRequest request = TestRequest.newBuilder()
                    .setQuery("hello world")
                    .build();

            TestResponse response = place.invokeGrpc(
                    TestServiceGrpc::newBlockingStub,
                    TestServiceGrpc.TestServiceBlockingStub::uppercase,
                    request);

            assertEquals("HELLO WORLD", response.getResult());
        }

        @ParameterizedTest
        @MethodSource("emissary.grpc.GrpcConnectionPlaceTest#recoverableGrpcCodes")
        void testGrpcSuccessAfterRecoverableCodes(int code) {
            Status status = Status.fromCodeValue(code);

            TestRequest request = TestRequest.newBuilder()
                    .setQuery("hello world")
                    .build();

            AtomicInteger attemptNumber = new AtomicInteger(0);
            TestResponse response = place.invokeGrpc(
                    TestServiceGrpc::newBlockingStub,
                    (stub, payload) -> {
                        if (attemptNumber.incrementAndGet() < DEFAULT_GRPC_RETRY_MAX_ATTEMPTS) {
                            throw new StatusRuntimeException(status);
                        }
                        return stub.uppercase(request);
                    },
                    request);

            assertEquals("HELLO WORLD", response.getResult());
        }

        @ParameterizedTest
        @MethodSource("emissary.grpc.GrpcConnectionPlaceTest#recoverableGrpcCodes")
        void testGrpcFailureAfterMaxRecoverableCodes(int code) {
            Status status = Status.fromCodeValue(code);

            TestRequest request = TestRequest.newBuilder()
                    .setQuery("hello world")
                    .build();

            ServiceNotAvailableException e = assertThrows(ServiceNotAvailableException.class, () -> place.invokeGrpc(
                    TestServiceGrpc::newBlockingStub,
                    (stub, payload) -> {
                        throw new StatusRuntimeException(status);
                    },
                    request));

            assertTrue(e.getMessage().startsWith("Encountered gRPC runtime status error " + status.getCode().name()));
        }

        @ParameterizedTest
        @MethodSource("emissary.grpc.GrpcConnectionPlaceTest#nonRecoverableGrpcCodes")
        void testGrpcFailureAfterNonRecoverableCodes(int code) {
            Status status = Status.fromCodeValue(code);

            TestRequest request = TestRequest.newBuilder()
                    .setQuery("hello world")
                    .build();

            AtomicInteger attemptNumber = new AtomicInteger(0);
            ServiceException e = assertThrows(ServiceException.class, () -> place.invokeGrpc(
                    TestServiceGrpc::newBlockingStub,
                    (stub, payload) -> {
                        if (attemptNumber.incrementAndGet() < DEFAULT_GRPC_RETRY_MAX_ATTEMPTS) {
                            throw new StatusRuntimeException(status);
                        }
                        return stub.uppercase(request);
                    },
                    request));

            assertTrue(e.getMessage().startsWith("Encountered gRPC runtime status error " + status.getCode().name()));
        }

        @Test
        void testGrpcFailureAfterRuntimeExceptions() {
            TestRequest request = TestRequest.newBuilder()
                    .setQuery("hello world")
                    .build();

            AtomicInteger attemptNumber = new AtomicInteger(0);
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> place.invokeGrpc(
                    TestServiceGrpc::newBlockingStub,
                    (stub, payload) -> {
                        if (attemptNumber.incrementAndGet() < DEFAULT_GRPC_RETRY_MAX_ATTEMPTS) {
                            throw new IllegalStateException(ARBITRARY_RUNTIME_EXCEPTION_MESSAGE);
                        }
                        return stub.uppercase(request);
                    },
                    request));

            assertEquals(ARBITRARY_RUNTIME_EXCEPTION_MESSAGE, e.getMessage());
        }
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
