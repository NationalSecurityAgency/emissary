package emissary.output.filter;

import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.output.stats.ViewOutputStats;
import emissary.test.core.junit5.UnitTest;
import emissary.util.shell.Executrix;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.Marker;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class DataFilterTest extends UnitTest {

    @Test
    void testFilterSetup() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("OUTPUT_SPEC_FOO", "/tmp/%S%.%F%");
        config.addEntry("OUTPUT_SPEC_BAR", "/xyzzy/%S%.%F%");
        IDropOffFilter f = new DataFilter();
        f.initialize(config, "FOO");
        assertEquals("FOO", f.getFilterName(), "Filter name should be set");
        assertEquals("/tmp/%S%.%F%", f.getOutputSpec(), "Output spec should be build based on name");
    }

    @Test
    void testOutputFromFilter() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("OUTPUT_SPEC_FOO", "/tmp/%S%.%F%");
        config.addEntry("OUTPUT_TYPE", "FTYPE.PrimaryView");

        IDropOffFilter f = new DataFilter();
        f.initialize(config, "FOO", config);

        IBaseDataObject payload = DataObjectFactory.getInstance();
        payload.setData("This is the data".getBytes());
        payload.setFileType("FTYPE");
        payload.setFilename("/this/is/a/testfile");

        Map<String, Object> params = new HashMap<>();

        int status = f.filter(payload, params);

        File expected = new File("/tmp/testfile.FTYPE");
        assertEquals(IDropOffFilter.STATUS_SUCCESS, status, "Status of filter should be success");
        assertTrue(expected.exists(), "Output File should exist");

        String output = new String(Executrix.readDataFromFile("/tmp/testfile.FTYPE"));
        assertEquals(new String(payload.data()), output, "Output must be the payload and nothing else");

        expected.delete();
    }

    @Test
    void testViewOutputStatsRecorded() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("OUTPUT_SPEC_FOO", "/tmp/%S%.%F%");
        config.addEntry("OUTPUT_TYPE", "FTYPE.PrimaryView");
        config.addEntry("OUTPUT_TYPE", "FTYPE.GoodView");

        IDropOffFilter f = new DataFilter();
        f.initialize(config, "FOO", config);

        IBaseDataObject payload = DataObjectFactory.getInstance();
        payload.setData("This is the data".getBytes());
        payload.setFileType("FTYPE");
        payload.setFilename("/this/is/a/testfile");
        payload.addAlternateView("GoodView", "good view data".getBytes());
        payload.addAlternateView("BadView", "suppressed view data".getBytes());

        ViewOutputStats stats = new ViewOutputStats(10, TimeUnit.MINUTES);
        stats.startAndBind();
        try {
            int status = f.filter(payload, new HashMap<>());
            assertEquals(IDropOffFilter.STATUS_SUCCESS, status);

            // PrimaryView (OK) + GoodView (OK) + BadView (NOT_OUTPUTTABLE) => three distinct keys.
            // Getting three lines proves the NOT_OUTPUTTABLE branch recorded the suppressed view.
            Logger logger = mock(Logger.class);
            stats.logStats(logger);
            verify(logger, times(3)).info(any(Marker.class), eq(""));
        } finally {
            stats.shutdown();
            new File("/tmp/testfile.FTYPE").delete();
            new File("/tmp/testfile.FTYPE.GoodView").delete();
        }
    }
}
