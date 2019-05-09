package emissary.util.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.UnsupportedCharsetException;

import emissary.test.core.UnitTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ByteMatcherTest extends UnitTest {

    private final String data = "The quick brown fox jumped over the lazy dog";
    private ByteMatcher b;

    @Override
    @Before
    public void setUp() throws Exception {
        this.b = new ByteMatcher(this.data.getBytes());
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        this.b = null;
    }

    @Test
    public void testEmptyConstructor() {
        ByteMatcher byteMatcher = new ByteMatcher();
        Assert.assertEquals(-1, byteMatcher.indexOf("a token, hadoken"));
    }

    @Test
    public void testResetByteMatcher() {
        String localDataOne = "The quick brown fox jumped over the lazy dog";
        String localDataTwo = "But the faster dog ate the slower fox.";
        String firstToken = "fox";
        String secondToken = "dog";
        ByteMatcher byteMatcher = new ByteMatcher(localDataOne);

        Assert.assertEquals(16, byteMatcher.indexOf(firstToken));
        Assert.assertEquals(41, byteMatcher.indexOf(secondToken));

        // Reuse the same ByteMatcher
        byteMatcher.resetData(localDataTwo);

        Assert.assertEquals(34, byteMatcher.indexOf(firstToken));
        Assert.assertEquals(15, byteMatcher.indexOf(secondToken));
    }

    @Test(expected = UnsupportedCharsetException.class)
    public void testByteMatcherWithCharset() {
        String localDataOne = "The quick brown fox jumped over the lazy dog";
        String localDataTwo = "But the faster dog ate the slower fox.";
        String firstToken = "fox";
        String secondToken = "dog";
        ByteMatcher byteMatcher = new ByteMatcher(localDataOne);

        // Back to the first data, but with a different charset
        byteMatcher.resetData(localDataOne, "ISO-8859-1");

        Assert.assertEquals(16, byteMatcher.indexOf(firstToken));
        Assert.assertEquals(41, byteMatcher.indexOf(secondToken));

        // Back to the second data, with a charset that doesn't exist.
        byteMatcher.resetData(localDataTwo, "NoSuchCharset");
    }

    @Test
    public void testSimpleScan() {
        assertEquals("Match pos same as string", this.data.indexOf("fox"), this.b.indexOf("fox"));
    }

    @Test
    public void testIndexOfBytes() {
        assertEquals("Match pos same as string", this.data.indexOf("fox"), this.b.indexOf("fox".getBytes()));
    }

    @Test
    public void testOffsetScan() {
        assertEquals("Match pos same as string using offset", this.data.indexOf("fox", 9), this.b.indexOf("fox", 9));
    }

    @Test
    public void testNotFound() {
        assertEquals("Match pos same as string when not found", this.data.indexOf("llama"), this.b.indexOf("llama"));
    }

    @Test
    public void testLength() {
        assertEquals("Length same as string", this.data.length(), this.b.length());
    }

    @Test
    public void testStrcmp() {
        final int pos = this.b.indexOf("fox");
        assertTrue("Match pos work with strcmp", this.b.strcmp(pos, "fox"));
    }

    @Test
    public void testStrcmpNotFound() {
        assertFalse("Strcmp fails when at wrong pos", this.b.strcmp(5, "lazy"));
    }

    @Test
    public void testSlice() {
        final int pos = this.b.indexOf("fox");
        assertEquals("Slice extraction", "fox", new String(this.b.slice(pos, pos + 3)));
    }

    @Test
    public void testSliceAtEndOfRange() {
        final int pos = this.b.indexOf("dog");
        assertEquals("Slice extraction at end of range", "dog", new String(this.b.slice(pos, pos + 3)));
    }

    @Test
    public void testBadSlice() {
        final byte[] res = this.b.slice(0, this.data.length() + 10);
        assertNotNull("Bad slice must not return null", res);
        assertEquals("Length of bad slice must be 0", 0, res.length);
    }

    @Test
    public void testByteAt() {
        assertEquals("ByteAt matches string charAt", this.data.charAt(11), (char) this.b.byteAt(11));
    }

    @Test
    public void testGetValueDefaultDelim() {
        this.b = new ByteMatcher("Abc\nkey=value\r\nanother=key and value\n\r\n".getBytes());
        assertEquals("Value extraction", "value", this.b.getValue("key"));
    }

    @Test
    public void testGetSValue() {
        this.b = new ByteMatcher("Abc\nkey: 6\nvalue\r\nanother: 14\nkey and value\n\r\n".getBytes());
        assertEquals("SValue extraction", "value", new String(this.b.getSValue("key")).trim());
    }

    @Test
    public void testGetSValueNotFound() {
        this.b = new ByteMatcher("Abc\nkey: 6\nvalue\r\nanother: 14\nkey and value\n\r\n".getBytes());
        assertEquals("SValue extraction", null, this.b.getSValue("foo"));
    }

    @Test
    public void testGetSValueWithOfs() {
        this.b = new ByteMatcher("Abc\nkey: 6\nvalue\r\nanother: 14\nkey and value\n\r\n".getBytes());
        assertEquals("SValue extraction", "value", new String(this.b.getSValue("key", 4, this.data.length())).trim());
    }

    @Test
    public void testGetValueDefaultDelimWithOfs() {
        this.b = new ByteMatcher("Abc\nkey=value\r\nanother=key and value\n\r\n".getBytes());
        assertEquals("Value extraction", "value", this.b.getValue("key", 0));
    }

    @Test
    public void testGetValue() {
        this.b = new ByteMatcher("Abc\nkey: value\r\nanother: key and value\n\r\n".getBytes());
        assertEquals("Value extraction", " value", this.b.getValue("key", 0, ":"));
    }

    @Test
    public void testGetValueMultiCharDelim() {
        this.b = new ByteMatcher("Abc\nkey : value\r\nanother : key and value\n\r\n".getBytes());
        assertEquals("Value extraction", "value", this.b.getValue("key", 0, " : "));
    }

    @Test
    public void testGetValueNoKey() {
        this.b = new ByteMatcher("Abc\nkey: value\r\nanother: key and value\n\r\n".getBytes());
        assertEquals("Value extraction", null, this.b.getValue("foo", 0, ":"));
    }

    @Test
    public void testGetValueNoDelimiter() {
        this.b = new ByteMatcher("Abc\nkey: value\r\nanother: key and value\n\r\n".getBytes());
        assertEquals("Value extraction", null, this.b.getValue("key", 0, "="));
    }

    @Test
    public void testGetValueNoValue() {
        this.b = new ByteMatcher("Abc\nkey:\r\nanother: key and value\n\r\n".getBytes());
        assertEquals("Value extraction", "", this.b.getValue("key", 0, ":"));
    }

    @Test
    public void testIgnoreCaseScan() {
        assertEquals("Pos in case insensitive search", this.data.indexOf("fox"), this.b.indexIgnoreCase("foX"));
    }

    @Test
    public void testIgnoreCaseByteScan() {
        assertEquals("Pos in case insensitive search", this.data.indexOf("fox"), this.b.indexIgnoreCase("foX".getBytes()));
    }

    @Test
    public void testIgnoreCaseScanWithOffset() {
        assertEquals("Pos in case insensitive search", this.data.indexOf("fox"), this.b.indexIgnoreCase("foX", 0));
    }

    @Test
    public void testBadConditionsOnIndexOf() {
        assertEquals("Bad startOfs gives not found", -1, this.b.indexOf("lazy", this.data.length() + 5));
    }

    @Test
    public void testBadConditionsOnStrcmp() {
        assertFalse("Null pattern gives false", this.b.strcmp(0, null));
    }

    @Test
    public void testBadConditionOnByteAt() {
        try {
            this.b.byteAt(this.data.length() + 5);
            fail("Should have thrown exception on byteAt out of bounds");
        } catch (ArrayIndexOutOfBoundsException ex) {
            // expected
        }
    }

    @Test
    public void testStartsWith() {
        assertTrue("Starts with must work", this.b.startsWith("The quick"));
    }

    @Test
    public void testNotStartsWith() {
        assertFalse("Starts with must not lie", this.b.startsWith("Fred"));
    }

    @Test
    public void testBadConditionOnIndexIgnoreCase() {
        assertEquals("IndexIgnore cannot find pattern with bad ofs", -1, this.b.indexIgnoreCase("lazy".getBytes(), this.data.length() + 5));
    }
}
