package emissary.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.util.Map;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.core.Namespace;
import emissary.core.ResourceWatcher;
import emissary.test.core.FunctionalTest;
import emissary.util.io.ResourceReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FTestCoordinatePlace extends FunctionalTest {

    private CoordinationPlace place;
    private Configurator config;

    @Override
    @Before
    public void setUp() throws Exception {


        InputStream configStream = new ResourceReader().getConfigDataAsStream(this);
        place = new CoordinationPlace(configStream);
        config = ConfigUtil.getConfigInfo(new ResourceReader().getConfigDataAsStream(this));
    }

    @Override
    @After
    public void tearDown() throws Exception {
        demolishServer();
    }

    @Test
    public void testRequiredPlaceCreation() throws Exception {
        for (String key : config.findEntries("SERVICE_COORDINATION")) {
            Namespace.lookup(key); // will throw exception if not present
        }
    }

    @Test
    public void testProcessing() throws Exception {
        IBaseDataObject payload = DataObjectFactory.getInstance("test data".getBytes(), "test.dat", place.getPrimaryProxy());
        place.processHeavyDuty(payload);
        assertEquals("Current form must be set by coordinate place", config.findStringEntry("OUTPUT_FORM"), payload.currentForm());
    }

    @Test
    public void testProcessingWithResourceTracking() throws Exception {
        IBaseDataObject payload = DataObjectFactory.getInstance("test data".getBytes(), "test.dat", place.getPrimaryProxy());
        ResourceWatcher rw = new ResourceWatcher();
        place.processHeavyDuty(payload);
        assertEquals("Current form must be set by coordinate place", config.findStringEntry("OUTPUT_FORM"), payload.currentForm());
        Map<String, com.codahale.metrics.Timer> resourcesUsed = rw.getStats();
        rw.quit();
        assertTrue("Resource must be tracked for coordinated place", resourcesUsed.containsKey("ToLowerPlace"));
        assertTrue("Resource must be tracked for coordinated place", resourcesUsed.containsKey("ToUpperPlace"));
        assertFalse("Resource must not be tracked for container place", resourcesUsed.containsKey("CoordinationPlace"));
    }

}
