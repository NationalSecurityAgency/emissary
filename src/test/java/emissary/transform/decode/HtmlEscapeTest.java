package emissary.transform.decode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import emissary.test.core.UnitTest;
import emissary.util.CharacterCounterSet;
import org.junit.Test;

public class HtmlEscapeTest extends UnitTest {

    private static String W = "Президент Буш";

    @Test
    public void testEntity() {
        String[] t = {
                "<HTML><HEAD></HEAD><BODY>Help me. No change.</BODY></HTML>", "Test&nbsp;Space", "Copy&copy;Right",
                W + "&raquo;<font  color=\"navy\">"};

        String[] ans = {
                "<HTML><HEAD></HEAD><BODY>Help me. No change.</BODY></HTML>", "Test Space", "Copy\u00A9Right", W + "\u00BB<font  color=\"navy\">"};

        for (int i = 0; i < t.length; i++) {
            String s = HtmlEscape.unescapeEntities(t[i]);
            assertEquals("Entities must be escaped in string '" + t[i] + "'", ans[i], s);
            byte[] b = HtmlEscape.unescapeEntities(t[i].getBytes());
            assertEquals("Entity bytes must be escaped in '" + t[i] + "'", ans[i], new String(b));
        }
    }

    @Test
    public void testBrokenEntity() {
        String[] t = {"Test&nbsp;Space", "Test&;nbsp;Space", "Test&nbsp Space", W + "&;raquo;<font  color=\"navy\">"};

        String[] ans = {"Test Space", "Test Space", "Test  Space", W + "\u00BB<font  color=\"navy\">"};

        for (int i = 0; i < t.length; i++) {
            String s = HtmlEscape.unescapeEntities(t[i]);
            assertEquals("Entities must be escaped in string '" + t[i] + "'", ans[i], s);
            byte[] b = HtmlEscape.unescapeEntities(t[i].getBytes());
            assertEquals("Entity bytes must be escaped in '" + t[i] + "'", ans[i], new String(b));
        }
    }

    @Test
    public void testEntityRemovalInString() {
        String t = "anti&shy;dis&shy;estab&shy;lish&shy;ment&shy;ary";
        String s = "antidisestablishmentary";
        assertEquals("Entities should have been removed in string", s, HtmlEscape.unescapeEntities(t));
    }

    @Test
    public void testEntityRemovalInBytes() {
        String t = "anti&shy;dis&shy;estab&shy;lish&shy;ment&shy;ary";
        String s = "antidisestablishmentary";
        assertEquals("Entities should have been removed in bytes", s, new String(HtmlEscape.unescapeEntities(t.getBytes())));
    }

    @Test
    public void testEscapingBeyondBMPInString() {
        String t = "Test &#x1D4A5; Script J";
        StringBuilder sb = new StringBuilder();
        sb.append("Test ").appendCodePoint(0x1D4A5).append(" Script J");
        assertEquals("Hex char beyond BMP must be escaped in String", sb.toString(), HtmlEscape.unescapeHtml(t));
    }

    @Test
    public void testEscapingBeyondBMPInBytes() {
        String t = "Test &#x1D4A5; Script J";
        StringBuilder sb = new StringBuilder();
        sb.append("Test ").appendCodePoint(0x1D4A5).append(" Script J");
        assertEquals("Hex char beyond BMP must be escaped in bytes", sb.toString(), new String(HtmlEscape.unescapeHtml(t.getBytes())));
    }

    @Test
    public void testEscapeEntityBeyondBMPInString() {
        String t = "Test &Jscr; Script J";
        StringBuilder sb = new StringBuilder();
        sb.append("Test ").appendCodePoint(0x1D4A5).append(" Script J");
        assertEquals("Entity beyond BMP must be escaped in String", sb.toString(), HtmlEscape.unescapeEntities(t));
    }

