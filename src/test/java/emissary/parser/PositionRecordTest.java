package emissary.parser;

import static org.junit.Assert.assertEquals;

import emissary.test.core.UnitTest;
import org.junit.Test;

/**
 * PositionRecord is pretty stupid so this shouldn't take much effort to test...
 */
public class PositionRecordTest extends UnitTest {

    @Test
    public void testRec() {
        PositionRecord p1 = new PositionRecord(500, 5000);
        assertEquals("Storing position", 500, p1.getPosition());
        assertEquals("Storing length", 5000, p1.getLength());
    }

    @Test
    public void testNullArray() {
        PositionRecord p = new PositionRecord(null);
        assertEquals("Position not stored on null array", 0, p.getPosition());
        assertEquals("Length not stored on null array", 0, p.getLength());
    }

    @Test
    public void testTooLongArray() {
        long[] arry = {1, 2, 3};
        PositionRecord p = new PositionRecord(arry);
        assertEquals("Position not stored on too long array", 0, p.getPosition());
        assertEquals("Length not stored on too long array", 0, p.getLength());
    }

}
