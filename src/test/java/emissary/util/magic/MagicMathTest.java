package emissary.util.magic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MagicMathTest {

    @Test
    void testStringToInt() {
        int x = MagicMath.stringToInt("13");
        assertEquals(13, x);

        // existing behavior
        assertThrows(NullPointerException.class, () -> MagicMath.stringToInt(null));

        String entry = "blah";
        Exception exception = assertThrows(NumberFormatException.class, () -> MagicMath.stringToInt(entry));
        assertEquals("java.lang.NumberFormatException: For input string: \"blah\"", exception.toString());
    }
}
