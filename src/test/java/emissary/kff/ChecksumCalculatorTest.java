package emissary.kff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Set;

import emissary.test.core.junit5.UnitTest;
import org.junit.jupiter.api.Test;

class ChecksumCalculatorTest extends UnitTest {

    static final byte[] DATA = "This is a test".getBytes();

    // echo -n "This is a test" | openssl sha1
    static final String DATA_SHA1 = "a54d88e06612d820bc3be72877c74f257b561b19";

    // echo -n "This is a test" | openssl sha256
    static final String DATA_SHA256 = "c7be1ed902fb8dd4d48997c6452f5d7e509fbcdbe2808b16bcf4edce4c07d14e";

    // generated from a file containing "This is a test" with no NL at the end
    static final String DATA_SSDEEP = "3:hMCEpn:hup";

    @Test
    void testNoArgCtor() {
        try {
            ChecksumCalculator cc = new ChecksumCalculator();
            ChecksumResults cr = cc.digest(DATA);
            assertNotNull(cr, "Results created");
            Set<String> algs = cr.getResultsPresent();
            assertNotNull(algs, "Algorithms present");
            assertEquals(2, algs.size(), "Two alg used for default ctor");
            assertTrue(algs.contains("SHA-1"), "SHA-1 alg used for default ctor");
            assertTrue(algs.contains("CRC32"), "CRC-32 alg used for default ctor");
            assertEquals(DATA_SHA1, cr.getHashString("SHA-1"), "SHA-1 computation");
        } catch (NoSuchAlgorithmException ex) {
            fail("Unable to get SHA-1 algorithm", ex);
        }
    }

    @Test
    void testSpecifiedArgWithCrc() {
        try {
            ChecksumCalculator cc = new ChecksumCalculator("SHA-1", true);
            ChecksumResults cr = cc.digest(DATA);
            assertNotNull(cr, "Results created");
            Set<String> algs = cr.getResultsPresent();
            assertNotNull(algs, "Algorithms present");
            assertEquals(2, algs.size(), "Two alg used for (string,boolean) ctor");
            assertTrue(algs.contains("SHA-1"), "SHA-1 alg used for (string,boolean) ctor");
            assertEquals(DATA_SHA1, cr.getHashString("SHA-1"), "SHA-1 computation");
            assertTrue(cc.getUseCRC(), "Using CRC");
            assertTrue(algs.contains("CRC32"), "Using CRC and in alg set");
            assertNotEquals(-1L, cr.getCrc(), "CRC computed");
        } catch (NoSuchAlgorithmException ex) {
            fail("Unable to get SHA-1 algorithm", ex);
        }
    }

    @Test
    void testSpecifiedArgWitouthCrc() {
        try {
            ChecksumCalculator cc = new ChecksumCalculator("SHA-1", false);
            ChecksumResults cr = cc.digest(DATA);
            assertNotNull(cr, "Results created");
            Set<String> algs = cr.getResultsPresent();
            assertNotNull(algs, "Algorithms present");
            assertEquals(1, algs.size(), "One alg used for (string,boolean) ctor");
            assertTrue(algs.contains("SHA-1"), "SHA-1 alg used for (string,boolean) ctor");
            assertEquals(DATA_SHA1, cr.getHashString("SHA-1"), "SHA-1 computation");
            assertFalse(cc.getUseCRC(), "Not using CRC");
            assertEquals(-1L, cr.getCrc(), "CRC not computed");
        } catch (NoSuchAlgorithmException ex) {
            fail("Unable to get SHA-1 algorithm", ex);
        }
    }

    @Test
    void testSpecifiedArgWithCrcAsList() {
        try {
            ChecksumCalculator cc = new ChecksumCalculator(new String[] {"SHA-1", "CRC32"});
            ChecksumResults cr = cc.digest(DATA);
            assertNotNull(cr, "Results created");
            Set<String> algs = cr.getResultsPresent();
            assertNotNull(algs, "Algorithms present");
            assertEquals(2, algs.size(), "Two alg used for string[] ctor");
            assertTrue(algs.contains("SHA-1"), "SHA-1 alg used for string[] ctor");
            assertTrue(algs.contains("CRC32"), "CRC32 alg used for string[] ctor");
            assertEquals(DATA_SHA1, cr.getHashString("SHA-1"), "SHA-1 computation");
            assertTrue(cc.getUseCRC(), "Using CRC");
            assertNotEquals(-1L, cr.getCrc(), "CRC computed");
        } catch (NoSuchAlgorithmException ex) {
            fail("Unable to get SHA-1 algorithm", ex);
        }
    }

    @Test
    void testSpecifiedArgWithoutCrcAsList() {
        try {
            ChecksumCalculator cc = new ChecksumCalculator(new String[] {"SHA-1"});
            ChecksumResults cr = cc.digest(DATA);
            assertNotNull(cr, "Results created");
            Set<String> algs = cr.getResultsPresent();
            assertNotNull(algs, "Algorithms present");
            assertEquals(1, algs.size(), "One alg used for string[] ctor");
            Iterator<String> i = algs.iterator();
            assertEquals("SHA-1", i.next(), "SHA-1 alg used for string[] ctor");
            assertEquals(DATA_SHA1, cr.getHashString("SHA-1"), "SHA-1 computation");
            assertFalse(cc.getUseCRC(), "Not using CRC");
            assertEquals(-1L, cr.getCrc(), "CRC not computed");
        } catch (NoSuchAlgorithmException ex) {
            fail("Unable to get SHA-1 algorithm", ex);
        }
    }


    @Test
    void testMultipleShaVariantsSpecifiedAsList() {
        try {
            ChecksumCalculator cc = new ChecksumCalculator(new String[] {"SHA-1", "SHA-256", "SSDEEP"});
            cc.setUseSsdeep(true);
            assertTrue(cc.getUseSsdeep(), "SSDEEP being used");
            ChecksumResults cr = cc.digest(DATA);
            assertNotNull(cr, "Results created");
            Set<String> algs = cr.getResultsPresent();
            assertNotNull(algs, "Algorithms present");
            assertEquals(3, algs.size(), "Three algs used for string[] ctor");
            Iterator<String> i = algs.iterator();
            assertEquals("SHA-1", i.next(), "SHA-1 alg used for string[] ctor");
            assertEquals("SHA-256", i.next(), "SHA-256 alg used for string[] ctor");
            assertEquals("SSDEEP", i.next(), "SSDEEP alg used for string[] ctor");
            assertEquals(DATA_SHA1, cr.getHashString("SHA-1"), "SHA-1 computation");
            assertEquals(DATA_SHA256, cr.getHashString("SHA-256"), "SHA-256 computation");
            assertEquals(DATA_SSDEEP, cr.getHashString("SSDEEP"), "SSDEEP computation");
            assertFalse(cc.getUseCRC(), "Not using CRC");
            assertEquals(-1L, cr.getCrc(), "CRC not computed");
        } catch (NoSuchAlgorithmException ex) {
            fail("Unable to get SHA-1 or SHA-256 algorithm", ex);
        }
    }
}
