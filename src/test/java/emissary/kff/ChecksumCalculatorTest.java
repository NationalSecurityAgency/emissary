package emissary.kff;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Set;

import emissary.test.core.UnitTest;
import org.junit.Test;

public class ChecksumCalculatorTest extends UnitTest {

    static final byte[] DATA = "This is a test".getBytes();

    // echo -n "This is a test" | openssl sha1
    static final String DATA_SHA1 = "a54d88e06612d820bc3be72877c74f257b561b19";

    // echo -n "This is a test" | openssl sha256
    static final String DATA_SHA256 = "c7be1ed902fb8dd4d48997c6452f5d7e509fbcdbe2808b16bcf4edce4c07d14e";

    // generated from a file containing "This is a test" with no NL at the end
    static final String DATA_SSDEEP = "3:hMCEpn:hup";

    @Test
    public void testNoArgCtor() {
        try {
            ChecksumCalculator cc = new ChecksumCalculator();
            ChecksumResults cr = cc.digest(DATA);
            assertNotNull("Results created", cr);
            Set<String> algs = cr.getResultsPresent();
            assertNotNull("Algorithms present", algs);
            assertEquals("Two alg used for default ctor", 2, algs.size());
            assertTrue("SHA-1 alg used for default ctor", algs.contains("SHA-1"));
            assertTrue("CRC-32 alg used for default ctor", algs.contains("CRC32"));
            assertEquals("SHA-1 computation", DATA_SHA1, cr.getHashString("SHA-1"));
        } catch (NoSuchAlgorithmException ex) {
            fail("Unable to get SHA-1 algorithm");
        }
    }

    @Test
    public void testSpecifiedArgWithCrc() {
        try {
            ChecksumCalculator cc = new ChecksumCalculator("SHA-1", true);
            ChecksumResults cr = cc.digest(DATA);
            assertNotNull("Results created", cr);
            Set<String> algs = cr.getResultsPresent();
            assertNotNull("Algorithms present", algs);
            assertEquals("Two alg used for (string,boolean) ctor", 2, algs.size());
            assertTrue("SHA-1 alg used for (string,boolean) ctor", algs.contains("SHA-1"));
            assertEquals("SHA-1 computation", DATA_SHA1, cr.getHashString("SHA-1"));
            assertTrue("Using CRC", cc.getUseCRC());
            assertTrue("Using CRC and in alg set", algs.contains("CRC32"));
            assertTrue("CRC computed", cr.getCrc() != -1L);
        } catch (NoSuchAlgorithmException ex) {
            fail("Unable to get SHA-1 algorithm");
        }
    }

    @Test
    public void testSpecifiedArgWitouthCrc() {
        try {
            ChecksumCalculator cc = new ChecksumCalculator("SHA-1", false);
            ChecksumResults cr = cc.digest(DATA);
            assertNotNull("Results created", cr);
            Set<String> algs = cr.getResultsPresent();
            assertNotNull("Algorithms present", algs);
            assertEquals("One alg used for (string,boolean) ctor", 1, algs.size());
            assertTrue("SHA-1 alg used for (string,boolean) ctor", algs.contains("SHA-1"));
            assertEquals("SHA-1 computation", DATA_SHA1, cr.getHashString("SHA-1"));
            assertFalse("Not using CRC", cc.getUseCRC());
            assertTrue("CRC not computed", cr.getCrc() == -1L);
        } catch (NoSuchAlgorithmException ex) {
            fail("Unable to get SHA-1 algorithm");
        }
    }

    @Test
    public void testSpecifiedArgWithCrcAsList() {
        try {
            ChecksumCalculator cc = new ChecksumCalculator(new String[] {"SHA-1", "CRC32"});
            ChecksumResults cr = cc.digest(DATA);
            assertNotNull("Results created", cr);
            Set<String> algs = cr.getResultsPresent();
            assertNotNull("Algorithms present", algs);
            assertEquals("Two alg used for string[] ctor", 2, algs.size());
            assertTrue("SHA-1 alg used for string[] ctor", algs.contains("SHA-1"));
            assertTrue("CRC32 alg used for string[] ctor", algs.contains("CRC32"));
            assertEquals("SHA-1 computation", DATA_SHA1, cr.getHashString("SHA-1"));
            assertTrue("Using CRC", cc.getUseCRC());
            assertTrue("CRC computed", cr.getCrc() != -1L);
        } catch (NoSuchAlgorithmException ex) {
            fail("Unable to get SHA-1 algorithm");
        }
    }

    @Test
    public void testSpecifiedArgWithoutCrcAsList() {
        try {
            ChecksumCalculator cc = new ChecksumCalculator(new String[] {"SHA-1"});
            ChecksumResults cr = cc.digest(DATA);
            assertNotNull("Results created", cr);
            Set<String> algs = cr.getResultsPresent();
            assertNotNull("Algorithms present", algs);
            assertEquals("One alg used for string[] ctor", 1, algs.size());
            Iterator<String> i = algs.iterator();
            assertEquals("SHA-1 alg used for string[] ctor", "SHA-1", i.next());
            assertEquals("SHA-1 computation", DATA_SHA1, cr.getHashString("SHA-1"));
            assertFalse("Not using CRC", cc.getUseCRC());
            assertTrue("CRC not computed", cr.getCrc() == -1L);
        } catch (NoSuchAlgorithmException ex) {
            fail("Unable to get SHA-1 algorithm");
        }
    }


    @Test
    public void testMultipleShaVariantsSpecifiedAsList() {
        try {
            ChecksumCalculator cc = new ChecksumCalculator(new String[] {"SHA-1", "SHA-256", "SSDEEP"});
            ChecksumResults cr = cc.digest(DATA);
            assertNotNull("Results created", cr);
            Set<String> algs = cr.getResultsPresent();
            assertNotNull("Algorithms present", algs);
            assertEquals("Three algs used for string[] ctor", 3, algs.size());
            Iterator<String> i = algs.iterator();
            assertEquals("SHA-1 alg used for string[] ctor", "SHA-1", i.next());
            assertEquals("SHA-256 alg used for string[] ctor", "SHA-256", i.next());
            assertEquals("SSDEEP alg used for string[] ctor", "SSDEEP", i.next());
            assertEquals("SHA-1 computation", DATA_SHA1, cr.getHashString("SHA-1"));
            assertEquals("SHA-256 computation", DATA_SHA256, cr.getHashString("SHA-256"));
            assertEquals("SSDEEP computation", DATA_SSDEEP, cr.getHashString("SSDEEP"));
            assertFalse("Not using CRC", cc.getUseCRC());
            assertTrue("CRC not computed", cr.getCrc() == -1L);
        } catch (NoSuchAlgorithmException ex) {
            fail("Unable to get SHA-1 or SHA-256 algorithm");
        }
    }
}
