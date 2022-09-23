package emissary.place;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Map;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.core.Namespace;
import emissary.core.ResourceWatcher;
import emissary.test.core.junit5.FunctionalTest;
import emissary.util.io.ResourceReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FTestCoordinatePlace extends FunctionalTest {

    private CoordinationPlace place;
    private Configurator config;

    @Override
    @BeforeEach
    public void setUp() throws Exception {


        InputStream configStream = new ResourceReader().getConfigDataAsStream(this);
        place = new CoordinationPlace(configStream);
        config = ConfigUtil.getConfigInfo(new ResourceReader().getConfigDataAsStream(this));
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        demolishServer();
    }

    @Test
    void testRequiredPlaceCreation() throws Exception {
        for (String key : config.findEntries("SERVICE_COORDINATION")) {
            assertNotNull(Namespace.lookup(key)); // will throw exception if not present
        }

    }

    @Test
    void testProcessing() throws Exception {
        IBaseDataObject payload = DataObjectFactory.getInstance("test data".getBytes(), "test.dat", place.getPrimaryProxy());
        place.processHeavyDuty(payload);
        assertEquals(config.findStringEntry("OUTPUT_FORM"), payload.currentForm(), "Current form must be set by coordinate place");
    }

    @Test
    void testProcessingWithResourceTracking() throws Exception {
        IBaseDataObject payload = DataObjectFactory.getInstance("test data".getBytes(), "test.dat", place.getPrimaryProxy());
        ResourceWatcher rw = new ResourceWatcher();
        place.processHeavyDuty(payload);
        assertEquals(config.findStringEntry("OUTPUT_FORM"), payload.currentForm(), "Current form must be set by coordinate place");
        Map<String, com.codahale.metrics.Timer> resourcesUsed = rw.getStats();
        rw.quit();
        assertTrue(resourcesUsed.containsKey("ToLowerPlace"), "Resource must be tracked for coordinated place");
        assertTrue(resourcesUsed.containsKey("ToUpperPlace"), "Resource must be tracked for coordinated place");
        assertFalse(resourcesUsed.containsKey("CoordinationPlace"), "Resource must not be tracked for container place");
    }

}
