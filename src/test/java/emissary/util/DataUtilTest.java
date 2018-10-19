package emissary.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;

import emissary.core.DataObjectFactory;
import emissary.core.Form;
import emissary.core.IBaseDataObject;
import emissary.id.WorkUnit;
import emissary.test.core.UnitTest;
import org.junit.Test;

public class DataUtilTest extends UnitTest {

    @Test
    public void testPushDedupedForm() {
        final IBaseDataObject d = DataObjectFactory.getInstance();
        final String myform = "FOO";
        DataUtil.pushDedupedForm(d, myform);
        assertTrue("failed to add " + myform, d.getAllCurrentForms().contains(myform));
        assertEquals("wrong number of forms", 1, d.getAllCurrentForms().size());
        // now make sure adding again doesn't create dupe
        DataUtil.pushDedupedForm(d, myform);
        assertTrue("failed to add " + myform, d.getAllCurrentForms().contains(myform));
        assertEquals("wrong number of forms", 1, d.getAllCurrentForms().size());
    }

    @Test
    public void testPushDedupedForms() {
        final IBaseDataObject d = DataObjectFactory.getInstance();
        // this has a duplicate value
        final Collection<String> myforms = Arrays.asList("FOO", "FOO", "BAR");
        DataUtil.pushDedupedForms(d, myforms);
        assertTrue("failed to add FOO", d.getAllCurrentForms().contains("FOO"));
        assertTrue("failed to add BAR", d.getAllCurrentForms().contains("BAR"));
        assertEquals("wrong number of forms", 2, d.getAllCurrentForms().size());
    }

    @Test
    public void testEmptyData() {
        final byte[] empty = {};
        final IBaseDataObject d1 = DataObjectFactory.getInstance();
        d1.setData(empty);
        assertTrue("empty: " + d1, DataUtil.isEmpty(d1));
        assertFalse("not empty: " + d1, DataUtil.isNotEmpty(d1));

        final byte[] whitespace = {' '};
        final byte[] whitespaces = {' ', ' '};
        final byte[] control = {' ', ByteUtil.Ascii_DEL, ' '};
        final byte[] foo = {'f', 'o', '1'};
        final byte[] whiten = {' ', ' ', '1'};
        final byte[] whitencontrol = {' ', ' ', '1', ByteUtil.Ascii_ESC};
        final byte[] W = "Президент Буш".getBytes();
        for (final byte[] bytes : Arrays.asList(whitespace, whitespaces, control, foo, whiten, whitencontrol, W)) {
            assertFalse("empty: " + bytes, DataUtil.isEmpty(bytes));
            final IBaseDataObject d2 = DataObjectFactory.getInstance();
            d2.setData(bytes);
            assertFalse("empty: " + d2, DataUtil.isEmpty(d2));
            assertTrue("empty: " + d2, DataUtil.isNotEmpty(d2));
        }
    }

    @Test
    public void testEmptyWorkUnit() {
        assertTrue("Empty work unit is empty", DataUtil.isEmpty(new WorkUnit()));
        assertFalse("Work unit is not empty", DataUtil.isEmpty(new WorkUnit("foo", "abc".getBytes(), Form.UNKNOWN)));
    }

    @Test
    public void testSetEmpty() {
        final IBaseDataObject d = DataObjectFactory.getInstance();
        DataUtil.setEmptySession(d);
        assertEquals("Session must be set empty", d.currentForm(), d.getFileType());
    }

    @Test
    public void testCsvEscape() {
        assertEquals("Doesn't need escaping", "foo", DataUtil.csvescape("foo"));
        assertEquals("Comma escaping", "\"foo,bar\"", DataUtil.csvescape("foo,bar"));
        assertEquals("CR escaping", "foo bar", DataUtil.csvescape("foo\nbar"));
        assertEquals("LF escaping", "foo bar", DataUtil.csvescape("foo\rbar"));
        assertEquals("DQ escaping", "\"foo\"\"bar\"", DataUtil.csvescape("foo\"bar"));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testGetEventDate() {
        // TODO: fix up deprecated methods
        final IBaseDataObject d = DataObjectFactory.getInstance();
        final Calendar now = DataUtil.getCal(new Date());
        Calendar dcal = DataUtil.getEventDate(d);
        assertEquals("Default eventDate is now", TimeUtil.getDateAsPath(now.getTime()), TimeUtil.getDateAsPath(dcal.getTime()));

        d.putParameter("FILE_DATE", "2013-01-01 12:34:56");
        dcal = DataUtil.getEventDate(d);
        assertEquals("FILE_DATE is used when present", "2013-01-01/12/30", TimeUtil.getDateAsPath(dcal.getTime()));

        d.putParameter("EventDate", "2012-01-01 12:34:56");
        dcal = DataUtil.getEventDate(d);
        assertEquals("EventDate is used when present", "2012-01-01/12/30", TimeUtil.getDateAsPath(dcal.getTime()));

        d.setParameter("EventDate", "ArmyBoots");
        dcal = DataUtil.getEventDate(d);
        assertEquals("Now is used when field is invalid", TimeUtil.getDateAsPath(now.getTime()), TimeUtil.getDateAsPath(dcal.getTime()));
    }

    @Test
    public void testSetCurrentFormAndFiletype() {
        final IBaseDataObject d = DataObjectFactory.getInstance(null, "foo", "UNKNOWN");
        DataUtil.setCurrentFormAndFiletype(d, "PETERPAN");
        assertEquals("Set current form", "PETERPAN", d.currentForm());
        assertEquals("Set file type", "PETERPAN", d.getFileType());
    }
}
