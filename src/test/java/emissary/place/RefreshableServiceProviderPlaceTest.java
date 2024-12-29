package emissary.place;

import emissary.core.IBaseDataObject;
import emissary.core.Namespace;
import emissary.directory.DirectoryEntry;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.annotation.Nullable;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshableServiceProviderPlaceTest extends UnitTest {

    private static final byte[] cfgData = ("SERVICE_KEY = \"UNKNOWN.TEST_PLACE.ID.http://localhost:8001/RefreshablePlaceTest$6050\"\n" +
            "KEY_1 = 200").getBytes();
    private static final byte[] cfgDataReload = ("SERVICE_KEY = \"*.TEST_PLACE.ID.http://localhost:8001/RefreshablePlaceTest$5060\"\n" +
            "KEY_1 = 300").getBytes();

    @Nullable
    private RefreshablePlaceTest place = null;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        InputStream config = new ByteArrayInputStream(cfgData);
        place = new RefreshablePlaceTest(config, null, "http://localhost:8001/RefreshablePlaceTest");
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        assertNotNull(place);
        place.shutDown();
        place = null;
    }

    @Test
    void testReconfigure() {
        assertNotNull(place, "Place created and configured");
        assertEquals("RefreshablePlaceTest", place.getPlaceName(), "Configured place name");
        assertEquals("UNKNOWN", place.getPrimaryProxy(), "Primary proxy");
        assertEquals("UNKNOWN.TEST_PLACE.ID.http://localhost:8001/RefreshablePlaceTest", place.getKey(), "Key generation");
        DirectoryEntry de = place.getDirectoryEntry();
        assertNotNull(de, "Directory entry");
        assertEquals(60, de.getCost(), "Cost in directory entry");
        assertEquals(50, de.getQuality(), "Quality in directory entry");
        assertEquals("Description not available", de.getDescription(), "Description in directory entry");
        assertNotNull(place.configG);
        assertEquals(200, place.configG.findIntEntry("KEY_1", 0));
        assertDoesNotThrow(() -> Namespace.lookup("http://localhost:8001/RefreshablePlaceTest"));

        place.invalidate();
        place.refresh(new ByteArrayInputStream(cfgDataReload));
        assertNotNull(place, "Place created and configured");
        assertEquals("RefreshablePlaceTest", place.getPlaceName(), "Configured place name");
        // assertEquals("*", placeTest.getPrimaryProxy(), "Primary proxy");
        assertEquals("UNKNOWN", place.getPrimaryProxy(), "Primary proxy");
        // assertEquals("*.TEST_PLACE.ID.http://localhost:8001/PlaceTest", placeTest.getKey(), "Key generation");
        assertEquals("UNKNOWN.TEST_PLACE.ID.http://localhost:8001/RefreshablePlaceTest", place.getKey(), "Key generation");
        de = place.getDirectoryEntry();
        assertNotNull(de, "Directory entry");
        // assertEquals(50, de.getCost(), "Cost in directory entry");
        assertEquals(60, de.getCost(), "Cost in directory entry");
        // assertEquals(40, de.getQuality(), "Quality in directory entry");
        assertEquals(50, de.getQuality(), "Quality in directory entry");
        assertEquals("Description not available", de.getDescription(), "Description in directory entry");
        assertNotNull(place.configG);
        assertEquals(300, place.configG.findIntEntry("KEY_1", 0));
        assertDoesNotThrow(() -> Namespace.lookup("http://localhost:8001/RefreshablePlaceTest"));
    }

    @Test
    void testInvalidate() {
        assertNotNull(place, "Place created and configured");
        assertFalse(place.isInvalidated());
        assertNotNull(place.configG);
        assertEquals(200, place.configG.findIntEntry("KEY_1", 0));

        place.refresh(new ByteArrayInputStream(cfgDataReload));
        assertFalse(place.isInvalidated());
        assertEquals(200, place.configG.findIntEntry("KEY_1", 0));

        place.invalidate();
        assertTrue(place.isInvalidated());
        place.refresh(new ByteArrayInputStream(cfgDataReload));
        assertFalse(place.isInvalidated());
        assertEquals(300, place.configG.findIntEntry("KEY_1", 0));
    }

    private static final class RefreshablePlaceTest extends ServiceProviderRefreshablePlace {

        public RefreshablePlaceTest(InputStream config, @Nullable String dir, @Nullable String loc) throws IOException {
            super(config, dir, loc);
        }

        @Override
        public void process(IBaseDataObject d) {
            assertNotNull(d);
        }

        @Override
        protected void reconfigurePlace() {}
    }
}
