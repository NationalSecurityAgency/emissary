package emissary.util.search;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.Collections;
import javax.annotation.Nullable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ByteMatcherTest extends UnitTest {

    private static final String DATA = "The quick brown fox jumped over the lazy dog";
    private static final String LIST_DATA = "This is a test. Is this a test? Yes, this is a test. TEST!";
    @Nullable
    private ByteMatcher b;
    private ByteMatcher bl;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        this.b = new ByteMatcher(DATA.getBytes());
        this.bl = new ByteMatcher(LIST_DATA.getBytes());
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
        String localDataTwo = "But the faster dog ate the slower fox.";
        String firstToken = "fox";
        String secondToken = "dog";
        ByteMatcher byteMatcher = new ByteMatcher(DATA);

        assertEquals(16, byteMatcher.indexOf(firstToken));
        assertEquals(41, byteMatcher.indexOf(secondToken));

        // Reuse the same ByteMatcher
        byteMatcher.resetData(localDataTwo);

        assertEquals(34, byteMatcher.indexOf(firstToken));
        assertEquals(15, byteMatcher.indexOf(secondToken));
    }

    @Test
    void testByteMatcherWithCharset() {
        String localDataOne = DATA;
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
        assertEquals(DATA.indexOf("fox"), this.b.indexOf("fox"), "Match pos same as string");
    }

    @Test
    void testIndexOfBytes() {
        assertEquals(DATA.indexOf("fox"), this.b.indexOf("fox".getBytes()), "Match pos same as string");
    }

    @Test
    void testListIndexOf() {
        // Case Sensitive
        ArrayList<Integer> findTestCaseSensitive = new ArrayList<>();
        Collections.addAll(findTestCaseSensitive, 10, 26, 47);
        // Byte pattern param test.
        assertEquals(findTestCaseSensitive, this.bl.listIndexOf("test".getBytes()));
        assertEquals(findTestCaseSensitive, this.bl.listIndexOf("test".getBytes(), 0));
        assertEquals(findTestCaseSensitive, this.bl.listIndexOf("test".getBytes(), 0, LIST_DATA.length()));

        // String pattern param test
        assertEquals(findTestCaseSensitive, this.bl.listIndexOf("test"));
        assertEquals(findTestCaseSensitive, this.bl.listIndexOf("test", 0));
        assertEquals(findTestCaseSensitive, this.bl.listIndexOf("test", 0, LIST_DATA.length()));

        // Case-insensitive
        ArrayList<Integer> findTestCaseInsensitive = new ArrayList<>();
        Collections.addAll(findTestCaseInsensitive, 10, 26, 47, 53);
        // Byte pattern param test
        assertEquals(findTestCaseInsensitive, this.bl.indexListIgnoreCase("test".getBytes()));
        assertEquals(findTestCaseInsensitive, this.bl.indexListIgnoreCase("test".getBytes(), 0));
        assertEquals(findTestCaseInsensitive, this.bl.indexListIgnoreCase("test".getBytes(), 0, LIST_DATA.length()));

        // String pattern param test
        assertEquals(findTestCaseInsensitive, this.bl.indexListIgnoreCase("test"));
        assertEquals(findTestCaseInsensitive, this.bl.indexListIgnoreCase("test", 0));
        assertEquals(findTestCaseInsensitive, this.bl.indexListIgnoreCase("test", 0, LIST_DATA.length()));

        String stop = "Stop";
    }

    @Test
    void testOffsetScan() {
        assertEquals(DATA.indexOf("fox", 9), this.b.indexOf("fox", 9), "Match pos same as string using offset");
    }

    @Test
    void testNotFound() {
        assertEquals(DATA.indexOf("llama"), this.b.indexOf("llama"), "Match pos same as string when not found");
    }

    @Test
    void testIndexOfBytesExcludedByEndIndex() {
        assertEquals(ByteMatcher.NOTFOUND, this.b.indexOf("dog".getBytes(), 0, b.length() - 1), "Match pos not found");
    }

    @Test
    void testIndexOfBytesIncludedWithEndIndex() {
        assertEquals(DATA.indexOf("dog"), this.b.indexOf("dog".getBytes(), 0, b.length()), "Match pos same as string");
    }

    @Test
    void testIndexOfExcludedByEndIndex() {
        assertEquals(ByteMatcher.NOTFOUND, this.b.indexOf("dog", 0, b.length() - 1), "Match pos not found");
    }

    @Test
    void testIndexOfIncludedWithEndIndex() {
        assertEquals(DATA.indexOf("dog"), this.b.indexOf("dog", 0, b.length()), "Match pos same as string");
    }

    @Test
    void testLength() {
        assertEquals(DATA.length(), this.b.length(), "Length same as string");
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
        final byte[] res = this.b.slice(0, DATA.length() + 10);
        assertNotNull(res, "Bad slice must not return null");
        assertEquals(0, res.length, "Length of bad slice must be 0");
    }

    @Test
    void testByteAt() {
        assertEquals(DATA.charAt(11), (char) this.b.byteAt(11), "ByteAt matches string charAt");
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
        assertEquals("value", new String(this.b.getSValue("key", 4, DATA.length())).trim(), "SValue extraction");
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
        assertEquals(DATA.indexOf("fox"), this.b.indexIgnoreCase("foX"), "Pos in case insensitive search");
    }

    @Test
    void testIgnoreCaseByteScan() {
        assertEquals(DATA.indexOf("fox"), this.b.indexIgnoreCase("foX".getBytes()), "Pos in case insensitive search");
    }

    @Test
    void testIgnoreCaseScanWithOffset() {
        assertEquals(DATA.indexOf("fox"), this.b.indexIgnoreCase("foX", 0), "Pos in case insensitive search");
    }

    @Test
    void testIndexIgnoreCaseScanExcludedByEndIndex() {
        assertEquals(ByteMatcher.NOTFOUND, this.b.indexIgnoreCase("Dog", 0, b.length() - 1), "Match pos not found");
    }

    @Test
    void testIndexIgnoreCaseScanIncludedWithEndIndex() {
        assertEquals(DATA.indexOf("dog"), this.b.indexIgnoreCase("Dog", 0, b.length()), "Match pos same as string");
    }

    @Test
    void testBadConditionsOnIndexOf() {
        assertEquals(-1, this.b.indexOf("lazy", DATA.length() + 5), "Bad startOfs gives not found");
    }

    @Test
    void testBadConditionsOnStrcmp() {
        assertFalse(this.b.strcmp(0, null), "Null pattern gives false");
    }

    @Test
    void testBadConditionOnByteAt() {
        int byteAt = DATA.length() + 5;
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
        assertEquals(-1, this.b.indexIgnoreCase("lazy".getBytes(), DATA.length() + 5), "IndexIgnore cannot find pattern with bad ofs");
    }

    @Test
    void testNullDataBytesIndexOf() {
        this.b = new ByteMatcher((byte[]) null);
        assertEquals(-1, this.b.indexOf("Fred".getBytes()), "Match pos not found");
    }

    @Test
    void testNullDataBytesIgnoreCase() {
        this.b = new ByteMatcher((byte[]) null);
        assertEquals(-1, this.b.indexIgnoreCase("Fred".getBytes()), "Match pos not found");
    }

}
