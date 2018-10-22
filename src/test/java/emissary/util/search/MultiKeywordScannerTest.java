package emissary.util.search;

import org.junit.Assert;
import org.junit.Test;

public class MultiKeywordScannerTest {

    private String[] defaultKeywords = {"fox", "dog"};
    private String defaultData = "the quick brown fox jumped over the lazy dog";

    @Test
    public void testFindAll() {
        MultiKeywordScanner multiKeywordScanner = new MultiKeywordScanner();
        multiKeywordScanner.loadKeywords(defaultKeywords);
        HitList hits = multiKeywordScanner.findAll(defaultData.getBytes());
        Assert.assertEquals(2, hits.size());
        Assert.assertEquals(0, hits.get(0).getID());
        Assert.assertEquals(16, hits.get(0).getOffset());
        Assert.assertEquals(1, hits.get(1).getID());
        Assert.assertEquals(41, hits.get(1).getOffset());

        hits = multiKeywordScanner.findAll(null);
        Assert.assertTrue(hits.isEmpty());
    }

    @Test
    public void testFindAllStart() {
        MultiKeywordScanner multiKeywordScanner = new MultiKeywordScanner();
        multiKeywordScanner.loadKeywords(defaultKeywords);
        HitList hits = multiKeywordScanner.findAll(defaultData.getBytes(), 28);
        Assert.assertEquals(1, hits.size());
        Assert.assertEquals(1, hits.get(0).getID());
        Assert.assertEquals(41, hits.get(0).getOffset());

        hits = multiKeywordScanner.findAll(null, 0);
        Assert.assertTrue(hits.isEmpty());
    }

    @Test
    public void testFindAllStartStop() {
        MultiKeywordScanner multiKeywordScanner = new MultiKeywordScanner();
        multiKeywordScanner.loadKeywords(defaultKeywords);
        HitList hits = multiKeywordScanner.findAll(defaultData.getBytes(), 0, 24);
        Assert.assertEquals(1, hits.size());
        Assert.assertEquals(0, hits.get(0).getID());
        Assert.assertEquals(16, hits.get(0).getOffset());

        hits = multiKeywordScanner.findAll(null, 0, 0);
        Assert.assertTrue(hits.isEmpty());
    }

    @Test
    public void testFindNext() {
        MultiKeywordScanner multiKeywordScanner = new MultiKeywordScanner();
        multiKeywordScanner.loadKeywords(defaultKeywords);
        HitList hits = multiKeywordScanner.findNext(defaultData.getBytes());
        Assert.assertEquals(1, hits.size());
        Assert.assertEquals(0, hits.get(0).getID());
        Assert.assertEquals(16, hits.get(0).getOffset());

        hits = multiKeywordScanner.findNext(defaultData.getBytes());
        Assert.assertEquals(1, hits.size());
        Assert.assertEquals(1, hits.get(0).getID());
        Assert.assertEquals(41, hits.get(0).getOffset());

        hits = multiKeywordScanner.findNext(null);
        Assert.assertTrue(hits.isEmpty());
    }

    @Test
    public void testFindNextStart() {
        MultiKeywordScanner multiKeywordScanner = new MultiKeywordScanner();
        multiKeywordScanner.loadKeywords(defaultKeywords);
        HitList hits = multiKeywordScanner.findNext(defaultData.getBytes(), 28);
        Assert.assertEquals(1, hits.size());
        Assert.assertEquals(1, hits.get(0).getID());
        Assert.assertEquals(41, hits.get(0).getOffset());

        hits = multiKeywordScanner.findNext(null, 28);
        Assert.assertTrue(hits.isEmpty());
    }

    @Test
    public void testFindNextStartStop() {
        MultiKeywordScanner multiKeywordScanner = new MultiKeywordScanner();
        multiKeywordScanner.loadKeywords(defaultKeywords);
        HitList hits = multiKeywordScanner.findNext(defaultData.getBytes(), 0, 24);
        Assert.assertEquals(1, hits.size());
        Assert.assertEquals(0, hits.get(0).getID());
        Assert.assertEquals(16, hits.get(0).getOffset());

        hits = multiKeywordScanner.findNext(null, 0, 0);
        Assert.assertTrue(hits.isEmpty());
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

        Assert.assertEquals(3, hits.size());
        Assert.assertEquals(0, hits.get(0).getID());
        Assert.assertEquals(4, hits.get(0).getOffset());
        Assert.assertEquals(1, hits.get(1).getID());
        Assert.assertEquals(10, hits.get(1).getOffset());
        Assert.assertEquals(2, hits.get(2).getID());
        Assert.assertEquals(36, hits.get(2).getOffset());
    }
}
