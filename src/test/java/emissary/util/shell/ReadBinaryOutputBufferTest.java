package emissary.util.shell;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.*;

class ReadBinaryOutputBufferTest {

    @Test
    void runImpl() {
        try (InputStream is = IOUtils.toInputStream("Testing", Charset.defaultCharset())) {
            ReadBinaryOutputBuffer buffer = new ReadBinaryOutputBuffer(is, new ByteArrayOutputStream());
            buffer.runImpl();
            buffer.finish();
            assertArrayEquals("Testing".getBytes(), buffer.getBytes());
            assertNotNull(buffer.getByteStream());
        } catch (Exception e) {
            fail(e);
        }
    }
}
