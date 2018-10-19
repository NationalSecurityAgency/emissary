package emissary.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import emissary.test.core.UnitTest;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class BaseDataWithRemappingTest extends UnitTest {
    private BaseDataObject b = null;
    private static MetadataDictionary md = null;

    @BeforeClass
    public static void beforeClass() {
        // this runs before UnitTest has a chance to setup, so do that first
        new UnitTest().setupSystemProperties();

        md = new RemappingMetadataDictionary();
        Namespace.bind(md.getDictionaryName(), md);
    }

    @AfterClass
    public static void afterClass() {
        Namespace.unbind(md.getDictionaryName());
    }

    @Override
    @Before
    public void setUp() throws Exception {
        b = new BaseDataObject("This is a test".getBytes(), "filename.txt");
        b.pushCurrentForm("ONE");
        b.pushCurrentForm("TWO");
        b.pushCurrentForm("THREE");
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        b = null;
    }

    @Test
    public void testAltViews() {
        b.addAlternateView("TESTVIEW1", "alternate view".getBytes());
        b.addAlternateView("TESTVIEW2", "alternate view".getBytes());
        b.addAlternateView("TESTVIEW3", "alternate view".getBytes());

        b.addAlternateView("TESTVIEW2", null);
        assertNull("Null view after removal", b.getAlternateView("TESTVIEW2"));
        assertNull("Empty byte buffer after removal", b.getAlternateViewBuffer("TESTVIEW2"));

        // Also null with remapped name
        assertNull("Null view by mapped name after removal", b.getAlternateView("testview2"));
        assertNull("Empty byte buffer by mapped name after removal", b.getAlternateViewBuffer("testview2"));
    }

    @Test
    public void testSetOfAltViewNames() {
        b.addAlternateView("TESTVIEW1", "alternate view".getBytes());
        b.addAlternateView("TESTVIEW2", "alternate view".getBytes());
        b.addAlternateView("TESTVIEW3", "alternate view".getBytes());
        Set<String> vnames = b.getAlternateViewNames();
        assertEquals("Count of view names", 3, vnames.size());
        assertTrue("Altview names should have been remapped", vnames.contains("testview1"));
        assertTrue("Altview names should have been remapped", vnames.contains("testview2"));
        assertTrue("Altview names should have been remapped", vnames.contains("testview3"));
    }

    @Test
    public void testMapOfAltViews() {
        b.addAlternateView("TESTVIEW1", "alternate view".getBytes());
        b.addAlternateView("TESTVIEW2", "alternate view".getBytes());
        b.addAlternateView("TESTVIEW3", "alternate view".getBytes());
        Map<String, byte[]> v = b.getAlternateViews();
        assertEquals("Count of views", 3, v.size());
        Set<String> vnames = v.keySet();
        assertTrue("Altview names should have been remapped", vnames.contains("testview1"));
        assertTrue("Altview names should have been remapped", vnames.contains("testview2"));
        assertTrue("Altview names should have been remapped", vnames.contains("testview3"));
    }

    @Test
    public void testParameters() {
        b.putParameter("ME", "YOU");
        assertEquals("Gotten parameter", "YOU", b.getStringParameter("ME"));
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("ONE", "uno");
        map.put("TWO", "dos");
        map.put("THREE", "tres");
        b.putParameters(map);
        assertEquals("Map put parameter gotten", "uno", b.getStringParameter("ONE"));
        assertEquals("Map put parameter gotten", "dos", b.getStringParameter("TWO"));
        assertEquals("Map put parameter gotten", "tres", b.getStringParameter("THREE"));
        assertEquals("Gotten parameter", "YOU", b.getStringParameter("ME"));

        // Deletes
        b.deleteParameter("THREE");
        assertNull("Deleted param is gone", b.getParameter("THREE"));

        // Overwrite
        b.putParameter("ME", "THEM");
        assertEquals("Gotten parameter", "THEM", b.getStringParameter("ME"));

        // Clear
        b.clearParameters();
        assertNull("Deleted param is gone", b.getParameter("THREE"));
        assertNull("Deleted param is gone", b.getParameter("ME"));
        Map<?, ?> m = b.getParameters();
        assertNotNull("Clear paramters leave empty map", m);
        assertEquals("Clear parameters leaves empty map", 0, m.size());
    }

}
