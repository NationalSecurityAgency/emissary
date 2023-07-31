package emissary.kff;

import emissary.test.core.junit5.UnitTest;
import emissary.util.io.ResourceReader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class KffFileTest extends UnitTest {

    private static final String ITEM_NAME = "Some_item_name"; // "000000206738748EDD92C4E3D2E823896700F849";
    private static final byte[] expectedSha1Bytes = {(byte) 0, (byte) 0, (byte) 0, (byte) 32, (byte) 103, (byte) 56, (byte) 116,
            (byte) -114, (byte) -35, (byte) -110, (byte) -60, (byte) -29, (byte) -46, (byte) -24, (byte) 35, (byte) -119,
            (byte) 103, (byte) 0, (byte) -8, (byte) 73};
    private static final byte[] expectedCrcBytes = {(byte) -21, (byte) -47, (byte) 5, (byte) -96};
    private static KffFile kffFile;
    private static final String resourcePath = new ResourceReader()
            .getResource("emissary/kff/KffFileTest/tmp.bin").getPath();

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        kffFile = new KffFile(resourcePath, "testFilter", KffFilter.FilterType.Unknown);
        kffFile.setPreferredAlgorithm("SHA-1");
    }

    @Test
    void testKffFileCreation() {
        assertEquals("testFilter", kffFile.getName());
        kffFile.setFilterType(KffFilter.FilterType.Ignore);
        assertEquals(KffFilter.FilterType.Ignore, kffFile.getFilterType());
        assertEquals("SHA-1", kffFile.getPreferredAlgorithm());
    }

    @Test
    void testKffFileCheck() {
        ChecksumResults results = new ChecksumResults();
        results.setHash("SHA-1", expectedSha1Bytes);
        results.setHash("CRC32", expectedCrcBytes);
        try {
            assertTrue(kffFile.check(ITEM_NAME, results));
        } catch (Exception e) {
            fail(e);
        }
        byte[] incorrectSha1Bytes = {(byte) 0, (byte) 0, (byte) 0, (byte) 32, (byte) 103, (byte) 56, (byte) 116,
                (byte) -114, (byte) -35, (byte) -110, (byte) -60, (byte) -29, (byte) -46, (byte) -24, (byte) 35, (byte) -119,
                (byte) 103, (byte) 0, (byte) -8, (byte) 70};
        results = new ChecksumResults();
        results.setHash("SHA-1", incorrectSha1Bytes);
        try {
            assertFalse(kffFile.check(ITEM_NAME, results));
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void testKffFileMain() {
        String[] args = {resourcePath, resourcePath};
        assertDoesNotThrow(() -> KffFile.main(args));
    }
}