    @Test
    public void testEscapeEntityBeyondBMPInBytes() {
        String t = "Test &Jscr; Script J";
        StringBuilder sb = new StringBuilder();
        sb.append("Test ").appendCodePoint(0x1D4A5).append(" Script J");
        assertEquals("Entity beyond BMP must be escaped in bytes", sb.toString(), new String(HtmlEscape.unescapeEntities(t.getBytes())));
    }

    @Test
    public void testEscape() {
        String[] t = {"<HTML><HEAD></HEAD><BODY>Help me. No change.</BODY></HTML>", "Test&#0097;Space", "Copy&#0169;Right"};

        String[] ans = {"<HTML><HEAD></HEAD><BODY>Help me. No change.</BODY></HTML>", "TestaSpace", "Copy\u00A9Right"};

        for (int i = 0; i < t.length; i++) {
            String s = HtmlEscape.unescapeHtml(t[i]);
            assertEquals("Characters must be escaped in " + t[i], ans[i], s);
            byte[] b = HtmlEscape.unescapeHtml(t[i].getBytes());
            assertEquals("Character bytes must be escaped in " + t[i], ans[i], new String(b));
        }
    }

    @Test
    public void testHexEscapeWithoutLeadingZero() {
        String t = "&#x41F;&#x440;&#x435;&#x437;&#x438;&#x434;&#x435;&#x43D;&#x442; &#x411;&#x443;&#x448;";
        String s = W;
        assertEquals("Hex characters must be escaped", s, HtmlEscape.unescapeHtml(t));
        assertEquals("Hex characters must be escaped", s, new String(HtmlEscape.unescapeHtml(t.getBytes())));
    }

    @Test
    public void testHexEscape() {
        String[] t = {"<HTML><HEAD></HEAD><BODY>Help me. No change.</BODY></HTML>", "Test&#x0061;Space", "Copy&#x00a9;Right"};

        String[] ans = {"<HTML><HEAD></HEAD><BODY>Help me. No change.</BODY></HTML>", "TestaSpace", "Copy\u00A9Right"};

        for (int i = 0; i < t.length; i++) {
            String s = HtmlEscape.unescapeHtml(t[i]);
            assertEquals("Hex Characters must be escaped in " + t[i], ans[i], s);
            byte[] b = HtmlEscape.unescapeHtml(t[i].getBytes());
            assertEquals("Hex Character bytes must be escaped in " + t[i], ans[i], new String(b));
        }
    }

    @Test
    public void testNullInput() {
        assertEquals("Null cannot be returned for null input", "", HtmlEscape.unescapeHtml((String) null));
    }

    @Test
    public void testEmptyInput() {
        assertEquals("Null cannot be returned for null input", "", HtmlEscape.unescapeHtml(""));
    }

    @Test
    public void testNullByteInput() {
        assertNotNull("Null cannot be returned for null input", HtmlEscape.unescapeHtml((byte[]) null));
        assertEquals("Empty array returned for null input", 0, HtmlEscape.unescapeHtml((byte[]) null).length);
    }

    @Test
    public void testEmptyByteInput() {
        assertEquals("Empty array returned for 0 length input", 0, HtmlEscape.unescapeHtml(new byte[0]).length);
    }

    @Test
    public void testHexInputAsString() {
        assertTrue("Unescape non-hex input", HtmlEscape.unescapeHtmlChar("ffff", false) == null);
    }

    @Test
    public void testNonHexInputAsHex() {
        assertTrue("Unescape non-hex input", HtmlEscape.unescapeHtmlChar("gggg", true) == null);
    }

    @Test
    public void testNonterminatedEntityMarkerInByteArray() {
        String s = "alors le r&eacute";
        String t = "alors le ré";
        assertEquals("Non terminating entity case", t, new String(HtmlEscape.unescapeEntities(s.getBytes())));
    }

    @Test
    public void testNonterminatedEntityMarkerInString() {
        String s = "alors le r&eacute";
        String t = "alors le ré";
        assertEquals("Non terminating entity case", t, HtmlEscape.unescapeEntities(s));
    }

