package emissary.grpc;

import emissary.config.ConfigEntry;
import emissary.config.Configurator;
import emissary.core.BaseDataObject;
import emissary.core.IBaseDataObject;
import emissary.core.constants.Configurations;
import emissary.grpc.TestServiceGrpc.TestServiceBlockingStub;
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
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrpcConnectionPlaceTest extends UnitTest {
    private static final String DEFAULT_SERVICE_KEY = "*.GRPC.ANALYZE.http://foo.bar:1234/LetterCapitalizationPlace$1234";
    private static final String DEFAULT_GRPC_HOST = "localhost";
    private static final int DEFAULT_GRPC_PORT = 2222;
    private static final String ARBITRARY_RUNTIME_EXCEPTION_MESSAGE = "fail";

    private static final Server server = ServerBuilder.forPort(DEFAULT_GRPC_PORT)
            .addService(new TestServiceImpl())
            .build();

    private final ConfiguredPlaceFactory<LetterCapitalizationPlace> placeFactory = new ConfiguredPlaceFactory<>(
            LetterCapitalizationPlace.class,
            new ConfigEntry(Configurations.SERVICE_KEY, DEFAULT_SERVICE_KEY),
            new ConfigEntry(GrpcConnectionPlace.GRPC_HOST, DEFAULT_GRPC_HOST),
            new ConfigEntry(GrpcConnectionPlace.GRPC_PORT, String.valueOf(DEFAULT_GRPC_PORT)));

    private LetterCapitalizationPlace place;
    private IBaseDataObject dataObject;

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
    static void startServer() throws IOException {
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
            dataObject = new BaseDataObject("hello world".getBytes(), "");
        }

        @Test
        void testGrpcSuccess() {
            place.setGrpcClientMethod(TestServiceBlockingStub::uppercase);
            place.process(dataObject);
            assertEquals("HELLO WORLD", dataObject.getParameterAsString(LetterCapitalizationPlace.CAPITALIZED_DATA));
        }

        @ParameterizedTest
        @MethodSource("emissary.grpc.GrpcConnectionPlaceTest#recoverableGrpcCodes")
        void testGrpcRecoverableCodes(int code) {
            Status status = Status.fromCodeValue(code);
            place.setGrpcClientMethod((stub, payload) -> {
                throw new StatusRuntimeException(status);
            });

            ServiceNotAvailableException e = assertThrows(ServiceNotAvailableException.class, () -> place.process(dataObject));
            assertTrue(e.getMessage().startsWith("Encountered gRPC runtime status error " + status.getCode().name()));
            assertFalse(dataObject.hasParameter(LetterCapitalizationPlace.CAPITALIZED_DATA));
        }

        @ParameterizedTest
        @MethodSource("emissary.grpc.GrpcConnectionPlaceTest#nonRecoverableGrpcCodes")
        void testGrpcNonRecoverableCodes(int code) {
            Status status = Status.fromCodeValue(code);
            place.setGrpcClientMethod((stub, payload) -> {
                throw new StatusRuntimeException(status);
            });

            ServiceException e = assertThrows(ServiceException.class, () -> place.process(dataObject));
            assertTrue(e.getMessage().startsWith("Encountered gRPC runtime status error " + status.getCode().name()));
            assertFalse(dataObject.hasParameter(LetterCapitalizationPlace.CAPITALIZED_DATA));
        }

        @Test
        void testGrpcRuntimeException() {
            place.setGrpcClientMethod((stub, payload) -> {
                throw new IllegalStateException(ARBITRARY_RUNTIME_EXCEPTION_MESSAGE);
            });

            IllegalStateException e = assertThrows(IllegalStateException.class, () -> place.process(dataObject));
            assertEquals(ARBITRARY_RUNTIME_EXCEPTION_MESSAGE, e.getMessage());
            assertFalse(dataObject.hasParameter(LetterCapitalizationPlace.CAPITALIZED_DATA));
        }
    }

    @Nested
    class RetryEnabledTests extends UnitTest {
        private static final int DEFAULT_GRPC_RETRY_MAX_ATTEMPTS = 5;

        @BeforeEach
        public void setUpPlace() {
            place = placeFactory.buildPlace(
                    new ConfigEntry(RetryHandler.GRPC_RETRY_MAX_ATTEMPTS, String.valueOf(DEFAULT_GRPC_RETRY_MAX_ATTEMPTS)));
            dataObject = new BaseDataObject("hello world".getBytes(), "");
        }

        @Test
        void testGrpcSuccessFirstTry() {
            place.setGrpcClientMethod(TestServiceBlockingStub::uppercase);
            place.process(dataObject);
            assertEquals("HELLO WORLD", dataObject.getParameterAsString(LetterCapitalizationPlace.CAPITALIZED_DATA));
        }

        @ParameterizedTest
        @MethodSource("emissary.grpc.GrpcConnectionPlaceTest#recoverableGrpcCodes")
        void testGrpcSuccessAfterRecoverableCodes(int code) {
            Status status = Status.fromCodeValue(code);
            AtomicInteger attemptNumber = new AtomicInteger(0);
            place.setGrpcClientMethod((stub, payload) -> {
                if (attemptNumber.incrementAndGet() < DEFAULT_GRPC_RETRY_MAX_ATTEMPTS) {
                    throw new StatusRuntimeException(status);
                }
                return stub.uppercase(payload);
            });

            place.process(dataObject);
            assertEquals("HELLO WORLD", dataObject.getParameterAsString(LetterCapitalizationPlace.CAPITALIZED_DATA));
        }

        @ParameterizedTest
        @MethodSource("emissary.grpc.GrpcConnectionPlaceTest#recoverableGrpcCodes")
        void testGrpcFailureAfterMaxRecoverableCodes(int code) {
            Status status = Status.fromCodeValue(code);
            place.setGrpcClientMethod((stub, payload) -> {
                throw new StatusRuntimeException(status);
            });

            ServiceNotAvailableException e = assertThrows(ServiceNotAvailableException.class, () -> place.process(dataObject));
            assertTrue(e.getMessage().startsWith("Encountered gRPC runtime status error " + status.getCode().name()));
            assertFalse(dataObject.hasParameter(LetterCapitalizationPlace.CAPITALIZED_DATA));
        }

        @ParameterizedTest
        @MethodSource("emissary.grpc.GrpcConnectionPlaceTest#nonRecoverableGrpcCodes")
        void testGrpcFailureAfterNonRecoverableCodes(int code) {
            Status status = Status.fromCodeValue(code);
            AtomicInteger attemptNumber = new AtomicInteger(0);
            place.setGrpcClientMethod((stub, payload) -> {
                if (attemptNumber.incrementAndGet() < DEFAULT_GRPC_RETRY_MAX_ATTEMPTS) {
                    throw new StatusRuntimeException(status);
                }
                return stub.uppercase(payload);
            });

            ServiceException e = assertThrows(ServiceException.class, () -> place.process(dataObject));
            assertTrue(e.getMessage().startsWith("Encountered gRPC runtime status error " + status.getCode().name()));
            assertFalse(dataObject.hasParameter(LetterCapitalizationPlace.CAPITALIZED_DATA));
        }

        @Test
        void testGrpcFailureAfterRuntimeExceptions() {
            AtomicInteger attemptNumber = new AtomicInteger(0);
            place.setGrpcClientMethod((stub, payload) -> {
                if (attemptNumber.incrementAndGet() < DEFAULT_GRPC_RETRY_MAX_ATTEMPTS) {
                    throw new IllegalStateException(ARBITRARY_RUNTIME_EXCEPTION_MESSAGE);
                }
                return stub.uppercase(payload);
            });

            IllegalStateException e = assertThrows(IllegalStateException.class, () -> place.process(dataObject));
            assertEquals(ARBITRARY_RUNTIME_EXCEPTION_MESSAGE, e.getMessage());
            assertFalse(dataObject.hasParameter(LetterCapitalizationPlace.CAPITALIZED_DATA));
        }
    }

    public static class LetterCapitalizationPlace extends GrpcConnectionPlace {
        public static final String CAPITALIZED_DATA = "CAPITALIZED_DATA";

        private BiFunction<TestServiceBlockingStub, TestRequest, TestResponse> grpcClientMethod;
        private boolean isConnectionValidated = false;
        private boolean isConnectionPassivated = false;

        public LetterCapitalizationPlace(Configurator cfg) throws IOException {
            super(cfg);
        }

        @Override
        public void process(IBaseDataObject o) {
            TestRequest request = TestRequest.newBuilder()
                    .setQuery(new String(o.data()))
                    .build();

            TestResponse response = invokeGrpc(TestServiceGrpc::newBlockingStub, grpcClientMethod, request);
            o.setParameter(CAPITALIZED_DATA, response.getResult());
        }

        public void setGrpcClientMethod(BiFunction<TestServiceBlockingStub, TestRequest, TestResponse> method) {
            grpcClientMethod = method;
        }

        public boolean getIsConnectionValidated() {
            return isConnectionValidated;
        }

        public boolean getIsConnectionPassivated() {
            return isConnectionPassivated;
        }

        @Override
        protected boolean validateConnection(ManagedChannel managedChannel) {
            TestServiceBlockingStub stub = TestServiceGrpc.newBlockingStub(managedChannel);
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
