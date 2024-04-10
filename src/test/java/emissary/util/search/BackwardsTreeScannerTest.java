package emissary.util.search;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BackwardsTreeScannerTest {

    private final String[] defaultKeywords = {"fox", "dog"};
    private static final String DEFAULT_DATA = "the quick brown fox jumped over the lazy dog";

    @Test
    void testBothConstructors() throws Exception {
        HitList hits = new HitList();
        BackwardsTreeScanner first = new BackwardsTreeScanner();
        first.resetKeywords(defaultKeywords);
        int result = first.scan(DEFAULT_DATA.getBytes(), DEFAULT_DATA.length() - 1, hits);
        assertEquals(39, result);

        hits = new HitList();
        BackwardsTreeScanner second = new BackwardsTreeScanner(defaultKeywords);
        result = second.scan(DEFAULT_DATA.getBytes(), DEFAULT_DATA.length() - 1, hits);
        assertEquals(39, result);
    }

    @Test
    void testExpectedException() throws Exception {
        HitList hits = new HitList();
        BackwardsTreeScanner second = new BackwardsTreeScanner(defaultKeywords);
        assertThrows(Exception.class, () -> second.scan(null, DEFAULT_DATA.length() - 1, hits));
    }
}
