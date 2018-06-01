package emissary.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import emissary.test.core.UnitTest;
import org.junit.Test;

public class DataByteBufferSlicerTest extends UnitTest {
    @Test
    public void testSinglePositionRecord() {
        byte[] input = "000000000011111xxxxx22222yyyyy33333zzzzzqqqqqqqqqqqqqq".getBytes();
        ByteBuffer buffer = ByteBuffer.wrap(input);
        byte[] result = DataByteBufferSlicer.makeDataSlice(buffer, new PositionRecord(10, 5));
        assertEquals("Byte buffer slice with one position record", "11111", new String(result));
    }

    @Test
    public void testSingleMultipleRecord() {
        byte[] input = "000000000011111xxxxx22222yyyyy33333zzzzzqqqqqqqqqqqqqq".getBytes();
        List<PositionRecord> pr = new ArrayList<PositionRecord>();
        pr.add(new PositionRecord(10, 5));
        pr.add(new PositionRecord(20, 5));
        pr.add(new PositionRecord(30, 5));
        pr.add(new PositionRecord(35, 0)); // ignored
        pr.add(new PositionRecord(-2, -1)); // ignored
        pr.add(new PositionRecord(-2, 100)); // ignored
        ByteBuffer buffer = ByteBuffer.wrap(input);
        byte[] result = DataByteBufferSlicer.makeDataSlice(buffer, pr);
        assertEquals("Byte buffer slice with one position record", "111112222233333", new String(result));
    }

    @Test
    public void testSpliceEmptyPositionRecordList() {
        List<PositionRecord> pr = new ArrayList<PositionRecord>();
        byte[] input = "000000000011111xxxxx22222yyyyy33333zzzzzqqqqqqqqqqqqqq".getBytes();
        ByteBuffer buffer = ByteBuffer.wrap(input);
        assertNull("Null should be returned for empty position record list", DataByteBufferSlicer.makeDataSlice(buffer, pr));
    }

    @Test
    public void testSpliceNullPositionRecordList() {
        List<PositionRecord> pr = null;
        byte[] input = "000000000011111xxxxx22222yyyyy33333zzzzzqqqqqqqqqqqqqq".getBytes();
        ByteBuffer buffer = ByteBuffer.wrap(input);
        assertNull("Null should be returned for null position record list", DataByteBufferSlicer.makeDataSlice(buffer, pr));
    }

}
