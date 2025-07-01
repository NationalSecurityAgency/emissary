package emissary.grpc.sample;

import emissary.config.ConfigEntry;
import emissary.core.BaseDataObject;
import emissary.core.IBaseDataObject;
import emissary.grpc.place.GrpcRouterPlace;
import emissary.grpc.sample.router.LetterCasePlace;
import emissary.grpc.sample.router.LetterCaseServiceImpl;
import emissary.test.core.junit5.UnitTest;
import emissary.test.util.ConfiguredPlaceFactory;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LetterCasePlaceTest extends UnitTest {
    private static final String UPPER_ID = "UPPER";
    private static final String LOWER_ID = "LOWER";
    private static final String CAMEL_ID = "CAMEL";
    private static final String UNSUPPORTED_FORM = "UNSUPPORTED-FORM";
    private static final String DATA = "ArBiTrArY dAtA sTrInG";
    private static final String FILENAME = "123.dat";
    private static final String LOCALHOST = "localhost";
    private static final int UPPER_PORT = 2223;
    private static final int LOWER_PORT = 2224;


    private static final Server upperCaseServer = ServerBuilder.forPort(UPPER_PORT)
            .addService(new LetterCaseServiceImpl.UpperCaseServiceImpl())
            .build();

    private static final Server lowerCaseServer = ServerBuilder.forPort(LOWER_PORT)
            .addService(new LetterCaseServiceImpl.LowerCaseServiceImpl())
            .build();

    private final ConfiguredPlaceFactory<LetterCasePlace> factory = new ConfiguredPlaceFactory<>(LetterCasePlace.class);
    private LetterCasePlace place;
    private IBaseDataObject data;

    @BeforeEach
    void initializeData() {
        data = new BaseDataObject(DATA.getBytes(), FILENAME);
    }

    @BeforeAll
    static void startServers() throws IOException {
        upperCaseServer.start();
        lowerCaseServer.start();
    }

    @AfterAll
    static void stopServer() {
        upperCaseServer.shutdownNow();
        lowerCaseServer.shutdownNow();
    }

    @Test
    void testMismatchedHostPortConfigs() {
        Runnable invocation = () -> factory.buildPlace(new ConfigEntry(GrpcRouterPlace.GRPC_HOST + CAMEL_ID, LOCALHOST));
        IllegalStateException e = assertThrows(IllegalStateException.class, invocation::run);
        String nestedErrorMessage = e.getCause().getCause().getMessage();
        assertEquals("gRPC hostname target-IDs do not match gRPC port number target-IDs", nestedErrorMessage);
    }

    @Test
    void testNoHostPortConfigs() {
        Runnable invocation = () -> factory.buildPlace(Set.of(
                GrpcRouterPlace.GRPC_HOST + UPPER_ID,
                GrpcRouterPlace.GRPC_HOST + LOWER_ID,
                GrpcRouterPlace.GRPC_PORT + UPPER_ID,
                GrpcRouterPlace.GRPC_PORT + LOWER_ID));
        IllegalStateException e = assertThrows(IllegalStateException.class, invocation::run);
        String nestedErrorMessage = e.getCause().getCause().getMessage();
        assertEquals("Missing required arguments: GRPC_HOST_{Target-ID} and GRPC_PORT_{Target-ID}", nestedErrorMessage);
    }

    @Test
    void testUpperCaseRouting() {
        place = factory.buildPlace();
        data.setCurrentForm(LetterCasePlace.FORM_PREFIX + UPPER_ID);
        place.process(data);
        String altView = new String(data.getAlternateView(LetterCasePlace.ALTERNATE_VIEW_NAME));
        assertEquals(DATA.toUpperCase(Locale.ROOT), altView);
        assertNotEquals(DATA.toLowerCase(Locale.ROOT), altView);
    }

    @Test
    void testLowerCaseRouting() {
        place = factory.buildPlace();
        data.setCurrentForm(LetterCasePlace.FORM_PREFIX + LOWER_ID);
        place.process(data);
        String altView = new String(data.getAlternateView(LetterCasePlace.ALTERNATE_VIEW_NAME));
        assertEquals(DATA.toLowerCase(Locale.ROOT), altView);
        assertNotEquals(DATA.toUpperCase(Locale.ROOT), altView);
    }

    @Test
    void testInvalidRouting() {
        place = factory.buildPlace();
        data.setCurrentForm(LetterCasePlace.FORM_PREFIX + CAMEL_ID);
        Runnable invocation = () -> place.process(data);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, invocation::run);
        assertEquals(String.format("Target-ID %s was never configured", CAMEL_ID), e.getMessage());
    }

    @Test
    void testUnsupportedForm() {
        place = factory.buildPlace();
        data.setCurrentForm(UNSUPPORTED_FORM);
        Runnable invocation = () -> place.process(data);
        IllegalStateException e = assertThrows(IllegalStateException.class, invocation::run);
        assertEquals("Unsupported form type " + UNSUPPORTED_FORM, e.getMessage());
    }
}
