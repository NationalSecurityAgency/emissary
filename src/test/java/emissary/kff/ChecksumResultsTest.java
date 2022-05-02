package emissary.kff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Set;

import emissary.test.core.UnitTest;
import org.junit.jupiter.api.Test;

class ChecksumResultsTest extends UnitTest {

    static final byte[] DATA = "This is a test".getBytes();

    @Test
    void testResultPresentSet() {
        try {
            ChecksumCalculator cc = new ChecksumCalculator(new String[] {"SHA-1", "SHA-256"});
            ChecksumResults cr = cc.digest(DATA);
            assertNotNull(cr, "Results obect returned");
            Set<String> algs = cr.getResultsPresent();
            assertNotNull(algs, "Algorithm set returned");
            assertEquals(2, algs.size(), "All results present");
            Iterator<String> i = algs.iterator();
            assertEquals("SHA-1", i.next(), "SHA-1 alg present");
            assertEquals("SHA-256", i.next(), "SHA-256 alg present");
        } catch (NoSuchAlgorithmException ex) {
            throw new AssertionError("Unable to get SHA-1 or SHA-256 algorithm", ex);
        }
    }
}
