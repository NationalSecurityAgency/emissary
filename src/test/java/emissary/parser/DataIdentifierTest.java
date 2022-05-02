package emissary.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import emissary.config.ServiceConfigGuide;
import emissary.test.core.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataIdentifierTest extends UnitTest {

    byte[] DATA = new byte[1000];

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        Arrays.fill(DATA, (byte) 'a');
    }

    @Test
    void testId() throws Exception {
        DataIdentifier id = new DataIdentifier();
        assertEquals(DataIdentifier.UNKNOWN_TYPE, id.identify(DATA), "Unknown default id");
        assertTrue(id.getTestStringMaxSize() > 0, "Test string size");

        assertEquals(DataIdentifier.UNKNOWN_TYPE, id.identify(DATA), "Unknown raf id");
        super.tearDown();
    }

    @Test
    void testDataShorterThanPattern() {
        ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry("TYPE_FOO", "aaa===aaa");
        DataIdentifier id = new DataIdentifier(config);
        String result = id.identify("aaa".getBytes());
        assertEquals(DataIdentifier.UNKNOWN_TYPE, result, "Unknown id on data shorter than pattern");
    }

    @Test
    void testDataLongerThanPatternThatMatches() {
        ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry("TYPE_FOO", "aaa===aaa");
        DataIdentifier id = new DataIdentifier(config);
        String result = id.identify("aaa===aaa\n\nbbb===bbb".getBytes());
        assertEquals("FOO", result, "Valid id on data longer than pattern");
    }

    @Test
    void testDataLongerThanPatternThatDoesntMatch() {
        ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry("TYPE_FOO", "aaa===aaa");
        DataIdentifier id = new DataIdentifier(config);
        String result = id.identify("ccc===ccc\n\nbbb===bbb".getBytes());
        assertEquals(DataIdentifier.UNKNOWN_TYPE, result, "Valid id on data longer than pattern");
    }

    @Test
    void testIdentificationOfEmptyByteArray() {
        ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry("TYPE_FOO", "aaa===aaa");
        DataIdentifier id = new DataIdentifier(config);
        String result = id.identify("".getBytes());
        assertEquals(DataIdentifier.UNKNOWN_TYPE, result, "Unknown id on empty byte array");
    }

    @Test
    void testExtensibility() {
        DataIdentifierT id = new DataIdentifierT();
        assertEquals(id.getTestStringMaxSize(), id.checkSize(), "String size to test");

        // Check byte array
        assertEquals(id.checkSize(), id.checkString(DATA).length(), "Test string generation");
        assertEquals(5, id.checkString(DATA, 5).length(), "Test string generation with length");
        assertEquals(DATA.length, id.checkString(DATA, DATA.length + 100).length(), "Test string generation with oversize length");
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
