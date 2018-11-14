package emissary.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.core.Namespace;
import emissary.core.ResourceWatcher;
import emissary.place.sample.ToLowerPlace;
import emissary.test.core.FunctionalTest;
import emissary.util.io.ResourceReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FTestSkippingCoordinationPlace extends FunctionalTest {
    private CoordinationPlace place;
    private Configurator config;

    @Override
    @Before
    public void setUp() throws Exception {


        InputStream configStream = new ResourceReader().getConfigDataAsStream(this);
        place = new CoordinationSkipperPlace(configStream);
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
        assertFalse("Resource must not be tracked for coordinated place which should be skipped", resourcesUsed.containsKey("ToLowerPlace"));
        assertTrue("Resource must be tracked for coordinated place", resourcesUsed.containsKey("ToUpperPlace"));
        assertFalse("Resource must not be tracked for container place", resourcesUsed.containsKey("CoordinationPlace"));
    }

    // extending base coordination class to test the overridden methods in FTest
    private static final class CoordinationSkipperPlace extends CoordinationPlace {
        public CoordinationSkipperPlace(InputStream cfgInfo) throws IOException {
            super(cfgInfo);
        }

        @Override
        protected boolean shouldSkip(IBaseDataObject d, IServiceProviderPlace p) {
            // this would have to be test in state to allow specific places to skipped

            // skipping ToLowerPlace
            if (p instanceof ToLowerPlace) {
                // apply any condition for the ToLowerPlace case, but always return false for testing purpose
                return true;
            }

            // all the other places should return false
            return false;
        }
    }
}
