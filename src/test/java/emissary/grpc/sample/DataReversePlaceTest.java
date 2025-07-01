package emissary.grpc.sample;

import emissary.config.ConfigEntry;
import emissary.core.BaseDataObject;
import emissary.core.IBaseDataObject;
import emissary.grpc.exceptions.ServiceException;
import emissary.grpc.exceptions.ServiceNotAvailableException;
import emissary.grpc.place.GrpcConnectionPlace;
import emissary.grpc.pool.ConnectionFactory;
import emissary.grpc.retry.RetryHandler;
import emissary.grpc.sample.connection.DataReversePlace;
import emissary.grpc.sample.connection.DataReverseServiceImpl;
import emissary.test.core.junit5.UnitTest;
import emissary.test.util.ConfiguredPlaceFactory;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataReversePlaceTest extends UnitTest {
    private static final String MESSAGE = "hello world";
    private static final String EXCEPTION_MESSAGE = "fail";
    private static final byte[] DATA = MESSAGE.getBytes();
    private static final byte[] REVERSED_DATA = new StringBuilder(MESSAGE).reverse().toString().getBytes();
    private static final String FILENAME = "123.dat";
    private static final String TRUE = "true";
    private static final String FALSE = "false";
    private static final String DEFAULT_GRPC_HOST = "localhost";
    private static final int DEFAULT_GRPC_PORT = 2222;

    private static final Server server = ServerBuilder.forPort(DEFAULT_GRPC_PORT)
            .addService(new DataReverseServiceImpl())
            .build();

    private final ConfiguredPlaceFactory<DataReversePlace> factory = new ConfiguredPlaceFactory<>(DataReversePlace.class,
            new ConfigEntry(GrpcConnectionPlace.GRPC_HOST, DEFAULT_GRPC_HOST),
            new ConfigEntry(GrpcConnectionPlace.GRPC_PORT, String.valueOf(DEFAULT_GRPC_PORT)));

    private DataReversePlace place;
    private IBaseDataObject dataObject;

    static Stream<Integer> grpcCodes() {
        return Arrays.stream(Status.Code.values()).map(Status.Code::value);
    }

    static Stream<Integer> recoverableGrpcCodes() {
        return IntStream.of(Status.Code.RESOURCE_EXHAUSTED.value(), Status.Code.UNAVAILABLE.value()).boxed();
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
    void testConnectionIsNotValidated() {
        place = factory.buildPlace(new ConfigEntry(ConnectionFactory.GRPC_POOL_TEST_BEFORE_BORROW, FALSE));
        assertFalse(place.getWasValidateConnectionCalled());
        ManagedChannel channel = place.acquireChannel();
        assertFalse(place.getWasValidateConnectionCalled());
        place.returnChannel(channel);
    }

    @Test
    void testConnectionIsValidated() {
        place = factory.buildPlace(new ConfigEntry(ConnectionFactory.GRPC_POOL_TEST_BEFORE_BORROW, TRUE));
        assertFalse(place.getWasValidateConnectionCalled());
        ManagedChannel channel = place.acquireChannel();
        assertTrue(place.getWasValidateConnectionCalled());
        place.returnChannel(channel);
    }

    @Test
    void testConnectionIsNotPassivated() {
        place = factory.buildPlace(new ConfigEntry(DataReversePlace.GRPC_POOL_KILL_AFTER_RETURN, FALSE));
        ManagedChannel channel = place.acquireChannel();
        assertFalse(channel.isShutdown());
        place.returnChannel(channel);
        assertFalse(channel.isShutdown());
    }

    @Test
    void testConnectionIsPassivated() {
        place = factory.buildPlace(new ConfigEntry(DataReversePlace.GRPC_POOL_KILL_AFTER_RETURN, TRUE));
        ManagedChannel channel = place.acquireChannel();
        assertFalse(channel.isShutdown());
        place.returnChannel(channel);
        assertTrue(channel.isShutdown());
    }

    @Nested
    class RetryDisabledTests extends UnitTest {
        @BeforeEach
        void setUpPlace() {
            place = factory.buildPlace();
            dataObject = new BaseDataObject(DATA, FILENAME);
        }

        @Test
        void testGrpcSuccess() {
            place.process(dataObject);
            assertArrayEquals(REVERSED_DATA, dataObject.getAlternateView(DataReversePlace.REVERSED_DATA));
        }

        @ParameterizedTest
        @MethodSource("emissary.grpc.sample.DataReversePlaceTest#recoverableGrpcCodes")
        void testGrpcRecoverableCodes(int code) {
            Status status = Status.fromCodeValue(code);
            Runnable invocation = () -> place.throwExceptionsDuringProcess(dataObject, new StatusRuntimeException(status));
            ServiceNotAvailableException e = assertThrows(ServiceNotAvailableException.class, invocation::run);
            assertTrue(e.getMessage().startsWith("Encountered gRPC runtime status error " + status.getCode().name()));
            assertTrue(dataObject.getAlternateViewNames().isEmpty());
        }

        @ParameterizedTest
        @MethodSource("emissary.grpc.sample.DataReversePlaceTest#nonRecoverableGrpcCodes")
        void testGrpcNonRecoverableCodes(int code) {
            Status status = Status.fromCodeValue(code);
            Runnable invocation = () -> place.throwExceptionsDuringProcess(dataObject, new StatusRuntimeException(status));
            ServiceException e = assertThrows(ServiceException.class, invocation::run);
            assertTrue(e.getMessage().startsWith("Encountered gRPC runtime status error " + status.getCode().name()));
            assertTrue(dataObject.getAlternateViewNames().isEmpty());
        }

        @Test
        void testGrpcRuntimeException() {
            Runnable invocation = () -> place.throwExceptionsDuringProcess(dataObject, new IllegalStateException(EXCEPTION_MESSAGE));
            IllegalStateException e = assertThrows(IllegalStateException.class, invocation::run);
            assertEquals(EXCEPTION_MESSAGE, e.getMessage());
            assertTrue(dataObject.getAlternateViewNames().isEmpty());
        }
    }

    @Nested
    class RetryEnabledTests extends UnitTest {
        private static final int RETRY_ATTEMPTS = 5;

        @BeforeEach
        void setUpPlace() {
            place = factory.buildPlace(new ConfigEntry(RetryHandler.GRPC_RETRY_MAX_ATTEMPTS, String.valueOf(RETRY_ATTEMPTS)));
            dataObject = new BaseDataObject(DATA, FILENAME);
        }

        @Test
        void testGrpcSuccessFirstTry() {
            place.process(dataObject);
            assertArrayEquals(REVERSED_DATA, dataObject.getAlternateView(DataReversePlace.REVERSED_DATA));
        }

        @ParameterizedTest
        @MethodSource("emissary.grpc.sample.DataReversePlaceTest#recoverableGrpcCodes")
        void testGrpcSuccessAfterRecoverableCodes(int code) {
            Status status = Status.fromCodeValue(code);
            AtomicInteger attemptNumber = new AtomicInteger(0);

            Runnable invocation = () -> place.throwExceptionsDuringProcessWithSuccessfulRetry(
                    dataObject, new StatusRuntimeException(status), RETRY_ATTEMPTS, attemptNumber);
            invocation.run();

            assertArrayEquals(REVERSED_DATA, dataObject.getAlternateView(DataReversePlace.REVERSED_DATA));
            assertEquals(RETRY_ATTEMPTS, attemptNumber.get());
        }

        @ParameterizedTest
        @MethodSource("emissary.grpc.sample.DataReversePlaceTest#recoverableGrpcCodes")
        void testGrpcFailureAfterMaxRecoverableCodes(int code) {
            Status status = Status.fromCodeValue(code);
            AtomicInteger attemptNumber = new AtomicInteger(0);
            int retryAttempts = RETRY_ATTEMPTS + 1;

            Runnable invocation = () -> place.throwExceptionsDuringProcessWithSuccessfulRetry(
                    dataObject, new StatusRuntimeException(status), retryAttempts, attemptNumber);
            ServiceNotAvailableException e = assertThrows(ServiceNotAvailableException.class, invocation::run);

            assertTrue(e.getMessage().startsWith("Encountered gRPC runtime status error " + status.getCode().name()));
            assertTrue(dataObject.getAlternateViewNames().isEmpty());
            assertEquals(RETRY_ATTEMPTS, attemptNumber.get());
        }

        @ParameterizedTest
        @MethodSource("emissary.grpc.sample.DataReversePlaceTest#nonRecoverableGrpcCodes")
        void testGrpcFailureAfterNonRecoverableCodes(int code) {
            Status status = Status.fromCodeValue(code);
            AtomicInteger attemptNumber = new AtomicInteger(0);

            Runnable invocation = () -> place.throwExceptionsDuringProcessWithSuccessfulRetry(
                    dataObject, new StatusRuntimeException(status), RETRY_ATTEMPTS, attemptNumber);
            ServiceException e = assertThrows(ServiceException.class, invocation::run);

            assertTrue(e.getMessage().startsWith("Encountered gRPC runtime status error " + status.getCode().name()));
            assertTrue(dataObject.getAlternateViewNames().isEmpty());
            assertEquals(1, attemptNumber.get());
        }

        @Test
        void testGrpcFailureAfterRuntimeExceptions() {
            AtomicInteger attemptNumber = new AtomicInteger(0);

            Runnable invocation = () -> place.throwExceptionsDuringProcessWithSuccessfulRetry(
                    dataObject, new IllegalStateException(EXCEPTION_MESSAGE), RETRY_ATTEMPTS, attemptNumber);
            IllegalStateException e = assertThrows(IllegalStateException.class, invocation::run);

            assertEquals(EXCEPTION_MESSAGE, e.getMessage());
            assertTrue(dataObject.getAlternateViewNames().isEmpty());
            assertEquals(1, attemptNumber.get());
        }
    }
}
