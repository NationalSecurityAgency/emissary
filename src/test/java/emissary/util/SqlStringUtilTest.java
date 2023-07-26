package emissary.util;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlStringUtilTest extends UnitTest {
    @Test
    void testStripTicks() {
        assertEquals("tick", SqlStringUtil.stripTicks("`ti`ck`"), "All '`' characters should be removed");
    }

    @Test
    void testStripIdentifierMarks() {
        assertEquals("tick", SqlStringUtil.stripIdentifierMarks("[`ti[`ck`]"), "All identifier characters should be removed");
    }

    @Test
    void testStripEdgeQuotes() {
        assertEquals("quote", SqlStringUtil.stripEdgeQuotes(" N'quote' "), "Edge quotes and spaces should be removed");
        assertEquals("Nq'uote'", SqlStringUtil.stripEdgeQuotes("Nq'uote'"), "Quotes should not removed");
    }

    @Test
    void testFixUnicodeString() {
        assertEquals("'str", SqlStringUtil.fixUnicodeString(" N'str "), "N and spaces should be removed");
        assertEquals("", SqlStringUtil.fixUnicodeString(null), "Null should become empty string");
    }

    @Test
    void testSplitObjectNames() {
        String strIn = "split. object.name";
        String[] res = SqlStringUtil.splitObjectNames(strIn);
        assertEquals(3, res.length);
        assertEquals("split", res[0]);
        assertEquals("object", res[1]);
        assertEquals("name", res[2]);
    }
}
