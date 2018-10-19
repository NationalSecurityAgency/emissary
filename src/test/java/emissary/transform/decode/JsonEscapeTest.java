package emissary.transform.decode;

import static org.junit.Assert.assertEquals;

import emissary.test.core.UnitTest;
import org.junit.Test;

public class JsonEscapeTest extends UnitTest {
    @Test
    public void testEscapedAngleBracketChars() {
        String input = "\\u003cThis is a Test\\u003e";
        byte[] output = JsonEscape.unescape(input.getBytes());
        assertEquals("String should have been escaped", "<This is a Test>", new String(output));
    }

    @Test
    public void testNoDamage() {
        String input = "\\ufoodebar this is normal\\u";
        byte[] output = JsonEscape.unescape(input.getBytes());
        assertEquals("String should not be damaged during failed unescaping", input, new String(output));
    }

    @Test
    public void testOctalEscape() {
        String input = "\\42This is a Test\\42";
        byte[] output = JsonEscape.unescape(input.getBytes());
        assertEquals("String should have been octal escaped", "\"This is a Test\"", new String(output));
    }

    @Test
    public void testOctalEscapeWithNumbersTrailing() {
        String input = "\\04277This is a Test\\04277";
        byte[] output = JsonEscape.unescape(input.getBytes());
        assertEquals("String should have been octal escaped", "\"77This is a Test\"77", new String(output));
    }

    @Test
    public void testNoDamageOnNonOctalEscape() {
        String input = "\\99This is a Test\\99";
        byte[] output = JsonEscape.unescape(input.getBytes());
        assertEquals("String must not be damaged during failed unescaping", input, new String(output));
    }

    @Test
    public void testNewLineEscape() {
        String input = "This \\nis a Test";
        byte[] output = JsonEscape.unescape(input.getBytes());
        assertEquals("String must be newline escaped", "This \nis a Test", new String(output));
    }

}
