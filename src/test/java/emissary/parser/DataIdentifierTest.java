package emissary.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import emissary.config.ServiceConfigGuide;
import emissary.test.core.UnitTest;
import org.junit.Before;
import org.junit.Test;

public class DataIdentifierTest extends UnitTest {

    byte[] DATA = new byte[1000];

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        for (int i = 0; i < DATA.length; i++) {
            DATA[i] = 'a';
        }
    }

    @Test
    public void testId() throws Exception {
        DataIdentifier id = new DataIdentifier();
        assertEquals("Unknown default id", DataIdentifier.UNKNOWN_TYPE, id.identify(DATA));
        assertTrue("Test string size", id.getTestStringMaxSize() > 0);

        assertEquals("Unknown raf id", DataIdentifier.UNKNOWN_TYPE, id.identify(DATA));
        super.tearDown();
    }

    @Test
    public void testDataShorterThanPattern() {
        ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry("TYPE_FOO", "aaa===aaa");
        DataIdentifier id = new DataIdentifier(config);
        String result = id.identify("aaa".getBytes());
        assertEquals("Unknown id on data shorter than pattern", DataIdentifier.UNKNOWN_TYPE, result);
    }

    @Test
    public void testDataLongerThanPatternThatMatches() {
        ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry("TYPE_FOO", "aaa===aaa");
        DataIdentifier id = new DataIdentifier(config);
        String result = id.identify("aaa===aaa\n\nbbb===bbb".getBytes());
        assertEquals("Valid id on data longer than pattern", "FOO", result);
    }

    @Test
    public void testDataLongerThanPatternThatDoesntMatch() {
        ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry("TYPE_FOO", "aaa===aaa");
        DataIdentifier id = new DataIdentifier(config);
        String result = id.identify("ccc===ccc\n\nbbb===bbb".getBytes());
        assertEquals("Valid id on data longer than pattern", DataIdentifier.UNKNOWN_TYPE, result);
    }

    @Test
    public void testIdentificationOfEmptyByteArray() {
        ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry("TYPE_FOO", "aaa===aaa");
        DataIdentifier id = new DataIdentifier(config);
        String result = id.identify("".getBytes());
        assertEquals("Unknown id on empty byte array", DataIdentifier.UNKNOWN_TYPE, result);
    }

    @Test
    public void testExtensibility() throws Exception {
        DataIdentifierT id = new DataIdentifierT();
        assertEquals("String size to test", id.getTestStringMaxSize(), id.checkSize());

        // Check byte array
        assertEquals("Test string generation", id.checkSize(), id.checkString(DATA).length());
        assertEquals("Test string generation with length", 5, id.checkString(DATA, 5).length());
        assertEquals("Test string generation with oversize length", DATA.length, id.checkString(DATA, DATA.length + 100).length());
    }


    private static final class DataIdentifierT extends DataIdentifier {
        public int checkSize() {
            return DATA_ID_STR_SZ;
        }

        public String checkString(byte[] data) {
            return super.getTestString(data);
        }

        public String checkString(byte[] data, int limit) {
            return super.getTestString(data, limit);
        }
    }


}