    @Test
    public void testNonterminatedEntityMarkerWithSpaceInByteArray() {
        String s = "&;foobarb ";
        assertEquals("Non terminating entity case", s, new String(HtmlEscape.unescapeEntities(s.getBytes())));
    }

    @Test
    public void testNonterminatedEntityMarkerWithSpaceInString() {
        String s = "&;foobarb ";
        assertEquals("Non terminating entity case", s, HtmlEscape.unescapeEntities(s));
    }

    @Test
    public void testNonterminatedEntityMarkerWithExtraSemicolonInByteArray() {
        String s = "&;foobarb";
        assertEquals("Non terminating entity case", s, new String(HtmlEscape.unescapeEntities(s.getBytes())));
    }

    @Test
    public void testNonterminatedEntityMarkerWithExtraSemicolonInString() {
        String s = "&;foobarb";
        assertEquals("Non terminating entity case", s, HtmlEscape.unescapeEntities(s));
    }

    @Test
    public void testMissingSemicolonInString() {
        assertEquals("Missing semi-colon must be handled", "a  b", HtmlEscape.unescapeEntities("a&nbsp b"));
    }

    @Test
    public void testMissingSemicolonInByteArray() {
        assertEquals("Missing semi-colon must be handled", "a  b", new String(HtmlEscape.unescapeEntities("a&nbsp b".getBytes())));
    }

    @Test
    public void testExtraSemicolonInString() {
        assertEquals("Extra semi-colon must be handled", "a b", HtmlEscape.unescapeEntities("a&;nbsp;b"));
    }

    @Test
    public void testExtraSemicolonInByteArray() {
        assertEquals("Extra semi-colon must be handled", "a b", new String(HtmlEscape.unescapeEntities("a&;nbsp;b".getBytes())));
    }

    @Test
    public void testHandlingOf160AndNbspAreIdentical() {
        assertEquals("Entity 160 is an nbsp", "a  b", new String(HtmlEscape.unescapeEntities("a&#160;&nbsp;b".getBytes())));
    }

    @Test
    public void testCountingOfWhitespaceEscapes() {
        CharacterCounterSet c = new CharacterCounterSet();
        HtmlEscape.unescapeEntities("a&nbsp;b&#160;", c);
        assertEquals("Counted nbsp as whitespace", 2, c.getWhitespaceCount());
    }

    @Test
    public void testCountingOfWhitespaceEscapesAsBytes() {
        CharacterCounterSet c = new CharacterCounterSet();
        HtmlEscape.unescapeEntities("a&nbsp;b&#160;".getBytes(), c);
        assertEquals("Counted nbsp as whitespace", 2, c.getWhitespaceCount());
    }

    @Test
    public void testCountingEncodedLetters() {
        CharacterCounterSet c = new CharacterCounterSet();
        String s = "alors le r&eacute;";
        HtmlEscape.unescapeEntities(s, c);
        assertEquals("Counted eacute as letter", 1, c.getLetterCount());
    }

    @Test
    public void testCountingEncodedLettersAsBytes() {
        CharacterCounterSet c = new CharacterCounterSet();
        String s = "alors le r&eacute;";
        HtmlEscape.unescapeEntities(s.getBytes(), c);
        assertEquals("Counted eacute as letter", 1, c.getLetterCount());
    }

    @Test
    public void testTwoDigitNumericString() {
        assertEquals("Short numeric encoded string", ",", HtmlEscape.unescapeHtml("&#44;"));
    }

    @Test
    public void testTwoDigitNumericByteArray() {
        assertEquals("Short numeric encoded byte array", ",", new String(HtmlEscape.unescapeHtml("&#44;".getBytes())));
    }

    @Test
    public void testTwoDigitNumericHexString() {
        assertEquals("Short numeric encoded hex string", ",", HtmlEscape.unescapeHtml("&#x2c;"));
    }

    @Test
    public void testTwoDigitNumericHexByteArray() {
        assertEquals("Short numeric encoded hex byte array", ",", new String(HtmlEscape.unescapeHtml("&#x2c;".getBytes())));
    }
}
