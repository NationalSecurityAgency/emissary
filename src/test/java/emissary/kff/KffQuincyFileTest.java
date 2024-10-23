package emissary.kff;

import emissary.test.core.junit5.UnitTest;
import emissary.util.io.ResourceReader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class KffQuincyFileTest extends UnitTest {

    private static final String resourcePath = new ResourceReader()
            .getResource("emissary/kff/test.dat").getPath();

    @SuppressWarnings("NonFinalStaticField")
    private static KffQuincyFile kffQuincyFile;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        kffQuincyFile = new KffQuincyFile(resourcePath, "testFilter", KffFilter.FilterType.UNKNOWN);
    }

    @Test
    void testCreation() {
        assertEquals("MD5", kffQuincyFile.getPreferredAlgorithm());
        assertEquals(KffFilter.FilterType.UNKNOWN, kffQuincyFile.ftype);
        assertEquals("testFilter", kffQuincyFile.filterName);
        assertEquals(16, kffQuincyFile.recordLength);
    }

    @Test
    void testKffQuincyFileMain() {
        String[] args = {resourcePath, resourcePath};
        assertDoesNotThrow(() -> KffQuincyFile.main(args));
    }
}
