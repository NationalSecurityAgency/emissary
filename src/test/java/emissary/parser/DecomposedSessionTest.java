package emissary.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Multimap;
import emissary.test.core.UnitTest;
import org.junit.Before;
import org.junit.Test;

public class DecomposedSessionTest extends UnitTest {

    byte[] DATA = new byte[1000];

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        for (int i = 0; i < DATA.length; i++) {
            DATA[i] = 'a';
        }
    }

    @Test
    public void testDataNoCopy() {
        DecomposedSession d = new DecomposedSession();
        d.setData(DATA);
        assertSame("Data array must not be copied", DATA, d.getData());
    }

    @Test
    public void testSettingNullData() {
        DecomposedSession d = new DecomposedSession();
        d.setData(null);
        assertNull("Null data array must not be copied", d.getData());
        assertFalse("Null data must be reflected as not present", d.hasData());
    }

    @Test
    public void testDataWithCopy() {
        DecomposedSession d = new DecomposedSession();
        d.setData(DATA, true);
        assertNotSame("Data must be a copy", DATA, d.getData());
    }

    @Test
    public void testDataPortion() {
        DecomposedSession d = new DecomposedSession();
        d.setData(DATA, 10, 100);
        assertEquals("Data must be the specified portion", 90, d.getData().length);
    }

    @Test
    public void testClassification() {
        DecomposedSession d = new DecomposedSession();
        d.setClassification("FOO");
        assertEquals("Classification get/set must match", "FOO", d.getClassification());
    }

    @Test
    public void testHeader() {
        DecomposedSession d = new DecomposedSession();
        d.setHeader(DATA);
        assertSame("Header must not make a copy", DATA, d.getHeader());
    }

    @Test
    public void testFooter() {
        DecomposedSession d = new DecomposedSession();
        d.setFooter(DATA);
        assertSame("Footer must not make a copy", DATA, d.getFooter());
    }

    @Test
    public void testHasHeader() {
        DecomposedSession d = new DecomposedSession();
        assertFalse("Must indicate absence of header", d.hasHeader());
        d.setHeader(DATA);
        assertTrue("Must indicate presence of header", d.hasHeader());
    }

    @Test
    public void testHasFooter() {
        DecomposedSession d = new DecomposedSession();
        assertFalse("Must indicate absence of footer", d.hasFooter());
        d.setFooter(DATA);
        assertTrue("Must indicate presence of footer", d.hasFooter());
    }

    @Test
    public void testHasClassification() {
        DecomposedSession d = new DecomposedSession();
        assertFalse("Must indicate absence of classification", d.hasClassification());
        d.setClassification("FOO");
        assertTrue("Must indicate presence of classification", d.hasClassification());
    }

    @Test
    public void testHasData() {
        DecomposedSession d = new DecomposedSession();
        assertFalse("Must indicate absence of data", d.hasData());
        d.setData(DATA);
        assertTrue("Must indicate presence of data", d.hasData());
    }

    @Test
    public void testHasMetaData() {
        DecomposedSession d = new DecomposedSession();
        assertFalse("Must indicate absence of meta", d.hasMetaData());
        d.addMetaData("foo", "bar");
        assertTrue("Must indicate presence of meta", d.hasMetaData());
    }

    @Test
    public void testStringMetadata() {
        DecomposedSession d = new DecomposedSession();
        assertNull("Must indicate absence of meta item", d.getStringMetadataItem("foo"));
        d.addMetaData("foo", "bar");
        assertEquals("Must indicate value of meta item", "bar", d.getStringMetadataItem("foo"));
        assertEquals("Must indicate value of meta item and separator must not be used on one item", "bar", d.getStringMetadataItem("foo", "-"));
        d.addMetaData("foo", "bar2");
        assertEquals("Must indicate value of meta item with default separator", "bar;bar2", d.getStringMetadataItem("foo"));
        assertEquals("Must indicate value of meta item with custom separator", "bar-bar2", d.getStringMetadataItem("foo", "-"));
    }

    @Test
    public void testMetadataProcessing() {
        DecomposedSession d = new DecomposedSession();
        d.addMetaData("foo", null);
        assertFalse("Null valued metadata must not be added", d.hasMetaData());
        d.addMetaData(null, "bar");
        assertFalse("Null keyed metadata must not be added", d.hasMetaData());

        Map<String, List<Object>> m = new HashMap<String, List<Object>>();
        m.put("foo", Arrays.asList(new Object[] {"bar1", "bar2"}));
        d.addMetaData(m);
        assertTrue("Mapped metadata must be present", d.hasMetaData());
        assertEquals("Mapped metadata values must be present", 2, d.getMetaDataItem("foo").size());

        Multimap<String, Object> mm = d.getMultimap();
        mm.remove("foo", "bar2");
        assertEquals("Returned multimap must be live object", 1, d.getMetaDataItem("foo").size());
    }

    @Test
    public void testInitialForms() {
        DecomposedSession d = new DecomposedSession();
        assertTrue("No initial forms set", d.getInitialForms() == null || d.getInitialForms().size() == 0);

        d.setInitialForms(null);
        assertEquals("No initial forms are added from a null list", 0, d.getInitialForms().size());

        d.addInitialForm(null);
        d.addInitialForm("foo");
        assertNotNull("Initial forms set", d.getInitialForms());
        assertEquals("Proper number of initial forms", 1, d.getInitialForms().size());
        assertEquals("Proper initial form set", "foo", d.getInitialForms().get(0));
        List<String> newForms = new ArrayList<String>();
        newForms.add("bar");
        newForms.add("baz");
        d.setInitialForms(newForms);
        assertEquals("Proper number of initial forms in list", 2, d.getInitialForms().size());
        assertEquals("Propert initial form set", "bar", d.getInitialForms().get(0));

        d.addInitialForm(null);
        assertEquals("Null form does not get added to list", 2, d.getInitialForms().size());
    }

    @Test
    public void testValidWithData() {
        DecomposedSession d = new DecomposedSession();
        assertFalse("Must indicate invalid", d.isValid());
        d.setData(DATA);
        assertTrue("Must indicate valid", d.isValid());
    }

    @Test
    public void testValidWithHeader() {
        DecomposedSession d = new DecomposedSession();
        d.setHeader(DATA);
        assertTrue("Must indicate valid", d.isValid());
    }

    @Test
    public void testValidWithFooter() {
        DecomposedSession d = new DecomposedSession();
        d.setFooter(DATA);
        assertTrue("Must indicate valid", d.isValid());
    }

}
