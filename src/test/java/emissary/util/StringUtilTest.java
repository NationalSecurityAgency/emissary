package emissary.util;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StringUtilTest extends UnitTest {
    @Test
    void testXMLCorrect() {
        final String xml1 = String.valueOf(new char[] {0, 1, 2, 3, 4});
        final String xml2 = String.valueOf(new char[] {20, 'A', 10});
        final String xml3 = "XMLcorrect";
        assertEquals("/0x0/0x1/0x2/0x3/0x4", StringUtil.XMLcorrect(xml1), "All characters should be corrected");
        assertEquals("/0x14A\n", StringUtil.XMLcorrect(xml2), "Only last 2 characters should be untouched");
        assertEquals(xml3, StringUtil.XMLcorrect(xml3), "All characters should be untouched");
    }

    @Test
    void testStripTicks() {
        assertEquals("tick", StringUtil.stripTicks("`ti`ck`"), "All '`' characters should be removed");
    }

    @Test
    void testStripIdentifierMarks() {
        assertEquals("tick", StringUtil.stripIdentifierMarks("[`ti[`ck`]"), "All identifier characters should be removed");
    }

    @Test
    void testStripEdgeQuotes() {
        assertEquals("quote", StringUtil.stripEdgeQuotes(" 'quote' "), "Edge quotes and spaces should be removed");
        assertEquals("q'uote'", StringUtil.stripEdgeQuotes("q'uote'"), "Quotes should not removed");
    }

    @Test
    void testFixUnicodeString() {
        assertEquals("'str", StringUtil.fixUnicodeString(" N'str "), "N and spaces should be removed");
        assertEquals("", StringUtil.fixUnicodeString(null), "Null should become empty string");
    }

    @Test
    void testSplitObjectNames() {
        String strIn = "split. object.name";
        String[] res = StringUtil.splitObjectNames(strIn);
        assertEquals(3, res.length);
        assertEquals("split", res[0]);
        assertEquals("object", res[1]);
        assertEquals("name", res[2]);
    }
}
