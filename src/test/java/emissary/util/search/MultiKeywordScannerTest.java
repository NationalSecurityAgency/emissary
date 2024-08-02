package emissary.util.search;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiKeywordScannerTest {

    private final String[] defaultKeywords = {"fox", "dog"};
    private static final String DEFAULT_DATA = "the quick brown fox jumped over the lazy dog";

    @Test
    void testFindAll() {
        MultiKeywordScanner multiKeywordScanner = new MultiKeywordScanner();
        multiKeywordScanner.loadKeywords(defaultKeywords);
        HitList hits = multiKeywordScanner.findAll(DEFAULT_DATA.getBytes());
        assertEquals(2, hits.size());
        assertEquals(0, hits.get(0).getId());
        assertEquals(16, hits.get(0).getOffset());
        assertEquals(1, hits.get(1).getId());
        assertEquals(41, hits.get(1).getOffset());

        hits = multiKeywordScanner.findAll(null);
        assertTrue(hits.isEmpty());
    }

    @Test
    void testFindAllStart() {
        MultiKeywordScanner multiKeywordScanner = new MultiKeywordScanner();
        multiKeywordScanner.loadKeywords(defaultKeywords);
        HitList hits = multiKeywordScanner.findAll(DEFAULT_DATA.getBytes(), 28);
        assertEquals(1, hits.size());
        assertEquals(1, hits.get(0).getId());
        assertEquals(41, hits.get(0).getOffset());

        hits = multiKeywordScanner.findAll(null, 0);
        assertTrue(hits.isEmpty());
    }

    @Test
    void testFindAllStartStop() {
        MultiKeywordScanner multiKeywordScanner = new MultiKeywordScanner();
        multiKeywordScanner.loadKeywords(defaultKeywords);
        HitList hits = multiKeywordScanner.findAll(DEFAULT_DATA.getBytes(), 0, 24);
        assertEquals(1, hits.size());
        assertEquals(0, hits.get(0).getId());
        assertEquals(16, hits.get(0).getOffset());

        hits = multiKeywordScanner.findAll(null, 0, 0);
        assertTrue(hits.isEmpty());
    }

    @Test
    void testFindNext() {
        MultiKeywordScanner multiKeywordScanner = new MultiKeywordScanner();
        multiKeywordScanner.loadKeywords(defaultKeywords);
        HitList hits = multiKeywordScanner.findNext(DEFAULT_DATA.getBytes());
        assertEquals(1, hits.size());
        assertEquals(0, hits.get(0).getId());
        assertEquals(16, hits.get(0).getOffset());

        hits = multiKeywordScanner.findNext(DEFAULT_DATA.getBytes());
        assertEquals(1, hits.size());
        assertEquals(1, hits.get(0).getId());
        assertEquals(41, hits.get(0).getOffset());

        hits = multiKeywordScanner.findNext(null);
        assertTrue(hits.isEmpty());
    }

    @Test
    void testFindNextStart() {
        MultiKeywordScanner multiKeywordScanner = new MultiKeywordScanner();
        multiKeywordScanner.loadKeywords(defaultKeywords);
        HitList hits = multiKeywordScanner.findNext(DEFAULT_DATA.getBytes(), 28);
        assertEquals(1, hits.size());
        assertEquals(1, hits.get(0).getId());
        assertEquals(41, hits.get(0).getOffset());

        hits = multiKeywordScanner.findNext(null, 28);
        assertTrue(hits.isEmpty());
    }

    @Test
    void testFindNextStartStop() {
        MultiKeywordScanner multiKeywordScanner = new MultiKeywordScanner();
        multiKeywordScanner.loadKeywords(defaultKeywords);
        HitList hits = multiKeywordScanner.findNext(DEFAULT_DATA.getBytes(), 0, 24);
        assertEquals(1, hits.size());
        assertEquals(0, hits.get(0).getId());
        assertEquals(16, hits.get(0).getOffset());

        hits = multiKeywordScanner.findNext(null, 0, 0);
        assertTrue(hits.isEmpty());
    }

    @Test
    void testMultiKeywordScannerReset() {
        MultiKeywordScanner multiKeywordScanner = new MultiKeywordScanner();
        multiKeywordScanner.loadKeywords(defaultKeywords);

        // Take advantage of resetting the data in the underlying BackwardsTreeScanner
        // instead of re-instantiating the whole object.
        String[] keywords = {"quick", "brown", "lazy"};
        multiKeywordScanner.loadKeywords(keywords);
        HitList hits = multiKeywordScanner.findAll(DEFAULT_DATA.getBytes());

        assertEquals(3, hits.size());
        assertEquals(0, hits.get(0).getId());
        assertEquals(4, hits.get(0).getOffset());
        assertEquals(1, hits.get(1).getId());
        assertEquals(10, hits.get(1).getOffset());
        assertEquals(2, hits.get(2).getId());
        assertEquals(36, hits.get(2).getOffset());
    }
}
