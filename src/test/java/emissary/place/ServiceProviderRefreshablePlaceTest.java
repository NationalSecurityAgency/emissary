package emissary.place;

import emissary.core.IBaseDataObject;
import emissary.core.Namespace;
import emissary.test.core.junit5.UnitTest;

import jakarta.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceProviderRefreshablePlaceTest extends UnitTest {

    private static final byte[] cfgData = ("SERVICE_KEY = \"UNKNOWN.TEST_PLACE.ID.http://localhost:8001/RefreshablePlaceTest$6050\"\n" +
            "KEY_1 = 200").getBytes();

    @Nullable
    private RefreshablePlaceTest place = null;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        InputStream config = new ByteArrayInputStream(cfgData);
        place = new RefreshablePlaceTest(config, null, "http://localhost:8001/RefreshablePlaceTest");
        assertNotNull(place);
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
    void invalidate() {
        assertFalse(place.isInvalidated());
        place.invalidate();
        assertTrue(place.isInvalidated());
    }

    @Test
    void refreshErrs() {
        // need to set invalidate first
        assertThrows(IllegalStateException.class, () -> place.refresh(false, false));

        place.invalidate();
        assertDoesNotThrow(() -> place.refresh(false, false));

        // now defunct
        assertThrows(IllegalStateException.class, () -> place.refresh(false, false));
    }

    @Test
    void refresh() throws Exception {
        place.invalidate();
        assertDoesNotThrow(() -> place.refresh(false, false));

        Set<RefreshablePlaceTest> newPlaceSet = Namespace.lookup(RefreshablePlaceTest.class);
        assertEquals(1, newPlaceSet.size());

        RefreshablePlaceTest newPlace = newPlaceSet.stream().findFirst().get();
        assertEquals("300", newPlace.configG.findStringEntry("KEY_1"));
        assertEquals("UNKNOWN.TEST_PLACE.ID.http://localhost:8001/RefreshablePlaceTest", newPlace.getKey());
    }

    @Test
    void force_refresh() throws Exception {
        place.invalidate();
        assertDoesNotThrow(() -> place.refresh(true, false));

        Set<RefreshablePlaceTest> newPlaceSet = Namespace.lookup(RefreshablePlaceTest.class);
        assertEquals(1, newPlaceSet.size());

        RefreshablePlaceTest newPlace = newPlaceSet.stream().findFirst().get();
        assertEquals("300", newPlace.configG.findStringEntry("KEY_1"));
        assertEquals("*.TEST_PLACE.ID.http://localhost:8001/RefreshablePlaceTest", newPlace.getKey());
    }

    public static final class RefreshablePlaceTest extends ServiceProviderRefreshablePlace {

        public RefreshablePlaceTest(InputStream config, @Nullable String dir, @Nullable String loc) throws IOException {
            super(config, dir, loc);
        }

        public RefreshablePlaceTest(final ServiceProviderRefreshablePlace place, final boolean register) throws IOException {
            super(place, register);
        }

        @Override
        public void process(IBaseDataObject d) {
            assertNotNull(d);
        }
    }
}
