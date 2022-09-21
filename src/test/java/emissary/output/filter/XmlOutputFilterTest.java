package emissary.output.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import emissary.config.ServiceConfigGuide;
import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.test.core.junit5.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class XmlOutputFilterTest extends UnitTest {

    private ServiceConfigGuide config;
    private IBaseDataObject payload;
    private IDropOffFilter f;
    private Path tmpDir;

    @BeforeEach
    public void setup() throws IOException {
        tmpDir = java.nio.file.Files.createTempDirectory(null);

        config = new ServiceConfigGuide();
        config.removeAllEntries("OUTPUT_PATH");
        config.addEntry("OUTPUT_SPEC_FOO", "/tmp/%S%.%F%");
        config.addEntry("OUTPUT_SPEC_BAR", "/xyzzy/%S%.%F%");
        config.addEntry("OUTPUT_PATH", tmpDir.toAbsolutePath().toString());

        f = new XmlOutputFilter();

        payload = DataObjectFactory.getInstance();
        payload.setData("This is the data".getBytes());
        payload.setFileType("FTYPE");
        payload.setFilename("/this/is/a/testfile");
    }

    @Override
    @AfterEach
    public void tearDown() throws IOException {
        Files.deleteIfExists(tmpDir);
        config = null;
    }

    @Test
    void testFilterSetup() {
        f.initialize(config, "FOO");
        assertEquals("FOO", f.getFilterName(), "Filter name should be set");
        assertEquals("/tmp/%S%.%F%", f.getOutputSpec(), "Output spec should be build based on name");
    }

    @Test
    void testOutputFromFilter() {
        f.initialize(config, "FOO", config);
        List<IBaseDataObject> payloadList = Lists.newArrayList(payload);

        Map<String, Object> params = new HashMap<>();

        assertTrue(f.isOutputtable(payload, params), "Payload should be outputtable");
        assertTrue(f.isOutputtable(payloadList, params), "Payload list should be outputtable");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int status = f.filter(payloadList, params, output);

        assertEquals(IDropOffFilter.STATUS_SUCCESS, status, "Status of filter should be success");
        assertTrue(output.toString().contains("<name>/this/is/a/testfile</name>"), "Output must contain name field '" + output + "'");
    }

}
