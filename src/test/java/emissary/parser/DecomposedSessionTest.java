package emissary.parser;

import emissary.test.core.junit5.UnitTest;

import com.google.common.collect.Multimap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecomposedSessionTest extends UnitTest {

    byte[] DATA = new byte[1000];

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        Arrays.fill(DATA, (byte) 'a');
    }

    @Test
    void testDataNoCopy() {
        DecomposedSession d = new DecomposedSession();
        d.setData(DATA);
        assertSame(DATA, d.getData(), "Data array must not be copied");
    }

    @Test
    void testSettingNullData() {
        DecomposedSession d = new DecomposedSession();
        d.setData(null);
        assertNull(d.getData(), "Null data array must not be copied");
        assertFalse(d.hasData(), "Null data must be reflected as not present");
    }

    @Test
    void testDataWithCopy() {
        DecomposedSession d = new DecomposedSession();
        d.setData(DATA, true);
        assertNotSame(DATA, d.getData(), "Data must be a copy");
    }

    @Test
    void testDataPortion() {
        DecomposedSession d = new DecomposedSession();
        d.setData(DATA, 10, 100);
        assertEquals(90, d.getData().length, "Data must be the specified portion");
    }

    @Test
    void testClassification() {
        DecomposedSession d = new DecomposedSession();
        d.setClassification("FOO");
        assertEquals("FOO", d.getClassification(), "Classification get/set must match");
    }

    @Test
    void testHeader() {
        DecomposedSession d = new DecomposedSession();
        d.setHeader(DATA);
        assertSame(DATA, d.getHeader(), "Header must not make a copy");
    }

    @Test
    void testFooter() {
        DecomposedSession d = new DecomposedSession();
        d.setFooter(DATA);
        assertSame(DATA, d.getFooter(), "Footer must not make a copy");
    }

    @Test
    void testHasHeader() {
        DecomposedSession d = new DecomposedSession();
        assertFalse(d.hasHeader(), "Must indicate absence of header");
        d.setHeader(DATA);
        assertTrue(d.hasHeader(), "Must indicate presence of header");
    }

    @Test
    void testHasFooter() {
        DecomposedSession d = new DecomposedSession();
        assertFalse(d.hasFooter(), "Must indicate absence of footer");
        d.setFooter(DATA);
        assertTrue(d.hasFooter(), "Must indicate presence of footer");
    }

    @Test
    void testHasClassification() {
        DecomposedSession d = new DecomposedSession();
        assertFalse(d.hasClassification(), "Must indicate absence of classification");
        d.setClassification("FOO");
        assertTrue(d.hasClassification(), "Must indicate presence of classification");
    }

    @Test
    void testHasData() {
        DecomposedSession d = new DecomposedSession();
        assertFalse(d.hasData(), "Must indicate absence of data");
        d.setData(DATA);
        assertTrue(d.hasData(), "Must indicate presence of data");
    }

    @Test
    void testHasMetaData() {
        DecomposedSession d = new DecomposedSession();
        assertFalse(d.hasMetaData(), "Must indicate absence of meta");
        d.addMetaData("foo", "bar");
        assertTrue(d.hasMetaData(), "Must indicate presence of meta");
    }

    @Test
    void testStringMetadata() {
        DecomposedSession d = new DecomposedSession();
        assertNull(d.getStringMetadataItem("foo"), "Must indicate absence of meta item");
        d.addMetaData("foo", "bar");
        assertEquals("bar", d.getStringMetadataItem("foo"), "Must indicate value of meta item");
        assertEquals("bar", d.getStringMetadataItem("foo", "-"), "Must indicate value of meta item and separator must not be used on one item");
        d.addMetaData("foo", "bar2");
        assertEquals("bar;bar2", d.getStringMetadataItem("foo"), "Must indicate value of meta item with default separator");
        assertEquals("bar-bar2", d.getStringMetadataItem("foo", "-"), "Must indicate value of meta item with custom separator");
    }

    @Test
    void testMetadataProcessing() {
        DecomposedSession d = new DecomposedSession();
        d.addMetaData("foo", null);
        assertFalse(d.hasMetaData(), "Null valued metadata must not be added");
        d.addMetaData(null, "bar");
        assertFalse(d.hasMetaData(), "Null keyed metadata must not be added");

        Map<String, List<Object>> m = new HashMap<>();
        m.put("foo", Arrays.asList(new Object[] {"bar1", "bar2"}));
        d.addMetaData(m);
        assertTrue(d.hasMetaData(), "Mapped metadata must be present");
        assertEquals(2, d.getMetaDataItem("foo").size(), "Mapped metadata values must be present");

        Multimap<String, Object> mm = d.getMultimap();
        mm.remove("foo", "bar2");
        assertEquals(1, d.getMetaDataItem("foo").size(), "Returned multimap must be live object");
    }

    @Test
    void testInitialForms() {
        DecomposedSession d = new DecomposedSession();
        assertTrue(d.getInitialForms() == null || d.getInitialForms().size() == 0, "No initial forms set");

        d.setInitialForms(null);
        assertEquals(0, d.getInitialForms().size(), "No initial forms are added from a null list");

        d.addInitialForm(null);
        d.addInitialForm("foo");
        assertNotNull(d.getInitialForms(), "Initial forms set");
        assertEquals(1, d.getInitialForms().size(), "Proper number of initial forms");
        assertEquals("foo", d.getInitialForms().get(0), "Proper initial form set");
        List<String> newForms = new ArrayList<>();
        newForms.add("bar");
        newForms.add("baz");
        d.setInitialForms(newForms);
        assertEquals(2, d.getInitialForms().size(), "Proper number of initial forms in list");
        assertEquals("bar", d.getInitialForms().get(0), "Propert initial form set");

        d.addInitialForm(null);
        assertEquals(2, d.getInitialForms().size(), "Null form does not get added to list");
    }

    @Test
    void testValidWithData() {
        DecomposedSession d = new DecomposedSession();
        assertFalse(d.isValid(), "Must indicate invalid");
        d.setData(DATA);
        assertTrue(d.isValid(), "Must indicate valid");
    }

    @Test
    void testValidWithHeader() {
        DecomposedSession d = new DecomposedSession();
        d.setHeader(DATA);
        assertTrue(d.isValid(), "Must indicate valid");
    }

    @Test
    void testValidWithFooter() {
        DecomposedSession d = new DecomposedSession();
        d.setFooter(DATA);
        assertTrue(d.isValid(), "Must indicate valid");
    }

}
