package emissary.parser;

import emissary.config.ServiceConfigGuide;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.stream.Stream;

import static emissary.parser.DataIdentifier.UNKNOWN_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals(UNKNOWN_TYPE, id.identify(DATA), "Unknown default id");
        assertTrue(id.getTestStringMaxSize() > 0, "Test string size");

        assertEquals(UNKNOWN_TYPE, id.identify(DATA), "Unknown raf id");
        super.tearDown();
    }

    static Stream<Arguments> arguments() {
        return Stream.of(
                Arguments.of("aaa", UNKNOWN_TYPE, "Unknown id on data shorter than pattern"),
                Arguments.of("aaa===aaa\n\nbbb===bb", "FOO", "Valid id on data longer than pattern"),
                Arguments.of("ccc===ccc\n\nbbb===bbb", UNKNOWN_TYPE, "Valid id on data longer than pattern"),
                Arguments.of("", UNKNOWN_TYPE, "Unknown id on empty byte array"));
    }

    @ParameterizedTest
    @MethodSource("arguments")
    void testIdentify(String identify, String expected, String msg) {
        ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry("TYPE_FOO", "aaa===aaa");
        DataIdentifier id = new DataIdentifier(config);
        String result = id.identify(identify.getBytes());
        assertEquals(expected, result, msg);
    }

    @Test
    void testExtensibility() {
        MyDataIdentifier id = new MyDataIdentifier();
        assertEquals(id.getTestStringMaxSize(), id.checkSize(), "String size to test");

        // Check byte array
        assertEquals(id.checkSize(), id.checkString(DATA).length(), "Test string generation");
        assertEquals(5, id.checkString(DATA, 5).length(), "Test string generation with length");
        assertEquals(DATA.length, id.checkString(DATA, DATA.length + 100).length(), "Test string generation with oversize length");
    }


    private static final class MyDataIdentifier extends DataIdentifier {
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
