package emissary.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import emissary.test.core.UnitTest;
import org.junit.jupiter.api.Test;

/**
 * PositionRecord is pretty stupid so this shouldn't take much effort to test...
 */
class PositionRecordTest extends UnitTest {

    @Test
    void testRec() {
        PositionRecord p1 = new PositionRecord(500, 5000);
        assertEquals(500, p1.getPosition(), "Storing position");
        assertEquals(5000, p1.getLength(), "Storing length");
    }

    @Test
    void testNullArray() {
        PositionRecord p = new PositionRecord(null);
        assertEquals(0, p.getPosition(), "Position not stored on null array");
        assertEquals(0, p.getLength(), "Length not stored on null array");
    }

    @Test
    void testTooLongArray() {
        long[] arry = {1, 2, 3};
        PositionRecord p = new PositionRecord(arry);
        assertEquals(0, p.getPosition(), "Position not stored on too long array");
        assertEquals(0, p.getLength(), "Length not stored on too long array");
    }

}
