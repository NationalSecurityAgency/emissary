package emissary.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import emissary.test.core.UnitTest;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("deprecation")
// duh, this is testing deprecated class
public class SimpleParserTest extends UnitTest {

    @Test
    public void testInterface() {
        // Compile should test this but in case anyone changes it
        // They will have to look here :-)
        SimpleParser sp = new SimpleParser(DATA);
        assertTrue("SimpleParser interface definition", sp instanceof SessionParser);
    }


    @Test
    public void testDataSlicing() throws ParserException, ParserEOFException {
        SimpleParser sp = new SimpleParser(DATA);
        DecomposedSession sd = sp.getNextSession();
        assertNotNull("Session object created", sd);
        assertTrue("Session decomposed", sd.isValid());
        assertEquals("Data size", DATA.length, sd.getData().length);
    }

    @Test
    public void testNonExistingSession() throws ParserException, ParserEOFException {
        SimpleParser sp = new SimpleParser(DATA);
        DecomposedSession sd = sp.getNextSession();
        assertTrue("Session decomposed", sd.isValid());
        try {
            sp.getNextSession();
            fail("Produced extra session rather than throw ParserEOF");
        } catch (ParserEOFException ex) {
            // expected
        }
    }

    @Test
    public void testDecompWithHeaderAndFooter() throws ParserException, ParserEOFException {

        SimpleParser p = new SimpleParser(DATA) {

            @Override
            public DecomposedSession getNextSession() throws ParserException, ParserEOFException {
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

                Map<String, Object> map = new HashMap<String, Object>();
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
                assertNotNull("InputSession to string", s);

                return decomposeSession(i);
            }
        };

        DecomposedSession d = p.getNextSession();
        assertNotNull("Decomposition", d);
        assertTrue("Session decomposed", d.isValid());
        assertTrue("Header decomposition", d.hasHeader());
        assertTrue("Footer decomposition", d.hasFooter());
        assertTrue("Data decomposition", d.hasData());
        assertTrue("Metadata decompsition", d.hasMetaData());
        byte[] header = d.getHeader();
        assertNotNull("Header decomposition", header);
        assertEquals("Header decomp size", 10, header.length);
        byte[] footer = d.getFooter();
        assertNotNull("Footer decomposition", footer);
        assertEquals("Footer decomp size", 10, footer.length);
        byte[] data = d.getData();
        assertNotNull("Data decomposition", data);
        Map<String, Collection<Object>> meta = d.getMetaData();
        assertNotNull("MetaData map extraction", meta);
        assertTrue("MetaData map population " + meta.keySet(), meta.size() > 0);
        assertEquals("MetaData map value", "BAR", d.getStringMetadataItem("FOO"));
        assertTrue("MetaData should always have doc size", meta.containsKey(SessionParser.ORIG_DOC_SIZE_KEY));
        assertFalse("Null metadata values not carried", meta.containsKey("BAZ"));
        assertTrue("Map string entry", meta.containsKey("KEY1"));
        assertTrue("Map posrec entry", meta.containsKey("KEY2"));
        assertFalse("Map object entry", meta.containsKey("KEY3"));
        assertEquals("Data decomp size", 80, data.length);
        assertFalse("SimleParser should not set classification", d.hasClassification());


        int oldMetaSize = meta.size();
        Map<String, Object> newMeta = new HashMap<String, Object>();
        newMeta.put("NEWKEY1", "NEWVALUE1");
        newMeta.put("NEWKEY2", "NEWVALUE2");
        d.addMetaData(newMeta);
        meta = d.getMetaData();
        assertEquals("Consolidated meta", newMeta.size() + oldMetaSize, meta.size());
    }


    private PositionRecord O = new PositionRecord(0, 100);
    private List<PositionRecord> H = new ArrayList<PositionRecord>();
    private List<PositionRecord> F = new ArrayList<PositionRecord>();
    private List<PositionRecord> D = new ArrayList<PositionRecord>();
    private byte[] DATA = new byte[100];

    @Before
    public void initData() {
        H.add(new PositionRecord(0, 10));
        D.add(new PositionRecord(10, 80));
        F.add(new PositionRecord(new long[] {90, 10}));
        for (int i = 0; i < DATA.length; i++) {
            DATA[i] = 'a';
        }
    }

}
