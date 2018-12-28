package emissary.transform.decode;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;
import emissary.test.core.UnitTest;

public class JsonEscapeTest extends UnitTest {
    @Test
    public void testEscapedAngleBracketChars() {
        String input = "\\u003cThis is ツ \\U200E a Test\\u003e";
        byte[] output = JsonEscape.unescape(input.getBytes(StandardCharsets.UTF_8));
        assertEquals("String should have been escaped", "<This is ツ \u200E a Test>", new String(output, StandardCharsets.UTF_8));
    }

    @Test
    public void testNoDamage() {
        String input = "\\ufoodebar this ツ \\U is normal\\u";
        byte[] output = JsonEscape.unescape(input.getBytes(StandardCharsets.UTF_8));
        assertEquals("String should not be damaged during failed unescaping", input, new String(output, StandardCharsets.UTF_8));
    }

    @Test
    public void testOctalEscape() {
        String input = "\\42This is ツ a Test\\42";
        byte[] output = JsonEscape.unescape(input.getBytes(StandardCharsets.UTF_8));
        assertEquals("String should have been octal escaped", "\"This is ツ a Test\"", new String(output, StandardCharsets.UTF_8));
    }

    @Test
    public void testOctalEscapeWithNumbersTrailing() {
        String input = "\\04277This is ツ a Test\\04277";
        byte[] output = JsonEscape.unescape(input.getBytes(StandardCharsets.UTF_8));
        assertEquals("String should have been octal escaped", "\"77This is ツ a Test\"77", new String(output, StandardCharsets.UTF_8));
    }

    @Test
    public void testNoDamageOnNonOctalEscape() {
        String input = "\\99This is ツ a Test\\99";
        byte[] output = JsonEscape.unescape(input.getBytes(StandardCharsets.UTF_8));
        assertEquals("String must not be damaged during failed unescaping", input, new String(output, StandardCharsets.UTF_8));
    }

    @Test
    public void testNewLineEscape() {
        String input = "This \\nis ツ a Test";
        byte[] output = JsonEscape.unescape(input.getBytes(StandardCharsets.UTF_8));
        assertEquals("String must be newline escaped", "This \nis ツ a Test", new String(output, StandardCharsets.UTF_8));
    }

    @Test
    public void testEscapedAngleBracketCharsByteWiseStream() throws Exception {
        String input = "\\u003cThis is ツ \\U200E a Test\\u003e";
        byte[] output = unescapeByteWiseStream(input.getBytes(StandardCharsets.UTF_8));
        assertEquals("String should have been escaped", "<This is ツ \u200E a Test>", new String(output, StandardCharsets.UTF_8));
    }

    @Test
    public void testNoDamageByteWiseStream() throws Exception {
        String input = "\\ufoodebar this is ツ \\U normal\\u";
        byte[] output = unescapeByteWiseStream(input.getBytes(StandardCharsets.UTF_8));
        assertEquals("String should not be damaged during failed unescaping", input, new String(output, StandardCharsets.UTF_8));
    }

    @Test
    public void testOctalEscapeByteWiseStream() throws Exception {
        String input = "\\42This is ツ a Test\\42";
        byte[] output = unescapeByteWiseStream(input.getBytes(StandardCharsets.UTF_8));
        assertEquals("String should have been octal escaped", "\"This is ツ a Test\"", new String(output, StandardCharsets.UTF_8));
    }

    @Test
    public void testOctalEscapeWithNumbersTrailingByteWiseStream() throws Exception {
        String input = "\\04277This is ツ a Test\\04277";
        byte[] output = unescapeByteWiseStream(input.getBytes(StandardCharsets.UTF_8));
        assertEquals("String should have been octal escaped", "\"77This is ツ a Test\"77", new String(output, StandardCharsets.UTF_8));
    }

    @Test
    public void testNoDamageOnNonOctalEscapeByteWiseStream() throws Exception {
        String input = "\\99This is ツ a Test\\99";
        byte[] output = unescapeByteWiseStream(input.getBytes(StandardCharsets.UTF_8));
        assertEquals("String must not be damaged during failed unescaping", input, new String(output, StandardCharsets.UTF_8));
    }

    @Test
    public void testNewLineEscapeByteWiseStream() throws Exception {
        String input = "This \\nis ツ a Test";
        byte[] output = unescapeByteWiseStream(input.getBytes(StandardCharsets.UTF_8));
        assertEquals("String must be newline escaped", "This \nis ツ a Test", new String(output, StandardCharsets.UTF_8));
    }

    private static byte[] unescapeByteWiseStream(byte[] bytes) throws IOException {
        try (InputStream in = new JsonEscape.UnEscapeInputStream(new ByteArrayInputStream(bytes));
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            int read = in.read();
            while (read != -1) {
                out.write(read);
                read = in.read();
            }
            return out.toByteArray();
        }
    }
}
