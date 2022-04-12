package emissary.kff;

import static org.junit.jupiter.api.Assertions.assertEquals;

import emissary.test.core.UnitTest;
import emissary.util.io.ResourceReader;
import org.junit.Before;
import org.junit.Test;

public class KffQuincyFileTest extends UnitTest {

    private static final String resourcePath = new ResourceReader()
            .getResource("emissary/kff/test.dat").getPath();
    private static KffQuincyFile kffQuincyFile;

    @Override
    @Before
    public void setUp() throws Exception {
        kffQuincyFile = new KffQuincyFile(resourcePath, "testFilter", KffFilter.FilterType.Unknown);
    }

    @Test
    public void testCreation() {
        assertEquals("MD5", kffQuincyFile.getPreferredAlgorithm());
        assertEquals(KffFilter.FilterType.Unknown, kffQuincyFile.ftype);
        assertEquals("testFilter", kffQuincyFile.filterName);
        assertEquals(16, kffQuincyFile.recordLength);
    }

    @Test
    public void testKffQuincyFileMain() {
        String[] args = {resourcePath, resourcePath};
        try {
            KffQuincyFile.main(args);
        } catch (Exception e) {
            throw new AssertionError();
        }
    }
}
