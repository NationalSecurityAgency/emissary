package emissary.util.shell;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

class ReadBinaryOutputBufferTest {

    @Test
    void runImpl() {
        try (InputStream is = IOUtils.toInputStream("Testing", Charset.defaultCharset())) {
            ReadBinaryOutputBuffer buffer = new ReadBinaryOutputBuffer(is, new ByteArrayOutputStream());
            buffer.runImpl();
            buffer.finish();
            assertArrayEquals("Testing".getBytes(UTF_8), buffer.getBytes());
            assertNotNull(buffer.getByteStream());
        } catch (Exception e) {
            fail(e);
        }
    }
}
