package emissary.output.formatter;

import emissary.config.ServiceConfigGuide;
import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.output.sink.JsonSink;
import emissary.output.sink.WriteStatus;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;

import static java.nio.charset.StandardCharsets.UTF_8;
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
        payload.setData("This is the data".getBytes(UTF_8));
        payload.setFileType("FTYPE");
        payload.setFilename("/this/is/a/testfile");
        payload.appendParameter("FOO", "bar");
    }

    @Test
    void testSetup() {
        f.initialize(config, "FOO", config);
        assertEquals("FOO", f.getName(), "Formatter name should be set");
    }

    @Test
    void testSinkWrite() {
        JsonSink sink = new JsonSink();
        sink.initialize(config, "FOO", config);
        assertEquals(WriteStatus.SUCCESS,
                sink.write(Collections.singletonList(payload), new HashMap<>()), "Write should succeed");
        sink.close();
    }

    @Test
    void testParamDeny() throws Exception {
        config.addEntry("OUTPUT_FILTER", "emissary.output.formatter.filter.FilterOutput");
        config.addEntry("PARAMS", "FOO");
        f.initialize(config, "FOO", config);
        byte[] json = f.convert(Collections.singletonList(payload), new HashMap<>());
        String s = new String(json, UTF_8);
        assertFalse(s.contains("\"FOO\""), "Denylisted parameter should be omitted from JSON");
    }

    @Test
    void testParamAllow() throws Exception {
        config.addEntry("OUTPUT_FILTER", "emissary.output.formatter.filter.FilterOutput");
        config.addEntry("PARAMS", "BAR");
        f.initialize(config, "FOO", config);
        byte[] json = f.convert(Collections.singletonList(payload), new HashMap<>());
        String s = new String(json, UTF_8);
        assertTrue(s.contains("\"FOO\""), "Allowed parameter should be present in JSON");
    }

    @Test
    void testWriteToStream() throws Exception {
        f.initialize(config, "FOO", config);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        f.writeTo(buf, Collections.singletonList(payload), new HashMap<>());
        String s = buf.toString(UTF_8.name());
        assertTrue(s.contains("\"FOO\""), "writeTo should produce valid JSON with allowed parameters");
        assertTrue(s.contains("\"payload\""), "writeTo should include payload when emitPayload is true");
    }

    @Test
    void testViewDeny() throws Exception {
        config.addEntry("OUTPUT_FILTER", "emissary.output.formatter.filter.FilterOutput");
        config.addEntry("VIEWS", "JSON_ML");
        config.addEntry("VIEWS", "JSON_LANG_*");
        f.initialize(config, "FOO", config);

        payload.addAlternateView("JSON_PRETTY", "pretty".getBytes(UTF_8));
        payload.addAlternateView("JSON_ML", "ml".getBytes(UTF_8));
        payload.addAlternateView("JSON_LANG_ENG", "eng".getBytes(UTF_8));

        byte[] json = f.convert(Collections.singletonList(payload), new HashMap<>());
        String s = new String(json, UTF_8);

        assertTrue(s.contains("\"JSON_PRETTY\""), "Allowed view should be present");
        assertFalse(s.contains("\"JSON_ML\""), "Denylisted view should be omitted");
        assertFalse(s.contains("\"JSON_LANG_ENG\""), "Wildcard-denied view should be omitted");
    }

    @Test
    void testViewsByDefault() throws Exception {
        f.initialize(config, "FOO", config);

        payload.addAlternateView("JSON_PRETTY", "pretty".getBytes(UTF_8));
        payload.addAlternateView("JSON_ML", "ml".getBytes(UTF_8));

        byte[] json = f.convert(Collections.singletonList(payload), new HashMap<>());
        String s = new String(json, UTF_8);

        assertTrue(s.contains("\"JSON_PRETTY\""), "All views should be present when no deny list configured");
        assertTrue(s.contains("\"JSON_ML\""), "All views should be present when no deny list configured");
    }

    @Test
    void testMaxViewSize() throws Exception {
        config.addEntry("OUTPUT_FILTER", "emissary.output.formatter.filter.FilterMaxViewSize");
        config.addEntry("CONTENT_MAX_VIEW_SIZE", "5");
        f.initialize(config, "FOO", config);

        payload.addAlternateView("SMALL", "abc".getBytes(UTF_8));
        payload.addAlternateView("LARGE", "abcdefghijk".getBytes(UTF_8));

        byte[] json = f.convert(Collections.singletonList(payload), new HashMap<>());
        String s = new String(json, UTF_8);

        assertTrue(s.contains("\"SMALL\""), "View within the size limit should be present");
        assertFalse(s.contains("\"LARGE\""), "Oversized view should be omitted");
    }

    @Test
    void testMultiValueParamKeepsAllowedValues() throws Exception {
        config.addEntry("OUTPUT_FILTER", "emissary.output.formatter.filter.FilterOutput");
        config.addEntry("PARAM_VALUE_FOO", "bad");
        f.initialize(config, "FOO", config);

        payload.appendParameter("FOO", "good");
        payload.appendParameter("FOO", "bad");

        byte[] json = f.convert(Collections.singletonList(payload), new HashMap<>());
        String s = new String(json, UTF_8);

        assertTrue(s.contains("\"good\""), "Allowed value should be present");
        assertFalse(s.contains("\"bad\""), "Denied value should be omitted");
    }

    @Test
    void testMaxValueSize() throws Exception {
        config.addEntry("OUTPUT_FILTER", "emissary.output.formatter.filter.FilterMaxValueSize");
        config.addEntry("METADATA_MAX_VALUE_SIZE", "2");
        f.initialize(config, "FOO", config);

        byte[] json = f.convert(Collections.singletonList(payload), new HashMap<>());
        String s = new String(json, UTF_8);

        assertFalse(s.contains("\"FOO\""), "Value exceeding the size limit should be omitted");
    }

    @Test
    void testEmitMetadata() throws Exception {
        config.addEntry("EMIT", "metadata");
        f.initialize(config, "FOO", config);

        payload.addAlternateView("JSON_PRETTY", "pretty".getBytes(UTF_8));

        byte[] json = f.convert(Collections.singletonList(payload), new HashMap<>());
        String s = new String(json, UTF_8);

        assertFalse(s.contains("\"payload\""), "Payload should be omitted in metadata mode");
        assertFalse(s.contains("\"JSON_PRETTY\""), "Views should be omitted in metadata mode");
        assertTrue(s.contains("\"FOO\""), "Parameters should still be output in metadata mode");
    }

    @Test
    void testContentOnly() throws Exception {
        config.addEntry("OUTPUT_FILTER", "emissary.output.formatter.filter.FilterOutput");
        config.addEntry("PARAMS", "*");
        f.initialize(config, "FOO", config);

        payload.addAlternateView("JSON_PRETTY", "pretty".getBytes(UTF_8));

        byte[] json = f.convert(Collections.singletonList(payload), new HashMap<>());
        String s = new String(json, UTF_8);

        assertTrue(s.contains("\"payload\""), "Payload should be output in content-only mode");
        assertTrue(s.contains("\"JSON_PRETTY\""), "Views should be output in content-only mode");
        assertFalse(s.contains("\"FOO\""), "Denied parameters should be omitted");
    }

    @Test
    void testEmitContent() throws Exception {
        config.addEntry("EMIT", "content");
        f.initialize(config, "FOO", config);

        payload.addAlternateView("JSON_PRETTY", "pretty".getBytes(UTF_8));

        byte[] json = f.convert(Collections.singletonList(payload), new HashMap<>());
        String s = new String(json, UTF_8);

        assertTrue(s.contains("\"payload\""), "Payload should be output in content mode");
        assertTrue(s.contains("\"JSON_PRETTY\""), "Views should be output in content mode");
        assertFalse(s.contains("\"FOO\""), "Parameters should be omitted in content mode even without filters");
    }
}
