package emissary.place;

import emissary.core.BaseDataObject;
import emissary.core.IBaseDataObject;
import emissary.test.core.junit5.LogbackTester;
import emissary.test.core.junit5.LogbackTester.SimplifiedLogEvent;
import emissary.test.core.junit5.UnitTest;

import ch.qos.logback.classic.Level;
import jakarta.annotation.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ComparisonPlaceTest extends UnitTest {
    private static final String TEST_FILENAME_FORM = "TEST";
    private static final String BAD_PLACE_A_CLASS_NAME = "emissary.place.ComparisonPlaceTest.BadPlaceAClassName";
    private static final String BAD_PLACE_A_CONFIG_NAME = "emissary.place.ComparisonPlaceTest.BadPlaceAConfigName";
    private static final String BAD_PLACE_B_CLASS_NAME = "emissary.place.ComparisonPlaceTest.BadPlaceBClassName";
    private static final String BAD_PLACE_B_CONFIG_NAME = "emissary.place.ComparisonPlaceTest.BadPlaceBConfigName";
    private static final String MISSING_LOGGING_IDENTIFIER = "emissary.place.ComparisonPlaceTest.MissingLoggingIdentifier";
    private static final String MISSING_PLACE_A_CLASS_NAME = "emissary.place.ComparisonPlaceTest.MissingPlaceAClassName";
    private static final String MISSING_PLACE_A_CONFIG_NAME = "emissary.place.ComparisonPlaceTest.MissingPlaceAConfigName";
    private static final String MISSING_PLACE_B_CLASS_NAME = "emissary.place.ComparisonPlaceTest.MissingPlaceBClassName";
    private static final String MISSING_PLACE_B_CONFIG_NAME = "emissary.place.ComparisonPlaceTest.MissingPlaceBConfigName";
    private static final String PROCESS_PLACE_PROCESSHD_PLACE = "emissary.place.ComparisonPlaceTest.ProcessPlaceProcessHDPlace";
    private static final String PROCESS_PLACE_NO_CHANGES = "emissary.place.ComparisonPlaceTest.ProcessPlaceNoChanges";
    private static final String PROCESS_PLACE_A_CHANGES = "emissary.place.ComparisonPlaceTest.ProcessPlaceAChanges";
    private static final String PROCESS_PLACE_B_CHANGES = "emissary.place.ComparisonPlaceTest.ProcessPlaceBChanges";
    private static final String PROCESSHD_PLACE_NO_CHANGES = "emissary.place.ComparisonPlaceTest.ProcessHDPlaceNoChanges";
    private static final String PROCESSHD_PLACE_A_CHANGES = "emissary.place.ComparisonPlaceTest.ProcessHDPlaceAChanges";
    private static final String PROCESSHD_PLACE_B_CHANGES = "emissary.place.ComparisonPlaceTest.ProcessHDPlaceBChanges";

    @Test
    void testConfiguration() throws Exception {
        assertThrows(NullPointerException.class, () -> new ComparisonPlace(null, null, MISSING_LOGGING_IDENTIFIER));
        assertThrows(NullPointerException.class, () -> new ComparisonPlace(null, null, MISSING_PLACE_A_CLASS_NAME));
        assertThrows(NullPointerException.class, () -> new ComparisonPlace(null, null, MISSING_PLACE_A_CONFIG_NAME));
        assertThrows(NullPointerException.class, () -> new ComparisonPlace(null, null, MISSING_PLACE_B_CLASS_NAME));
        assertThrows(NullPointerException.class, () -> new ComparisonPlace(null, null, MISSING_PLACE_B_CONFIG_NAME));

        assertThrows(IOException.class, () -> new ComparisonPlace(null, null, BAD_PLACE_A_CLASS_NAME));
        assertThrows(IOException.class, () -> new ComparisonPlace(null, null, BAD_PLACE_A_CONFIG_NAME));
        assertThrows(IOException.class, () -> new ComparisonPlace(null, null, BAD_PLACE_B_CLASS_NAME));
        assertThrows(IOException.class, () -> new ComparisonPlace(null, null, BAD_PLACE_B_CONFIG_NAME));

        assertThrows(IllegalArgumentException.class, () -> new ComparisonPlace(null, null, PROCESS_PLACE_PROCESSHD_PLACE));
    }

    @Test
    void testProcessPlaceNoChanges() throws Exception {
        testComparisonPlace(PROCESS_PLACE_NO_CHANGES, null);
    }

    @Test
    void testProcessPlaceAChanges() throws Exception {
        final String logMessage = "COMPARISONPLACETEST: PDiff: meta are not equal-Differing Keys: [] : [KEY]";

        testComparisonPlace(PROCESS_PLACE_A_CHANGES, logMessage);
    }

    @Test
    void testProcessPlaceBChanges() throws Exception {
        final String logMessage = "COMPARISONPLACETEST: PDiff: meta are not equal-Differing Keys: [KEY] : []";

        testComparisonPlace(PROCESS_PLACE_B_CHANGES, logMessage);
    }

    @Test
    void testProcessHDPlaceNoChanges() throws Exception {
        testComparisonPlace(PROCESSHD_PLACE_NO_CHANGES, null);
    }

    @Test
    void testProcessHDPlaceAChanges() throws Exception {
        final String logMessage = "COMPARISONPLACETEST: PDiff: meta are not equal-Differing Keys: [] : [KEY]\n" +
                "COMPARISONPLACETEST: CDiff: COMPARISONPLACETEST : 0 : meta are not equal-Differing Keys: [] : [KEY]";

        testComparisonPlace(PROCESSHD_PLACE_A_CHANGES, logMessage);
    }

    @Test
    void testProcessHDPlaceBChanges() throws Exception {
        final String logMessage = "COMPARISONPLACETEST: PDiff: meta are not equal-Differing Keys: [KEY] : []\n" +
                "COMPARISONPLACETEST: CDiff: COMPARISONPLACETEST : 0 : meta are not equal-Differing Keys: [KEY] : []";

        testComparisonPlace(PROCESSHD_PLACE_B_CHANGES, logMessage);
    }

    private static void testComparisonPlace(final String configuration, @Nullable final String logMessage) throws Exception {
        try (LogbackTester logbackTester = new LogbackTester(ComparisonPlace.class.getName())) {
            final ComparisonPlace comparisonPlace = new ComparisonPlace(null, null, configuration);
            final byte[] data = configuration.getBytes(StandardCharsets.UTF_8);
            final IBaseDataObject ibdo = new BaseDataObject(data, TEST_FILENAME_FORM, TEST_FILENAME_FORM);
            final List<SimplifiedLogEvent> legEvents = new ArrayList<>();

            if (logMessage != null) {
                legEvents.add(new SimplifiedLogEvent(Level.INFO, logMessage, null));
            }

            comparisonPlace.processHeavyDuty(ibdo);

            logbackTester.checkLogList(legEvents);
        }
    }
}
