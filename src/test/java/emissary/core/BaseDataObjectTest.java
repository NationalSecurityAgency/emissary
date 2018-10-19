package emissary.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
import org.hamcrest.core.Is;
import org.hamcrest.core.IsNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class BaseDataObjectTest extends UnitTest {

    private BaseDataObject b = null;

    @Override
    @Before
    public void setUp() throws Exception {
        this.b = new BaseDataObject("This is a test".getBytes(), "filename.txt");
        this.b.pushCurrentForm("ONE");
        this.b.pushCurrentForm("TWO");
        this.b.pushCurrentForm("THREE");
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        this.b = null;
    }

    @Test
    public void testInterface() {
        // This should pass by compilation, but in case anyone
        // ever thinks of taking it out, this may remind them...
        assertTrue("Implements the interface", this.b instanceof emissary.core.IBaseDataObject);
    }

    @Test
    public void testConstructors() {
        final BaseDataObject b2 = new BaseDataObject("This is a test".getBytes(), "filename.txt", "ONE");
        assertEquals("Current form in ctor", "ONE", b2.currentForm());
        assertThat(b2.getCreationTimestamp(), IsNull.notNullValue());

        final BaseDataObject b3 = new BaseDataObject("test".getBytes(), "filename.txt", null);
        assertEquals("Current form with null in ctor", "", b3.currentForm());
        assertThat(b3.getCreationTimestamp(), IsNull.notNullValue());
    }

    @Test
    public void testDataLength() {
        assertEquals("Data length", "This is a test".length(), this.b.dataLength());
    }

    @Test
    public void testNullDataLength() {
        this.b.setData(null);
        assertEquals("Null data length", 0, this.b.dataLength());
    }

    @Test
    public void testZeroLengthDataSlice() {
        final byte[] ary = new byte[0];
        this.b.setData(ary, 0, 0);
        assertEquals("Null data length is zero", 0, this.b.dataLength());
    }

    @Test
    public void testDataSliceLength() {
        final byte[] ary = "abcdefghijk".getBytes();
        this.b.setData(ary, 3, 4);
        assertEquals("Array slice must use length", 4, this.b.dataLength());
    }

    @Test
    public void testDataSliceData() {
        final byte[] ary = "abcdefghijk".getBytes();
        this.b.setData(ary, 3, 4);
        assertEquals("Array slice must use proper data", "defg", new String(this.b.data()));
    }

    @Test
    public void testNullData() {
        this.b.setData(null);
        assertNotNull("Data array can never be null", this.b.data());
    }

    @Test
    public void testNullDataSlice() {
        this.b.setData(null, 3, 4);
        assertNotNull("Data slice can never be null", this.b.data());
    }


    @Test
    public void testShortName() {
        assertEquals("Short name", "filename.txt", this.b.shortName());
    }

    @Test
    public void testByteArrays() {
        this.b.setHeader("A fine header".getBytes());
        this.b.setFooter("A good footer".getBytes());
        this.b.addAlternateView("TESTVIEW", "alternate view".getBytes());
        assertEquals("Data bytes retrieved", "This is a test", new String(this.b.data()));
        assertEquals("Header bytes", "A fine header", new String(this.b.header()));
        assertEquals("Footer bytes", "A good footer", new String(this.b.footer()));
        assertEquals("Alt view bytes", "alternate view", new String(this.b.getAlternateView("TESTVIEW")));

        final ByteBuffer hb = this.b.headerBuffer();
        final ByteBuffer fb = this.b.footerBuffer();
        final ByteBuffer db = this.b.dataBuffer();
        final ByteBuffer vb = this.b.getAlternateViewBuffer("TESTVIEW");
        assertEquals("Byte buffer on header", "A fine header".length(), hb.array().length);
        assertEquals("Byte buffer on footer", "A good footer".length(), fb.array().length);
        assertEquals("Byte buffer on data", "This is a test".length(), db.array().length);
        assertNotNull("Byte buffer on view", vb);
        assertEquals("Byte buffer on view", "alternate view".length(), vb.array().length);

        this.b.addAlternateView("TESTVIEW", null);
        assertNull("Byte buffer on removed view", this.b.getAlternateView("TESTVIEW"));
    }

    @Test
    public void testNonExistentAltViews() {
        assertNull("No such view", this.b.getAlternateView("NOSUCHVIEW"));
    }

    @Test
    public void testNonExistentAltViewBuffer() {
        assertNull("Byte buffer on no such view", this.b.getAlternateViewBuffer("NOSUCHVIEW"));
    }

    @Test
    public void testAltViews() {
        this.b.addAlternateView("TESTVIEW1", "alternate view".getBytes());
        this.b.addAlternateView("TESTVIEW2", "alternate view".getBytes());
        this.b.addAlternateView("TESTVIEW3", "alternate view".getBytes());

        this.b.addAlternateView("TESTVIEW2", null);
        assertNull("Null view after removal", this.b.getAlternateView("TESTVIEW2"));
        assertNull("Empty byte buffer after removal", this.b.getAlternateViewBuffer("TESTVIEW2"));
    }

    @Test
    public void testAltViewSlice() {
        this.b.addAlternateView("TESTVIEW1", "abcdefghij".getBytes(), 3, 4);
        assertEquals("Alt view slice must use proper data", "defg", new String(this.b.getAlternateView("TESTVIEW1")));
    }

    @Test
    public void testSetOfAltViewNames() {
        this.b.addAlternateView("TESTVIEW1", "alternate view".getBytes());
        this.b.addAlternateView("TESTVIEW2", "alternate view".getBytes());
        this.b.addAlternateView("TESTVIEW3", "alternate view".getBytes());
        final Set<String> vnames = this.b.getAlternateViewNames();
        assertEquals("Count of view names", 3, vnames.size());

        List<String> source = new ArrayList<String>(vnames);
        List<String> sorted = new ArrayList<String>(vnames);
        Collections.sort(sorted);
        assertEquals("Views are sorted", sorted, source);
    }

    @Test
    public void testMapOfAltViews() {
        this.b.addAlternateView("TESTVIEW1", "alternate view".getBytes());
        this.b.addAlternateView("TESTVIEW2", "alternate view".getBytes());
        this.b.addAlternateView("TESTVIEW3", "alternate view".getBytes());
        final Map<String, byte[]> v = this.b.getAlternateViews();
        assertEquals("Count of views", 3, v.size());

        List<String> source = new ArrayList<String>(v.keySet());
        List<String> sorted = new ArrayList<String>(v.keySet());
        Collections.sort(sorted);
        assertEquals("Views are sorted", sorted, source);
    }

    @Test
    public void testAppendAltView() {
        this.b.addAlternateView("T1", "alternate view".getBytes());
        this.b.appendAlternateView("T1", " more stuff".getBytes());
        assertEquals("Appended alternate view contents", "alternate view more stuff", new String(this.b.getAlternateView("T1")));
    }

    @Test
    public void testAppendAltViewOnEmpty() {
        this.b.appendAlternateView("T1", "more stuff".getBytes());
        assertEquals("Appended alternate view contents", "more stuff", new String(this.b.getAlternateView("T1")));
    }

    @Test
    public void testAppendAltViewSlice() {
        this.b.addAlternateView("T1", "alternate view".getBytes());
        this.b.appendAlternateView("T1", "xx more stuff xx".getBytes(), 2, 11);
        assertEquals("Appended alternate view contents", "alternate view more stuff", new String(this.b.getAlternateView("T1")));
    }

    @Test
    public void testAppendAltViewSliceOnEmpty() {
        this.b.appendAlternateView("T1", "xx more stuff xx".getBytes(), 3, 10);
        assertEquals("Appended alternate view contents", "more stuff", new String(this.b.getAlternateView("T1")));
    }

    @Test
    public void testWindowsShortName() {
        this.b.setFilename("c:\\Program Files\\Windows\\filename.txt");
        assertEquals("Short windows name", "filename.txt", this.b.shortName());
    }

    @Test
    public void testUnixShortName() {
        this.b.setFilename("/usr/local/share/filename.txt");
        assertEquals("Short windows name", "filename.txt", this.b.shortName());
    }

    @Test
    public void testFilename() {
        assertEquals("Short name", "filename.txt", this.b.getFilename());
    }

    @Test
    public void testCurrentFormTop() {
        assertEquals("Current form push", "THREE", this.b.currentForm());
    }

    @Test
    public void testCurrentFormEnqueue() {
        final int i = this.b.enqueueCurrentForm("FOUR");
        assertEquals("Enqueue return value", 4, i);
        assertEquals("Current form push", "THREE", this.b.currentForm());
        assertEquals("Prev bottom form", "ONE", this.b.currentFormAt(2));
        assertEquals("Bottom form", "FOUR", this.b.currentFormAt(3));
    }

    @Test
    public void testPopCurrentForm() {
        final String s = this.b.popCurrentForm();
        assertEquals("Pop return value", "THREE", s);
        assertEquals("Current form after pop", "TWO", this.b.currentForm());
        this.b.popCurrentForm();
        assertEquals("Current form after pop pop", "ONE", this.b.currentForm());
        this.b.popCurrentForm();
        assertEquals("No forms left", "", this.b.currentForm());
    }

    @Test
    public void testCurrentFormAt() {
        assertEquals("Current form at", "TWO", this.b.currentFormAt(1));
    }

    @Test
    public void testSearchCurrentForm() {
        assertEquals("Successful search", 1, this.b.searchCurrentForm("TWO"));

        final List<String> l = new ArrayList<String>();
        l.add("CHOP");
        l.add("TWO");
        l.add("CHIP");
        assertEquals("Successful list search", "TWO", this.b.searchCurrentForm(l));

    }

    @Test
    public void testReplaceCurrentForm() {
        this.b.replaceCurrentForm("NEWONE");
        assertEquals("Not found search", -1, this.b.searchCurrentForm("ONE"));
        assertEquals("Not found search", -1, this.b.searchCurrentForm("TWO"));
        assertEquals("Not found search", -1, this.b.searchCurrentForm("THREE"));
        assertEquals("Successful search", 0, this.b.searchCurrentForm("NEWONE"));
        assertEquals("One form left", 1, this.b.currentFormSize());

        this.b.replaceCurrentForm(null);
        assertEquals("Not found search", -1, this.b.searchCurrentForm("NEWONE"));
        assertEquals("No forms left", 0, this.b.currentFormSize());
    }


    @Test
    public void testBadSearchCurrentForm() {
        assertEquals("Not found search", -1, this.b.searchCurrentForm("SEVENTEEN"));

        final List<String> l2 = new ArrayList<String>();
        l2.add("SHIP");
        l2.add("SHAPE");
        assertNull("Search but no match", this.b.searchCurrentForm(l2));
    }

    @Test
    public void testFormSize() {
        assertEquals("Form stack size", 3, this.b.currentFormSize());
    }

    @Test
    public void testDeleteFormFromBottom() {
        this.b.deleteCurrentFormAt(0);
        assertEquals("Form remaining on bottom", "TWO", this.b.currentForm());
        assertEquals("Stack size after delete", 2, this.b.currentFormSize());
    }

    @Test
    public void testDeleteFormFromTop() {
        this.b.deleteCurrentFormAt(2);
        assertEquals("Form remaining on top", "THREE", this.b.currentForm());
        assertEquals("Stack size after delete", 2, this.b.currentFormSize());
    }

    @Test
    public void testDeleteFormFromMiddle() {
        final int i = this.b.deleteCurrentFormAt(1);
        assertEquals("Delete return value", 2, i);
        assertEquals("Form remaining on top", "THREE", this.b.currentForm());
        assertEquals("Form remaining on bottom", "ONE", this.b.currentFormAt(1));
        assertEquals("Stack size after delete", 2, this.b.currentFormSize());
    }

    @Test
    public void testDeleteFormFromIllegalPosition() {
        this.b.deleteCurrentFormAt(7);
        assertEquals("Stack size after delete", 3, this.b.currentFormSize());
        this.b.deleteCurrentFormAt(-1);
        assertEquals("Stack size after delete", 3, this.b.currentFormSize());
    }

    @Test
    public void testDeleteCurrentFormFromNull() {
        while (this.b.currentFormSize() > 0) {
            this.b.popCurrentForm();
        }
        assertEquals("Delete from empty current forms failed", 0, this.b.deleteCurrentFormAt(0));
    }

    @Test
    public void testPrintMeta() {
        this.b.putParameter("FOO", "QUUZ");
        assertTrue("PrintMeta returnss valid params", this.b.printMeta().indexOf("QUUZ") > -1);
    }

    @Test
    public void testSetParameters() {
        final Map<String, Object> m = new HashMap<String, Object>();
        m.put("A", "B");
        this.b.putParameter("FOO", "BAR");
        this.b.setParameters(m);
        assertNull("Set parameters must clear old data", this.b.getParameter("FOO"));
    }

    @Test
    public void testStringParameterOnNonStringValue() {
        this.b.putParameter("A", Long.valueOf(1L));
        assertEquals("Non-string parameters must call toString method", "1", this.b.getStringParameter("A"));
    }

    @Test
    public void testStringParameterOnNullValue() {
        this.b.putParameter("A", null);
        assertNull("Null parameter must be returned as null", this.b.getStringParameter("A"));
    }

    @Test
    public void testNumSiblings() {
        this.b.setNumSiblings(10);
        assertEquals("NumSiblings simple set/get failed", 10, this.b.getNumSiblings());
    }

    @Test
    public void testBirthOrder() {
        this.b.setBirthOrder(10);
        assertEquals("BirthOrder simple set/get failed", 10, this.b.getBirthOrder());
    }

    @Test
    public void testFontEncoding() {
        this.b.setFontEncoding("zhosa");
        assertEquals("FontEncoding simple set/get failed", "zhosa", this.b.getFontEncoding());
    }

    @Test
    public void testFileTypeIsEmpty() {
        this.b.setFileType(Form.UNKNOWN);
        assertTrue("Unknown form must count as empty", this.b.isFileTypeEmpty());
        this.b.setFileType("BAR");
        final String[] fakeEmpties = {Form.UNKNOWN, "FOO", "BAR"};
        assertTrue("Unknown form must count as empty when passing in list", this.b.setFileTypeIfEmpty("BAZ", fakeEmpties));
        assertEquals("Failed to use supplied list of empty forms " + Arrays.asList(fakeEmpties), "BAZ", this.b.getFileType());

        this.b.setFileType("TEST-UNWRAPPED");
        assertTrue("Unknown form must count as empty when passing in list", this.b.setFileTypeIfEmpty("ZAZ"));
        assertTrue(this.b.getFileType().equals("ZAZ"));
    }

    @Test
    public void testAlternateViewCount() {
        this.b.addAlternateView("FOO", "abcd".getBytes());
        assertEquals("Number of alternate views failed", 1, this.b.getNumAlternateViews());
        this.b.addAlternateView("BAR", "abcd".getBytes());
        assertEquals("Number of alternate views failed to increment", 2, this.b.getNumAlternateViews());
    }

    @Test
    public void testSetBroken() {
        this.b.setBroken("This is broken");
        assertTrue("Broken indicator failed", this.b.isBroken());
        this.b.setBroken("This is still broken");
        assertTrue("Broken indicator failed after append", this.b.isBroken());
        assertTrue("Broken indicator string failed", this.b.getBroken().indexOf("still") > -1);
    }

    @Test
    public void testDeleteFormByName() {
        this.b.deleteCurrentForm("TWO");
        assertEquals("Remaining form count", 2, this.b.currentFormSize());
        this.b.deleteCurrentForm("ONE");
        assertEquals("Remaining form count", 1, this.b.currentFormSize());
        this.b.deleteCurrentForm("THREE");
        assertEquals("Remaining form count", 0, this.b.currentFormSize());

        this.b.pushCurrentForm("ONE");
        this.b.deleteCurrentForm("BOGUS");
        assertEquals("Remaining form count", 1, this.b.currentFormSize());
        this.b.deleteCurrentForm(null);
        assertEquals("Remaining form count", 1, this.b.currentFormSize());

        this.b.deleteCurrentForm("ONE");
        assertEquals("Remaining form count", 0, this.b.currentFormSize());
        this.b.deleteCurrentForm("BOGUS");
        assertEquals("Remaining form count", 0, this.b.currentFormSize());
        this.b.deleteCurrentForm(null);
        assertEquals("Remaining form count", 0, this.b.currentFormSize());
    }

    @Test
    public void testAddCurrentFormAtBottom() {
        this.b.addCurrentFormAt(0, "FOUR");
        assertEquals("Form on top", "FOUR", this.b.currentForm());
        assertEquals("Form on bottom", "ONE", this.b.currentFormAt(3));
        assertEquals("Stack size after add", 4, this.b.currentFormSize());
    }

    @Test
    public void testAddCurrentFormInMiddle() {
        this.b.addCurrentFormAt(1, "FOUR");
        assertEquals("Form remaining on top", "THREE", this.b.currentForm());
        assertEquals("Form on bottom", "ONE", this.b.currentFormAt(3));
        assertEquals("Stack size after add", 4, this.b.currentFormSize());
    }

    @Test
    public void testAddCurrentFormAtTop() {
        this.b.addCurrentFormAt(3, "FOUR");
        assertEquals("Form on top", "THREE", this.b.currentForm());
        assertEquals("Form on bottom", "FOUR", this.b.currentFormAt(3));
        assertEquals("Stack size after add", 4, this.b.currentFormSize());
    }

    @Test
    public void testSetCurrentForm() {
        this.b.setCurrentForm("FOUR");
        assertEquals("Form on top", "FOUR", this.b.currentForm());
        assertEquals("Form on bottom", "ONE", this.b.currentFormAt(2));
        assertEquals("Stack size after set", 3, this.b.currentFormSize());
        assertTrue("To string with current form", this.b.toString().indexOf("FOUR") > -1);
    }

    @Test
    public void testHistoryInToString() {
        this.b.setCurrentForm("UNKNOWN");
        this.b.appendTransformHistory("*.FOOPLACE.*.http://host:1234/fooPlace");
        this.b.appendTransformHistory("*.BARPLACE.*.http://host:1234/barPlace");
        final String s = this.b.toString();
        assertTrue("history elements in toString", s.indexOf("FOOPLACE") > -1);
        assertTrue("history elements in toString", s.indexOf("BARPLACE") > -1);
    }

    @Test
    public void testSetCurrentFormEmpty() {
        this.b.popCurrentForm();
        this.b.popCurrentForm();
        this.b.popCurrentForm();
        this.b.setCurrentForm("FOUR");
        assertEquals("Form on top", "FOUR", this.b.currentForm());
        assertEquals("Form on bottom", "FOUR", this.b.currentFormAt(0));
        assertEquals("Stack size after set", 1, this.b.currentFormSize());
    }

    @Test
    public void testCurrentFormNullHandling() {
        try {
            this.b.pushCurrentForm(null);
            fail("Current form cannot push null");
        } catch (IllegalArgumentException expected) {
        }
        try {
            this.b.enqueueCurrentForm(null);
            fail("Current form cannot enqueue null");
        } catch (IllegalArgumentException expected) {
        }
        try {
            this.b.setCurrentForm(null);
            fail("Current form cannot set null");
        } catch (IllegalArgumentException expected) {
        }
        try {
            this.b.addCurrentFormAt(0, null);
            fail("Current form cannot set null");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testPullToTopFromMiddle() {
        this.b.pullFormToTop("TWO");
        assertEquals("Form on top", "TWO", this.b.currentForm());
        assertEquals("Form on bottom", "ONE", this.b.currentFormAt(2));
        assertEquals("Stack size after set", 3, this.b.currentFormSize());
    }

    @Test
    public void testPullToTopFromBottom() {
        this.b.pullFormToTop("ONE");
        assertEquals("Form on top", "ONE", this.b.currentForm());
        assertEquals("Form on bottom", "TWO", this.b.currentFormAt(2));
        assertEquals("Stack size after set", 3, this.b.currentFormSize());
    }

    @Test
    public void testPullToTopFromTop() {
        this.b.pullFormToTop("THREE");
        assertEquals("Form on top", "THREE", this.b.currentForm());
        assertEquals("Form on bottom", "ONE", this.b.currentFormAt(2));
        assertEquals("Stack size after set", 3, this.b.currentFormSize());
    }

    @Test
    public void testPullNonExistentToTop() {
        this.b.pullFormToTop("SEVENTEEN");
        assertEquals("Form on top", "THREE", this.b.currentForm());
        assertEquals("Form on bottom", "ONE", this.b.currentFormAt(2));
        assertEquals("Stack size after set", 3, this.b.currentFormSize());
    }

    @Test
    public void testGetAllForms() {
        final List<String> al = this.b.getAllCurrentForms();
        assertEquals("Forms returned", 3, al.size());
        assertEquals("Form order", "THREE", al.get(0).toString());
    }

    @Test
    public void testProcessingError() {
        assertNull("Empty processing error", this.b.getProcessingError());
        this.b.addProcessingError("ONE");
        this.b.addProcessingError("TWO");
        assertEquals("Catted proc error", "ONE\nTWO\n", this.b.getProcessingError());
    }

    @Test
    public void testBeforeStart() {
        assertTrue("Before start on empty history", this.b.beforeStart());
        this.b.appendTransformHistory("*.FOOPLACE.*.http://host:1234/fooPlace");
        assertFalse("Before start with history", this.b.beforeStart());
        this.b.clearTransformHistory();
        assertEquals("Empty history", 0, this.b.transformHistory().size());
        assertTrue("Re-emptied history before start", this.b.beforeStart());

        this.b.appendTransformHistory("*.FOOPLACE.*.http://host:1234/fooPlace");
        this.b.appendTransformHistory("*.<SPROUT>.*.http://host:1234/barPlace");
        assertTrue("Before start with sprout key on end", this.b.beforeStart());
        this.b.appendTransformHistory("UNKNOWN.FOOPLACE.ID.http://host:1234/bazPlace");
        assertFalse("Not before start with sprout key on list", this.b.beforeStart());
    }

    @Test
    public void testAltViewRemapping() {
        try {
            final byte[] configData = ("RENAME_PROPERTIES = \"FLUBBER\"\n" + "RENAME_FOO =\"BAR\"\n").getBytes();

            final ByteArrayInputStream str = new ByteArrayInputStream(configData);
            final Configurator conf = ConfigUtil.getConfigInfo(str);
            MetadataDictionary.initialize(MetadataDictionary.DEFAULT_NAMESPACE_NAME, conf);
            this.b.addAlternateView("PROPERTIES", configData);
            this.b.addAlternateView("FOO", configData, 20, 10);
            assertNotNull("Remapped alt view retrieved by original name", this.b.getAlternateView("PROPERTIES"));
            assertNotNull("Remapped alt view retrieved by new name", this.b.getAlternateView("FLUBBER"));
            assertNotNull("Remapped alt view slice retrieved by original name", this.b.getAlternateView("FOO"));
            assertNotNull("Remapped alt view slice retrieved by new name", this.b.getAlternateView("BAR"));
            final Set<String> avnames = this.b.getAlternateViewNames();
            assertTrue("Alt view names contains remapped name", avnames.contains("FLUBBER"));
            assertTrue("Alt view slice names contains remapped name", avnames.contains("BAR"));

            // Delete by orig name
            this.b.addAlternateView("FOO", null, 20, 10);
            assertTrue("View removed by orig name", this.b.getAlternateViewNames().size() == 1);
            // Delete by mapped name
            this.b.addAlternateView("FLUBBER", null);
            assertTrue("View removed by orig name", this.b.getAlternateViewNames().size() == 0);
        } catch (Exception ex) {
            fail("Could not configure test: " + ex.getMessage());
        } finally {
            // Clean up
            Namespace.unbind(MetadataDictionary.DEFAULT_NAMESPACE_NAME);
        }
    }

    @Test
    public void testParametersMapSignature() {
        final Map<String, Object> map = new HashMap<String, Object>();
        map.put("ONE", "uno");
        map.put("TWO", "dos");
        map.put("THREE", "tres");

        this.b.putParameters(map);

        final Map<String, String> smap = new HashMap<String, String>();
        smap.put("FOUR", "cuatro");
        smap.put("FIVE", "cinco");
        smap.put("SIX", "seis");

        this.b.putParameters(smap);

        Map<String, Collection<Object>> result = this.b.getParameters();
        assertEquals("Added all types of parameters", 6, result.size());

        // Put in some maps
        this.b.setParameter("SEVEN", map);
        this.b.putParameter("EIGHT", smap);

        // Put in a map of map
        final Map<String, Map<String, String>> combo = new HashMap<String, Map<String, String>>();
        combo.put("NINE", smap);
        this.b.putParameters(combo);

        result = this.b.getParameters();
        assertEquals("Added all types of parameters", 9, result.size());

    }

    @Test
    public void testParametersMapInterfaceSignature() {

        final IBaseDataObject i = this.b;

        final Map<String, Object> map = new HashMap<String, Object>();
        map.put("ONE", "uno");
        map.put("TWO", "dos");
        map.put("THREE", "tres");

        i.putParameters(map);

        final Map<String, String> smap = new HashMap<String, String>();
        smap.put("FOUR", "cuatro");
        smap.put("FIVE", "cinco");
        smap.put("SIX", "seis");

        i.putParameters(smap);

        Map<String, Collection<Object>> result = this.b.getParameters();
        assertEquals("Added all types of parameters", 6, result.size());

        // Put in some maps
        i.setParameter("SEVEN", map);
        i.putParameter("EIGHT", smap);

        // Put in a map of map
        final Map<String, Map<String, String>> combo = new HashMap<String, Map<String, String>>();
        combo.put("NINE", smap);
        i.putParameters(combo);

        result = i.getParameters();
        assertEquals("Added all types of parameters", 9, result.size());
    }

    @Test
    public void testPutUniqueParameters() {

        this.b.putParameter("ONE", "uno");
        this.b.putParameter("TWO", "deux");

        final Map<String, Object> map = new HashMap<String, Object>();
        map.put("ONE", "uno");
        map.put("TWO", "dos");
        map.put("THREE", "tres");

        this.b.putUniqueParameters(map);

        assertEquals("When putting unique parameters values must collapse", 1, this.b.getParameter("ONE").size());
        assertEquals("When putting unique parameters distinct values must be stored", 2, this.b.getParameter("TWO").size());
        assertEquals("When putting unique parameters new keys must be stored", 1, this.b.getParameter("THREE").size());
    }

    @Test
    public void testMergeParameters() {

        this.b.putParameter("ONE", "uno");
        this.b.putParameter("TWO", "deux");

        final Map<String, Object> map = new HashMap<String, Object>();
        map.put("ONE", "uno");
        map.put("TWO", "dos");
        map.put("THREE", "tres");

        this.b.mergeParameters(map);

        assertEquals("When merging parameters previous values must override", "uno", this.b.getStringParameter("ONE"));
        assertEquals("When merging parameters previous values must override", "deux", this.b.getStringParameter("TWO"));
        assertEquals("When merging  parameters new keys must be stored", "tres", this.b.getStringParameter("THREE"));
    }

    @Test
    public void testPutParametersWithPolicy() {

        this.b.putParameter("ONE", "uno");
        this.b.putParameter("TWO", "deux");

        final Map<String, Object> map = new HashMap<String, Object>();
        map.put("ONE", "uno");
        map.put("TWO", "dos");
        map.put("THREE", "tres");

        this.b.putParameters(map, IBaseDataObject.MergePolicy.KEEP_ALL);

        assertEquals("When specifying KEEP_ALL values must all stay", "uno;uno", this.b.getStringParameter("ONE"));
        assertEquals("When specifying KEEP_ALL values must all stay", "deux;dos", this.b.getStringParameter("TWO"));
        assertEquals("When specifying KEEP_ALL new keys must be stored", "tres", this.b.getStringParameter("THREE"));
    }

    @Test
    public void testPutParametersWithMultimapAsMap() {
        final Multimap<String, String> map = ArrayListMultimap.create();
        map.put("ONE", "uno");
        map.put("ONE", "ein");
        map.put("ONE", "neo");
        map.put("TWO", "deux");
        this.b.putParameter("TWO", "dos");
        this.b.putParameters(map.asMap());
        assertEquals("Multimap parameters should merge", 3, this.b.getParameter("ONE").size());
        assertEquals("Multimap parameters should merge", 2, this.b.getParameter("TWO").size());
        map.clear();
        assertEquals("Multimap parameters should be detached from callers map", 3, this.b.getParameter("ONE").size());
        assertEquals("Multimap parameters should be detached from callers map", 2, this.b.getParameter("TWO").size());
        map.put("THREE", "tres");
        this.b.mergeParameters(map.asMap());
        assertEquals("Multimap parameters should merge", 1, this.b.getParameter("THREE").size());
        map.put("FOUR", "cuatro");
        this.b.putUniqueParameters(map.asMap());
        assertEquals("Multimap parameters should remain unique", 1, this.b.getParameter("THREE").size());
        assertEquals("Multimap params should add on unique", 1, this.b.getParameter("FOUR").size());
    }

    @Test
    public void testParameters() {
        this.b.putParameter("ME", "YOU");
        assertEquals("Gotten parameter", "YOU", this.b.getStringParameter("ME"));
        final Map<String, Object> map = new HashMap<String, Object>();
        map.put("ONE", "uno");
        map.put("TWO", "dos");
        map.put("THREE", "tres");
        this.b.putParameters(map);
        assertEquals("Map put parameter gotten", "uno", this.b.getStringParameter("ONE"));
        assertEquals("Map put parameter gotten", "dos", this.b.getStringParameter("TWO"));
        assertEquals("Map put parameter gotten", "tres", this.b.getStringParameter("THREE"));
        assertEquals("Gotten parameter", "YOU", this.b.getStringParameter("ME"));

        // Deletes
        this.b.deleteParameter("THREE");
        assertNull("Deleted param is gone", this.b.getParameter("THREE"));

        // Overwrite
        this.b.putParameter("ME", "THEM");
        assertEquals("Gotten parameter", "THEM", this.b.getStringParameter("ME"));

        // Clear
        this.b.clearParameters();
        assertNull("Deleted param is gone", this.b.getParameter("THREE"));
        assertNull("Deleted param is gone", this.b.getParameter("ME"));
        final Map<?, ?> m = this.b.getParameters();
        assertNotNull("Clear paramters leave empty map", m);
        assertEquals("Clear parameters leaves empty map", 0, m.size());

    }

    @Test
    public void testHasParameter() {
        this.b.setParameter("FOO", "BAR");
        assertTrue("Has parameter must be true when present", this.b.hasParameter("FOO"));
    }

    @Test
    public void testHasParameterMiss() {
        assertFalse("Has parameter must be false when not present", this.b.hasParameter("FOO"));
    }

    @Test
    public void testHasParameterAfterDelete() {
        this.b.putParameter("FOO", "BARZILLAI");
        this.b.deleteParameter("FOO");
        assertFalse("Has parameter must be false after parameter has been removed", this.b.hasParameter("FOO"));
    }

    @Test
    public void testAppendDuplicateParameters() {
        this.b.appendParameter("YO", "GABBA");
        this.b.appendParameter("YO", "GABBA");
        assertEquals("Appended duplicate parameters should be preserved", "GABBA;GABBA", this.b.getStringParameter("YO"));
        assertTrue("HasParameter should be true", this.b.hasParameter("YO"));
    }

    @Test
    public void testAppendUniqueParameters() {
        this.b.appendUniqueParameter("YO", "GABBA");
        this.b.appendUniqueParameter("YO", "GABBA");
        assertEquals("Appended unique  parameters should be collapsed", "GABBA", this.b.getStringParameter("YO"));
        assertTrue("HasParameter should be true", this.b.hasParameter("YO"));
    }

    @Test
    public void testParametersWithMixtureOfSingleValuesAndLists() {
        final Map<String, Object> p = new HashMap<String, Object>();
        final List<String> foolist = new ArrayList<String>();
        foolist.add("FOO1");
        foolist.add("FOO2");
        foolist.add("FOO3");
        p.put("FOO", foolist);
        this.b.putParameters(p);
        assertEquals("Returned list size should match what was put in", 3, this.b.getParameter("FOO").size());
        this.b.appendParameter("FOO", "FOO4");
        assertEquals("Returned string should be combination of initial list and added value", "FOO1;FOO2;FOO3;FOO4",
                this.b.getStringParameter("FOO"));
    }

    @Test
    public void testParametersWithMixtureOfSingleValuesAndSets() {
        final Map<String, Object> p = new HashMap<String, Object>();
        final Set<String> fooset = new TreeSet<String>();
        fooset.add("FOO1");
        fooset.add("FOO2");
        fooset.add("FOO3");
        p.put("FOO", fooset);
        this.b.putParameters(p);
        assertEquals("Returned list size should match what was put in", 3, this.b.getParameter("FOO").size());
        this.b.appendParameter("FOO", "FOO4");
        assertEquals("Returned string should be combination of initial set and added value", "FOO1;FOO2;FOO3;FOO4", this.b.getStringParameter("FOO"));
    }

    @Test
    public void testCookedParameters() {
        this.b.appendParameter("YO", "GABBA");
        this.b.appendParameter("YO", "GABBA");
        this.b.appendUniqueParameter("WHALE", "BLUBBER");
        this.b.appendUniqueParameter("WHALE", "BLUBBER");
        final Map<String, String> m = this.b.getCookedParameters();
        assertNotNull("Cooked parameters cannot be null", m);
        assertEquals("Cooked parameters should contains inserted value", "GABBA;GABBA", m.get("YO"));
        assertEquals("Cooked parameters should contains inserted unique value", "BLUBBER", m.get("WHALE"));
    }

    @Test
    public void testParameterKeys() {
        this.b.appendParameter("YO", "GABBA");
        this.b.appendParameter("YO", "GABBA");
        this.b.appendUniqueParameter("WHALE", "BLUBBER");
        this.b.appendUniqueParameter("WHALE", "BLUBBER");
        final Set<String> keys = this.b.getParameterKeys();
        assertNotNull("Parameter keys cannot be null", keys);
        assertTrue("Parameter keys should contains inserted key", keys.contains("YO"));
        assertTrue("Parameter keys should contains inserted unique key", keys.contains("WHALE"));
    }

    @Test
    public void testAppendParameter() {
        this.b.putParameter("ME", "YOU");
        this.b.appendParameter("ME", "FOO");
        assertEquals("Appended parameter value", "YOU;FOO", this.b.getStringParameter("ME"));
    }

    @Test
    public void testAppendParameterIterables() {
        this.b.putParameter("ME", "YOU");
        this.b.appendParameter("ME", Arrays.asList("FOO", "BAR", "BAZ"));
        assertEquals("Appended parameter value", "YOU;FOO;BAR;BAZ", this.b.getStringParameter("ME"));

        final Set<String> s = new TreeSet<String>();
        s.add("ZAB");
        s.add("RAB");
        s.add("OOF");
        this.b.appendParameter("ME", s);

        assertEquals("Appended set paramter value", "YOU;FOO;BAR;BAZ;OOF;RAB;ZAB", this.b.getStringParameter("ME"));
    }

    @Test
    public void testAppendParameterOntoEmpty() {
        this.b.appendParameter("ME", "FOO");
        assertEquals("Appended parameter value", "FOO", this.b.getStringParameter("ME"));
    }


    @Test
    public void testWhereAmI() {
        assertNotNull("WhereamI gets host name", this.b.whereAmI());
        assertFalse("WhereamI gets host name", "FAILED".equals(this.b.whereAmI()));
    }

    @Test
    public void testVisitHistory() {
        assertNull("No last place", this.b.getLastPlaceVisited());
        assertNull("No penultimate place", this.b.getPenultimatePlaceVisited());

        this.b.appendTransformHistory("UNKNOWN.FOO.ID.http://host:1234/FooPlace$1010");

        assertNull("Still no penultimate place", this.b.getPenultimatePlaceVisited());

        this.b.appendTransformHistory("UNKNOWN.BAR.ID.http://host:1234/BarPlace$2020");
        this.b.appendTransformHistory("UNKNOWN.BAZ.ID.http://host:1234/BazPlace$3030");
        this.b.appendTransformHistory("UNKNOWN.BAM.ID.http://host:1234/BamPlace$4040");

        final DirectoryEntry sde = this.b.getLastPlaceVisited();
        assertNotNull("Last place directory entry", sde);
        assertEquals("Last place key", "UNKNOWN.BAM.ID.http://host:1234/BamPlace$4040", sde.getFullKey());
        final DirectoryEntry pen = this.b.getPenultimatePlaceVisited();
        assertNotNull("Penultimate place", pen);
        assertEquals("Pen place key", "UNKNOWN.BAZ.ID.http://host:1234/BazPlace$3030", pen.getFullKey());

        assertTrue("Has visited last", this.b.hasVisited("*.BAM.*.*"));
        assertTrue("Has visited first", this.b.hasVisited("*.BAR.*.*"));
        assertFalse("No such visit", this.b.hasVisited("*.SHAZAM.*.*"));

        this.b.clearTransformHistory();
        assertFalse("Has no visited after clear", this.b.hasVisited("*.BAM.*.*"));
    }

    @Test
    public void testFiletype() {
        this.b.setFileType(emissary.core.Form.UNKNOWN);
        assertEquals("Filetype saved", emissary.core.Form.UNKNOWN, this.b.getFileType());

        this.b.setFileTypeIfEmpty("FOO");
        assertEquals("Filetype saved on empty", "FOO", this.b.getFileType());

        this.b.setFileTypeIfEmpty("BAR");
        assertEquals("Filetype ignored on non-empty", "FOO", this.b.getFileType());

        this.b.setFileType(null);
        assertNull("Null filetype set", this.b.getFileType());
        this.b.setFileTypeIfEmpty("BAZ");
        assertEquals("Filetype set on null as empty", "BAZ", this.b.getFileType());
    }

    @Test
    public void testClone() {
        try {
            this.b.setParameter("FOOBAR", "JOEBLOGGS");
            final IBaseDataObject clone = this.b.clone();
            assertEquals("Names must match", this.b.getFilename(), clone.getFilename());
            final String savedName = this.b.getFilename();
            this.b.setFilename("foo.bar");
            assertEquals("Names must be detached after clone", savedName, clone.getFilename());

            assertEquals("Current form size must match", this.b.currentFormSize(), clone.currentFormSize());
            this.b.popCurrentForm();
            assertEquals("Current form stack must be detached after clone", this.b.currentFormSize(), clone.currentFormSize() - 1);
        } catch (CloneNotSupportedException ex) {
            fail("Clone must be supported on BaseDataObject");
        }
    }

    @Test
    public void testHeaderEncoding() {
        this.b.setHeaderEncoding("foo");
        assertEquals("Header encoding simple string set/get failed", "foo", this.b.getHeaderEncoding());
    }

    @Test
    public void testDefaultPriority() {
        assertEquals("Default priority failed", Priority.DEFAULT, this.b.getPriority());
    }

    @Test
    public void testUpdatedPriority() {
        this.b.setPriority(1);
        assertEquals("Updated priority failed", 1, this.b.getPriority());
    }

    @Test
    public void testDefaultConstructor_setDateTime() {
        // setup
        final BaseDataObject ibdo = new BaseDataObject();

        // verify
        assertThat(ibdo.getCreationTimestamp(), IsNull.notNullValue());
    }

    @Test
    public void testDefaultConstructor_getSetDateTime() {
        // setup
        final Date date = new Date(0);

        // test
        this.b.setCreationTimestamp(date);

        // verify
        assertThat(this.b.getCreationTimestamp(), Is.is(date));
    }

    @Test
    public void testNullTimestampSettingThrowsException() {
        try {
            this.b.setCreationTimestamp(null);
            fail("Should not be able to set null timestamp");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testExtractedRecords() {
        final BaseDataObject other = new BaseDataObject();
        assertFalse("Expected no extracted records", this.b.hasExtractedRecords());
        assertNull(this.b.getExtractedRecords());

        this.b.setExtractedRecords(new ArrayList<IBaseDataObject>());
        assertFalse("Expected no extracted records", this.b.hasExtractedRecords());
        assertNotNull("Expected non-null extracted records", this.b.getExtractedRecords());
        assertEquals("Expected empty extracted records", 0, this.b.getExtractedRecords().size());

        this.b.setExtractedRecords(Collections.<IBaseDataObject>singletonList(other));
        assertTrue("Expected extracted records", this.b.hasExtractedRecords());
        assertEquals("Expected a single extracted record", 1, this.b.getExtractedRecords().size());
        assertEquals("Expected a single extracted record", 1, this.b.getExtractedRecordCount());

        try {
            this.b.setExtractedRecords(null);
            fail("Shoud not be able to add null extracted record list");
        } catch (IllegalArgumentException expected) {
        }

        try {
            this.b.addExtractedRecord(null);
            fail("Should not be able to add null extracted record");
        } catch (IllegalArgumentException expected) {
        }

        this.b.addExtractedRecord(other);
        assertTrue("Expected extracted records", this.b.hasExtractedRecords());
        assertEquals("Expected a two extracted record", 2, this.b.getExtractedRecords().size());
        assertEquals("Expected a two extracted record", 2, this.b.getExtractedRecordCount());

        this.b.addExtractedRecord(other);
        assertTrue("Expected extracted records", this.b.hasExtractedRecords());
        assertEquals("Expected three extracted record", 3, this.b.getExtractedRecords().size());
        assertEquals("Expected three extracted record", 3, this.b.getExtractedRecordCount());

        this.b.clearExtractedRecords();
        assertFalse("Expected no extracted records", this.b.hasExtractedRecords());
        assertNull(this.b.getExtractedRecords());

        final List<IBaseDataObject> list = new ArrayList<IBaseDataObject>();
        list.add(new BaseDataObject());
        list.add(null);

        try {
            this.b.setExtractedRecords(list);
            fail("Should not be able to use a list with null elements");
        } catch (IllegalArgumentException expected) {
        }

        try {
            this.b.addExtractedRecords(list);
            fail("Should not be able to use a list with null elements");
        } catch (IllegalArgumentException expected) {
        }

        list.remove(1);

        try {
            this.b.addExtractedRecords(null);
            fail("Should fail to add null list");
        } catch (IllegalArgumentException expected) {
        }

        this.b.addExtractedRecords(list);
        assertEquals("Expect one extracted record", 1, this.b.getExtractedRecordCount());

        this.b.addExtractedRecords(list);
        assertEquals("Expect two extracted record", 2, this.b.getExtractedRecordCount());

        final List<ExtendedDataObject> elist = new ArrayList<ExtendedDataObject>();
        elist.add(new ExtendedDataObject());
        elist.add(new ExtendedDataObject());

        this.b.addExtractedRecords(elist);
        assertEquals("Expected extended records to be added", 4, this.b.getExtractedRecordCount());

    }

    @Test
    public void testSetCurrentFormWithBoolean() {
        IBaseDataObject testIbdo = DataObjectFactory.getInstance(null, "dummy", "FORM-1");
        testIbdo.enqueueCurrentForm("FORM-2");
        testIbdo.enqueueCurrentForm("FORM-3");

        assertEquals("Form stack should have 3 forms before test", 3, testIbdo.currentFormSize());
        testIbdo.setCurrentForm("FINAL-FORM", true);
        assertEquals("Form stack should have been cleared except for final set form", 1, testIbdo.currentFormSize());
        assertEquals("Form should be set to FINAL-FORM", "FINAL-FORM", testIbdo.currentForm());

        testIbdo.enqueueCurrentForm("FINAL-FORM-2");
        testIbdo.setCurrentForm("FINAL-FORM-3", false);
        assertEquals("Form stack should be 2, since we didnt clear the entire stack", 2, testIbdo.currentFormSize());
        assertEquals("Top of form stack should be form 3", "FINAL-FORM-3", testIbdo.currentFormAt(0));
        assertEquals("2nd form in stack should be form 2", "FINAL-FORM-2", testIbdo.currentFormAt(1));
    }

    @Test
    public void testExtractedRecordClone() {

        final List<IBaseDataObject> list = new ArrayList<IBaseDataObject>();
        list.add(new BaseDataObject());
        this.b.addExtractedRecords(list);

        try {
            assertEquals("Cloned IBDO should have same sized extracted record list", this.b.getExtractedRecordCount(), this.b.clone()
                    .getExtractedRecordCount());
        } catch (CloneNotSupportedException ex) {
            fail("Clone method should have been called not " + ex.getMessage());
        }
    }
}
