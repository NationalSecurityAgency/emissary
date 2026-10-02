package emissary.output.formatter;

import emissary.config.ServiceConfigGuide;
import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmlFormatterTest extends UnitTest {

    private ServiceConfigGuide config;
    private XmlFormatter f;
    private IBaseDataObject payload;

    @BeforeEach
    public void setup(@TempDir final Path tmpDir) {
        config = new ServiceConfigGuide();
        config.removeAllEntries("OUTPUT_PATH");
        config.addEntry("OUTPUT_PATH", tmpDir.toAbsolutePath().toString());

        f = new XmlFormatter();

        payload = DataObjectFactory.getInstance();
        payload.setData("This is the data".getBytes(UTF_8));
        payload.setFileType("FTYPE");
        payload.setFilename("/this/is/a/testfile");
        payload.appendParameter("FOO", "bar");
    }

    @Test
    void testSetup() {
        f.initialize(config, "FOO", config);
        assertTrue(f.getName().equals("FOO"), "Formatter name should be set");
    }

    @Test
    void testOutput() throws Exception {
        f.initialize(config, "FOO", config);
        byte[] xml = f.convert(Collections.singletonList(payload), new HashMap<>());
        String s = new String(xml, UTF_8);
        assertTrue(s.contains("<payload-list>"), "Output should be a payload list document");
        assertTrue(s.contains("<payload>"), "Output should contain a payload element");
        assertTrue(s.contains(">FTYPE<"), "Output should contain the file type");
        assertTrue(s.contains("FOO"), "Output should contain the parameter name");
    }

    @Test
    void testWriteToStream() throws Exception {
        f.initialize(config, "FOO", config);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        f.writeTo(buf, Collections.singletonList(payload), new HashMap<>());
        assertTrue(buf.toString(UTF_8.name()).contains("<payload>"), "writeTo should produce the payload document");
    }
}
