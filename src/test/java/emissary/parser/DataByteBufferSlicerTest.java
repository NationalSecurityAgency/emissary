package emissary.parser;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DataByteBufferSlicerTest extends UnitTest {
    @Test
    void testSinglePositionRecord() {
        byte[] input = "000000000011111xxxxx22222yyyyy33333zzzzzqqqqqqqqqqqqqq".getBytes();
        ByteBuffer buffer = ByteBuffer.wrap(input);
        byte[] result = DataByteBufferSlicer.makeDataSlice(buffer, new PositionRecord(10, 5));
        assertEquals("11111", new String(result), "Byte buffer slice with one position record");
    }

    @Test
    void testSingleMultipleRecord() {
        byte[] input = "000000000011111xxxxx22222yyyyy33333zzzzzqqqqqqqqqqqqqq".getBytes();
        List<PositionRecord> pr = new ArrayList<>();
        pr.add(new PositionRecord(10, 5));
        pr.add(new PositionRecord(20, 5));
        pr.add(new PositionRecord(30, 5));
        pr.add(new PositionRecord(35, 0)); // ignored
        pr.add(new PositionRecord(-2, -1)); // ignored
        pr.add(new PositionRecord(-2, 100)); // ignored
        ByteBuffer buffer = ByteBuffer.wrap(input);
        byte[] result = DataByteBufferSlicer.makeDataSlice(buffer, pr);
        assertEquals("111112222233333", new String(result), "Byte buffer slice with one position record");
    }

    @Test
    void testSpliceEmptyPositionRecordList() {
        List<PositionRecord> pr = new ArrayList<>();
        byte[] input = "000000000011111xxxxx22222yyyyy33333zzzzzqqqqqqqqqqqqqq".getBytes();
        ByteBuffer buffer = ByteBuffer.wrap(input);
        assertNull(DataByteBufferSlicer.makeDataSlice(buffer, pr), "Null should be returned for empty position record list");
    }

    @Test
    void testSpliceNullPositionRecordList() {
        List<PositionRecord> pr = null;
        byte[] input = "000000000011111xxxxx22222yyyyy33333zzzzzqqqqqqqqqqqqqq".getBytes();
        ByteBuffer buffer = ByteBuffer.wrap(input);
        assertNull(DataByteBufferSlicer.makeDataSlice(buffer, pr), "Null should be returned for null position record list");
    }

}
