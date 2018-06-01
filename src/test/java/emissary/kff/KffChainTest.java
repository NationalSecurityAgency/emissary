package emissary.kff;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import emissary.test.core.UnitTest;
import org.junit.Test;

public class KffChainTest extends UnitTest {

    static final byte[] DATA = "This is a test".getBytes();

    // echo -n "This is a test" | openssl sha1
    static final String DATA_SHA1 = "a54d88e06612d820bc3be72877c74f257b561b19";


    @Test
    public void testAlgorithmsUsedWithAddAlgorithm() {
        KffChain chain = new KffChain();
        chain.addAlgorithm("MD5");
        chain.addAlgorithm("SHA-1");
        chain.addAlgorithm("SHA-256");

        assertEquals("Algorithms stored in chain", 3, chain.getAlgorithms().size());

        try {
            ChecksumResults cr = chain.computeSums(DATA);
            Set<String> algs = cr.getResultsPresent();
            assertNotNull("Algorithm set returned", algs);
            assertEquals("All results present", 3, algs.size());
            Iterator<String> i = algs.iterator();
            assertEquals("MD5 alg present", "MD5", i.next());
            assertEquals("SHA-1 alg present", "SHA-1", i.next());
            assertEquals("SHA-256 alg present", "SHA-256", i.next());
        } catch (Exception ex) {
            fail("Could not compute results: " + ex.getMessage());
        }
    }

    @Test
    public void testAlgorithmsUsedWithSetAlgorithm() {
        KffChain chain = new KffChain();
        List<String> myAlgs = new ArrayList<String>();
        myAlgs.add("MD5");
        myAlgs.add("SHA-1");
        myAlgs.add("SHA-256");
        chain.setAlgorithms(myAlgs);
        assertEquals("Algorithms stored in chain", 3, chain.getAlgorithms().size());
        try {
            ChecksumResults cr = chain.computeSums(DATA);
            Set<String> algs = cr.getResultsPresent();
            assertNotNull("Algorithm set returned", algs);
            assertEquals("All results present", 3, algs.size());
            Iterator<String> i = algs.iterator();
            assertEquals("MD5 alg present", "MD5", i.next());
            assertEquals("SHA-1 alg present", "SHA-1", i.next());
            assertEquals("SHA-256 alg present", "SHA-256", i.next());
        } catch (Exception ex) {
            fail("Could not compute results: " + ex.getMessage());
        }
    }

    @Test
    public void testComputationsOnEmptyFilterChain() {
        KffChain chain = new KffChain();
        List<String> myAlgs = new ArrayList<String>();
        myAlgs.add("MD5");
        myAlgs.add("SHA-1");
        myAlgs.add("SHA-256");
        chain.setAlgorithms(myAlgs);

        assertEquals("Algorithms stored in chain", 3, chain.getAlgorithms().size());
        assertEquals("Size of chain is zero", 0, chain.size());
        try {
            KffResult kr = chain.check("TEST ITEM", DATA);
            Set<String> algs = kr.getResultNames();
            assertNotNull("Algorithm set returned", algs);
            assertEquals("All results present", 3, algs.size());
            Iterator<String> i = algs.iterator();
            assertEquals("MD5 alg present", "MD5", i.next());
            assertEquals("SHA-1 alg present", "SHA-1", i.next());
            assertEquals("SHA-256 alg present", "SHA-256", i.next());
            assertEquals("Item name copied", "TEST ITEM", kr.getItemName());

            // Test values on convenience methods match
            assertEquals("SHA-1 convenience method", kr.getResultString("SHA-1"), kr.getShaString());

            byte[] md5c = kr.getMd5();
            byte[] md5r = kr.getResult("MD5");
            assertEquals("MD5 Results match", md5c.length, md5r.length);
            for (int j = 0; j < md5c.length; j++) {
                assertEquals("MD5 results match at pos " + j, md5r[j], md5c[j]);
            }

            assertFalse("Canot have a hit on zero length chain", kr.isHit());
        } catch (Exception ex) {
            fail("Could not compute results: " + ex.getMessage());
        }
    }

}
