package emissary.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import emissary.test.core.UnitTest;
import org.junit.BeforeClass;
import org.junit.Test;

public class InputSessionTest extends UnitTest {

    @Test
    public void testRecordCounting() throws ParserException {
        InputSession i = new InputSession(O, H, F, D);

        assertEquals("Data record count", 1, i.getDataCount());
        assertEquals("Footer record count", 1, i.getFooterCount());
        assertEquals("Header record count", 1, i.getHeaderCount());

        assertEquals("Data record count", 1, i.getData().size());
        assertEquals("Footer record count", 1, i.getFooter().size());
        assertEquals("Header record count", 1, i.getHeader().size());
    }


    @Test
    public void testDataRanging() throws ParserException {
        InputSession i = new InputSession(O, H, F, D);
        assertEquals("Starting position", 0, i.getStart());
        assertEquals("Length of data", 100, i.getLength());
    }

    @Test
    public void testBadRangeWithOverallFirst() {
        InputSession i = new InputSession(O);
        try {
            i.addHeaderRec(1, 100);
            fail("Allowed header to be set out of bounds");
        } catch (ParserException ex) {
        }
        try {
            i.addDataRec(1, 100);
            fail("Allowed header to be set out of bounds");
        } catch (ParserException ex) {
        }
        try {
            i.addFooterRec(1, 100);
            fail("Allowed header to be set out of bounds");
        } catch (ParserException ex) {
        }
        try {
            i.addHeaderRec(new PositionRecord(1, 100));
            fail("Allowed header to be set out of bounds");
        } catch (ParserException ex) {
        }
        try {
            i.addDataRec(new PositionRecord(1, 100));
            fail("Allowed header to be set out of bounds");
        } catch (ParserException ex) {
        }
        try {
            i.addFooterRec(new PositionRecord(1, 100));
            fail("Allowed header to be set out of bounds");
        } catch (ParserException ex) {
        }
    }

    @Test
    public void testBadRangeWithOverallLast() throws ParserException {
        InputSession i = new InputSession();
        i.addHeaderRec(new PositionRecord(1, 100));
        try {
            i.setOverall(O);
            fail("Allowed data to be set out of bounds");
        } catch (ParserException ex) {
        }
    }

    @Test
    public void testBadMetadataRange() {
        InputSession i = new InputSession(O);
        try {
            i.addMetaDataRec("FOO", new PositionRecord(1, 100));
            fail("Allowed metadata element to be set out of bounds");
        } catch (ParserException ex) {
        }
    }

    private static PositionRecord O = new PositionRecord(0, 100);
    private static List<PositionRecord> H = new ArrayList<PositionRecord>();
    private static List<PositionRecord> F = new ArrayList<PositionRecord>();
    private static List<PositionRecord> D = new ArrayList<PositionRecord>();

    @BeforeClass
    public static void loadPositionRecords() {
        H.add(new PositionRecord(0, 10));
        D.add(new PositionRecord(10, 80));
        F.add(new PositionRecord(90, 10));
    }

}
