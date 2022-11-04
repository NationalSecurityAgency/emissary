package emissary.parser;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import emissary.test.core.junit5.UnitTest;
import org.junit.jupiter.api.Test;

class DataByteArraySlicerTest extends UnitTest {
    @Test
    void testSpliceMultiplePositionRecords() {
        List<PositionRecord> pr = new ArrayList<>();
        pr.add(new PositionRecord(10, 5));
        pr.add(new PositionRecord(20, 5));
        pr.add(new PositionRecord(30, 5));
        pr.add(new PositionRecord(35, 0)); // ignored
        pr.add(new PositionRecord(-2, -1)); // ignored
        pr.add(new PositionRecord(-2, 100)); // ignored
        byte[] input = "000000000011111xxxxx22222yyyyy33333zzzzzqqqqqqqqqqqqqq".getBytes(UTF_8);
        byte[] result = DataByteArraySlicer.makeDataSlice(input, pr);
        assertEquals("111112222233333", new String(result, UTF_8), "Result of multiple position records splicing");
    }

    @Test
    void testSpliceSinglePositionRecord() {
        List<PositionRecord> pr = new ArrayList<>();
        pr.add(new PositionRecord(10, 5));
        byte[] input = "000000000011111xxxxx22222yyyyy33333zzzzzqqqqqqqqqqqqqq".getBytes(UTF_8);
        byte[] result = DataByteArraySlicer.makeDataSlice(input, pr);
        assertEquals("11111", new String(result, UTF_8), "Result of single position records splicing");
    }

    @Test
    void testSpliceEmptyPositionRecordList() {
        List<PositionRecord> pr = new ArrayList<>();
        byte[] input = "000000000011111xxxxx22222yyyyy33333zzzzzqqqqqqqqqqqqqq".getBytes(UTF_8);
        assertNull(DataByteArraySlicer.makeDataSlice(input, pr), "Null should be returned for empty position record list");
    }

    @Test
    void testSpliceNullPositionRecordList() {
        List<PositionRecord> pr = null;
        byte[] input = "000000000011111xxxxx22222yyyyy33333zzzzzqqqqqqqqqqqqqq".getBytes(UTF_8);
        assertNull(DataByteArraySlicer.makeDataSlice(input, pr), "Null should be returned for null position record list");
    }
}
