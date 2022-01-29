package emissary.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import emissary.core.EmissaryException;
import emissary.directory.EmissaryNode;
import emissary.test.core.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StartupTest extends UnitTest {

    private Startup placeUnderTest;

    private final String placeKey = "http://localhost:8001/TestPlaceKey";

    @BeforeEach
    public void setup() throws IOException {
        String config = "# The most basic node configuration\n"
                + "LOCAL_DIRECTORY = \"DirectoryPlace:emissary.directory.DirectoryPlace\"\n"
                + "PLACE = \"ToUpperPlace:emissary.place.sample.ToUpperPlace\"\n"
                + "PLACE = \"ToLowerPlace:emissary.place.sample.ToLowerPlace\"\n"
                + "PLACE = \"FilePickUpPlace:emissary.pickup.file.FilePickUpPlace\"\n";
        try (InputStream is = new ByteArrayInputStream(config.getBytes())) {
            placeUnderTest = new Startup(is, new EmissaryNode());
        }
    }

    @AfterEach
    public void tearDown() throws InterruptedException {
        placeUnderTest = null;
    }

    @Test
    void testPlaceHostWithFormattedKey() {
        String expected = "localhost:8001";
        String directoryKey = "FORM.SERVICE_NAME.PHASE." + placeKey + "$3050";
        String result = Startup.placeHost(directoryKey);
        assertEquals(expected, result, "Expected placeHost not returned");
    }

    @Test
    void testPlaceHostWithNotFormattedKey() {
        String expected = "";
        String result = Startup.placeHost(placeKey);
        assertEquals(expected, result, "Expected placeHost not returned");
    }

    @Test
    void testPlaceName() {
        String expected = "TestPlaceKey";
        String result = Startup.placeName(placeKey);
        assertEquals(expected, result, "Expected placeName not returned");
    }

    @Test
    void testPlaceNameNoSlash() {
        String expected = "TestPlaceKey";
        String result = Startup.placeName(expected);
        assertEquals(expected, result, "Expected placeName not returned");
    }

    @Test
    void testSetActionAdd() {
        int expected = 1;
        int result = Startup.setAction("-Add");
        assertEquals(expected, result, "Wrong directory value returned");
    }

    @Test
    void testSetActionDelete() {
        int expected = 2;
        int result = Startup.setAction("-Delete");
        assertEquals(expected, result, "Wrong directory value returned");
    }

    @Test
    void testSetActionStart() {
        int expected = 0;
        int result = Startup.setAction("-start");
        assertEquals(expected, result, "Wrong directory value returned");
    }

    @Test
    void testSetActionDefault() {
        int expected = 0;
        int result = Startup.setAction("unknown");
        assertEquals(expected, result, "Wrong directory value returned");
    }

    @Test
    void testBootstrapFailLocalDirectorySetup() {
        Startup test = spy(placeUnderTest);
        doReturn(false).when(test).localDirectorySetup(anyMap());
        boolean result = test.bootstrap();
        assertFalse(result, "The bootstrap should have failed");
    }

    /**
     * This test will fail when running with the rest of the tests, ignore the others for now until we can figure out how to
     * stop the node between runs
     *
     * @throws EmissaryException
     */
    @Test
    void testStart() throws EmissaryException {
        placeUnderTest.start();
        assertEquals(3, placeUnderTest.places.size(), "No places were started");
        assertEquals(0, placeUnderTest.failedLocalDirectories.size(), "Some places failed to startup");
        assertEquals(1, placeUnderTest.localDirectories.size(), "Number of local directories wasn't correct");
        assertEquals(3, placeUnderTest.places.size(), "Number of places started wasn't correct");
        assertEquals(1, placeUnderTest.pickupLists.size(), "Number of pickup places started wasn't correct");
        assertNotNull(placeUnderTest.localDirectories.get(""), "Local dir key value should be \"\" since it is running locally");
        String localDirKey = "EMISSARY_DIRECTORY_SERVICES.DIRECTORY.STUDY.http://localhost:8001/DirectoryPlace$5050[1]";
        assertEquals(localDirKey, placeUnderTest.localDirectories.get(""), "Local directory key was not the expected value");
        String toUpperKey = "http://localhost:8001/ToUpperPlace";
        assertNotNull(placeUnderTest.places.get(toUpperKey), "ToUpperKey not found");
        assertEquals(toUpperKey, placeUnderTest.places.get(toUpperKey), "ToUpper value should be its key value");
        String toLowerKey = "http://localhost:8001/ToLowerPlace";
        assertNotNull(placeUnderTest.places.get(toLowerKey), "ToLowerKey not found");
        assertEquals(toLowerKey, placeUnderTest.places.get(toLowerKey), "ToLower value should be its key value");
        assertNotNull(placeUnderTest.pickupLists.get(""), "Local pickup list key value should be \"\" since it is running locally");

        List<Startup.PlaceEntry> entryList = placeUnderTest.pickupLists.get("");
        assertEquals(1, entryList.size(), "Should only be one place entry in the pickup list");

        Startup.PlaceEntry entry = entryList.get(0);
        // I believe this is empty because we are local
        assertEquals("", entry.host, "PickupPlace entry host value wrong");
        assertEquals("http://localhost:8001/FilePickUpPlace", entry.getLocation(), "PickupPlace entry placeLocation value wrong");
        assertEquals("emissary.pickup.file.FilePickUpPlace", entry.className, "PickupPlace entry class value wrong");
    }

    @Test
    void testStartBadBootstrap() {
        Startup spiedClass = spy(placeUnderTest);
        doReturn(false).when(spiedClass).bootstrap();
        assertThrows(EmissaryException.class, spiedClass::start);
    }
}
