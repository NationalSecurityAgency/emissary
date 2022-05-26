package emissary.util.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.UnsupportedCharsetException;

import emissary.test.core.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ByteMatcherTest extends UnitTest {

    private final String data = "The quick brown fox jumped over the lazy dog";
    private ByteMatcher b;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        this.b = new ByteMatcher(this.data.getBytes());
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        this.b = null;
    }

    @Test
    void testEmptyConstructor() {
        ByteMatcher byteMatcher = new ByteMatcher();
        assertEquals(-1, byteMatcher.indexOf("a token, hadoken"));
    }

    @Test
    void testResetByteMatcher() {
        String localDataOne = "The quick brown fox jumped over the lazy dog";
        String localDataTwo = "But the faster dog ate the slower fox.";
        String firstToken = "fox";
        String secondToken = "dog";
        ByteMatcher byteMatcher = new ByteMatcher(localDataOne);

        assertEquals(16, byteMatcher.indexOf(firstToken));
        assertEquals(41, byteMatcher.indexOf(secondToken));

        // Reuse the same ByteMatcher
        byteMatcher.resetData(localDataTwo);

        assertEquals(34, byteMatcher.indexOf(firstToken));
        assertEquals(15, byteMatcher.indexOf(secondToken));
    }

    @Test
    void testByteMatcherWithCharset() {
        String localDataOne = "The quick brown fox jumped over the lazy dog";
        String localDataTwo = "But the faster dog ate the slower fox.";
        String firstToken = "fox";
        String secondToken = "dog";
        ByteMatcher byteMatcher = new ByteMatcher(localDataOne);

        // Back to the first data, but with a different charset
        byteMatcher.resetData(localDataOne, "ISO-8859-1");

        assertEquals(16, byteMatcher.indexOf(firstToken));
        assertEquals(41, byteMatcher.indexOf(secondToken));

        // Back to the second data, with a charset that doesn't exist.
        assertThrows(UnsupportedCharsetException.class, () -> byteMatcher.resetData(localDataTwo, "NoSuchCharset"));
    }

    @Test
    void testSimpleScan() {
        assertEquals(this.data.indexOf("fox"), this.b.indexOf("fox"), "Match pos same as string");
    }

    @Test
    void testIndexOfBytes() {
        assertEquals(this.data.indexOf("fox"), this.b.indexOf("fox".getBytes()), "Match pos same as string");
    }

    @Test
    void testOffsetScan() {
        assertEquals(this.data.indexOf("fox", 9), this.b.indexOf("fox", 9), "Match pos same as string using offset");
    }

    @Test
    void testNotFound() {
        assertEquals(this.data.indexOf("llama"), this.b.indexOf("llama"), "Match pos same as string when not found");
    }

    @Test
    void testLength() {
        assertEquals(this.data.length(), this.b.length(), "Length same as string");
    }

    @Test
    void testStrcmp() {
        final int pos = this.b.indexOf("fox");
        assertTrue(this.b.strcmp(pos, "fox"), "Match pos work with strcmp");
    }

    @Test
    void testStrcmpNotFound() {
        assertFalse(this.b.strcmp(5, "lazy"), "Strcmp fails when at wrong pos");
    }

    @Test
    void testSlice() {
        final int pos = this.b.indexOf("fox");
        assertEquals("fox", new String(this.b.slice(pos, pos + 3)), "Slice extraction");
    }

    @Test
    void testSliceAtEndOfRange() {
        final int pos = this.b.indexOf("dog");
        assertEquals("dog", new String(this.b.slice(pos, pos + 3)), "Slice extraction at end of range");
    }

    @Test
    void testBadSlice() {
        final byte[] res = this.b.slice(0, this.data.length() + 10);
        assertNotNull(res, "Bad slice must not return null");
        assertEquals(0, res.length, "Length of bad slice must be 0");
    }

    @Test
    void testByteAt() {
        assertEquals(this.data.charAt(11), (char) this.b.byteAt(11), "ByteAt matches string charAt");
    }

    @Test
    void testGetValueDefaultDelim() {
        this.b = new ByteMatcher("Abc\nkey=value\r\nanother=key and value\n\r\n".getBytes());
        assertEquals("value", this.b.getValue("key"), "Value extraction");
    }

    @Test
    void testGetSValue() {
        this.b = new ByteMatcher("Abc\nkey: 6\nvalue\r\nanother: 14\nkey and value\n\r\n".getBytes());
        assertEquals("value", new String(this.b.getSValue("key")).trim(), "SValue extraction");
    }

    @Test
    void testGetSValueNotFound() {
        this.b = new ByteMatcher("Abc\nkey: 6\nvalue\r\nanother: 14\nkey and value\n\r\n".getBytes());
        assertNull(this.b.getSValue("foo"), "SValue extraction");
    }

    @Test
    void testGetSValueWithOfs() {
        this.b = new ByteMatcher("Abc\nkey: 6\nvalue\r\nanother: 14\nkey and value\n\r\n".getBytes());
        assertEquals("value", new String(this.b.getSValue("key", 4, this.data.length())).trim(), "SValue extraction");
    }

    @Test
    void testGetValueDefaultDelimWithOfs() {
        this.b = new ByteMatcher("Abc\nkey=value\r\nanother=key and value\n\r\n".getBytes());
        assertEquals("value", this.b.getValue("key", 0), "Value extraction");
    }

    @Test
    void testGetValue() {
        this.b = new ByteMatcher("Abc\nkey: value\r\nanother: key and value\n\r\n".getBytes());
        assertEquals(" value", this.b.getValue("key", 0, ":"), "Value extraction");
    }

    @Test
    void testGetValueMultiCharDelim() {
        this.b = new ByteMatcher("Abc\nkey : value\r\nanother : key and value\n\r\n".getBytes());
        assertEquals("value", this.b.getValue("key", 0, " : "), "Value extraction");
    }

    @Test
    void testGetValueNoKey() {
        this.b = new ByteMatcher("Abc\nkey: value\r\nanother: key and value\n\r\n".getBytes());
        assertNull(this.b.getValue("foo", 0, ":"), "Value extraction");
    }

    @Test
    void testGetValueNoDelimiter() {
        this.b = new ByteMatcher("Abc\nkey: value\r\nanother: key and value\n\r\n".getBytes());
        assertNull(this.b.getValue("key", 0, "="), "Value extraction");
    }

    @Test
    void testGetValueNoValue() {
        this.b = new ByteMatcher("Abc\nkey:\r\nanother: key and value\n\r\n".getBytes());
        assertEquals("", this.b.getValue("key", 0, ":"), "Value extraction");
    }

    @Test
    void testIgnoreCaseScan() {
        assertEquals(this.data.indexOf("fox"), this.b.indexIgnoreCase("foX"), "Pos in case insensitive search");
    }

    @Test
    void testIgnoreCaseByteScan() {
        assertEquals(this.data.indexOf("fox"), this.b.indexIgnoreCase("foX".getBytes()), "Pos in case insensitive search");
    }

    @Test
    void testIgnoreCaseScanWithOffset() {
        assertEquals(this.data.indexOf("fox"), this.b.indexIgnoreCase("foX", 0), "Pos in case insensitive search");
    }

    @Test
    void testBadConditionsOnIndexOf() {
        assertEquals(-1, this.b.indexOf("lazy", this.data.length() + 5), "Bad startOfs gives not found");
    }

    @Test
    void testBadConditionsOnStrcmp() {
        assertFalse(this.b.strcmp(0, null), "Null pattern gives false");
    }

    @Test
    void testBadConditionOnByteAt() {
        int byteAt = this.data.length() + 5;
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> this.b.byteAt(byteAt));
    }

    @Test
    void testStartsWith() {
        assertTrue(this.b.startsWith("The quick"), "Starts with must work");
    }

    @Test
    void testNotStartsWith() {
        assertFalse(this.b.startsWith("Fred"), "Starts with must not lie");
    }

    @Test
    void testBadConditionOnIndexIgnoreCase() {
        assertEquals(-1, this.b.indexIgnoreCase("lazy".getBytes(), this.data.length() + 5), "IndexIgnore cannot find pattern with bad ofs");
    }
}
