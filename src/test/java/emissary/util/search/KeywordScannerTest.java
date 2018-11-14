package emissary.util.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.UnsupportedCharsetException;

import emissary.test.core.UnitTest;
import org.junit.Before;
import org.junit.Test;

public class KeywordScannerTest extends UnitTest {
    private final byte[] DATA = "THIS is a test of the Emergency broadcasting system.".getBytes();
    private KeywordScanner ks;

    @Override
    @Before
    public void setUp() throws Exception {
        this.ks = new KeywordScanner(this.DATA);
    }

    @Test
    public void testResetKeywordScanner() {
        assertEquals(0, this.ks.indexOf("THI".getBytes()));
        String otherData = "No, THIS is a test of the Emergency broadcasting system.";
        this.ks.resetData(otherData);
        assertEquals(4, this.ks.indexOf("THI".getBytes()));
    }

    @Test(expected = UnsupportedCharsetException.class)
    public void testKeywordScannerWithCharset() {
        assertEquals(0, this.ks.indexOf("THI".getBytes()));
        String otherData = "No, THIS is a test of the Emergency broadcasting system.";
        this.ks.resetData(otherData, "ISO-8859-1");
        assertEquals(4, this.ks.indexOf("THI".getBytes()));
        this.ks.resetData("Other other data", "NoSuchCharset");
    }

    @Test
    public void testConstructor() {
        assertEquals(0, this.ks.indexOf("THI".getBytes()));
        this.ks = new KeywordScanner(null);
        assertEquals(-1, this.ks.indexOf("THI".getBytes()));
    }

    @Test
    public void testEmptyConstructor() {
        assertEquals(0, this.ks.indexOf("THI".getBytes()));
        this.ks = new KeywordScanner();
        assertEquals(-1, this.ks.indexOf("THI".getBytes()));
    }

    @Test
    public void testCaseSensitiveSearchNotFound() {
        assertTrue(this.ks.isCaseSensitive());
        assertEquals("Case sensitive searching by default", -1, this.ks.indexOf("TEST".getBytes()));
    }

    @Test
    public void testCaseSensitiveSearchFound() {
        assertTrue(this.ks.isCaseSensitive());
        assertEquals("Case sensitive searching by default", 3, this.ks.indexOf("S is a".getBytes()));
    }

    @Test
    public void testBeginningOfRegion() {
        assertEquals("Hit at start of region", 0, this.ks.indexOf("THIS".getBytes()));
    }

    @Test
    public void testEndOfRegion() {
        assertEquals("Hit at end of region", this.DATA.length - 1, this.ks.indexOf(".".getBytes()));
    }

    @Test
    public void testCaseInsensitiveNotFound() {
        this.ks.setCaseSensitive(false);
        assertFalse(this.ks.isCaseSensitive());
        assertEquals("Case insensitive", -1, this.ks.indexOf("Foo".getBytes()));
    }

    @Test
    public void testCaseInsensitiveFound() {
        this.ks.setCaseSensitive(false);
        assertFalse(this.ks.isCaseSensitive());
        assertEquals("Case insensitive", 10, this.ks.indexOf("TeST".getBytes()));
    }

    @Test
    public void testIndexOf() {
        assertEquals(33, this.ks.indexOf("road".getBytes()));

        assertEquals(22, this.ks.indexOf("Emergency".getBytes(), 0));
        assertEquals(-1, this.ks.indexOf("Emergency".getBytes(), 40));

        assertEquals(12, this.ks.indexOf("st".getBytes(), 0, 30));
        assertEquals(12, this.ks.indexOf("st".getBytes(), 0, this.DATA.length));
        assertEquals(12, this.ks.indexOf("st".getBytes(), 12, this.DATA.length));
        assertEquals(39, this.ks.indexOf("st".getBytes(), 30, 41));
        assertEquals(39, this.ks.indexOf("st".getBytes(), 39, this.DATA.length));
        assertEquals(-1, this.ks.indexOf("st".getBytes(), 30, 40)); // no matches in this range

        assertEquals(22, this.ks.indexOf("E".getBytes(), 0, this.DATA.length));
        assertEquals(22, this.ks.indexOf("E".getBytes(), -1, this.DATA.length)); // start offset < 0

        assertEquals(-1, this.ks.indexOf("st".getBytes(), 0, this.DATA.length + 1)); // stop index > data length
        assertEquals(-1, this.ks.indexOf("st".getBytes(), this.DATA.length, this.DATA.length)); // start offset == data
                                                                                                // length
        assertEquals(-1, this.ks.indexOf("st".getBytes(), this.DATA.length + 1, this.DATA.length)); // start offset >
                                                                                                    // data length
        assertEquals(-1, this.ks.indexOf(null, 0, this.DATA.length)); // pattern is null

        assertEquals(-1, this.ks.indexOf("HI".getBytes(), 0, 2)); // stop index is exclusive
        assertEquals(1, this.ks.indexOf("HI".getBytes(), 0, 3));
        assertEquals(-1, this.ks.indexOf("HI".getBytes(), 0, -1)); // negative stop index results in failure to match
        assertEquals(-1, this.ks.indexOf("HI".getBytes(), 0, -5));
        assertEquals(-1, this.ks.indexOf("i".getBytes(), 5, 1)); // stop < start
        assertEquals(-1, this.ks.indexOf("i".getBytes(), 5, 5)); // stop == start

        assertEquals(-1, this.ks.indexOf("Emergency".getBytes(), 0, 5)); // pattern is longer than stop - start
        assertEquals(-1, this.ks.indexOf(new byte[75], 0, this.DATA.length)); // pattern is longer than the data

        // try using an array of negative byte values and a negative byte pattern
        this.ks = new KeywordScanner(new byte[] {-1, -1, -1, -3, -5, -7});
        assertEquals(2, this.ks.indexOf(new byte[] {-1, -3, -5}));
    }

    @Test
    public void testFindNext() {
        assertEquals(-1, this.ks.findNext()); // no pattern set
        assertEquals(-1, this.ks.findNext(20));

        // sets the pattern and returns first match
        assertEquals(12, this.ks.indexOf("st".getBytes()));

        assertEquals(39, this.ks.findNext());
        assertEquals(47, this.ks.findNext());
        assertEquals(-1, this.ks.findNext());
        assertEquals(-1, this.ks.findNext()); // does not loop back around

        // sets a new pattern and returns first match
        assertEquals(8, this.ks.indexOf("a".getBytes()));

        assertEquals(-1, this.ks.findNext(-1)); // negative stop
        assertEquals(-1, this.ks.findNext(75)); // stop > data length
        assertEquals(-1, this.ks.findNext(5)); // stop < previous position
        assertEquals(-1, this.ks.findNext(this.DATA.length)); // stop == data length
        assertEquals(35, this.ks.findNext(this.DATA.length - 1));

        // changing the pattern, we should have reset the position
        assertEquals(6, this.ks.indexOf("s".getBytes()));

        assertEquals(12, this.ks.findNext(13)); // stop is exclusive

        assertEquals(-1, this.ks.indexOf("X".getBytes())); // use a pattern that won't hit
        assertEquals(-1, this.ks.findNext()); // new pattern must be set in failure or we would get an index off the old
                                              // one

        // change the pattern back
        assertEquals(6, this.ks.indexOf("s".getBytes()));
        assertEquals(-1, this.ks.findNext(12)); // stop is short of the next position (12)
        assertEquals(-1, this.ks.findNext()); // we can't continue from last successful index returned, a bad stop ends
                                              // it
    }
}
