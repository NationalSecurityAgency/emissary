package emissary.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import emissary.test.core.UnitTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BaseDataWithRemappingTest extends UnitTest {
    private BaseDataObject b = null;
    private static MetadataDictionary md = null;

    @BeforeAll
    public static void beforeClass() {
        // this runs before UnitTest has a chance to setup, so do that first
        new UnitTest().setupSystemProperties();

        md = new RemappingMetadataDictionary();
        Namespace.bind(md.getDictionaryName(), md);
    }

    @AfterAll
    public static void afterClass() {
        Namespace.unbind(md.getDictionaryName());
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        b = new BaseDataObject("This is a test".getBytes(), "filename.txt");
        b.pushCurrentForm("ONE");
        b.pushCurrentForm("TWO");
        b.pushCurrentForm("THREE");
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        b = null;
    }

    @Test
    void testAltViews() {
        b.addAlternateView("TESTVIEW1", "alternate view".getBytes());
        b.addAlternateView("TESTVIEW2", "alternate view".getBytes());
        b.addAlternateView("TESTVIEW3", "alternate view".getBytes());

        b.addAlternateView("TESTVIEW2", null);
        assertNull(b.getAlternateView("TESTVIEW2"), "Null view after removal");
        assertNull(b.getAlternateViewBuffer("TESTVIEW2"), "Empty byte buffer after removal");

        // Also null with remapped name
        assertNull(b.getAlternateView("testview2"), "Null view by mapped name after removal");
        assertNull(b.getAlternateViewBuffer("testview2"), "Empty byte buffer by mapped name after removal");
    }

    @Test
    void testSetOfAltViewNames() {
        b.addAlternateView("TESTVIEW1", "alternate view".getBytes());
        b.addAlternateView("TESTVIEW2", "alternate view".getBytes());
        b.addAlternateView("TESTVIEW3", "alternate view".getBytes());
        Set<String> vnames = b.getAlternateViewNames();
        assertEquals(3, vnames.size(), "Count of view names");
        assertTrue(vnames.contains("testview1"), "Altview names should have been remapped");
        assertTrue(vnames.contains("testview2"), "Altview names should have been remapped");
        assertTrue(vnames.contains("testview3"), "Altview names should have been remapped");
    }

    @Test
    void testMapOfAltViews() {
        b.addAlternateView("TESTVIEW1", "alternate view".getBytes());
        b.addAlternateView("TESTVIEW2", "alternate view".getBytes());
        b.addAlternateView("TESTVIEW3", "alternate view".getBytes());
        Map<String, byte[]> v = b.getAlternateViews();
        assertEquals(3, v.size(), "Count of views");
        Set<String> vnames = v.keySet();
        assertTrue(vnames.contains("testview1"), "Altview names should have been remapped");
        assertTrue(vnames.contains("testview2"), "Altview names should have been remapped");
        assertTrue(vnames.contains("testview3"), "Altview names should have been remapped");
    }

    @Test
    void testParameters() {
        b.putParameter("ME", "YOU");
        assertEquals("YOU", b.getStringParameter("ME"), "Gotten parameter");
        Map<String, Object> map = new HashMap<>();
        map.put("ONE", "uno");
        map.put("TWO", "dos");
        map.put("THREE", "tres");
        b.putParameters(map);
        assertEquals("uno", b.getStringParameter("ONE"), "Map put parameter gotten");
        assertEquals("dos", b.getStringParameter("TWO"), "Map put parameter gotten");
        assertEquals("tres", b.getStringParameter("THREE"), "Map put parameter gotten");
        assertEquals("YOU", b.getStringParameter("ME"), "Gotten parameter");

        // Deletes
        b.deleteParameter("THREE");
        assertNull(b.getParameter("THREE"), "Deleted param is gone");

        // Overwrite
        b.putParameter("ME", "THEM");
        assertEquals("THEM", b.getStringParameter("ME"), "Gotten parameter");

        // Clear
        b.clearParameters();
        assertNull(b.getParameter("THREE"), "Deleted param is gone");
        assertNull(b.getParameter("ME"), "Deleted param is gone");
        Map<?, ?> m = b.getParameters();
        assertNotNull(m, "Clear paramters leave empty map");
        assertEquals(0, m.size(), "Clear parameters leaves empty map");
    }

}
