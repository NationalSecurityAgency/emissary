package emissary.transform.decode;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import emissary.test.core.junit5.UnitTest;
import org.junit.jupiter.api.Test;

class JsonEscapeTest extends UnitTest {
    @Test
    void testEscapedAngleBracketChars() {
        String input = "\\u003cThis is a Test\\u003e";
        byte[] output = JsonEscape.unescape(input.getBytes(UTF_8));
        assertEquals("<This is a Test>", new String(output, UTF_8), "String should have been escaped");
    }

    @Test
    void testNoDamage() {
        String input = "\\ufoodebar this is normal\\u";
        byte[] output = JsonEscape.unescape(input.getBytes(UTF_8));
        assertEquals(input, new String(output, UTF_8), "String should not be damaged during failed unescaping");
    }

    @Test
    void testOctalEscape() {
        String input = "\\42This is a Test\\42";
        byte[] output = JsonEscape.unescape(input.getBytes(UTF_8));
        assertEquals("\"This is a Test\"", new String(output, UTF_8), "String should have been octal escaped");
    }

    @Test
    void testOctalEscapeWithNumbersTrailing() {
        String input = "\\04277This is a Test\\04277";
        byte[] output = JsonEscape.unescape(input.getBytes(UTF_8));
        assertEquals("\"77This is a Test\"77", new String(output, UTF_8), "String should have been octal escaped");
    }

    @Test
    void testNoDamageOnNonOctalEscape() {
        String input = "\\99This is a Test\\99";
        byte[] output = JsonEscape.unescape(input.getBytes(UTF_8));
        assertEquals(input, new String(output, UTF_8), "String must not be damaged during failed unescaping");
    }

    @Test
    void testNewLineEscape() {
        String input = "This \\nis a Test";
        byte[] output = JsonEscape.unescape(input.getBytes(UTF_8));
        assertEquals("This \nis a Test", new String(output, UTF_8), "String must be newline escaped");
    }

}
