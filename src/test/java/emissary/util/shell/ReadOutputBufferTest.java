package emissary.util.shell;

import emissary.test.core.junit5.UnitTest;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.*;

class ReadOutputBufferTest extends UnitTest {

    @Test
    void runImpl() {
        try (InputStream is = IOUtils.toInputStream("Testing", Charset.defaultCharset())) {
            ReadOutputBuffer buffer = new ReadOutputBuffer(is, new StringBuilder());
            buffer.runImpl();
            buffer.finish();
            assertEquals("Testing\r\n", buffer.getString());
            assertNotNull(buffer.getBuilder());
            assertNull(buffer.getBuffer());
        } catch (Exception e) {
            fail(e);
        }
    }

}
