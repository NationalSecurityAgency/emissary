package emissary.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import emissary.test.core.UnitTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class InputSessionTest extends UnitTest {

    @Test
    void testRecordCounting() throws ParserException {
        InputSession i = new InputSession(O, H, F, D);

        assertEquals(1, i.getDataCount(), "Data record count");
        assertEquals(1, i.getFooterCount(), "Footer record count");
        assertEquals(1, i.getHeaderCount(), "Header record count");

        assertEquals(1, i.getData().size(), "Data record count");
        assertEquals(1, i.getFooter().size(), "Footer record count");
        assertEquals(1, i.getHeader().size(), "Header record count");
    }


    @Test
    void testDataRanging() throws ParserException {
        InputSession i = new InputSession(O, H, F, D);
        assertEquals(0, i.getStart(), "Starting position");
        assertEquals(100, i.getLength(), "Length of data");
    }

    @Test
    void testBadRangeWithOverallFirst() {
        InputSession i = new InputSession(O);
        assertThrows(ParserException.class, () -> i.addHeaderRec(1, 100));
        assertThrows(ParserException.class, () -> i.addDataRec(1, 100));
        assertThrows(ParserException.class, () -> i.addFooterRec(1, 100));
        assertThrows(ParserException.class, () -> i.addHeaderRec(new PositionRecord(1, 100)));
        assertThrows(ParserException.class, () -> i.addDataRec(new PositionRecord(1, 100)));
        assertThrows(ParserException.class, () -> i.addFooterRec(new PositionRecord(1, 100)));
    }

    @Test
    void testBadRangeWithOverallLast() throws ParserException {
        InputSession i = new InputSession();
        i.addHeaderRec(new PositionRecord(1, 100));
        assertThrows(ParserException.class, () -> i.setOverall(O));
    }

    @Test
    void testBadMetadataRange() {
        InputSession i = new InputSession(O);
        assertThrows(ParserException.class, () -> i.addMetaDataRec("FOO", new PositionRecord(1, 100)));
    }

    private static final PositionRecord O = new PositionRecord(0, 100);
    private static final List<PositionRecord> H = new ArrayList<>();
    private static final List<PositionRecord> F = new ArrayList<>();
    private static final List<PositionRecord> D = new ArrayList<>();

    @BeforeAll
    public static void loadPositionRecords() {
        H.add(new PositionRecord(0, 10));
        D.add(new PositionRecord(10, 80));
        F.add(new PositionRecord(90, 10));
    }

}
