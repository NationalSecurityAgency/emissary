package emissary.transform.decode;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Deprecated(forRemoval = true)
class JsonEscapeTest extends UnitTest {
    @Test
    void testEscapedAngleBracketChars() {
        String input = "\\u003cThis is a Test\\u003e";
        byte[] output = JsonEscape.unescape(input.getBytes());
        assertEquals("<This is a Test>", new String(output), "String should have been escaped");
    }

    @Test
    void testNoDamage() {
        String input = "\\ufoodebar this is normal\\u";
        byte[] output = JsonEscape.unescape(input.getBytes());
        assertEquals(input, new String(output), "String should not be damaged during failed unescaping");
    }

    @Test
    void testOctalEscape() {
        String input = "\\42This is a Test\\42";
        byte[] output = JsonEscape.unescape(input.getBytes());
        assertEquals("\"This is a Test\"", new String(output), "String should have been octal escaped");
    }

    @Test
    void testOctalEscapeWithNumbersTrailing() {
        String input = "\\04277This is a Test\\04277";
        byte[] output = JsonEscape.unescape(input.getBytes());
        assertEquals("\"77This is a Test\"77", new String(output), "String should have been octal escaped");
    }

    @Test
    void testNoDamageOnNonOctalEscape() {
        String input = "\\99This is a Test\\99";
        byte[] output = JsonEscape.unescape(input.getBytes());
        assertEquals(input, new String(output), "String must not be damaged during failed unescaping");
    }

    @Test
    void testNewLineEscape() {
        String input = "This \\nis a Test";
        byte[] output = JsonEscape.unescape(input.getBytes());
        assertEquals("This \nis a Test", new String(output), "String must be newline escaped");
    }

}
