package emissary.transform.decode;

import emissary.test.core.junit5.UnitTest;
import emissary.util.CharacterCounterSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class HtmlEscapeTest extends UnitTest {

    private static final String W = "Президент Буш";

    @Test
    void testEntity() {
        String[] t = {
                "<HTML><HEAD></HEAD><BODY>Help me. No change.</BODY></HTML>", "Test&nbsp;Space", "Copy&copy;Right",
                W + "&raquo;<font  color=\"navy\">"};

        String[] ans = {
                "<HTML><HEAD></HEAD><BODY>Help me. No change.</BODY></HTML>", "Test Space", "Copy\u00A9Right", W + "\u00BB<font  color=\"navy\">"};

        for (int i = 0; i < t.length; i++) {
            String s = HtmlEscape.unescapeEntities(t[i]);
            assertEquals(ans[i], s, "Entities must be escaped in string '" + t[i] + "'");
            byte[] b = HtmlEscape.unescapeEntities(t[i].getBytes());
            assertEquals(ans[i], new String(b), "Entity bytes must be escaped in '" + t[i] + "'");
        }
    }

    @Test
    void testBrokenEntity() {
        String[] t = {"Test&nbsp;Space", "Test&;nbsp;Space", "Test&nbsp Space", W + "&;raquo;<font  color=\"navy\">"};

        String[] ans = {"Test Space", "Test Space", "Test  Space", W + "\u00BB<font  color=\"navy\">"};

        for (int i = 0; i < t.length; i++) {
            String s = HtmlEscape.unescapeEntities(t[i]);
            assertEquals(ans[i], s, "Entities must be escaped in string '" + t[i] + "'");
            byte[] b = HtmlEscape.unescapeEntities(t[i].getBytes());
            assertEquals(ans[i], new String(b), "Entity bytes must be escaped in '" + t[i] + "'");
        }
    }

    @Test
    void testEntityRemovalInString() {
        String t = "anti&shy;dis&shy;estab&shy;lish&shy;ment&shy;ary";
        String s = "antidisestablishmentary";
        assertEquals(s, HtmlEscape.unescapeEntities(t), "Entities should have been removed in string");
    }

    @Test
    void testEntityRemovalInBytes() {
        String t = "anti&shy;dis&shy;estab&shy;lish&shy;ment&shy;ary";
        String s = "antidisestablishmentary";
        assertEquals(s, new String(HtmlEscape.unescapeEntities(t.getBytes())), "Entities should have been removed in bytes");
    }

    @Test
    void testEscapingBeyondBMPInString() {
        String t = "Test &#x1D4A5; Script J";
        StringBuilder sb = new StringBuilder();
        sb.append("Test ").appendCodePoint(0x1D4A5).append(" Script J");
        assertEquals(sb.toString(), HtmlEscape.unescapeHtml(t), "Hex char beyond BMP must be escaped in String");
    }

    @Test
    void testEscapingBeyondBMPInBytes() {
        String t = "Test &#x1D4A5; Script J";
        StringBuilder sb = new StringBuilder();
        sb.append("Test ").appendCodePoint(0x1D4A5).append(" Script J");
        assertEquals(sb.toString(), new String(HtmlEscape.unescapeHtml(t.getBytes())), "Hex char beyond BMP must be escaped in bytes");
    }

    @Test
    void testEscapeEntityBeyondBMPInString() {
        String t = "Test &Jscr; Script J";
        StringBuilder sb = new StringBuilder();
        sb.append("Test ").appendCodePoint(0x1D4A5).append(" Script J");
        assertEquals(sb.toString(), HtmlEscape.unescapeEntities(t), "Entity beyond BMP must be escaped in String");
    }

    @Test
    void testEscapeEntityBeyondBMPInBytes() {
        String t = "Test &Jscr; Script J";
        StringBuilder sb = new StringBuilder();
        sb.append("Test ").appendCodePoint(0x1D4A5).append(" Script J");
        assertEquals(sb.toString(), new String(HtmlEscape.unescapeEntities(t.getBytes())), "Entity beyond BMP must be escaped in bytes");
    }

    @Test
    void testEscape() {
        String[] t = {"<HTML><HEAD></HEAD><BODY>Help me. No change.</BODY></HTML>", "Test&#0097;Space", "Copy&#0169;Right"};

        String[] ans = {"<HTML><HEAD></HEAD><BODY>Help me. No change.</BODY></HTML>", "TestaSpace", "Copy\u00A9Right"};

        for (int i = 0; i < t.length; i++) {
            String s = HtmlEscape.unescapeHtml(t[i]);
            assertEquals(ans[i], s, "Characters must be escaped in " + t[i]);
            byte[] b = HtmlEscape.unescapeHtml(t[i].getBytes());
            assertEquals(ans[i], new String(b), "Character bytes must be escaped in " + t[i]);
        }
    }

    @Test
    void testHexEscapeWithoutLeadingZero() {
        String t = "&#x41F;&#x440;&#x435;&#x437;&#x438;&#x434;&#x435;&#x43D;&#x442; &#x411;&#x443;&#x448;";
        String s = W;
        assertEquals(s, HtmlEscape.unescapeHtml(t), "Hex characters must be escaped");
        assertEquals(s, new String(HtmlEscape.unescapeHtml(t.getBytes())), "Hex characters must be escaped");
    }

    @Test
    void testHexEscape() {
        String[] t = {"<HTML><HEAD></HEAD><BODY>Help me. No change.</BODY></HTML>", "Test&#x0061;Space", "Copy&#x00a9;Right"};

        String[] ans = {"<HTML><HEAD></HEAD><BODY>Help me. No change.</BODY></HTML>", "TestaSpace", "Copy\u00A9Right"};

        for (int i = 0; i < t.length; i++) {
            String s = HtmlEscape.unescapeHtml(t[i]);
            assertEquals(ans[i], s, "Hex Characters must be escaped in " + t[i]);
            byte[] b = HtmlEscape.unescapeHtml(t[i].getBytes());
            assertEquals(ans[i], new String(b), "Hex Character bytes must be escaped in " + t[i]);
        }
    }

    @Test
    void testNullInput() {
        assertEquals("", HtmlEscape.unescapeHtml((String) null), "Null cannot be returned for null input");
    }

    @Test
    void testEmptyInput() {
        assertEquals("", HtmlEscape.unescapeHtml(""), "Null cannot be returned for null input");
    }

    @Test
    void testNullByteInput() {
        assertNotNull(HtmlEscape.unescapeHtml((byte[]) null), "Null cannot be returned for null input");
        assertEquals(0, HtmlEscape.unescapeHtml((byte[]) null).length, "Empty array returned for null input");
    }

    @Test
    void testEmptyByteInput() {
        assertEquals(0, HtmlEscape.unescapeHtml(new byte[0]).length, "Empty array returned for 0 length input");
    }

    @Test
    void testHexInputAsString() {
        assertNull(HtmlEscape.unescapeHtmlChar("ffff", false), "Unescape non-hex input");
    }

    @Test
    void testNonHexInputAsHex() {
        assertNull(HtmlEscape.unescapeHtmlChar("gggg", true), "Unescape non-hex input");
    }

    @Test
    void testNonterminatedEntityMarkerInByteArray() {
        String s = "alors le r&eacute";
        String t = "alors le ré";
        assertEquals(t, new String(HtmlEscape.unescapeEntities(s.getBytes())), "Non terminating entity case");
    }

    @Test
    void testNonterminatedEntityMarkerInString() {
        String s = "alors le r&eacute";
        String t = "alors le ré";
        assertEquals(t, HtmlEscape.unescapeEntities(s), "Non terminating entity case");
    }

    @Test
    void testNonterminatedEntityMarkerWithSpaceInByteArray() {
        String s = "&;foobarb ";
        assertEquals(s, new String(HtmlEscape.unescapeEntities(s.getBytes())), "Non terminating entity case");
    }

    @Test
    void testNonterminatedEntityMarkerWithSpaceInString() {
        String s = "&;foobarb ";
        assertEquals(s, HtmlEscape.unescapeEntities(s), "Non terminating entity case");
    }

    @Test
    void testNonterminatedEntityMarkerWithExtraSemicolonInByteArray() {
        String s = "&;foobarb";
        assertEquals(s, new String(HtmlEscape.unescapeEntities(s.getBytes())), "Non terminating entity case");
    }

    @Test
    void testNonterminatedEntityMarkerWithExtraSemicolonInString() {
        String s = "&;foobarb";
        assertEquals(s, HtmlEscape.unescapeEntities(s), "Non terminating entity case");
    }

    @Test
    void testMissingSemicolonInString() {
        assertEquals("a  b", HtmlEscape.unescapeEntities("a&nbsp b"), "Missing semi-colon must be handled");
    }

    @Test
    void testMissingSemicolonInByteArray() {
        assertEquals("a  b", new String(HtmlEscape.unescapeEntities("a&nbsp b".getBytes())), "Missing semi-colon must be handled");
    }

    @Test
    void testExtraSemicolonInString() {
        assertEquals("a b", HtmlEscape.unescapeEntities("a&;nbsp;b"), "Extra semi-colon must be handled");
    }

    @Test
    void testExtraSemicolonInByteArray() {
        assertEquals("a b", new String(HtmlEscape.unescapeEntities("a&;nbsp;b".getBytes())), "Extra semi-colon must be handled");
    }

    @Test
    void testHandlingOf160AndNbspAreIdentical() {
        assertEquals("a  b", new String(HtmlEscape.unescapeEntities("a&#160;&nbsp;b".getBytes())), "Entity 160 is an nbsp");
    }

    @Test
    void testCountingOfWhitespaceEscapes() {
        CharacterCounterSet c = new CharacterCounterSet();
        HtmlEscape.unescapeEntities("a&nbsp;b&#160;", c);
        assertEquals(2, c.getWhitespaceCount(), "Counted nbsp as whitespace");
    }

    @Test
    void testCountingOfWhitespaceEscapesAsBytes() {
        CharacterCounterSet c = new CharacterCounterSet();
        HtmlEscape.unescapeEntities("a&nbsp;b&#160;".getBytes(), c);
        assertEquals(2, c.getWhitespaceCount(), "Counted nbsp as whitespace");
    }

    @Test
    void testCountingEncodedLetters() {
        CharacterCounterSet c = new CharacterCounterSet();
        String s = "alors le r&eacute;";
        HtmlEscape.unescapeEntities(s, c);
        assertEquals(1, c.getLetterCount(), "Counted eacute as letter");
    }

    @Test
    void testCountingEncodedLettersAsBytes() {
        CharacterCounterSet c = new CharacterCounterSet();
        String s = "alors le r&eacute;";
        HtmlEscape.unescapeEntities(s.getBytes(), c);
        assertEquals(1, c.getLetterCount(), "Counted eacute as letter");
    }

    @Test
    void testTwoDigitNumericString() {
        assertEquals(",", HtmlEscape.unescapeHtml("&#44;"), "Short numeric encoded string");
    }

    @Test
    void testTwoDigitNumericByteArray() {
        assertEquals(",", new String(HtmlEscape.unescapeHtml("&#44;".getBytes())), "Short numeric encoded byte array");
    }

    @Test
    void testTwoDigitNumericHexString() {
        assertEquals(",", HtmlEscape.unescapeHtml("&#x2c;"), "Short numeric encoded hex string");
    }

    @Test
    void testTwoDigitNumericHexByteArray() {
        assertEquals(",", new String(HtmlEscape.unescapeHtml("&#x2c;".getBytes())), "Short numeric encoded hex byte array");
    }
}
