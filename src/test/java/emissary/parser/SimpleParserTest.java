package emissary.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import emissary.test.core.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
// duh, this is testing deprecated class
class SimpleParserTest extends UnitTest {

    @Test
    void testInterface() {
        // Compile should test this but in case anyone changes it
        // They will have to look here :-)
        SimpleParser sp = new SimpleParser(DATA);
        assertTrue(sp instanceof SessionParser, "SimpleParser interface definition");
    }


    @Test
    void testDataSlicing() throws ParserException {
        SimpleParser sp = new SimpleParser(DATA);
        DecomposedSession sd = sp.getNextSession();
        assertNotNull(sd, "Session object created");
        assertTrue(sd.isValid(), "Session decomposed");
        assertEquals(DATA.length, sd.getData().length, "Data size");
    }

    @Test
    void testNonExistingSession() throws ParserException {
        SimpleParser sp = new SimpleParser(DATA);
        DecomposedSession sd = sp.getNextSession();
        assertTrue(sd.isValid(), "Session decomposed");
        assertThrows(ParserEOFException.class, sp::getNextSession);
    }

    @Test
    void testDecompWithHeaderAndFooter() throws ParserException {

        SimpleParser p = new SimpleParser(DATA) {

            @Override
            public DecomposedSession getNextSession() throws ParserException {
                if (isFullyParsed()) {
                    throw new ParserEOFException("Sessions completed");
                }

                List<PositionRecord> emptyList = Collections.emptyList();
                InputSession i = new InputSession(O, H, F, D);

                i.addHeaderRecs(null);
                i.addFooterRecs(null);
                i.addDataRecs(null);

                i.addHeaderRecs(emptyList);
                i.addFooterRecs(emptyList);
                i.addDataRecs(emptyList);

                Map<String, Object> map = new HashMap<>();
                map.put("KEY1", "VAL1");
                map.put("KEY2", new PositionRecord(2, 5));
                map.put("KEY3", new Object());
                i.addMetaDataRec("FOO", "BAR");
                i.addMetaDataRec("BAZ", (String) null);
                i.addMetaData(map);

                // i.validateAll(); is protected so...
                i.setOverall(O);
                i.setValid(true);
                setFullyParsed(true);

                String s = i.toString();
                assertNotNull(s, "InputSession to string");

                return decomposeSession(i);
            }
        };

        DecomposedSession d = p.getNextSession();
        assertNotNull(d, "Decomposition");
        assertTrue(d.isValid(), "Session decomposed");
        assertTrue(d.hasHeader(), "Header decomposition");
        assertTrue(d.hasFooter(), "Footer decomposition");
        assertTrue(d.hasData(), "Data decomposition");
        assertTrue(d.hasMetaData(), "Metadata decompsition");
        byte[] header = d.getHeader();
        assertNotNull(header, "Header decomposition");
        assertEquals(10, header.length, "Header decomp size");
        byte[] footer = d.getFooter();
        assertNotNull(footer, "Footer decomposition");
        assertEquals(10, footer.length, "Footer decomp size");
        byte[] data = d.getData();
        assertNotNull(data, "Data decomposition");
        Map<String, Collection<Object>> meta = d.getMetaData();
        assertNotNull(meta, "MetaData map extraction");
        assertTrue(meta.size() > 0, "MetaData map population " + meta.keySet());
        assertEquals("BAR", d.getStringMetadataItem("FOO"), "MetaData map value");
        assertTrue(meta.containsKey(SessionParser.ORIG_DOC_SIZE_KEY), "MetaData should always have doc size");
        assertFalse(meta.containsKey("BAZ"), "Null metadata values not carried");
        assertTrue(meta.containsKey("KEY1"), "Map string entry");
        assertTrue(meta.containsKey("KEY2"), "Map posrec entry");
        assertFalse(meta.containsKey("KEY3"), "Map object entry");
        assertEquals(80, data.length, "Data decomp size");
        assertFalse(d.hasClassification(), "SimleParser should not set classification");


        int oldMetaSize = meta.size();
        Map<String, Object> newMeta = new HashMap<>();
        newMeta.put("NEWKEY1", "NEWVALUE1");
        newMeta.put("NEWKEY2", "NEWVALUE2");
        d.addMetaData(newMeta);
        meta = d.getMetaData();
        assertEquals(newMeta.size() + oldMetaSize, meta.size(), "Consolidated meta");
    }


    private final PositionRecord O = new PositionRecord(0, 100);
    private final List<PositionRecord> H = new ArrayList<>();
    private final List<PositionRecord> F = new ArrayList<>();
    private final List<PositionRecord> D = new ArrayList<>();
    private final byte[] DATA = new byte[100];

    @BeforeEach
    public void initData() {
        H.add(new PositionRecord(0, 10));
        D.add(new PositionRecord(10, 80));
        F.add(new PositionRecord(new long[] {90, 10}));
        Arrays.fill(DATA, (byte) 'a');
    }

}
