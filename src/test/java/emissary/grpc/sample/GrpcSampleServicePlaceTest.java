package emissary.grpc.sample;

import emissary.config.ConfigEntry;
import emissary.core.BaseDataObject;
import emissary.core.IBaseDataObject;
import emissary.grpc.GrpcRoutingPlace;
import emissary.grpc.exceptions.ServiceException;
import emissary.grpc.exceptions.ServiceNotAvailableException;
import emissary.grpc.pool.ConnectionFactory;
import emissary.grpc.retry.RetryHandler;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrpcSampleServicePlaceTest extends UnitTest {
    private static final String FILENAME = "filename.dat";
    private static final String EXCEPTION_MESSAGE = "failed";

    private static final String ENDPOINT_1_ID = "EXAMPLE_ENDPOINT_1";
    private static final String ENDPOINT_2_ID = "EXAMPLE_ENDPOINT_2";
    private static final String ENDPOINT_HOST = "localhost";
    private static final int ENDPOINT_1_PORT = 2223;
    private static final int ENDPOINT_2_PORT = 2224;

    private static final Server endpointOneServer = ServerBuilder.forPort(ENDPOINT_1_PORT)
            .addService(new GrpcSampleServiceImpl.RepeatEachCharServiceImpl())
            .build();

    private static final Server endpointTwoServer = ServerBuilder.forPort(ENDPOINT_2_PORT)
            .addService(new GrpcSampleServiceImpl.RepeatWholeStringServiceImpl())
            .build();

    private static final byte[] INPUT_DATA = "Data123!".getBytes();
    private static final byte[] ENDPOINT_1_PROCESSED_DATA = "DDaattaa112233!!".getBytes(); // RepeatEachCharServiceImpl
    private static final byte[] ENDPOINT_2_PROCESSED_DATA = "Data123!Data123!".getBytes(); // RepeatWholeStringServiceImpl

    private final ConfiguredPlaceFactory<GrpcSampleServicePlace> placeFactory = new ConfiguredPlaceFactory<>(GrpcSampleServicePlace.class,
            new ConfigEntry(GrpcSampleServicePlace.GRPC_HOST + ENDPOINT_1_ID, ENDPOINT_HOST),
            new ConfigEntry(GrpcSampleServicePlace.GRPC_HOST + ENDPOINT_2_ID, ENDPOINT_HOST),
            new ConfigEntry(GrpcSampleServicePlace.GRPC_PORT + ENDPOINT_1_ID, Integer.toString(ENDPOINT_1_PORT)),
            new ConfigEntry(GrpcSampleServicePlace.GRPC_PORT + ENDPOINT_2_ID, Integer.toString(ENDPOINT_2_PORT)));

    private GrpcSampleServicePlace samplePlace;
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

    static String createSampleForm(String formSuffix) {
        return String.join("-", GrpcSampleServicePlace.SERVICE_PROXY, formSuffix);
    }

    static byte[] getSampleAltView(IBaseDataObject o) {
        return o.getAlternateView(GrpcSampleServicePlace.ALTERNATE_VIEW_NAME);
    }

    @BeforeAll
    static void startServers() throws IOException {
        endpointOneServer.start();
        endpointTwoServer.start();
    }

    @AfterAll
    static void stopServers() {
        endpointOneServer.shutdownNow();
        endpointTwoServer.shutdownNow();
    }

    @Nested
    class ManagedChannelHandlingTests extends UnitTest {
        private static final String TARGET_ID = ENDPOINT_1_ID;
        private static final String VALIDATION_PARAM = ConnectionFactory.GRPC_POOL_TEST_BEFORE_BORROW;
        private static final String PASSIVATION_PARAM = GrpcSampleServicePlace.GRPC_POOL_KILL_AFTER_RETURN;
        private AtomicBoolean connectionValidated;

        @BeforeEach
        void initialize() {
            connectionValidated = new AtomicBoolean(false);
        }

        @Test
        void testConnectionIsNotValidated() {
            samplePlace = placeFactory.buildPlace(new ConfigEntry(VALIDATION_PARAM, Boolean.FALSE.toString()));
            samplePlace.setValidationCheck(connectionValidated);
            assertFalse(connectionValidated.get());
            ManagedChannel channel = samplePlace.acquireChannel(TARGET_ID);
            assertFalse(connectionValidated.get());
            samplePlace.returnChannel(channel, TARGET_ID);
        }

        @Test
        void testConnectionIsValidated() {
            samplePlace = placeFactory.buildPlace(new ConfigEntry(VALIDATION_PARAM, Boolean.TRUE.toString()));
            samplePlace.setValidationCheck(connectionValidated);
            assertFalse(connectionValidated.get());
            ManagedChannel channel = samplePlace.acquireChannel(TARGET_ID);
            assertTrue(connectionValidated.get());
            samplePlace.returnChannel(channel, TARGET_ID);
        }

        @Test
        void testConnectionIsNotPassivated() {
            samplePlace = placeFactory.buildPlace(new ConfigEntry(PASSIVATION_PARAM, Boolean.FALSE.toString()));
            ManagedChannel channel = samplePlace.acquireChannel(TARGET_ID);
            assertFalse(channel.isShutdown());
            samplePlace.returnChannel(channel, TARGET_ID);
            assertFalse(channel.isShutdown());
        }

        @Test
        void testConnectionIsPassivated() {
            samplePlace = placeFactory.buildPlace(new ConfigEntry(PASSIVATION_PARAM, Boolean.TRUE.toString()));
            ManagedChannel channel = samplePlace.acquireChannel(TARGET_ID);
            assertFalse(channel.isShutdown());
            samplePlace.returnChannel(channel, TARGET_ID);
            assertTrue(channel.isShutdown());
        }
    }

    @Nested
    class RetryDisabledTests extends UnitTest {
        private static final String TARGET_ID = ENDPOINT_1_ID;
        private static final int RETRY_ATTEMPTS = 1;

        @BeforeEach
        void initialize() {
            samplePlace = placeFactory.buildPlace(
                    new ConfigEntry(RetryHandler.GRPC_RETRY_MAX_ATTEMPTS, Integer.toString(RETRY_ATTEMPTS)));
            dataObject = new BaseDataObject(INPUT_DATA, FILENAME, createSampleForm(TARGET_ID));
        }

        @Test
        void testGrpcSuccess() {
            samplePlace.process(dataObject);
            assertArrayEquals(ENDPOINT_1_PROCESSED_DATA, getSampleAltView(dataObject));
        }

        @ParameterizedTest
        @MethodSource("emissary.grpc.sample.GrpcSampleServicePlaceTest#recoverableGrpcCodes")
        void testGrpcRecoverableCodes(int code) {
            Status status = Status.fromCodeValue(code);
            Runnable invocation = () -> samplePlace.throwExceptionsDuringProcess(dataObject, new StatusRuntimeException(status));
            ServiceNotAvailableException e = assertThrows(ServiceNotAvailableException.class, invocation::run);
            assertTrue(e.getMessage().startsWith("Encountered gRPC runtime status error " + status.getCode().name()));
            assertTrue(dataObject.getAlternateViewNames().isEmpty());
        }

        @ParameterizedTest
        @MethodSource("emissary.grpc.sample.GrpcSampleServicePlaceTest#nonRecoverableGrpcCodes")
        void testGrpcNonRecoverableCodes(int code) {
            Status status = Status.fromCodeValue(code);
            Runnable invocation = () -> samplePlace.throwExceptionsDuringProcess(dataObject, new StatusRuntimeException(status));
            ServiceException e = assertThrows(ServiceException.class, invocation::run);
            assertTrue(e.getMessage().startsWith("Encountered gRPC runtime status error " + status.getCode().name()));
            assertTrue(dataObject.getAlternateViewNames().isEmpty());
        }

        @Test
        void testGrpcRuntimeException() {
            Runnable invocation = () -> samplePlace.throwExceptionsDuringProcess(dataObject, new IllegalStateException(EXCEPTION_MESSAGE));
            IllegalStateException e = assertThrows(IllegalStateException.class, invocation::run);
            assertEquals(EXCEPTION_MESSAGE, e.getMessage());
            assertTrue(dataObject.getAlternateViewNames().isEmpty());
        }
    }

    @Nested
    class RetryEnabledTests extends UnitTest {
        private static final String TARGET_ID = ENDPOINT_1_ID;
        private static final int RETRY_ATTEMPTS = 5;

        @BeforeEach
        void initialize() {
            samplePlace = placeFactory.buildPlace(
                    new ConfigEntry(RetryHandler.GRPC_RETRY_MAX_ATTEMPTS, Integer.toString(RETRY_ATTEMPTS)));
            dataObject = new BaseDataObject(INPUT_DATA, FILENAME, createSampleForm(TARGET_ID));
        }

        @Test
        void testGrpcSuccessFirstTry() {
            samplePlace.process(dataObject);
            assertArrayEquals(ENDPOINT_1_PROCESSED_DATA, getSampleAltView(dataObject));
        }

        @ParameterizedTest
        @MethodSource("emissary.grpc.sample.GrpcSampleServicePlaceTest#recoverableGrpcCodes")
        void testGrpcSuccessAfterRecoverableCodes(int code) {
            Status status = Status.fromCodeValue(code);
            AtomicInteger attemptNumber = new AtomicInteger(0);

            Runnable invocation = () -> samplePlace.throwExceptionsDuringProcessWithSuccessfulRetry(
                    dataObject, new StatusRuntimeException(status), RETRY_ATTEMPTS, attemptNumber);
            invocation.run();

            assertArrayEquals(ENDPOINT_1_PROCESSED_DATA, getSampleAltView(dataObject));
            assertEquals(RETRY_ATTEMPTS, attemptNumber.get());
        }

        @ParameterizedTest
        @MethodSource("emissary.grpc.sample.GrpcSampleServicePlaceTest#recoverableGrpcCodes")
        void testGrpcFailureAfterMaxRecoverableCodes(int code) {
            Status status = Status.fromCodeValue(code);
            AtomicInteger attemptNumber = new AtomicInteger(0);
            int retryAttempts = RETRY_ATTEMPTS + 1;

            Runnable invocation = () -> samplePlace.throwExceptionsDuringProcessWithSuccessfulRetry(
                    dataObject, new StatusRuntimeException(status), retryAttempts, attemptNumber);
            ServiceNotAvailableException e = assertThrows(ServiceNotAvailableException.class, invocation::run);

            assertTrue(e.getMessage().startsWith("Encountered gRPC runtime status error " + status.getCode().name()));
            assertTrue(dataObject.getAlternateViewNames().isEmpty());
            assertEquals(RETRY_ATTEMPTS, attemptNumber.get());
        }

        @ParameterizedTest
        @MethodSource("emissary.grpc.sample.GrpcSampleServicePlaceTest#nonRecoverableGrpcCodes")
        void testGrpcFailureAfterNonRecoverableCodes(int code) {
            Status status = Status.fromCodeValue(code);
            AtomicInteger attemptNumber = new AtomicInteger(0);

            Runnable invocation = () -> samplePlace.throwExceptionsDuringProcessWithSuccessfulRetry(
                    dataObject, new StatusRuntimeException(status), RETRY_ATTEMPTS, attemptNumber);
            ServiceException e = assertThrows(ServiceException.class, invocation::run);

            assertTrue(e.getMessage().startsWith("Encountered gRPC runtime status error " + status.getCode().name()));
            assertTrue(dataObject.getAlternateViewNames().isEmpty());
            assertEquals(1, attemptNumber.get());
        }

        @Test
        void testGrpcFailureAfterRuntimeExceptions() {
            AtomicInteger attemptNumber = new AtomicInteger(0);

            Runnable invocation = () -> samplePlace.throwExceptionsDuringProcessWithSuccessfulRetry(
                    dataObject, new IllegalStateException(EXCEPTION_MESSAGE), RETRY_ATTEMPTS, attemptNumber);
            IllegalStateException e = assertThrows(IllegalStateException.class, invocation::run);

            assertEquals(EXCEPTION_MESSAGE, e.getMessage());
            assertTrue(dataObject.getAlternateViewNames().isEmpty());
            assertEquals(1, attemptNumber.get());
        }
    }

    @Nested
    class ProcessRoutingTests extends UnitTest {
        private static final String ENDPOINT_3_ID = "EXAMPLE_ENDPOINT_3";

        @Test
        void testMismatchedHostPortConfigs() {
            IllegalArgumentException e = placeFactory.getBuildPlaceException(IllegalArgumentException.class,
                    new ConfigEntry(GrpcRoutingPlace.GRPC_HOST + ENDPOINT_3_ID, ENDPOINT_HOST));

            assertEquals("gRPC hostname target-IDs do not match gRPC port number target-IDs", e.getMessage());
        }

        @Test
        void testNoHostPortConfigs() {
            NullPointerException e = placeFactory.getBuildPlaceException(NullPointerException.class,
                    new ConfigEntry(GrpcRoutingPlace.GRPC_HOST + ENDPOINT_1_ID, null),
                    new ConfigEntry(GrpcRoutingPlace.GRPC_HOST + ENDPOINT_2_ID, null),
                    new ConfigEntry(GrpcRoutingPlace.GRPC_PORT + ENDPOINT_1_ID, null),
                    new ConfigEntry(GrpcRoutingPlace.GRPC_PORT + ENDPOINT_2_ID, null));

            assertEquals("Missing required arguments: GRPC_HOST_${Target-ID} and GRPC_PORT_${Target-ID}", e.getMessage());
        }

        @Test
        void testEndpointOneServiceRouting() {
            samplePlace = placeFactory.buildPlace();
            dataObject = new BaseDataObject(INPUT_DATA, FILENAME, createSampleForm(ENDPOINT_1_ID));
            samplePlace.process(dataObject);
            byte[] altView = getSampleAltView(dataObject);
            assertArrayEquals(ENDPOINT_1_PROCESSED_DATA, altView);
            assertFalse(Arrays.equals(ENDPOINT_2_PROCESSED_DATA, altView));
        }

        @Test
        void testEndpointTwoServiceRouting() {
            samplePlace = placeFactory.buildPlace();
            dataObject = new BaseDataObject(INPUT_DATA, FILENAME, createSampleForm(ENDPOINT_2_ID));
            samplePlace.process(dataObject);
            byte[] altView = getSampleAltView(dataObject);
            assertArrayEquals(ENDPOINT_2_PROCESSED_DATA, altView);
            assertFalse(Arrays.equals(ENDPOINT_1_PROCESSED_DATA, altView));
        }

        @Test
        void testInvalidRouting() {
            samplePlace = placeFactory.buildPlace();
            dataObject = new BaseDataObject(INPUT_DATA, FILENAME, createSampleForm(ENDPOINT_3_ID));
            Runnable invocation = () -> samplePlace.process(dataObject);
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, invocation::run);
            assertEquals(String.format("Target-ID %s was never configured", ENDPOINT_3_ID), e.getMessage());
        }
    }
}
