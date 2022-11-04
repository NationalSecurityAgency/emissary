package emissary.kff;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Set;

import emissary.test.core.junit5.UnitTest;
import org.junit.jupiter.api.Test;

class ChecksumResultsTest extends UnitTest {

    static final byte[] DATA = "This is a test".getBytes(UTF_8);

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
            fail("Unable to get SHA-1 or SHA-256 algorithm", ex);
        }
    }
}
