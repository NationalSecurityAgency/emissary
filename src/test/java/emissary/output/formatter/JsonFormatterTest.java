package emissary.output.formatter;

import emissary.config.ServiceConfigGuide;
import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonFormatterTest extends UnitTest {

    private ServiceConfigGuide config;
    private JsonFormatter f;
    private IBaseDataObject payload;

    @BeforeEach
    public void setup(@TempDir final Path tmpDir) {
        config = new ServiceConfigGuide();
        config.removeAllEntries("OUTPUT_PATH");
        config.addEntry("OUTPUT_PATH", tmpDir.toAbsolutePath().toString());

        f = new JsonFormatter();

        payload = DataObjectFactory.getInstance();
        payload.setData("This is the data".getBytes());
        payload.setFileType("FTYPE");
        payload.setFilename("/this/is/a/testfile");
        payload.appendParameter("FOO", "bar");
    }

    @Test
    void testFormatterSetup() {
        f.initialize(config, "FOO", config);
        assertEquals("FOO", f.getName(), "Formatter name should be set");
    }

    @Test
    void testOutput() {
        f.initialize(config, "FOO", config);
        assertEquals(IDropOffFormatter.STATUS_SUCCESS, f.write(Collections.singletonList(payload), new HashMap<>()), "Write should succeed");
    }

    @Test
    void testSharedMetadataDenyShapesOutput() throws Exception {
        config.addEntry("DENYLIST_FIELD", "FOO");
        f.initialize(config, "FOO", config);
        byte[] json = f.convert(Collections.singletonList(payload), new HashMap<>());
        String s = new String(json, StandardCharsets.UTF_8);
        assertFalse(s.contains("\"FOO\""), "Denylisted parameter should be omitted from JSON");
    }

    @Test
    void testNonDenylistedParameterIsOutput() throws Exception {
        config.addEntry("DENYLIST_FIELD", "BAR");
        f.initialize(config, "FOO", config);
        byte[] json = f.convert(Collections.singletonList(payload), new HashMap<>());
        String s = new String(json, StandardCharsets.UTF_8);
        assertTrue(s.contains("\"FOO\""), "Allowed parameter should be present in JSON");
    }

    @Test
    void testWriteToStreamsDirectly() throws Exception {
        f.initialize(config, "FOO", config);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        f.writeTo(buf, Collections.singletonList(payload), new HashMap<>());
        String s = buf.toString(StandardCharsets.UTF_8.name());
        assertTrue(s.contains("\"FOO\""), "writeTo should produce valid JSON with allowed parameters");
        assertTrue(s.contains("\"payload\""), "writeTo should include payload when emitPayload is true");
    }

    @Test
    void testContentDenyFiltersViews() throws Exception {
        config.addEntry("DENYLIST", "JSON_ML");
        config.addEntry("DENYLIST", "JSON_LANG_*");
        f.initialize(config, "FOO", config);

        payload.addAlternateView("JSON_PRETTY", "pretty".getBytes());
        payload.addAlternateView("JSON_ML", "ml".getBytes());
        payload.addAlternateView("JSON_LANG_ENG", "eng".getBytes());

        byte[] json = f.convert(Collections.singletonList(payload), new HashMap<>());
        String s = new String(json, StandardCharsets.UTF_8);

        assertTrue(s.contains("\"JSON_PRETTY\""), "Allowed view should be present");
        assertFalse(s.contains("\"JSON_ML\""), "Denylisted view should be omitted");
        assertFalse(s.contains("\"JSON_LANG_ENG\""), "Wildcard-denied view should be omitted");
    }

    @Test
    void testContentAcceptsAllByDefault() throws Exception {
        f.initialize(config, "FOO", config);

        payload.addAlternateView("JSON_PRETTY", "pretty".getBytes());
        payload.addAlternateView("JSON_ML", "ml".getBytes());

        byte[] json = f.convert(Collections.singletonList(payload), new HashMap<>());
        String s = new String(json, StandardCharsets.UTF_8);

        assertTrue(s.contains("\"JSON_PRETTY\""), "All views should be present when no deny list configured");
        assertTrue(s.contains("\"JSON_ML\""), "All views should be present when no deny list configured");
    }
}
