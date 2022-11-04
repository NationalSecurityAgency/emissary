package emissary.kff;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import emissary.test.core.junit5.UnitTest;
import org.junit.jupiter.api.Test;

class KffChainTest extends UnitTest {

    static final byte[] DATA = "This is a test".getBytes(UTF_8);

    @Test
    void testAlgorithmsUsedWithAddAlgorithm() {
        KffChain chain = new KffChain();
        chain.addAlgorithm("MD5");
        chain.addAlgorithm("SHA-1");
        chain.addAlgorithm("SHA-256");

        assertEquals(3, chain.getAlgorithms().size(), "Algorithms stored in chain");

        try {
            ChecksumResults cr = chain.computeSums(DATA);
            Set<String> algs = cr.getResultsPresent();
            assertNotNull(algs, "Algorithm set returned");
            assertEquals(3, algs.size(), "All results present");
            Iterator<String> i = algs.iterator();
            assertEquals("MD5", i.next(), "MD5 alg present");
            assertEquals("SHA-1", i.next(), "SHA-1 alg present");
            assertEquals("SHA-256", i.next(), "SHA-256 alg present");
        } catch (Exception ex) {
            fail("Could not compute results", ex);
        }
    }

    @Test
    void testAlgorithmsUsedWithSetAlgorithm() {
        KffChain chain = new KffChain();
        List<String> myAlgs = new ArrayList<>();
        myAlgs.add("MD5");
        myAlgs.add("SHA-1");
        myAlgs.add("SHA-256");
        chain.setAlgorithms(myAlgs);
        assertEquals(3, chain.getAlgorithms().size(), "Algorithms stored in chain");
        try {
            ChecksumResults cr = chain.computeSums(DATA);
            Set<String> algs = cr.getResultsPresent();
            assertNotNull(algs, "Algorithm set returned");
            assertEquals(3, algs.size(), "All results present");
            Iterator<String> i = algs.iterator();
            assertEquals("MD5", i.next(), "MD5 alg present");
            assertEquals("SHA-1", i.next(), "SHA-1 alg present");
            assertEquals("SHA-256", i.next(), "SHA-256 alg present");
        } catch (Exception ex) {
            fail("Could not compute results", ex);
        }
    }

    @Test
    void testComputationsOnEmptyFilterChain() {
        KffChain chain = new KffChain();
        List<String> myAlgs = new ArrayList<>();
        myAlgs.add("MD5");
        myAlgs.add("SHA-1");
        myAlgs.add("SHA-256");
        chain.setAlgorithms(myAlgs);

        assertEquals(3, chain.getAlgorithms().size(), "Algorithms stored in chain");
        assertEquals(0, chain.size(), "Size of chain is zero");
        try {
            KffResult kr = chain.check("TEST ITEM", DATA);
            Set<String> algs = kr.getResultNames();
            assertNotNull(algs, "Algorithm set returned");
            assertEquals(3, algs.size(), "All results present");
            Iterator<String> i = algs.iterator();
            assertEquals("MD5", i.next(), "MD5 alg present");
            assertEquals("SHA-1", i.next(), "SHA-1 alg present");
            assertEquals("SHA-256", i.next(), "SHA-256 alg present");
            assertEquals("TEST ITEM", kr.getItemName(), "Item name copied");

            // Test values on convenience methods match
            assertEquals(kr.getResultString("SHA-1"), kr.getShaString(), "SHA-1 convenience method");

            byte[] md5c = kr.getMd5();
            byte[] md5r = kr.getResult("MD5");
            assertEquals(md5c.length, md5r.length, "MD5 Results match");
            for (int j = 0; j < md5c.length; j++) {
                assertEquals(md5r[j], md5c[j], "MD5 results match at pos " + j);
            }

            assertFalse(kr.isHit(), "Cannot have a hit on zero length chain");
        } catch (Exception ex) {
            fail("Could not compute results", ex);
        }
    }

}
