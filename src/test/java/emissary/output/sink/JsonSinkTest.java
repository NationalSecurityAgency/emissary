package emissary.output.sink;

import emissary.config.ServiceConfigGuide;
import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.HashMap;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two sink classes configured side by side: {@link JsonSink} emits everything (payload, views, metadata) and
 * {@link JsonMetadataSink} emits metadata only.
 */
class JsonSinkTest extends UnitTest {

    private static IBaseDataObject payload() {
        IBaseDataObject p = DataObjectFactory.getInstance();
        p.setData("This is the data".getBytes(UTF_8));
        p.setFileType("FTYPE");
        p.setFilename("/this/is/a/testfile");
        p.appendParameter("FOO", "bar");
        p.addAlternateView("TEXT", "viewdata".getBytes(UTF_8));
        return p;
    }

    private static ServiceConfigGuide configWithOutput(final Path tmpDir) {
        ServiceConfigGuide config = new ServiceConfigGuide();
        config.removeAllEntries("OUTPUT_PATH");
        config.addEntry("OUTPUT_PATH", tmpDir.toAbsolutePath().toString());
        return config;
    }

    @Test
    void testFullJsonSinkWritesPayloadViewsAndMetadata(@TempDir final Path tmpDir) throws Exception {
        JsonSink sink = new JsonSink();
        sink.initialize(configWithOutput(tmpDir), null, configWithOutput(tmpDir));

        assertEquals("JSON", sink.getName(), "Unnamed JsonSink should default to JSON");
        String json = writeThrough(sink, payload());
        assertTrue(json.contains("\"payload\""), "Default JSON sink should write the primary view");
        assertTrue(json.contains("\"views\""), "Default JSON sink should write alternate views");
        assertTrue(json.contains("\"FOO\""), "Default JSON sink should write metadata parameters");
    }

    @Test
    void testMetadataJsonSinkOmitsContentButKeepsMetadata(@TempDir final Path tmpDir) throws Exception {
        ServiceConfigGuide config = configWithOutput(tmpDir);
        config.addEntry("CONTENT_FILTER", "emissary.output.formatter.filter.content.ContentDenyFilter");
        config.addEntry("DENYLIST", "*");
        JsonMetadataSink sink = new JsonMetadataSink();
        sink.initialize(config, null, config);

        assertEquals("METADATA", sink.getName(), "Unnamed JsonMetadataSink should default to METADATA");
        String json = writeThrough(sink, payload());
        assertFalse(json.contains("\"payload\""), "Metadata JSON sink should omit the primary view");
        assertFalse(json.contains("\"views\""), "Metadata JSON sink should omit alternate views");
        assertTrue(json.contains("\"FOO\""), "Metadata JSON sink should keep metadata parameters");
    }

    private static String writeThrough(final ISink sink, final IBaseDataObject p) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        assertEquals(ISink.STATUS_SUCCESS, sink.write(p, new HashMap<>(), buf));
        return buf.toString(UTF_8.name());
    }
}
