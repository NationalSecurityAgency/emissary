package emissary.grpc.sample;

import emissary.core.BaseDataObject;
import emissary.core.IBaseDataObject;
import emissary.grpc.sample.router.LetterCasePlace;
import emissary.grpc.sample.router.LowerCaseServiceImpl;
import emissary.grpc.sample.router.UpperCaseServiceImpl;
import emissary.test.core.junit5.UnitTest;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Locale;

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
    private static final int UPPER_PORT = 2223;
    private static final int LOWER_PORT = 2224;

    private static final Server upperCaseServer = ServerBuilder.forPort(UPPER_PORT)
            .addService(new UpperCaseServiceImpl())
            .build();

    private static final Server lowerCaseServer = ServerBuilder.forPort(LOWER_PORT)
            .addService(new LowerCaseServiceImpl())
            .build();

    private final LetterCasePlace place;
    private IBaseDataObject data;

    LetterCasePlaceTest() throws IOException {
        place = new LetterCasePlace();
    }

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
    void testUpperCaseRouting() {
        data.setCurrentForm(LetterCasePlace.FORM_PREFIX + UPPER_ID);
        place.process(data);
        String altView = new String(data.getAlternateView(LetterCasePlace.ALTERNATE_VIEW_NAME));
        assertEquals(DATA.toUpperCase(Locale.ROOT), altView);
        assertNotEquals(DATA.toLowerCase(Locale.ROOT), altView);
    }

    @Test
    void testLowerCaseRouting() {
        data.setCurrentForm(LetterCasePlace.FORM_PREFIX + LOWER_ID);
        place.process(data);
        String altView = new String(data.getAlternateView(LetterCasePlace.ALTERNATE_VIEW_NAME));
        assertEquals(DATA.toLowerCase(Locale.ROOT), altView);
        assertNotEquals(DATA.toUpperCase(Locale.ROOT), altView);
    }

    @Test
    void testInvalidRouting() {
        data.setCurrentForm(LetterCasePlace.FORM_PREFIX + CAMEL_ID);
        Runnable invocation = () -> place.process(data);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, invocation::run);
        assertEquals(String.format("Target-ID %s was never configured", CAMEL_ID), e.getMessage());
    }

    @Test
    void testUnsupportedForm() {
        data.setCurrentForm(UNSUPPORTED_FORM);
        Runnable invocation = () -> place.process(data);
        IllegalStateException e = assertThrows(IllegalStateException.class, invocation::run);
        assertEquals("Unsupported form type " + UNSUPPORTED_FORM, e.getMessage());
    }
}
