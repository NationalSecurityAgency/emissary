package emissary.kff;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Set;

import emissary.test.core.UnitTest;
import org.junit.Test;

public class ChecksumResultsTest extends UnitTest {

    static final byte[] DATA = "This is a test".getBytes();

    // echo -n "This is a test" | openssl sha1
    static final String DATA_SHA1 = "a54d88e06612d820bc3be72877c74f257b561b19";


    @Test
    public void testResultPresentSet() {
        try {
            ChecksumCalculator cc = new ChecksumCalculator(new String[] {"SHA-1", "SHA-256"});
            ChecksumResults cr = cc.digest(DATA);
            assertNotNull("Results obect returned", cr);
            Set<String> algs = cr.getResultsPresent();
            assertNotNull("Algorithm set returned", algs);
            assertEquals("All results present", 2, algs.size());
            Iterator<String> i = algs.iterator();
            assertEquals("SHA-1 alg present", "SHA-1", i.next());
            assertEquals("SHA-256 alg present", "SHA-256", i.next());
        } catch (NoSuchAlgorithmException ex) {
            fail("Unable to get SHA-1 or SHA-256 algorithm");
        }
    }
}
