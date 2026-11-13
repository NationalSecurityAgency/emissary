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
 * {@link JsonSink} emits everything (payload, views, metadata).
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
    void testFullOutput(@TempDir final Path tmpDir) throws Exception {
        JsonSink sink = new JsonSink();
        sink.initialize(configWithOutput(tmpDir), null, configWithOutput(tmpDir));

        assertEquals("JSON", sink.getName(), "Unnamed JsonSink should default to JSON");
        String json = writeThrough(sink, payload());
        assertTrue(json.contains("\"payload\""), "Default JSON sink should write the primary view");
        assertTrue(json.contains("\"views\""), "Default JSON sink should write alternate views");
        assertTrue(json.contains("\"FOO\""), "Default JSON sink should write metadata parameters");
    }

    @Test
    void testContentOnly(@TempDir final Path tmpDir) throws Exception {
        ServiceConfigGuide config = configWithOutput(tmpDir);
        config.addEntry("EMIT", "content");
        JsonSink sink = new JsonSink();
        sink.initialize(config, null, config);

        String json = writeThrough(sink, payload());
        assertTrue(json.contains("\"payload\""), "Content JSON sink should write the primary view");
        assertTrue(json.contains("\"views\""), "Content JSON sink should write alternate views");
        assertFalse(json.contains("\"FOO\""), "Content JSON sink should omit metadata parameters");
    }

    private static String writeThrough(final ISink sink, final IBaseDataObject p) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        assertEquals(WriteStatus.SUCCESS, sink.write(p, new HashMap<>(), buf));
        return buf.toString(UTF_8.name());
    }
}
