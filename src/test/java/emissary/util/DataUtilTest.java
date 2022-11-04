package emissary.util;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;

import emissary.core.DataObjectFactory;
import emissary.core.Form;
import emissary.core.IBaseDataObject;
import emissary.id.WorkUnit;
import emissary.test.core.junit5.UnitTest;
import org.junit.jupiter.api.Test;

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
        final Collection<String> myforms = Arrays.asList("FOO", "FOO", "BAR");
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

        final byte[] whitespace = {' '};
        final byte[] whitespaces = {' ', ' '};
        final byte[] control = {' ', ByteUtil.Ascii_DEL, ' '};
        final byte[] foo = {'f', 'o', '1'};
        final byte[] whiten = {' ', ' ', '1'};
        final byte[] whitencontrol = {' ', ' ', '1', ByteUtil.Ascii_ESC};
        final byte[] W = "Президент Буш".getBytes(UTF_8);
        for (final byte[] bytes : Arrays.asList(whitespace, whitespaces, control, foo, whiten, whitencontrol, W)) {
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
        assertFalse(DataUtil.isEmpty(new WorkUnit("foo", "abc".getBytes(UTF_8), Form.UNKNOWN)), "Work unit is not empty");
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

    @SuppressWarnings("deprecation")
    @Test
    void testGetEventDate() {
        // TODO: fix up deprecated methods
        final IBaseDataObject d = DataObjectFactory.getInstance();
        final Calendar now = DataUtil.getCal(new Date());
        Calendar dcal = DataUtil.getEventDate(d);
        assertEquals(TimeUtil.getDateAsPath(now.getTime()), TimeUtil.getDateAsPath(dcal.getTime()), "Default eventDate is now");

        d.putParameter("FILE_DATE", "2013-01-01 12:34:56");
        dcal = DataUtil.getEventDate(d);
        assertEquals("2013-01-01/12/30", TimeUtil.getDateAsPath(dcal.getTime()), "FILE_DATE is used when present");

        d.putParameter("EventDate", "2012-01-01 12:34:56");
        dcal = DataUtil.getEventDate(d);
        assertEquals("2012-01-01/12/30", TimeUtil.getDateAsPath(dcal.getTime()), "EventDate is used when present");

        d.setParameter("EventDate", "ArmyBoots");
        dcal = DataUtil.getEventDate(d);
        assertEquals(TimeUtil.getDateAsPath(now.getTime()), TimeUtil.getDateAsPath(dcal.getTime()), "Now is used when field is invalid");
    }

    @Test
    void testSetCurrentFormAndFiletype() {
        final IBaseDataObject d = DataObjectFactory.getInstance(null, "foo", "UNKNOWN");
        DataUtil.setCurrentFormAndFiletype(d, "PETERPAN");
        assertEquals("PETERPAN", d.currentForm(), "Set current form");
        assertEquals("PETERPAN", d.getFileType(), "Set file type");
    }
}
