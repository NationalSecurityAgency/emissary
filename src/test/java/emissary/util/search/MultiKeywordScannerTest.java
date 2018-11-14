package emissary.util.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MultiKeywordScannerTest {

    private String[] defaultKeywords = {"fox", "dog"};
    private String defaultData = "the quick brown fox jumped over the lazy dog";

    @Test
    public void testFindAll() {
        MultiKeywordScanner multiKeywordScanner = new MultiKeywordScanner();
        multiKeywordScanner.loadKeywords(defaultKeywords);
        HitList hits = multiKeywordScanner.findAll(defaultData.getBytes());
        assertEquals(2, hits.size());
        assertEquals(0, hits.get(0).getID());
        assertEquals(16, hits.get(0).getOffset());
        assertEquals(1, hits.get(1).getID());
        assertEquals(41, hits.get(1).getOffset());

        hits = multiKeywordScanner.findAll(null);
        assertTrue(hits.isEmpty());
    }

    @Test
    public void testFindAllStart() {
        MultiKeywordScanner multiKeywordScanner = new MultiKeywordScanner();
        multiKeywordScanner.loadKeywords(defaultKeywords);
        HitList hits = multiKeywordScanner.findAll(defaultData.getBytes(), 28);
        assertEquals(1, hits.size());
        assertEquals(1, hits.get(0).getID());
        assertEquals(41, hits.get(0).getOffset());

        hits = multiKeywordScanner.findAll(null, 0);
        assertTrue(hits.isEmpty());
    }

    @Test
    public void testFindAllStartStop() {
        MultiKeywordScanner multiKeywordScanner = new MultiKeywordScanner();
        multiKeywordScanner.loadKeywords(defaultKeywords);
        HitList hits = multiKeywordScanner.findAll(defaultData.getBytes(), 0, 24);
        assertEquals(1, hits.size());
        assertEquals(0, hits.get(0).getID());
        assertEquals(16, hits.get(0).getOffset());

        hits = multiKeywordScanner.findAll(null, 0, 0);
        assertTrue(hits.isEmpty());
    }

    @Test
    public void testFindNext() {
        MultiKeywordScanner multiKeywordScanner = new MultiKeywordScanner();
        multiKeywordScanner.loadKeywords(defaultKeywords);
        HitList hits = multiKeywordScanner.findNext(defaultData.getBytes());
        assertEquals(1, hits.size());
        assertEquals(0, hits.get(0).getID());
        assertEquals(16, hits.get(0).getOffset());

        hits = multiKeywordScanner.findNext(defaultData.getBytes());
        assertEquals(1, hits.size());
        assertEquals(1, hits.get(0).getID());
        assertEquals(41, hits.get(0).getOffset());

        hits = multiKeywordScanner.findNext(null);
        assertTrue(hits.isEmpty());
    }

    @Test
    public void testFindNextStart() {
        MultiKeywordScanner multiKeywordScanner = new MultiKeywordScanner();
        multiKeywordScanner.loadKeywords(defaultKeywords);
        HitList hits = multiKeywordScanner.findNext(defaultData.getBytes(), 28);
        assertEquals(1, hits.size());
        assertEquals(1, hits.get(0).getID());
        assertEquals(41, hits.get(0).getOffset());

        hits = multiKeywordScanner.findNext(null, 28);
        assertTrue(hits.isEmpty());
    }

    @Test
    public void testFindNextStartStop() {
        MultiKeywordScanner multiKeywordScanner = new MultiKeywordScanner();
        multiKeywordScanner.loadKeywords(defaultKeywords);
        HitList hits = multiKeywordScanner.findNext(defaultData.getBytes(), 0, 24);
        assertEquals(1, hits.size());
        assertEquals(0, hits.get(0).getID());
        assertEquals(16, hits.get(0).getOffset());

        hits = multiKeywordScanner.findNext(null, 0, 0);
        assertTrue(hits.isEmpty());
    }

    @Test
    public void testMultiKeywordScannerReset() {
        MultiKeywordScanner multiKeywordScanner = new MultiKeywordScanner();
        multiKeywordScanner.loadKeywords(defaultKeywords);

        // Take advantage of resetting the data in the underlying BackwardsTreeScanner
        // instead of re-instantiating the whole object.
        String[] keywords = {"quick", "brown", "lazy"};
        multiKeywordScanner.loadKeywords(keywords);
        HitList hits = multiKeywordScanner.findAll(defaultData.getBytes());

        assertEquals(3, hits.size());
        assertEquals(0, hits.get(0).getID());
        assertEquals(4, hits.get(0).getOffset());
        assertEquals(1, hits.get(1).getID());
        assertEquals(10, hits.get(1).getOffset());
        assertEquals(2, hits.get(2).getID());
        assertEquals(36, hits.get(2).getOffset());
    }
}
