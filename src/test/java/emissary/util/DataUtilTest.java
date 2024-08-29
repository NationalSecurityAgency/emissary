package emissary.util;

import emissary.core.DataObjectFactory;
import emissary.core.Form;
import emissary.core.IBaseDataObject;
import emissary.id.WorkUnit;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataUtilTest extends UnitTest {

    @Test
    void testPushDedupedForm() {
        final IBaseDataObject d = DataObjectFactory.getInstance();
        final String myform = "FOO";
        DataUtil.pushDedupedForm(d, myform);
        assertTrue(d.getAllCurrentForms().contains(myform), "failed to add " + myform);
        assertEquals(1, d.getAllCurrentForms().size(), "wrong number of forms");
        // now make sure adding again doesn't create dupe
        DataUtil.pushDedupedForm(d, myform);
        assertTrue(d.getAllCurrentForms().contains(myform), "failed to add " + myform);
        assertEquals(1, d.getAllCurrentForms().size(), "wrong number of forms");
    }

    @Test
    void testPushDedupedForms() {
        final IBaseDataObject d = DataObjectFactory.getInstance();
        // this has a duplicate value
        final List<String> myforms = Arrays.asList("FOO", "FOO", "BAR");
        DataUtil.pushDedupedForms(d, myforms);
        assertTrue(d.getAllCurrentForms().contains("FOO"), "failed to add FOO");
        assertTrue(d.getAllCurrentForms().contains("BAR"), "failed to add BAR");
        assertEquals(2, d.getAllCurrentForms().size(), "wrong number of forms");
    }

    @Test
    void testEmptyData() {
        final byte[] empty = {};
        final IBaseDataObject d1 = DataObjectFactory.getInstance();
        d1.setData(empty);
        assertTrue(DataUtil.isEmpty(d1), "empty: " + d1);
        assertFalse(DataUtil.isNotEmpty(d1), "not empty: " + d1);

        final byte[] blankSpace = {' '};
        final byte[] blankSpaces = {' ', ' '};
        final byte[] control = {' ', ByteUtil.ASCII_DEL, ' '};
        final byte[] foo = {'f', 'o', '1'};
        final byte[] blankNum = {' ', ' ', '1'};
        final byte[] blankNumControl = {' ', ' ', '1', ByteUtil.ASCII_ESC};
        final byte[] W = "Президент Буш".getBytes();
        for (final byte[] bytes : Arrays.asList(blankSpace, blankSpaces, control, foo, blankNum, blankNumControl, W)) {
            assertFalse(DataUtil.isEmpty(bytes), "empty: " + Arrays.toString(bytes));
            final IBaseDataObject d2 = DataObjectFactory.getInstance();
            d2.setData(bytes);
            assertFalse(DataUtil.isEmpty(d2), "empty: " + d2);
            assertTrue(DataUtil.isNotEmpty(d2), "empty: " + d2);
        }
    }

    @Test
    void testEmptyWorkUnit() {
        assertTrue(DataUtil.isEmpty(new WorkUnit()), "Empty work unit is empty");
        assertFalse(DataUtil.isEmpty(new WorkUnit("foo", "abc".getBytes(), Form.UNKNOWN)), "Work unit is not empty");
    }

    @Test
    void testSetEmpty() {
        final IBaseDataObject d = DataObjectFactory.getInstance();
        DataUtil.setEmptySession(d);
        assertEquals(d.currentForm(), d.getFileType(), "Session must be set empty");
    }

    @Test
    void testCsvEscape() {
        assertEquals("foo", DataUtil.csvescape("foo"), "Doesn't need escaping");
        assertEquals("\"foo,bar\"", DataUtil.csvescape("foo,bar"), "Comma escaping");
        assertEquals("foo bar", DataUtil.csvescape("foo\nbar"), "CR escaping");
        assertEquals("foo bar", DataUtil.csvescape("foo\rbar"), "LF escaping");
        assertEquals("\"foo\"\"bar\"", DataUtil.csvescape("foo\"bar"), "DQ escaping");
    }

    @Test
    void testSetCurrentFormAndFiletype() {
        final IBaseDataObject d = DataObjectFactory.getInstance(null, "foo", "UNKNOWN");
        DataUtil.setCurrentFormAndFiletype(d, "PETERPAN");
        assertEquals("PETERPAN", d.currentForm(), "Set current form");
        assertEquals("PETERPAN", d.getFileType(), "Set file type");
    }
}
