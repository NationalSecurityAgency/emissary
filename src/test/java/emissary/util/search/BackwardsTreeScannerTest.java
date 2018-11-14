package emissary.util.search;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BackwardsTreeScannerTest {

    private String[] defaultKeywords = {"fox", "dog"};
    private String defaultData = "the quick brown fox jumped over the lazy dog";

    @Test
    public void testBothConstructors() throws Exception {
        HitList hits = new HitList();
        BackwardsTreeScanner first = new BackwardsTreeScanner();
        first.resetKeywords(defaultKeywords);
        int result = first.scan(defaultData.getBytes(), defaultData.length() - 1, hits);
        assertEquals(39, result);

        hits = new HitList();
        BackwardsTreeScanner second = new BackwardsTreeScanner(defaultKeywords);
        result = second.scan(defaultData.getBytes(), defaultData.length() - 1, hits);
        assertEquals(39, result);
    }

    @Test(expected = Exception.class)
    public void testExpectedException() throws Exception {
        HitList hits = new HitList();
        BackwardsTreeScanner second = new BackwardsTreeScanner(defaultKeywords);
        second.scan(null, defaultData.length() - 1, hits);
    }
}
