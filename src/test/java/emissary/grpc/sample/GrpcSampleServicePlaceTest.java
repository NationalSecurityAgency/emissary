package emissary.grpc.sample;

import emissary.config.ConfigEntry;
import emissary.config.ServiceConfigGuide;
import emissary.core.BaseDataObject;
import emissary.core.IBaseDataObject;
import emissary.core.constants.Configurations;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrpcSampleServicePlaceTest extends UnitTest {
    private static final String SERVICE_KEY = "*.GRPC_SAMPLE_SERVICE.TRANSFORM." +
            "@{emissary.node.scheme}://@{emissary.node.name}:@{emissary.node.port}/GrpcSampleServicePlace$5050";
    private static final String FILENAME = "filename.dat";
    private static final String EXCEPTION_MESSAGE = "failed";

    private static final String ENDPOINT_1_ID = "EXAMPLE_ENDPOINT_1";
    private static final String ENDPOINT_2_ID = "EXAMPLE_ENDPOINT_2";
    private static final String ENDPOINT_HOST = "localhost";

    private Server endpointOneServer;
    private Server endpointTwoServer;

    private static final byte[] INPUT_DATA = "Data123!".getBytes();
    private static final byte[] ENDPOINT_1_PROCESSED_DATA = "DDaattaa112233!!".getBytes(); // RepeatEachCharServiceImpl
    private static final byte[] ENDPOINT_2_PROCESSED_DATA = "Data123!Data123!".getBytes(); // RepeatWholeStringServiceImpl

    private final ConfiguredPlaceFactory<GrpcSampleServicePlace> placeFactory = new ConfiguredPlaceFactory<>(
            GrpcSampleServicePlace.class, new ServiceConfigGuide(),
            new ConfigEntry(Configurations.SERVICE_KEY, SERVICE_KEY),
            new ConfigEntry(GrpcSampleServicePlace.GRPC_HOST + ENDPOINT_1_ID, ENDPOINT_HOST),
            new ConfigEntry(GrpcSampleServicePlace.GRPC_HOST + ENDPOINT_2_ID, ENDPOINT_HOST));

    private GrpcSampleServicePlace samplePlace;
    private IBaseDataObject dataObject;

    static String createSampleForm(String formSuffix) {
        return String.join("-", GrpcSampleServicePlace.SERVICE_PROXY, formSuffix);
    }

    static byte[] getSampleAltView(IBaseDataObject o) {
        return o.getAlternateView(GrpcSampleServicePlace.ALTERNATE_VIEW_NAME);
    }

    static String getPort(Server server) {
        return String.valueOf(server.getPort());
    }

    @BeforeEach
    void startServers() throws IOException {
        endpointOneServer = ServerBuilder.forPort(0)
                .addService(new GrpcSampleServiceImpl.RepeatEachCharServiceImpl())
                .build()
                .start();

        endpointTwoServer = ServerBuilder.forPort(0)
                .addService(new GrpcSampleServiceImpl.RepeatWholeStringServiceImpl())
                .build()
                .start();
    }

    @AfterEach
    void stopServers() {
        if (endpointOneServer != null && !endpointOneServer.isShutdown()) {
            endpointOneServer.shutdownNow();
        }

        if (endpointTwoServer != null && !endpointTwoServer.isShutdown()) {
            endpointTwoServer.shutdownNow();
        }
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
            samplePlace = placeFactory.buildPlace(
                    new ConfigEntry(VALIDATION_PARAM, Boolean.FALSE.toString()),
                    new ConfigEntry(GrpcSampleServicePlace.GRPC_PORT + ENDPOINT_1_ID, getPort(endpointOneServer)),
                    new ConfigEntry(GrpcSampleServicePlace.GRPC_PORT + ENDPOINT_2_ID, getPort(endpointTwoServer)));
            samplePlace.setValidationCheck(connectionValidated);
            assertFalse(connectionValidated.get());
            ManagedChannel channel = samplePlace.acquireChannel(TARGET_ID);
            assertFalse(connectionValidated.get());
            samplePlace.returnChannel(channel, TARGET_ID);
        }

        @Test
        void testConnectionIsValidated() {
            samplePlace = placeFactory.buildPlace(
                    new ConfigEntry(VALIDATION_PARAM, Boolean.TRUE.toString()),
                    new ConfigEntry(GrpcSampleServicePlace.GRPC_PORT + ENDPOINT_1_ID, getPort(endpointOneServer)),
                    new ConfigEntry(GrpcSampleServicePlace.GRPC_PORT + ENDPOINT_2_ID, getPort(endpointTwoServer)));
            samplePlace.setValidationCheck(connectionValidated);
            assertFalse(connectionValidated.get());
            ManagedChannel channel = samplePlace.acquireChannel(TARGET_ID);
            assertTrue(connectionValidated.get());
            samplePlace.returnChannel(channel, TARGET_ID);
        }

        @Test
        void testConnectionIsNotPassivated() {
            samplePlace = placeFactory.buildPlace(
                    new ConfigEntry(PASSIVATION_PARAM, Boolean.FALSE.toString()),
                    new ConfigEntry(GrpcSampleServicePlace.GRPC_PORT + ENDPOINT_1_ID, getPort(endpointOneServer)),
                    new ConfigEntry(GrpcSampleServicePlace.GRPC_PORT + ENDPOINT_2_ID, getPort(endpointTwoServer)));
            ManagedChannel channel = samplePlace.acquireChannel(TARGET_ID);
            assertFalse(channel.isShutdown());
            samplePlace.returnChannel(channel, TARGET_ID);
            assertFalse(channel.isShutdown());
        }

        @Test
        void testConnectionIsPassivated() {
            samplePlace = placeFactory.buildPlace(
                    new ConfigEntry(PASSIVATION_PARAM, Boolean.TRUE.toString()),
                    new ConfigEntry(GrpcSampleServicePlace.GRPC_PORT + ENDPOINT_1_ID, getPort(endpointOneServer)),
                    new ConfigEntry(GrpcSampleServicePlace.GRPC_PORT + ENDPOINT_2_ID, getPort(endpointTwoServer)));
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
                    new ConfigEntry(RetryHandler.GRPC_RETRY_MAX_ATTEMPTS, Integer.toString(RETRY_ATTEMPTS)),
                    new ConfigEntry(GrpcSampleServicePlace.GRPC_PORT + ENDPOINT_1_ID, getPort(endpointOneServer)),
                    new ConfigEntry(GrpcSampleServicePlace.GRPC_PORT + ENDPOINT_2_ID, getPort(endpointTwoServer)));
            dataObject = new BaseDataObject(INPUT_DATA, FILENAME, createSampleForm(TARGET_ID));
        }

        @Test
        void testGrpcSuccess() {
            samplePlace.process(dataObject);
            assertArrayEquals(ENDPOINT_1_PROCESSED_DATA, getSampleAltView(dataObject));
        }

        @ParameterizedTest
        @EnumSource(value = Status.Code.class, names = {"RESOURCE_EXHAUSTED", "UNAVAILABLE"}, mode = EnumSource.Mode.INCLUDE)
        void testGrpcRecoverableCodes(Status.Code code) {
            Status status = Status.fromCode(code);
            Runnable invocation = () -> samplePlace.throwExceptionsDuringProcess(dataObject, new StatusRuntimeException(status));
            ServiceNotAvailableException e = assertThrows(ServiceNotAvailableException.class, invocation::run);
            assertTrue(e.getMessage().endsWith(status.getCode().name()));
            assertTrue(dataObject.getAlternateViewNames().isEmpty());
        }

        @ParameterizedTest
        @EnumSource(value = Status.Code.class, names = {"RESOURCE_EXHAUSTED", "UNAVAILABLE"}, mode = EnumSource.Mode.EXCLUDE)
        void testGrpcNonRecoverableCodes(Status.Code code) {
            Status status = Status.fromCode(code);
            Runnable invocation = () -> samplePlace.throwExceptionsDuringProcess(dataObject, new StatusRuntimeException(status));
            ServiceException e = assertThrows(ServiceException.class, invocation::run);
            assertTrue(e.getMessage().endsWith(status.getCode().name()));
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
                    new ConfigEntry(RetryHandler.GRPC_RETRY_MAX_ATTEMPTS, Integer.toString(RETRY_ATTEMPTS)),
                    new ConfigEntry(GrpcSampleServicePlace.GRPC_PORT + ENDPOINT_1_ID, getPort(endpointOneServer)),
                    new ConfigEntry(GrpcSampleServicePlace.GRPC_PORT + ENDPOINT_2_ID, getPort(endpointTwoServer)));
            dataObject = new BaseDataObject(INPUT_DATA, FILENAME, createSampleForm(TARGET_ID));
        }

        @Test
        void testGrpcSuccessFirstTry() {
            samplePlace.process(dataObject);
            assertArrayEquals(ENDPOINT_1_PROCESSED_DATA, getSampleAltView(dataObject));
        }

        @ParameterizedTest
        @EnumSource(value = Status.Code.class, names = {"RESOURCE_EXHAUSTED", "UNAVAILABLE"}, mode = EnumSource.Mode.INCLUDE)
        void testGrpcSuccessAfterRecoverableCodes(Status.Code code) {
            Status status = Status.fromCode(code);
            AtomicInteger attemptNumber = new AtomicInteger(0);

            Runnable invocation = () -> samplePlace.throwExceptionsDuringProcessWithSuccessfulRetry(
                    dataObject, new StatusRuntimeException(status), RETRY_ATTEMPTS, attemptNumber);
            invocation.run();

            assertArrayEquals(ENDPOINT_1_PROCESSED_DATA, getSampleAltView(dataObject));
            assertEquals(RETRY_ATTEMPTS, attemptNumber.get());
        }


        @ParameterizedTest
        @EnumSource(value = Status.Code.class, names = {"RESOURCE_EXHAUSTED", "UNAVAILABLE"}, mode = EnumSource.Mode.INCLUDE)
        void testGrpcFailureAfterMaxRecoverableCodes(Status.Code code) {
            Status status = Status.fromCode(code);
            AtomicInteger attemptNumber = new AtomicInteger(0);
            int retryAttempts = RETRY_ATTEMPTS + 1;

            Runnable invocation = () -> samplePlace.throwExceptionsDuringProcessWithSuccessfulRetry(
                    dataObject, new StatusRuntimeException(status), retryAttempts, attemptNumber);
            ServiceNotAvailableException e = assertThrows(ServiceNotAvailableException.class, invocation::run);

            assertTrue(e.getMessage().endsWith(status.getCode().name()));
            assertTrue(dataObject.getAlternateViewNames().isEmpty());
            assertEquals(RETRY_ATTEMPTS, attemptNumber.get());
        }

        @ParameterizedTest
        @EnumSource(value = Status.Code.class, names = {"RESOURCE_EXHAUSTED", "UNAVAILABLE"}, mode = EnumSource.Mode.EXCLUDE)
        void testGrpcFailureAfterNonRecoverableCodes(Status.Code code) {
            Status status = Status.fromCode(code);
            AtomicInteger attemptNumber = new AtomicInteger(0);

            Runnable invocation = () -> samplePlace.throwExceptionsDuringProcessWithSuccessfulRetry(
                    dataObject, new StatusRuntimeException(status), RETRY_ATTEMPTS, attemptNumber);
            ServiceException e = assertThrows(ServiceException.class, invocation::run);

            assertTrue(e.getMessage().endsWith(status.getCode().name()));
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
                    new ConfigEntry(GrpcRoutingPlace.GRPC_HOST + ENDPOINT_3_ID, ENDPOINT_HOST),
                    new ConfigEntry(GrpcSampleServicePlace.GRPC_PORT + ENDPOINT_1_ID, getPort(endpointOneServer)),
                    new ConfigEntry(GrpcSampleServicePlace.GRPC_PORT + ENDPOINT_2_ID, getPort(endpointTwoServer)));

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
            samplePlace = placeFactory.buildPlace(
                    new ConfigEntry(GrpcSampleServicePlace.GRPC_PORT + ENDPOINT_1_ID, getPort(endpointOneServer)),
                    new ConfigEntry(GrpcSampleServicePlace.GRPC_PORT + ENDPOINT_2_ID, getPort(endpointTwoServer)));
            dataObject = new BaseDataObject(INPUT_DATA, FILENAME, createSampleForm(ENDPOINT_1_ID));
            samplePlace.process(dataObject);
            byte[] altView = getSampleAltView(dataObject);
            assertArrayEquals(ENDPOINT_1_PROCESSED_DATA, altView);
            assertFalse(Arrays.equals(ENDPOINT_2_PROCESSED_DATA, altView));
        }

        @Test
        void testEndpointTwoServiceRouting() {
            samplePlace = placeFactory.buildPlace(
                    new ConfigEntry(GrpcSampleServicePlace.GRPC_PORT + ENDPOINT_1_ID, getPort(endpointOneServer)),
                    new ConfigEntry(GrpcSampleServicePlace.GRPC_PORT + ENDPOINT_2_ID, getPort(endpointTwoServer)));
            dataObject = new BaseDataObject(INPUT_DATA, FILENAME, createSampleForm(ENDPOINT_2_ID));
            samplePlace.process(dataObject);
            byte[] altView = getSampleAltView(dataObject);
            assertArrayEquals(ENDPOINT_2_PROCESSED_DATA, altView);
            assertFalse(Arrays.equals(ENDPOINT_1_PROCESSED_DATA, altView));
        }

        @Test
        void testInvalidRouting() {
            samplePlace = placeFactory.buildPlace(
                    new ConfigEntry(GrpcSampleServicePlace.GRPC_PORT + ENDPOINT_1_ID, getPort(endpointOneServer)),
                    new ConfigEntry(GrpcSampleServicePlace.GRPC_PORT + ENDPOINT_2_ID, getPort(endpointTwoServer)));
            dataObject = new BaseDataObject(INPUT_DATA, FILENAME, createSampleForm(ENDPOINT_3_ID));
            Runnable invocation = () -> samplePlace.process(dataObject);
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, invocation::run);
            assertEquals(String.format("Target-ID %s was never configured", ENDPOINT_3_ID), e.getMessage());
        }
    }

    @Nested
    class SynchronicityTests {
        private static final String SYNCHRONICITY_1_ID = "SYNCHRONICITY_ENDPOINT_1";
        private static final String SYNCHRONICITY_2_ID = "SYNCHRONICITY_ENDPOINT_2";

        private Server startSynchronicityServer(CountDownLatch startedLatch, CountDownLatch releaseLatch) throws IOException {
            return ServerBuilder.forPort(0)
                    .addService(new GrpcSampleServiceImpl.CountdownLatchedIdentityServiceImpl(startedLatch, releaseLatch))
                    .build()
                    .start();
        }

        @Test
        void testBlockedResponsesAreProcessedSequentially() throws IOException, InterruptedException {
            Server synchronicityOneServer = null;
            Server synchronicityTwoServer = null;

            try {
                CountDownLatch startedLatchOne = new CountDownLatch(1);
                CountDownLatch releaseLatchOne = new CountDownLatch(1);
                synchronicityOneServer = startSynchronicityServer(startedLatchOne, releaseLatchOne);

                CountDownLatch startedLatchTwo = new CountDownLatch(1);
                CountDownLatch releaseLatchTwo = new CountDownLatch(1);
                synchronicityTwoServer = startSynchronicityServer(startedLatchTwo, releaseLatchTwo);

                samplePlace = placeFactory.buildPlace(
                        new ConfigEntry(GrpcSampleServicePlace.GRPC_HOST + ENDPOINT_1_ID, null),
                        new ConfigEntry(GrpcSampleServicePlace.GRPC_HOST + ENDPOINT_2_ID, null),
                        new ConfigEntry(GrpcSampleServicePlace.GRPC_HOST + SYNCHRONICITY_1_ID, ENDPOINT_HOST),
                        new ConfigEntry(GrpcSampleServicePlace.GRPC_HOST + SYNCHRONICITY_2_ID, ENDPOINT_HOST),
                        new ConfigEntry(GrpcSampleServicePlace.GRPC_PORT + SYNCHRONICITY_1_ID, getPort(synchronicityOneServer)),
                        new ConfigEntry(GrpcSampleServicePlace.GRPC_PORT + SYNCHRONICITY_2_ID, getPort(synchronicityTwoServer)));

                IBaseDataObject o = new BaseDataObject(INPUT_DATA, FILENAME);

                AtomicReference<Map<String, byte[]>> viewRef = new AtomicReference<>();
                AtomicReference<Throwable> errorRef = new AtomicReference<>();

                Thread clientThread = new Thread(() -> {
                    try {
                        samplePlace.processAllTargetsSequentially(o);
                        viewRef.set(o.getAlternateViews());
                    } catch (Throwable t) {
                        errorRef.set(t);
                    }
                });

                clientThread.start();

                assertTrue(startedLatchOne.await(1, TimeUnit.SECONDS), "First server should have received request");
                assertEquals(1L, startedLatchTwo.getCount(), "Second server should be waiting to receive request");
                assertNull(viewRef.get());
                assertNull(errorRef.get());

                assertTrue(clientThread.isAlive());
                releaseLatchOne.countDown();

                assertTrue(startedLatchTwo.await(1, TimeUnit.SECONDS));
                assertNull(viewRef.get());
                assertNull(errorRef.get());

                assertTrue(clientThread.isAlive());
                releaseLatchTwo.countDown();

                clientThread.join(1000); // Wait for thread to die
                assertFalse(clientThread.isAlive());

                assertEquals(2, viewRef.get().size());
                assertArrayEquals(INPUT_DATA, viewRef.get().get(SYNCHRONICITY_1_ID));
                assertArrayEquals(INPUT_DATA, viewRef.get().get(SYNCHRONICITY_2_ID));
                assertNull(errorRef.get());
            } finally {
                if (synchronicityOneServer != null) {
                    synchronicityOneServer.shutdownNow();
                }
                if (synchronicityTwoServer != null) {
                    synchronicityTwoServer.shutdownNow();
                }
            }
        }

        @Test
        void testFutureResponsesAreProcessedAsynchronously() throws IOException, InterruptedException {
            Server synchronicityOneServer = null;
            Server synchronicityTwoServer = null;

            try {
                CountDownLatch startedLatch = new CountDownLatch(2);
                CountDownLatch releaseLatch = new CountDownLatch(1);

                synchronicityOneServer = startSynchronicityServer(startedLatch, releaseLatch);
                synchronicityTwoServer = startSynchronicityServer(startedLatch, releaseLatch);

                samplePlace = placeFactory.buildPlace(
                        new ConfigEntry(GrpcSampleServicePlace.GRPC_HOST + ENDPOINT_1_ID, null),
                        new ConfigEntry(GrpcSampleServicePlace.GRPC_HOST + ENDPOINT_2_ID, null),
                        new ConfigEntry(GrpcSampleServicePlace.GRPC_HOST + SYNCHRONICITY_1_ID, ENDPOINT_HOST),
                        new ConfigEntry(GrpcSampleServicePlace.GRPC_HOST + SYNCHRONICITY_2_ID, ENDPOINT_HOST),
                        new ConfigEntry(GrpcSampleServicePlace.GRPC_PORT + SYNCHRONICITY_1_ID, getPort(synchronicityOneServer)),
                        new ConfigEntry(GrpcSampleServicePlace.GRPC_PORT + SYNCHRONICITY_2_ID, getPort(synchronicityTwoServer)));

                IBaseDataObject o = new BaseDataObject(INPUT_DATA, FILENAME);

                AtomicReference<Map<String, byte[]>> viewRef = new AtomicReference<>();
                AtomicReference<Throwable> errorRef = new AtomicReference<>();

                Thread clientThread = new Thread(() -> {
                    try {
                        samplePlace.processAllTargetsAsynchronously(o);
                        viewRef.set(o.getAlternateViews());
                    } catch (Throwable t) {
                        errorRef.set(t);
                    }
                });

                clientThread.start();

                assertTrue(startedLatch.await(2, TimeUnit.SECONDS), "Not all servers received requests");
                assertNull(viewRef.get());
                assertNull(errorRef.get());

                assertTrue(clientThread.isAlive());
                releaseLatch.countDown();

                clientThread.join(1000); // Wait for thread to die
                assertFalse(clientThread.isAlive());

                assertEquals(2, viewRef.get().size());
                assertArrayEquals(INPUT_DATA, viewRef.get().get(SYNCHRONICITY_1_ID));
                assertArrayEquals(INPUT_DATA, viewRef.get().get(SYNCHRONICITY_2_ID));
                assertNull(errorRef.get());
            } finally {
                if (synchronicityOneServer != null) {
                    synchronicityOneServer.shutdownNow();
                }
                if (synchronicityTwoServer != null) {
                    synchronicityTwoServer.shutdownNow();
                }
            }
        }
    }
}
