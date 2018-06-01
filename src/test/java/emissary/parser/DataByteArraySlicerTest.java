package emissary.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;

import emissary.test.core.UnitTest;
import org.junit.Test;

public class DataByteArraySlicerTest extends UnitTest {
    @Test
    public void testSpliceMultiplePositionRecords() {
        List<PositionRecord> pr = new ArrayList<PositionRecord>();
        pr.add(new PositionRecord(10, 5));
        pr.add(new PositionRecord(20, 5));
        pr.add(new PositionRecord(30, 5));
        pr.add(new PositionRecord(35, 0)); // ignored
        pr.add(new PositionRecord(-2, -1)); // ignored
        pr.add(new PositionRecord(-2, 100)); // ignored
        byte[] input = "000000000011111xxxxx22222yyyyy33333zzzzzqqqqqqqqqqqqqq".getBytes();
        byte[] result = DataByteArraySlicer.makeDataSlice(input, pr);
        assertEquals("Result of multiple position records splicing", "111112222233333", new String(result));
    }

    @Test
    public void testSpliceSinglePositionRecord() {
        List<PositionRecord> pr = new ArrayList<PositionRecord>();
        pr.add(new PositionRecord(10, 5));
        byte[] input = "000000000011111xxxxx22222yyyyy33333zzzzzqqqqqqqqqqqqqq".getBytes();
        byte[] result = DataByteArraySlicer.makeDataSlice(input, pr);
        assertEquals("Result of single position records splicing", "11111", new String(result));
    }

    @Test
    public void testSpliceEmptyPositionRecordList() {
        List<PositionRecord> pr = new ArrayList<PositionRecord>();
        byte[] input = "000000000011111xxxxx22222yyyyy33333zzzzzqqqqqqqqqqqqqq".getBytes();
        assertNull("Null should be returned for empty position record list", DataByteArraySlicer.makeDataSlice(input, pr));
    }

    @Test
    public void testSpliceNullPositionRecordList() {
        List<PositionRecord> pr = null;
        byte[] input = "000000000011111xxxxx22222yyyyy33333zzzzzqqqqqqqqqqqqqq".getBytes();
        assertNull("Null should be returned for null position record list", DataByteArraySlicer.makeDataSlice(input, pr));
    }
}
