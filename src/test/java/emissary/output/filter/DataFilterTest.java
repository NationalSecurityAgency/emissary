package emissary.output.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.test.core.junit5.UnitTest;
import emissary.util.shell.Executrix;
import org.junit.jupiter.api.Test;

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
        List<IBaseDataObject> payloadList = new ArrayList<>();
        payloadList.add(payload);

        Map<String, Object> params = new HashMap<>();

        int status = f.filter(payload, params);

        File expected = new File("/tmp/testfile.FTYPE");
        assertEquals(IDropOffFilter.STATUS_SUCCESS, status, "Status of filter should be success");
        assertTrue(expected.exists(), "Output File should exist");

        String output = new String(Executrix.readDataFromFile("/tmp/testfile.FTYPE"));
        assertEquals(new String(payload.data()), output, "Output must be the payload and nothing else");

        expected.delete();
    }

}
