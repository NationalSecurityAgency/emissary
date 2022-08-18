package emissary.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.directory.DirectoryEntry;
import emissary.pickup.Priority;
import emissary.test.core.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BaseDataObjectTest extends UnitTest {

    private BaseDataObject b = null;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        this.b = new BaseDataObject("This is a test".getBytes(), "filename.txt");
        this.b.pushCurrentForm("ONE");
        this.b.pushCurrentForm("TWO");
        this.b.pushCurrentForm("THREE");
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        this.b = null;
    }

    @Test
    void testInterface() {
        // This should pass by compilation, but in case anyone
        // ever thinks of taking it out, this may remind them...
        assertTrue(this.b instanceof emissary.core.IBaseDataObject, "Implements the interface");
    }

    @Test
    void testConstructors() {
        final BaseDataObject b2 = new BaseDataObject("This is a test".getBytes(), "filename.txt", "ONE");
        assertEquals("ONE", b2.currentForm(), "Current form in ctor");
        assertNotNull(b2.getCreationTimestamp());

        final BaseDataObject b3 = new BaseDataObject("test".getBytes(), "filename.txt", null);
        assertEquals("", b3.currentForm(), "Current form with null in ctor");
        assertNotNull(b3.getCreationTimestamp());
    }

    @Test
    void testDataLength() {
        assertEquals("This is a test".length(), this.b.dataLength(), "Data length");
    }

    @Test
    void testNullDataLength() {
        this.b.setData(null);
        assertEquals(0, this.b.dataLength(), "Null data length");
    }

    @Test
    void testZeroLengthDataSlice() {
        final byte[] ary = new byte[0];
        this.b.setData(ary, 0, 0);
        assertEquals(0, this.b.dataLength(), "Null data length is zero");
    }

    @Test
    void testDataSliceLength() {
        final byte[] ary = "abcdefghijk".getBytes();
        this.b.setData(ary, 3, 4);
        assertEquals(4, this.b.dataLength(), "Array slice must use length");
    }

    @Test
    void testDataSliceData() {
        final byte[] ary = "abcdefghijk".getBytes();
        this.b.setData(ary, 3, 4);
        assertEquals("defg", new String(this.b.data()), "Array slice must use proper data");
    }

    @Test
    void testNullData() {
        this.b.setData(null);
        assertNotNull(this.b.data(), "Data array can never be null");
    }

    @Test
    void testNullDataSlice() {
        this.b.setData(null, 3, 4);
        assertNotNull(this.b.data(), "Data slice can never be null");
    }


    @Test
    void testShortName() {
        assertEquals("filename.txt", this.b.shortName(), "Short name");
    }

    @Test
    void testByteArrays() {
        this.b.setHeader("A fine header".getBytes());
        this.b.setFooter("A good footer".getBytes());
        this.b.addAlternateView("TESTVIEW", "alternate view".getBytes());
        assertEquals("This is a test", new String(this.b.data()), "Data bytes retrieved");
        assertEquals("A fine header", new String(this.b.header()), "Header bytes");
        assertEquals("A good footer", new String(this.b.footer()), "Footer bytes");
        assertEquals("alternate view", new String(this.b.getAlternateView("TESTVIEW")), "Alt view bytes");

        final ByteBuffer hb = this.b.headerBuffer();
        final ByteBuffer fb = this.b.footerBuffer();
        final ByteBuffer db = this.b.dataBuffer();
        final ByteBuffer vb = this.b.getAlternateViewBuffer("TESTVIEW");
        assertEquals("A fine header".length(), hb.array().length, "Byte buffer on header");
        assertEquals("A good footer".length(), fb.array().length, "Byte buffer on footer");
        assertEquals("This is a test".length(), db.array().length, "Byte buffer on data");
        assertNotNull(vb, "Byte buffer on view");
        assertEquals("alternate view".length(), vb.array().length, "Byte buffer on view");

        this.b.addAlternateView("TESTVIEW", null);
        assertNull(this.b.getAlternateView("TESTVIEW"), "Byte buffer on removed view");
    }

    @Test
    void testNonExistentAltViews() {
        assertNull(this.b.getAlternateView("NOSUCHVIEW"), "No such view");
    }

    @Test
    void testNonExistentAltViewBuffer() {
        assertNull(this.b.getAlternateViewBuffer("NOSUCHVIEW"), "Byte buffer on no such view");
    }

    @Test
    void testAltViews() {
        this.b.addAlternateView("TESTVIEW1", "alternate view".getBytes());
        this.b.addAlternateView("TESTVIEW2", "alternate view".getBytes());
        this.b.addAlternateView("TESTVIEW3", "alternate view".getBytes());

        this.b.addAlternateView("TESTVIEW2", null);
        assertNull(this.b.getAlternateView("TESTVIEW2"), "Null view after removal");
        assertNull(this.b.getAlternateViewBuffer("TESTVIEW2"), "Empty byte buffer after removal");
    }

    @Test
    void testAltViewSlice() {
        this.b.addAlternateView("TESTVIEW1", "abcdefghij".getBytes(), 3, 4);
        assertEquals("defg", new String(this.b.getAlternateView("TESTVIEW1")), "Alt view slice must use proper data");
    }

    @Test
    void testSetOfAltViewNames() {
        this.b.addAlternateView("TESTVIEW1", "alternate view".getBytes());
        this.b.addAlternateView("TESTVIEW2", "alternate view".getBytes());
        this.b.addAlternateView("TESTVIEW3", "alternate view".getBytes());
        final Set<String> vnames = this.b.getAlternateViewNames();
        assertEquals(3, vnames.size(), "Count of view names");

        List<String> source = new ArrayList<>(vnames);
        List<String> sorted = new ArrayList<>(vnames);
        Collections.sort(sorted);
        assertEquals(sorted, source, "Views are sorted");
    }

    @Test
    void testMapOfAltViews() {
        this.b.addAlternateView("TESTVIEW1", "alternate view".getBytes());
        this.b.addAlternateView("TESTVIEW2", "alternate view".getBytes());
        this.b.addAlternateView("TESTVIEW3", "alternate view".getBytes());
        final Map<String, byte[]> v = this.b.getAlternateViews();
        assertEquals(3, v.size(), "Count of views");

        List<String> source = new ArrayList<>(v.keySet());
        List<String> sorted = new ArrayList<>(v.keySet());
        Collections.sort(sorted);
        assertEquals(sorted, source, "Views are sorted");
    }

    @Test
    void testAppendAltView() {
        this.b.addAlternateView("T1", "alternate view".getBytes());
        this.b.appendAlternateView("T1", " more stuff".getBytes());
        assertEquals("alternate view more stuff", new String(this.b.getAlternateView("T1")), "Appended alternate view contents");
    }

    @Test
    void testAppendAltViewOnEmpty() {
        this.b.appendAlternateView("T1", "more stuff".getBytes());
        assertEquals("more stuff", new String(this.b.getAlternateView("T1")), "Appended alternate view contents");
    }

    @Test
    void testAppendAltViewSlice() {
        this.b.addAlternateView("T1", "alternate view".getBytes());
        this.b.appendAlternateView("T1", "xx more stuff xx".getBytes(), 2, 11);
        assertEquals("alternate view more stuff", new String(this.b.getAlternateView("T1")), "Appended alternate view contents");
    }

    @Test
    void testAppendAltViewSliceOnEmpty() {
        this.b.appendAlternateView("T1", "xx more stuff xx".getBytes(), 3, 10);
        assertEquals("more stuff", new String(this.b.getAlternateView("T1")), "Appended alternate view contents");
    }

    @Test
    void testWindowsShortName() {
        this.b.setFilename("c:\\Program Files\\Windows\\filename.txt");
        assertEquals("filename.txt", this.b.shortName(), "Short windows name");
    }

    @Test
    void testUnixShortName() {
        this.b.setFilename("/usr/local/share/filename.txt");
        assertEquals("filename.txt", this.b.shortName(), "Short windows name");
    }

    @Test
    void testFilename() {
        assertEquals("filename.txt", this.b.getFilename(), "Short name");
    }

    @Test
    void testCurrentFormTop() {
        assertEquals("THREE", this.b.currentForm(), "Current form push");
    }

    @Test
    void testCurrentFormEnqueue() {
        final int i = this.b.enqueueCurrentForm("FOUR");
        assertEquals(4, i, "Enqueue return value");
        assertEquals("THREE", this.b.currentForm(), "Current form push");
        assertEquals("ONE", this.b.currentFormAt(2), "Prev bottom form");
        assertEquals("FOUR", this.b.currentFormAt(3), "Bottom form");
    }

    @Test
    void testPopCurrentForm() {
        final String s = this.b.popCurrentForm();
        assertEquals("THREE", s, "Pop return value");
        assertEquals("TWO", this.b.currentForm(), "Current form after pop");
        this.b.popCurrentForm();
        assertEquals("ONE", this.b.currentForm(), "Current form after pop pop");
        this.b.popCurrentForm();
        assertEquals("", this.b.currentForm(), "No forms left");
    }

    @Test
    void testCurrentFormAt() {
        assertEquals("TWO", this.b.currentFormAt(1), "Current form at");
    }

    @Test
    void testSearchCurrentForm() {
        assertEquals(1, this.b.searchCurrentForm("TWO"), "Successful search");

        final List<String> l = new ArrayList<>();
        l.add("CHOP");
        l.add("TWO");
        l.add("CHIP");
        assertEquals("TWO", this.b.searchCurrentForm(l), "Successful list search");

    }

    @Test
    void testReplaceCurrentForm() {
        this.b.replaceCurrentForm("NEWONE");
        assertEquals(-1, this.b.searchCurrentForm("ONE"), "Not found search");
        assertEquals(-1, this.b.searchCurrentForm("TWO"), "Not found search");
        assertEquals(-1, this.b.searchCurrentForm("THREE"), "Not found search");
        assertEquals(0, this.b.searchCurrentForm("NEWONE"), "Successful search");
        assertEquals(1, this.b.currentFormSize(), "One form left");

        this.b.replaceCurrentForm(null);
        assertEquals(-1, this.b.searchCurrentForm("NEWONE"), "Not found search");
        assertEquals(0, this.b.currentFormSize(), "No forms left");
    }


    @Test
    void testBadSearchCurrentForm() {
        assertEquals(-1, this.b.searchCurrentForm("SEVENTEEN"), "Not found search");

        final List<String> l2 = new ArrayList<>();
        l2.add("SHIP");
        l2.add("SHAPE");
        assertNull(this.b.searchCurrentForm(l2), "Search but no match");
    }

    @Test
    void testFormSize() {
        assertEquals(3, this.b.currentFormSize(), "Form stack size");
    }

    @Test
    void testDeleteFormFromBottom() {
        this.b.deleteCurrentFormAt(0);
        assertEquals("TWO", this.b.currentForm(), "Form remaining on bottom");
        assertEquals(2, this.b.currentFormSize(), "Stack size after delete");
    }

    @Test
    void testDeleteFormFromTop() {
        this.b.deleteCurrentFormAt(2);
        assertEquals("THREE", this.b.currentForm(), "Form remaining on top");
        assertEquals(2, this.b.currentFormSize(), "Stack size after delete");
    }

    @Test
    void testDeleteFormFromMiddle() {
        final int i = this.b.deleteCurrentFormAt(1);
        assertEquals(2, i, "Delete return value");
        assertEquals("THREE", this.b.currentForm(), "Form remaining on top");
        assertEquals("ONE", this.b.currentFormAt(1), "Form remaining on bottom");
        assertEquals(2, this.b.currentFormSize(), "Stack size after delete");
    }

    @Test
    void testDeleteFormFromIllegalPosition() {
        this.b.deleteCurrentFormAt(7);
        assertEquals(3, this.b.currentFormSize(), "Stack size after delete");
        this.b.deleteCurrentFormAt(-1);
        assertEquals(3, this.b.currentFormSize(), "Stack size after delete");
    }

    @Test
    void testDeleteCurrentFormFromNull() {
        while (this.b.currentFormSize() > 0) {
            this.b.popCurrentForm();
        }
        assertEquals(0, this.b.deleteCurrentFormAt(0), "Delete from empty current forms failed");
    }

    @Test
    void testPrintMeta() {
        this.b.putParameter("FOO", "QUUZ");
        assertTrue(this.b.printMeta().contains("QUUZ"), "PrintMeta returnss valid params");
    }

    @Test
    void testSetParameters() {
        final Map<String, Object> m = new HashMap<>();
        m.put("A", "B");
        this.b.putParameter("FOO", "BAR");
        this.b.setParameters(m);
        assertNull(this.b.getParameter("FOO"), "Set parameters must clear old data");
    }

    @Test
    void testStringParameterOnNonStringValue() {
        this.b.putParameter("A", 1L);
        assertEquals("1", this.b.getStringParameter("A"), "Non-string parameters must call toString method");
    }

    @Test
    void testStringParameterOnNullValue() {
        this.b.putParameter("A", null);
        assertNull(this.b.getStringParameter("A"), "Null parameter must be returned as null");
    }

    @Test
    void testNumSiblings() {
        this.b.setNumSiblings(10);
        assertEquals(10, this.b.getNumSiblings(), "NumSiblings simple set/get failed");
    }

    @Test
    void testBirthOrder() {
        this.b.setBirthOrder(10);
        assertEquals(10, this.b.getBirthOrder(), "BirthOrder simple set/get failed");
    }

    @Test
    void testFontEncoding() {
        this.b.setFontEncoding("zhosa");
        assertEquals("zhosa", this.b.getFontEncoding(), "FontEncoding simple set/get failed");
    }

    @Test
    void testFileTypeIsEmpty() {
        this.b.setFileType(Form.UNKNOWN);
        assertTrue(this.b.isFileTypeEmpty(), "Unknown form must count as empty");
        this.b.setFileType("BAR");
        final String[] fakeEmpties = {Form.UNKNOWN, "FOO", "BAR"};
        assertTrue(this.b.setFileTypeIfEmpty("BAZ", fakeEmpties), "Unknown form must count as empty when passing in list");
        assertEquals("BAZ", this.b.getFileType(), "Failed to use supplied list of empty forms " + Arrays.asList(fakeEmpties));

        this.b.setFileType("TEST-UNWRAPPED");
        assertTrue(this.b.setFileTypeIfEmpty("ZAZ"), "Unknown form must count as empty when passing in list");
        assertEquals("ZAZ", this.b.getFileType());
    }

    @Test
    void testAlternateViewCount() {
        this.b.addAlternateView("FOO", "abcd".getBytes());
        assertEquals(1, this.b.getNumAlternateViews(), "Number of alternate views failed");
        this.b.addAlternateView("BAR", "abcd".getBytes());
        assertEquals(2, this.b.getNumAlternateViews(), "Number of alternate views failed to increment");
    }

    @Test
    void testSetBroken() {
        this.b.setBroken("This is broken");
        assertTrue(this.b.isBroken(), "Broken indicator failed");
        this.b.setBroken("This is still broken");
        assertTrue(this.b.isBroken(), "Broken indicator failed after append");
        assertTrue(this.b.getBroken().contains("still"), "Broken indicator string failed");
    }

    @Test
    void testDeleteFormByName() {
        this.b.deleteCurrentForm("TWO");
        assertEquals(2, this.b.currentFormSize(), "Remaining form count");
        this.b.deleteCurrentForm("ONE");
        assertEquals(1, this.b.currentFormSize(), "Remaining form count");
        this.b.deleteCurrentForm("THREE");
        assertEquals(0, this.b.currentFormSize(), "Remaining form count");

        this.b.pushCurrentForm("ONE");
        this.b.deleteCurrentForm("BOGUS");
        assertEquals(1, this.b.currentFormSize(), "Remaining form count");
        this.b.deleteCurrentForm(null);
        assertEquals(1, this.b.currentFormSize(), "Remaining form count");

        this.b.deleteCurrentForm("ONE");
        assertEquals(0, this.b.currentFormSize(), "Remaining form count");
        this.b.deleteCurrentForm("BOGUS");
        assertEquals(0, this.b.currentFormSize(), "Remaining form count");
        this.b.deleteCurrentForm(null);
        assertEquals(0, this.b.currentFormSize(), "Remaining form count");
    }

    @Test
    void testAddCurrentFormAtBottom() {
        this.b.addCurrentFormAt(0, "FOUR");
        assertEquals("FOUR", this.b.currentForm(), "Form on top");
        assertEquals("ONE", this.b.currentFormAt(3), "Form on bottom");
        assertEquals(4, this.b.currentFormSize(), "Stack size after add");
    }

    @Test
    void testAddCurrentFormInMiddle() {
        this.b.addCurrentFormAt(1, "FOUR");
        assertEquals("THREE", this.b.currentForm(), "Form remaining on top");
        assertEquals("ONE", this.b.currentFormAt(3), "Form on bottom");
        assertEquals(4, this.b.currentFormSize(), "Stack size after add");
    }

    @Test
    void testAddCurrentFormAtTop() {
        this.b.addCurrentFormAt(3, "FOUR");
        assertEquals("THREE", this.b.currentForm(), "Form on top");
        assertEquals("FOUR", this.b.currentFormAt(3), "Form on bottom");
        assertEquals(4, this.b.currentFormSize(), "Stack size after add");
    }

    @Test
    void testSetCurrentForm() {
        this.b.setCurrentForm("FOUR");
        assertEquals("FOUR", this.b.currentForm(), "Form on top");
        assertEquals("ONE", this.b.currentFormAt(2), "Form on bottom");
        assertEquals(3, this.b.currentFormSize(), "Stack size after set");
        assertTrue(this.b.toString().contains("FOUR"), "To string with current form");
    }

    @Test
    void testHistoryInToString() {
        this.b.setCurrentForm("UNKNOWN");
        this.b.appendTransformHistory("*.FOOPLACE.*.http://host:1234/fooPlace");
        this.b.appendTransformHistory("*.BARPLACE.*.http://host:1234/barPlace");
        final String s = this.b.toString();
        assertTrue(s.contains("FOOPLACE"), "history elements in toString");
        assertTrue(s.contains("BARPLACE"), "history elements in toString");
    }

    @Test
    void testSetCurrentFormEmpty() {
        this.b.popCurrentForm();
        this.b.popCurrentForm();
        this.b.popCurrentForm();
        this.b.setCurrentForm("FOUR");
        assertEquals("FOUR", this.b.currentForm(), "Form on top");
        assertEquals("FOUR", this.b.currentFormAt(0), "Form on bottom");
        assertEquals(1, this.b.currentFormSize(), "Stack size after set");
    }

    @Test
    void testCurrentFormNullHandling() {
        assertThrows(IllegalArgumentException.class, () -> this.b.pushCurrentForm(null));
        assertThrows(IllegalArgumentException.class, () -> this.b.enqueueCurrentForm(null));
        assertThrows(IllegalArgumentException.class, () -> this.b.setCurrentForm(null));
        assertThrows(IllegalArgumentException.class, () -> this.b.addCurrentFormAt(0, null));
    }

    @Test
    void testPullToTopFromMiddle() {
        this.b.pullFormToTop("TWO");
        assertEquals("TWO", this.b.currentForm(), "Form on top");
        assertEquals("ONE", this.b.currentFormAt(2), "Form on bottom");
        assertEquals(3, this.b.currentFormSize(), "Stack size after set");
    }

    @Test
    void testPullToTopFromBottom() {
        this.b.pullFormToTop("ONE");
        assertEquals("ONE", this.b.currentForm(), "Form on top");
        assertEquals("TWO", this.b.currentFormAt(2), "Form on bottom");
        assertEquals(3, this.b.currentFormSize(), "Stack size after set");
    }

    @Test
    void testPullToTopFromTop() {
        this.b.pullFormToTop("THREE");
        assertEquals("THREE", this.b.currentForm(), "Form on top");
        assertEquals("ONE", this.b.currentFormAt(2), "Form on bottom");
        assertEquals(3, this.b.currentFormSize(), "Stack size after set");
    }

    @Test
    void testPullNonExistentToTop() {
        this.b.pullFormToTop("SEVENTEEN");
        assertEquals("THREE", this.b.currentForm(), "Form on top");
        assertEquals("ONE", this.b.currentFormAt(2), "Form on bottom");
        assertEquals(3, this.b.currentFormSize(), "Stack size after set");
    }

    @Test
    void testGetAllForms() {
        final List<String> al = this.b.getAllCurrentForms();
        assertEquals(3, al.size(), "Forms returned");
        assertEquals("THREE", al.get(0), "Form order");
    }

    @Test
    void testProcessingError() {
        assertNull(this.b.getProcessingError(), "Empty processing error");
        this.b.addProcessingError("ONE");
        this.b.addProcessingError("TWO");
        assertEquals("ONE\nTWO\n", this.b.getProcessingError(), "Catted proc error");
    }

    @Test
    void testBeforeStart() {
        assertTrue(this.b.beforeStart(), "Before start on empty history");
        this.b.appendTransformHistory("*.FOOPLACE.*.http://host:1234/fooPlace");
        assertFalse(this.b.beforeStart(), "Before start with history");
        this.b.clearTransformHistory();
        assertEquals(0, this.b.transformHistory().size(), "Empty history");
        assertTrue(this.b.beforeStart(), "Re-emptied history before start");

        this.b.appendTransformHistory("*.FOOPLACE.*.http://host:1234/fooPlace");
        this.b.appendTransformHistory("*.<SPROUT>.*.http://host:1234/barPlace");
        assertTrue(this.b.beforeStart(), "Before start with sprout key on end");
        this.b.appendTransformHistory("UNKNOWN.FOOPLACE.ID.http://host:1234/bazPlace");
        assertFalse(this.b.beforeStart(), "Not before start with sprout key on list");
    }

    @Test
    void testAltViewRemapping() {
        try {
            final byte[] configData = ("RENAME_PROPERTIES = \"FLUBBER\"\n" + "RENAME_FOO =\"BAR\"\n").getBytes();

            final ByteArrayInputStream str = new ByteArrayInputStream(configData);
            final Configurator conf = ConfigUtil.getConfigInfo(str);
            MetadataDictionary.initialize(MetadataDictionary.DEFAULT_NAMESPACE_NAME, conf);
            this.b.addAlternateView("PROPERTIES", configData);
            this.b.addAlternateView("FOO", configData, 20, 10);
            assertNotNull(this.b.getAlternateView("PROPERTIES"), "Remapped alt view retrieved by original name");
            assertNotNull(this.b.getAlternateView("FLUBBER"), "Remapped alt view retrieved by new name");
            assertNotNull(this.b.getAlternateView("FOO"), "Remapped alt view slice retrieved by original name");
            assertNotNull(this.b.getAlternateView("BAR"), "Remapped alt view slice retrieved by new name");
            final Set<String> avnames = this.b.getAlternateViewNames();
            assertTrue(avnames.contains("FLUBBER"), "Alt view names contains remapped name");
            assertTrue(avnames.contains("BAR"), "Alt view slice names contains remapped name");

            // Delete by orig name
            this.b.addAlternateView("FOO", null, 20, 10);
            assertEquals(1, this.b.getAlternateViewNames().size(), "View removed by orig name");
            // Delete by mapped name
            this.b.addAlternateView("FLUBBER", null);
            assertEquals(0, this.b.getAlternateViewNames().size(), "View removed by orig name");
        } catch (Exception ex) {
            fail("Could not configure test: " + ex.getMessage());
        } finally {
            // Clean up
            Namespace.unbind(MetadataDictionary.DEFAULT_NAMESPACE_NAME);
        }
    }

    @Test
    void testParametersMapSignature() {
        final Map<String, Object> map = new HashMap<>();
        map.put("ONE", "uno");
        map.put("TWO", "dos");
        map.put("THREE", "tres");

        this.b.putParameters(map);

        final Map<String, String> smap = new HashMap<>();
        smap.put("FOUR", "cuatro");
        smap.put("FIVE", "cinco");
        smap.put("SIX", "seis");

        this.b.putParameters(smap);

        Map<String, Collection<Object>> result = this.b.getParameters();
        assertEquals(6, result.size(), "Added all types of parameters");

        // Put in some maps
        this.b.setParameter("SEVEN", map);
        this.b.putParameter("EIGHT", smap);

        // Put in a map of map
        final Map<String, Map<String, String>> combo = new HashMap<>();
        combo.put("NINE", smap);
        this.b.putParameters(combo);

        result = this.b.getParameters();
        assertEquals(9, result.size(), "Added all types of parameters");

    }

    @Test
    void testParametersMapInterfaceSignature() {

        final IBaseDataObject i = this.b;

        final Map<String, Object> map = new HashMap<>();
        map.put("ONE", "uno");
        map.put("TWO", "dos");
        map.put("THREE", "tres");

        i.putParameters(map);

        final Map<String, String> smap = new HashMap<>();
        smap.put("FOUR", "cuatro");
        smap.put("FIVE", "cinco");
        smap.put("SIX", "seis");

        i.putParameters(smap);

        Map<String, Collection<Object>> result = this.b.getParameters();
        assertEquals(6, result.size(), "Added all types of parameters");

        // Put in some maps
        i.setParameter("SEVEN", map);
        i.putParameter("EIGHT", smap);

        // Put in a map of map
        final Map<String, Map<String, String>> combo = new HashMap<>();
        combo.put("NINE", smap);
        i.putParameters(combo);

        result = i.getParameters();
        assertEquals(9, result.size(), "Added all types of parameters");
    }

    @Test
    void testPutUniqueParameters() {

        this.b.putParameter("ONE", "uno");
        this.b.putParameter("TWO", "deux");

        final Map<String, Object> map = new HashMap<>();
        map.put("ONE", "uno");
        map.put("TWO", "dos");
        map.put("THREE", "tres");

        this.b.putUniqueParameters(map);

        assertEquals(1, this.b.getParameter("ONE").size(), "When putting unique parameters values must collapse");
        assertEquals(2, this.b.getParameter("TWO").size(), "When putting unique parameters distinct values must be stored");
        assertEquals(1, this.b.getParameter("THREE").size(), "When putting unique parameters new keys must be stored");
    }

    @Test
    void testMergeParameters() {

        this.b.putParameter("ONE", "uno");
        this.b.putParameter("TWO", "deux");

        final Map<String, Object> map = new HashMap<>();
        map.put("ONE", "uno");
        map.put("TWO", "dos");
        map.put("THREE", "tres");

        this.b.mergeParameters(map);

        assertEquals("uno", this.b.getStringParameter("ONE"), "When merging parameters previous values must override");
        assertEquals("deux", this.b.getStringParameter("TWO"), "When merging parameters previous values must override");
        assertEquals("tres", this.b.getStringParameter("THREE"), "When merging  parameters new keys must be stored");
    }

    @Test
    void testPutParametersWithPolicy() {

        this.b.putParameter("ONE", "uno");
        this.b.putParameter("TWO", "deux");

        final Map<String, Object> map = new HashMap<>();
        map.put("ONE", "uno");
        map.put("TWO", "dos");
        map.put("THREE", "tres");

        this.b.putParameters(map, IBaseDataObject.MergePolicy.KEEP_ALL);

        assertEquals("uno;uno", this.b.getStringParameter("ONE"), "When specifying KEEP_ALL values must all stay");
        assertEquals("deux;dos", this.b.getStringParameter("TWO"), "When specifying KEEP_ALL values must all stay");
        assertEquals("tres", this.b.getStringParameter("THREE"), "When specifying KEEP_ALL new keys must be stored");
    }

    @Test
    void testPutParametersWithMultimapAsMap() {
        final Multimap<String, String> map = ArrayListMultimap.create();
        map.put("ONE", "uno");
        map.put("ONE", "ein");
        map.put("ONE", "neo");
        map.put("TWO", "deux");
        this.b.putParameter("TWO", "dos");
        this.b.putParameters(map.asMap());
        assertEquals(3, this.b.getParameter("ONE").size(), "Multimap parameters should merge");
        assertEquals(2, this.b.getParameter("TWO").size(), "Multimap parameters should merge");
        map.clear();
        assertEquals(3, this.b.getParameter("ONE").size(), "Multimap parameters should be detached from callers map");
        assertEquals(2, this.b.getParameter("TWO").size(), "Multimap parameters should be detached from callers map");
        map.put("THREE", "tres");
        this.b.mergeParameters(map.asMap());
        assertEquals(1, this.b.getParameter("THREE").size(), "Multimap parameters should merge");
        map.put("FOUR", "cuatro");
        this.b.putUniqueParameters(map.asMap());
        assertEquals(1, this.b.getParameter("THREE").size(), "Multimap parameters should remain unique");
        assertEquals(1, this.b.getParameter("FOUR").size(), "Multimap params should add on unique");
    }

    @Test
    void testParameters() {
        this.b.putParameter("ME", "YOU");
        assertEquals("YOU", this.b.getStringParameter("ME"), "Gotten parameter");
        final Map<String, Object> map = new HashMap<>();
        map.put("ONE", "uno");
        map.put("TWO", "dos");
        map.put("THREE", "tres");
        this.b.putParameters(map);
        assertEquals("uno", this.b.getStringParameter("ONE"), "Map put parameter gotten");
        assertEquals("dos", this.b.getStringParameter("TWO"), "Map put parameter gotten");
        assertEquals("tres", this.b.getStringParameter("THREE"), "Map put parameter gotten");
        assertEquals("YOU", this.b.getStringParameter("ME"), "Gotten parameter");

        // Deletes
        this.b.deleteParameter("THREE");
        assertNull(this.b.getParameter("THREE"), "Deleted param is gone");

        // Overwrite
        this.b.putParameter("ME", "THEM");
        assertEquals("THEM", this.b.getStringParameter("ME"), "Gotten parameter");

        // Clear
        this.b.clearParameters();
        assertNull(this.b.getParameter("THREE"), "Deleted param is gone");
        assertNull(this.b.getParameter("ME"), "Deleted param is gone");
        final Map<?, ?> m = this.b.getParameters();
        assertNotNull(m, "Clear paramters leave empty map");
        assertEquals(0, m.size(), "Clear parameters leaves empty map");

    }

    @Test
    void testHasParameter() {
        this.b.setParameter("FOO", "BAR");
        assertTrue(this.b.hasParameter("FOO"), "Has parameter must be true when present");
    }

    @Test
    void testHasParameterMiss() {
        assertFalse(this.b.hasParameter("FOO"), "Has parameter must be false when not present");
    }

    @Test
    void testHasParameterAfterDelete() {
        this.b.putParameter("FOO", "BARZILLAI");
        this.b.deleteParameter("FOO");
        assertFalse(this.b.hasParameter("FOO"), "Has parameter must be false after parameter has been removed");
    }

    @Test
    void testAppendDuplicateParameters() {
        this.b.appendParameter("YO", "GABBA");
        this.b.appendParameter("YO", "GABBA");
        assertEquals("GABBA;GABBA", this.b.getStringParameter("YO"), "Appended duplicate parameters should be preserved");
        assertTrue(this.b.hasParameter("YO"), "HasParameter should be true");
    }

    @Test
    void testAppendUniqueParameters() {
        this.b.appendUniqueParameter("YO", "GABBA");
        this.b.appendUniqueParameter("YO", "GABBA");
        assertEquals("GABBA", this.b.getStringParameter("YO"), "Appended unique  parameters should be collapsed");
        assertTrue(this.b.hasParameter("YO"), "HasParameter should be true");
    }

    @Test
    void testParametersWithMixtureOfSingleValuesAndLists() {
        final Map<String, Object> p = new HashMap<>();
        final List<String> foolist = new ArrayList<>();
        foolist.add("FOO1");
        foolist.add("FOO2");
        foolist.add("FOO3");
        p.put("FOO", foolist);
        this.b.putParameters(p);
        assertEquals(3, this.b.getParameter("FOO").size(), "Returned list size should match what was put in");
        this.b.appendParameter("FOO", "FOO4");
        assertEquals("FOO1;FOO2;FOO3;FOO4",
                this.b.getStringParameter("FOO"),
                "Returned string should be combination of initial list and added value");
    }

    @Test
    void testParametersWithMixtureOfSingleValuesAndSets() {
        final Map<String, Object> p = new HashMap<>();
        final Set<String> fooset = new TreeSet<>();
        fooset.add("FOO1");
        fooset.add("FOO2");
        fooset.add("FOO3");
        p.put("FOO", fooset);
        this.b.putParameters(p);
        assertEquals(3, this.b.getParameter("FOO").size(), "Returned list size should match what was put in");
        this.b.appendParameter("FOO", "FOO4");
        assertEquals("FOO1;FOO2;FOO3;FOO4", this.b.getStringParameter("FOO"), "Returned string should be combination of initial set and added value");
    }

    @Test
    void testCookedParameters() {
        this.b.appendParameter("YO", "GABBA");
        this.b.appendParameter("YO", "GABBA");
        this.b.appendUniqueParameter("WHALE", "BLUBBER");
        this.b.appendUniqueParameter("WHALE", "BLUBBER");
        final Map<String, String> m = this.b.getCookedParameters();
        assertNotNull(m, "Cooked parameters cannot be null");
        assertEquals("GABBA;GABBA", m.get("YO"), "Cooked parameters should contains inserted value");
        assertEquals("BLUBBER", m.get("WHALE"), "Cooked parameters should contains inserted unique value");
    }

    @Test
    void testParameterKeys() {
        this.b.appendParameter("YO", "GABBA");
        this.b.appendParameter("YO", "GABBA");
        this.b.appendUniqueParameter("WHALE", "BLUBBER");
        this.b.appendUniqueParameter("WHALE", "BLUBBER");
        final Set<String> keys = this.b.getParameterKeys();
        assertNotNull(keys, "Parameter keys cannot be null");
        assertTrue(keys.contains("YO"), "Parameter keys should contains inserted key");
        assertTrue(keys.contains("WHALE"), "Parameter keys should contains inserted unique key");
    }

    @Test
    void testAppendParameter() {
        this.b.putParameter("ME", "YOU");
        this.b.appendParameter("ME", "FOO");
        assertEquals("YOU;FOO", this.b.getStringParameter("ME"), "Appended parameter value");
    }

    @Test
    void testAppendParameterIterables() {
        this.b.putParameter("ME", "YOU");
        this.b.appendParameter("ME", Arrays.asList("FOO", "BAR", "BAZ"));
        assertEquals("YOU;FOO;BAR;BAZ", this.b.getStringParameter("ME"), "Appended parameter value");

        final Set<String> s = new TreeSet<>();
        s.add("ZAB");
        s.add("RAB");
        s.add("OOF");
        this.b.appendParameter("ME", s);

        assertEquals("YOU;FOO;BAR;BAZ;OOF;RAB;ZAB", this.b.getStringParameter("ME"), "Appended set paramter value");
    }

    @Test
    void testAppendParameterOntoEmpty() {
        this.b.appendParameter("ME", "FOO");
        assertEquals("FOO", this.b.getStringParameter("ME"), "Appended parameter value");
    }


    @Test
    void testWhereAmI() {
        assertNotNull(this.b.whereAmI(), "WhereamI gets host name");
        assertNotEquals("FAILED", this.b.whereAmI(), "WhereamI gets host name");
    }

    @Test
    void testVisitHistory() {
        assertNull(this.b.getLastPlaceVisited(), "No last place");
        assertNull(this.b.getPenultimatePlaceVisited(), "No penultimate place");

        this.b.appendTransformHistory("UNKNOWN.FOO.ID.http://host:1234/FooPlace$1010");

        assertNull(this.b.getPenultimatePlaceVisited(), "Still no penultimate place");

        this.b.appendTransformHistory("UNKNOWN.BAR.ID.http://host:1234/BarPlace$2020");
        this.b.appendTransformHistory("UNKNOWN.BAZ.ID.http://host:1234/BazPlace$3030");
        this.b.appendTransformHistory("UNKNOWN.BAM.ID.http://host:1234/BamPlace$4040");

        final DirectoryEntry sde = this.b.getLastPlaceVisited();
        assertNotNull(sde, "Last place directory entry");
        assertEquals("UNKNOWN.BAM.ID.http://host:1234/BamPlace$4040", sde.getFullKey(), "Last place key");
        final DirectoryEntry pen = this.b.getPenultimatePlaceVisited();
        assertNotNull(pen, "Penultimate place");
        assertEquals("UNKNOWN.BAZ.ID.http://host:1234/BazPlace$3030", pen.getFullKey(), "Pen place key");

        assertTrue(this.b.hasVisited("*.BAM.*.*"), "Has visited last");
        assertTrue(this.b.hasVisited("*.BAR.*.*"), "Has visited first");
        assertFalse(this.b.hasVisited("*.SHAZAM.*.*"), "No such visit");

        this.b.clearTransformHistory();
        assertFalse(this.b.hasVisited("*.BAM.*.*"), "Has no visited after clear");
    }

    @Test
    void testFiletype() {
        this.b.setFileType(emissary.core.Form.UNKNOWN);
        assertEquals(emissary.core.Form.UNKNOWN, this.b.getFileType(), "Filetype saved");

        this.b.setFileTypeIfEmpty("FOO");
        assertEquals("FOO", this.b.getFileType(), "Filetype saved on empty");

        this.b.setFileTypeIfEmpty("BAR");
        assertEquals("FOO", this.b.getFileType(), "Filetype ignored on non-empty");

        this.b.setFileType(null);
        assertNull(this.b.getFileType(), "Null filetype set");
        this.b.setFileTypeIfEmpty("BAZ");
        assertEquals("BAZ", this.b.getFileType(), "Filetype set on null as empty");
    }

    @Test
    void testClone() {
        try {
            this.b.setParameter("FOOBAR", "JOEBLOGGS");
            final IBaseDataObject clone = this.b.clone();
            assertEquals(this.b.getFilename(), clone.getFilename(), "Names must match");
            final String savedName = this.b.getFilename();
            this.b.setFilename("foo.bar");
            assertEquals(savedName, clone.getFilename(), "Names must be detached after clone");

            assertEquals(this.b.currentFormSize(), clone.currentFormSize(), "Current form size must match");
            this.b.popCurrentForm();
            assertEquals(this.b.currentFormSize(), clone.currentFormSize() - 1, "Current form stack must be detached after clone");
        } catch (CloneNotSupportedException ex) {
            throw new AssertionError("Clone must be supported on BaseDataObject", ex);
        }
    }

    @Test
    void testHeaderEncoding() {
        this.b.setHeaderEncoding("foo");
        assertEquals("foo", this.b.getHeaderEncoding(), "Header encoding simple string set/get failed");
    }

    @Test
    void testDefaultPriority() {
        assertEquals(Priority.DEFAULT, this.b.getPriority(), "Default priority failed");
    }

    @Test
    void testUpdatedPriority() {
        this.b.setPriority(1);
        assertEquals(1, this.b.getPriority(), "Updated priority failed");
    }

    @Test
    void testDefaultConstructor_setDateTime() {
        // setup
        final BaseDataObject ibdo = new BaseDataObject();

        // verify
        assertNotNull(ibdo.getCreationTimestamp());
    }

    @Test
    void testDefaultConstructor_getSetDateTime() {
        // setup
        final Date date = new Date(0);

        // test
        this.b.setCreationTimestamp(date);

        // verify
        assertEquals(date, this.b.getCreationTimestamp());
    }

    @Test
    void testNullTimestampSettingThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> this.b.setCreationTimestamp(null));
    }

    @Test
    void testExtractedRecords() {
        final BaseDataObject other = new BaseDataObject();
        assertFalse(this.b.hasExtractedRecords(), "Expected no extracted records");
        assertNull(this.b.getExtractedRecords());

        this.b.setExtractedRecords(new ArrayList<>());
        assertFalse(this.b.hasExtractedRecords(), "Expected no extracted records");
        assertNotNull(this.b.getExtractedRecords(), "Expected non-null extracted records");
        assertEquals(0, this.b.getExtractedRecords().size(), "Expected empty extracted records");

        this.b.setExtractedRecords(Collections.<IBaseDataObject>singletonList(other));
        assertTrue(this.b.hasExtractedRecords(), "Expected extracted records");
        assertEquals(1, this.b.getExtractedRecords().size(), "Expected a single extracted record");
        assertEquals(1, this.b.getExtractedRecordCount(), "Expected a single extracted record");

        assertThrows(IllegalArgumentException.class, () -> this.b.setExtractedRecords(null));

        assertThrows(IllegalArgumentException.class, () -> this.b.addExtractedRecord(null));

        this.b.addExtractedRecord(other);
        assertTrue(this.b.hasExtractedRecords(), "Expected extracted records");
        assertEquals(2, this.b.getExtractedRecords().size(), "Expected a two extracted record");
        assertEquals(2, this.b.getExtractedRecordCount(), "Expected a two extracted record");

        this.b.addExtractedRecord(other);
        assertTrue(this.b.hasExtractedRecords(), "Expected extracted records");
        assertEquals(3, this.b.getExtractedRecords().size(), "Expected three extracted record");
        assertEquals(3, this.b.getExtractedRecordCount(), "Expected three extracted record");

        this.b.clearExtractedRecords();
        assertFalse(this.b.hasExtractedRecords(), "Expected no extracted records");
        assertNull(this.b.getExtractedRecords());

        final List<IBaseDataObject> list = new ArrayList<>();
        list.add(new BaseDataObject());
        list.add(null);

        assertThrows(IllegalArgumentException.class, () -> this.b.setExtractedRecords(list));

        assertThrows(IllegalArgumentException.class, () -> this.b.addExtractedRecords(list));

        list.remove(1);

        assertThrows(IllegalArgumentException.class, () -> this.b.addExtractedRecords(null));

        this.b.addExtractedRecords(list);
        assertEquals(1, this.b.getExtractedRecordCount(), "Expect one extracted record");

        this.b.addExtractedRecords(list);
        assertEquals(2, this.b.getExtractedRecordCount(), "Expect two extracted record");

        final List<ExtendedDataObject> elist = new ArrayList<>();
        elist.add(new ExtendedDataObject());
        elist.add(new ExtendedDataObject());

        this.b.addExtractedRecords(elist);
        assertEquals(4, this.b.getExtractedRecordCount(), "Expected extended records to be added");

    }

    @Test
    void testSetCurrentFormWithBoolean() {
        IBaseDataObject testIbdo = DataObjectFactory.getInstance(null, "dummy", "FORM-1");
        testIbdo.enqueueCurrentForm("FORM-2");
        testIbdo.enqueueCurrentForm("FORM-3");

        assertEquals(3, testIbdo.currentFormSize(), "Form stack should have 3 forms before test");
        testIbdo.setCurrentForm("FINAL-FORM", true);
        assertEquals(1, testIbdo.currentFormSize(), "Form stack should have been cleared except for final set form");
        assertEquals("FINAL-FORM", testIbdo.currentForm(), "Form should be set to FINAL-FORM");

        testIbdo.enqueueCurrentForm("FINAL-FORM-2");
        testIbdo.setCurrentForm("FINAL-FORM-3", false);
        assertEquals(2, testIbdo.currentFormSize(), "Form stack should be 2, since we didnt clear the entire stack");
        assertEquals("FINAL-FORM-3", testIbdo.currentFormAt(0), "Top of form stack should be form 3");
        assertEquals("FINAL-FORM-2", testIbdo.currentFormAt(1), "2nd form in stack should be form 2");
    }

    @Test
    void testExtractedRecordClone() {

        final List<IBaseDataObject> list = new ArrayList<>();
        list.add(new BaseDataObject());
        this.b.addExtractedRecords(list);

        try {
            assertEquals(this.b.getExtractedRecordCount(), this.b.clone()
                    .getExtractedRecordCount(), "Cloned IBDO should have same sized extracted record list");
        } catch (CloneNotSupportedException ex) {
            fail("Clone method should have been called not " + ex.getMessage());
        }
    }
}
