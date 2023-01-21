package emissary.util.search;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.UnsupportedCharsetException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeywordScannerTest extends UnitTest {
    private final byte[] DATA = "THIS is a test of the Emergency broadcasting system.".getBytes(UTF_8);
    private KeywordScanner ks;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        this.ks = new KeywordScanner(this.DATA);
    }

    @Test
    void testResetKeywordScanner() {
        assertEquals(0, this.ks.indexOf("THI".getBytes(UTF_8)));
        String otherData = "No, THIS is a test of the Emergency broadcasting system.";
        this.ks.resetData(otherData);
        assertEquals(4, this.ks.indexOf("THI".getBytes(UTF_8)));
    }

    @Test
    void testKeywordScannerWithCharset() {
        assertEquals(0, this.ks.indexOf("THI".getBytes(UTF_8)));
        String otherData = "No, THIS is a test of the Emergency broadcasting system.";
        this.ks.resetData(otherData, "ISO-8859-1");
        assertEquals(4, this.ks.indexOf("THI".getBytes(UTF_8)));
        assertThrows(UnsupportedCharsetException.class, () -> this.ks.resetData("Other other data", "NoSuchCharset"));
    }

    @Test
    void testConstructor() {
        assertEquals(0, this.ks.indexOf("THI".getBytes(UTF_8)));
        this.ks = new KeywordScanner(null);
        assertEquals(-1, this.ks.indexOf("THI".getBytes(UTF_8)));
    }

    @Test
    void testEmptyConstructor() {
        assertEquals(0, this.ks.indexOf("THI".getBytes(UTF_8)));
        this.ks = new KeywordScanner();
        assertEquals(-1, this.ks.indexOf("THI".getBytes(UTF_8)));
    }

    @Test
    void testCaseSensitiveSearchNotFound() {
        assertTrue(this.ks.isCaseSensitive());
        assertEquals(-1, this.ks.indexOf("TEST".getBytes(UTF_8)), "Case sensitive searching by default");
    }

    @Test
    void testCaseSensitiveSearchFound() {
        assertTrue(this.ks.isCaseSensitive());
        assertEquals(3, this.ks.indexOf("S is a".getBytes(UTF_8)), "Case sensitive searching by default");
    }

    @Test
    void testBeginningOfRegion() {
        assertEquals(0, this.ks.indexOf("THIS".getBytes(UTF_8)), "Hit at start of region");
    }

    @Test
    void testEndOfRegion() {
        assertEquals(this.DATA.length - 1, this.ks.indexOf(".".getBytes(UTF_8)), "Hit at end of region");
    }

    @Test
    void testCaseInsensitiveNotFound() {
        this.ks.setCaseSensitive(false);
        assertFalse(this.ks.isCaseSensitive());
        assertEquals(-1, this.ks.indexOf("Foo".getBytes(UTF_8)), "Case insensitive");
    }

    @Test
    void testCaseInsensitiveFound() {
        this.ks.setCaseSensitive(false);
        assertFalse(this.ks.isCaseSensitive());
        assertEquals(10, this.ks.indexOf("TeST".getBytes(UTF_8)), "Case insensitive");
    }

    @Test
    void testIndexOf() {
        assertEquals(33, this.ks.indexOf("road".getBytes(UTF_8)));
        assertEquals(22, this.ks.indexOf("Emergency".getBytes(UTF_8), 0));
        assertEquals(-1, this.ks.indexOf("Emergency".getBytes(UTF_8), 40));
        assertEquals(12, this.ks.indexOf("st".getBytes(UTF_8), 0, 30));
        assertEquals(12, this.ks.indexOf("st".getBytes(UTF_8), 0, this.DATA.length));
        assertEquals(12, this.ks.indexOf("st".getBytes(UTF_8), 12, this.DATA.length));
        assertEquals(39, this.ks.indexOf("st".getBytes(UTF_8), 30, 41));
        assertEquals(39, this.ks.indexOf("st".getBytes(UTF_8), 39, this.DATA.length));
        // no matches in this range
        assertEquals(-1, this.ks.indexOf("st".getBytes(UTF_8), 30, 40));
        assertEquals(22, this.ks.indexOf("E".getBytes(UTF_8), 0, this.DATA.length));
        // start offset < 0
        assertEquals(22, this.ks.indexOf("E".getBytes(UTF_8), -1, this.DATA.length));
        // stop index > data length
        assertEquals(-1, this.ks.indexOf("st".getBytes(UTF_8), 0, this.DATA.length + 1));
        // start offset == data length
        assertEquals(-1, this.ks.indexOf("st".getBytes(UTF_8), this.DATA.length, this.DATA.length));
        // start offset > data length
        assertEquals(-1, this.ks.indexOf("st".getBytes(UTF_8), this.DATA.length + 1, this.DATA.length));
        // pattern is null
        assertEquals(-1, this.ks.indexOf(null, 0, this.DATA.length));
        // stop index is exclusive
        assertEquals(-1, this.ks.indexOf("HI".getBytes(UTF_8), 0, 2));
        assertEquals(1, this.ks.indexOf("HI".getBytes(UTF_8), 0, 3));
        // negative stop index results in failure to match
        assertEquals(-1, this.ks.indexOf("HI".getBytes(UTF_8), 0, -1));
        assertEquals(-1, this.ks.indexOf("HI".getBytes(UTF_8), 0, -5));
        // stop < start
        assertEquals(-1, this.ks.indexOf("i".getBytes(UTF_8), 5, 1));
        // stop == start
        assertEquals(-1, this.ks.indexOf("i".getBytes(UTF_8), 5, 5));
        // pattern is longer than stop - start
        assertEquals(-1, this.ks.indexOf("Emergency".getBytes(UTF_8), 0, 5));
        // pattern is longer than the data
        assertEquals(-1, this.ks.indexOf(new byte[75], 0, this.DATA.length));

        // try using an array of negative byte values and a negative byte pattern
        this.ks = new KeywordScanner(new byte[] {-1, -1, -1, -3, -5, -7});
        assertEquals(2, this.ks.indexOf(new byte[] {-1, -3, -5}));
    }

    @Test
    void testFindNext() {
        // no pattern set
        assertEquals(-1, this.ks.findNext());
        assertEquals(-1, this.ks.findNext(20));
        // sets the pattern and returns first match
        assertEquals(12, this.ks.indexOf("st".getBytes(UTF_8)));
        assertEquals(39, this.ks.findNext());
        assertEquals(47, this.ks.findNext());
        assertEquals(-1, this.ks.findNext());
        // does not loop back around
        assertEquals(-1, this.ks.findNext());
        // sets a new pattern and returns first match
        assertEquals(8, this.ks.indexOf("a".getBytes(UTF_8)));
        // negative stop
        assertEquals(-1, this.ks.findNext(-1));
        // stop > data length
        assertEquals(-1, this.ks.findNext(75));
        // stop < previous position
        assertEquals(-1, this.ks.findNext(5));
        // stop == data length
        assertEquals(-1, this.ks.findNext(this.DATA.length));
        assertEquals(35, this.ks.findNext(this.DATA.length - 1));
        // changing the pattern, we should have reset the position
        assertEquals(6, this.ks.indexOf("s".getBytes(UTF_8)));
        // stop is exclusive
        assertEquals(12, this.ks.findNext(13));
        // use a pattern that won't hit
        assertEquals(-1, this.ks.indexOf("X".getBytes(UTF_8)));
        // new pattern must be set in failure or we would get an index off the old one
        assertEquals(-1, this.ks.findNext());
        // change the pattern back
        assertEquals(6, this.ks.indexOf("s".getBytes(UTF_8)));
        // stop is short of the next position (12)
        assertEquals(-1, this.ks.findNext(12));
        // we can't continue from last successful index returned, a bad stop ends it
        assertEquals(-1, this.ks.findNext());
    }
}
