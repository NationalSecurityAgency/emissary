package emissary.output;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.core.BaseDataObject;
import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.output.DropOffUtil.FileTypeCheckParameter;
import emissary.test.core.UnitTest;
import emissary.util.TimeUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for DropOffUtil
 *
 */
public class DropOffUtilTest extends UnitTest {
    private DropOffUtil util = null;
    private IBaseDataObject payload = null;

    @Before
    public void createUtil() {
        final Configurator cfg = new ServiceConfigGuide();
        final List<String> dates = new ArrayList<String>();
        dates.add("EventDate");
        dates.add("FILE_DATE");
        cfg.addEntries("DATE_PARAMETER", dates);

        final List<String> ids = new ArrayList<String>();
        ids.add("MY_ID");
        ids.add("SHORTNAME");
        ids.add("AUTO_GENERATED_ID");
        cfg.addEntries("ID", ids);

        cfg.addEntry("DATE_FORMAT", "E, d MMM yyyy HH:mm:ss Z");

        cfg.addEntry("AUTO_GENERATED_ID_PREFIX", "ABCDEFGH");
        cfg.addEntry("PARENT_PARAM", "FOO");

        this.util = new DropOffUtil(cfg);
        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(), "/eat/prefix/testPath", "UNKNOWN");
    }

    @After
    public void destroyUtil() throws Exception {
        super.tearDown();
        this.util = null;
        this.payload = null;
    }

    @Test
    public void testGeneratedFilenameUniqueness() {
        final Set<String> names = new HashSet<String>();
        for (int i = 0; i < 10000; i++) {
            names.add(this.util.generateBuildFileName());
        }
        assertEquals("Names must all be unique", 10000, names.size());
    }

    @Test
    public void testGetBestId() {
        // Test auto gen //////////////////////////////
        final IBaseDataObject tld = DataObjectFactory.getInstance("This is another test".getBytes(), "/eat/prefix/anotherTestPath", "UNKNOWN");
        Configurator cfg = new ServiceConfigGuide();
        final List<String> dates = new ArrayList<String>();
        dates.add("EventDate");
        dates.add("FILE_DATE");
        cfg.addEntries("DATE_FORMAT", dates);

        List<String> ids = new ArrayList<String>();
        ids.add("AUTO_GENERATED_ID");
        cfg.addEntries("ID", ids);

        cfg.addEntry("DATE_FORMAT", "E, d MMM yyyy HH:mm:ss Z");

        cfg.addEntry("AUTO_GENERATED_ID_PREFIX", "ABCDEFGH");

        this.util = new DropOffUtil(cfg);
        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(), "/eat/prefix/testPath", "UNKNOWN");
        String id = this.util.getBestId(this.payload, tld);
        assertTrue("auto gen id should start with a (truncated to 4 char) prefix", id.startsWith("ABCD"));
        assertTrue("an auto gen id parameter should have been set", "yes".equals(this.payload.getStringParameter("AUTO_GENERATED_ID")));

        // Test auto gen without prefix //////////////////////////////

        cfg.removeEntry("AUTO_GENERATED_ID_PREFIX", "ABCDEFGH");

        this.util = new DropOffUtil(cfg);
        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(), "/eat/prefix/testPath", "UNKNOWN");
        id = this.util.getBestId(this.payload, tld);
        assertTrue("an auto gen id parameter should have been set", "yes".equals(this.payload.getStringParameter("AUTO_GENERATED_ID")));

        // Test get ID from parameter //////////////////////////////
        cfg = new ServiceConfigGuide();

        ids = new ArrayList<String>();
        ids.add("MY_ID");
        ids.add("SHORTNAME");
        ids.add("AUTO_GENERATED_ID");
        cfg.addEntries("ID", ids);

        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(), "/eat/prefix/testPath", "UNKNOWN");
        this.payload.setParameter("MY_ID", "672317892139");
        this.util = new DropOffUtil(cfg);
        id = this.util.getBestId(this.payload, tld);

        assertTrue("an auto gen id parameter should NOT have been set", this.payload.getStringParameter("AUTO_GENERATED_ID") == null);
        assertTrue("the MY_ID parameter should have been used", "672317892139".equals(id));

        // Test shortname //////////////////////////////
        cfg = new ServiceConfigGuide();

        ids = new ArrayList<String>();
        ids.add("SHORTNAME");
        ids.add("AUTO_GENERATED_ID");
        cfg.addEntries("ID", ids);

        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(), "/eat/prefix/testPath", "UNKNOWN");

        this.util = new DropOffUtil(cfg);
        id = this.util.getBestId(this.payload, tld);

        assertTrue("an auto gen id parameter should NOT have been set", this.payload.getStringParameter("AUTO_GENERATED_ID") == null);
        assertTrue("the SHORTNAME parameter should have been used", "testPath".equals(id));

        // Test force auto gen by specifying a blank shortname
        // //////////////////////////////

        cfg = new ServiceConfigGuide();

        ids = new ArrayList<String>();
        ids.add("SHORTNAME");
        cfg.addEntries("ID", ids);

        cfg.addEntry("AUTO_GENERATED_ID_PREFIX", "ABCDEFGH");

        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(), "", "UNKNOWN");// shortname is blank

        this.util = new DropOffUtil(cfg);
        id = this.util.getBestId(this.payload, tld);

        assertTrue("an auto gen id parameter should have been set", "yes".equals(this.payload.getStringParameter("AUTO_GENERATED_ID")));
        assertFalse("the SHORTNAME parameter should NOT have been used", "testPath".equals(id));
        assertTrue("auto gen id should start with a (truncated to 4 char) prefix", id.startsWith("ABCD"));

        // Test force auto gen by specifying nothing
        // //////////////////////////////

        cfg = new ServiceConfigGuide();

        ids = new ArrayList<String>();
        cfg.addEntries("ID", ids);

        cfg.addEntry("AUTO_GENERATED_ID_PREFIX", "ABCDEFGH");

        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(), "", "UNKNOWN");// shortname is blank

        this.util = new DropOffUtil(cfg);
        id = this.util.getBestId(this.payload, tld);

        assertTrue("an auto gen id parameter should have been set", "yes".equals(this.payload.getStringParameter("AUTO_GENERATED_ID")));
        assertFalse("the SHORTNAME parameter should NOT have been used", "testPath".equals(id));
        assertTrue("auto gen id should start with a (truncated to 4 char) prefix", id.startsWith("ABCD"));
    }

    @Test
    public void testGetBestIdFrom() {
        // Test auto gen //////////////////////////////
        Configurator cfg = new ServiceConfigGuide();
        final List<String> dates = new ArrayList<String>();
        dates.add("EventDate");
        dates.add("FILE_DATE");
        cfg.addEntries("DATE_FORMAT", dates);

        List<String> ids = new ArrayList<String>();
        ids.add("AUTO_GENERATED_ID");
        cfg.addEntries("ID", ids);

        cfg.addEntry("DATE_FORMAT", "E, d MMM yyyy HH:mm:ss Z");

        cfg.addEntry("AUTO_GENERATED_ID_PREFIX", "ABCDEFGH");

        this.util = new DropOffUtil(cfg);
        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(), "/eat/prefix/testPath", "UNKNOWN");
        String id = this.util.getBestIdFrom(this.payload);
        assertTrue("auto gen id should start with a (truncated to 4 char) prefix", id.startsWith("ABCD"));
        assertTrue("an auto gen id parameter should have been set", "yes".equals(this.payload.getStringParameter("AUTO_GENERATED_ID")));

        // Test auto gen without prefix //////////////////////////////

        cfg.removeEntry("AUTO_GENERATED_ID_PREFIX", "ABCDEFGH");

        this.util = new DropOffUtil(cfg);
        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(), "/eat/prefix/testPath", "UNKNOWN");
        id = this.util.getBestIdFrom(this.payload);
        assertFalse("auto gen id should NOT start with a (truncated to 4 char) prefix", id.startsWith("ABCD"));
        assertTrue("an auto gen id parameter should have been set", "yes".equals(this.payload.getStringParameter("AUTO_GENERATED_ID")));

        // Test get ID from parameter //////////////////////////////
        cfg = new ServiceConfigGuide();

        ids = new ArrayList<String>();
        ids.add("MY_ID");
        ids.add("SHORTNAME");
        ids.add("AUTO_GENERATED_ID");
        cfg.addEntries("ID", ids);

        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(), "/eat/prefix/testPath", "UNKNOWN");
        this.payload.setParameter("MY_ID", "672317892139");
        this.util = new DropOffUtil(cfg);
        id = this.util.getBestIdFrom(this.payload);

        assertTrue("an auto gen id parameter should NOT have been set", this.payload.getStringParameter("AUTO_GENERATED_ID") == null);
        assertTrue("the MY_ID parameter should have been used", "672317892139".equals(id));

        // Test shortname //////////////////////////////
        cfg = new ServiceConfigGuide();

        ids = new ArrayList<String>();
        ids.add("SHORTNAME");
        ids.add("AUTO_GENERATED_ID");
        cfg.addEntries("ID", ids);

        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(), "/eat/prefix/testPath", "UNKNOWN");

        this.util = new DropOffUtil(cfg);
        id = this.util.getBestIdFrom(this.payload);

        assertTrue("an auto gen id parameter should NOT have been set", this.payload.getStringParameter("AUTO_GENERATED_ID") == null);
        assertTrue("the SHORTNAME parameter should have been used", "testPath".equals(id));

        // Test force auto gen by specifying a blank shortname
        // //////////////////////////////

        cfg = new ServiceConfigGuide();

        ids = new ArrayList<String>();
        ids.add("SHORTNAME");
        cfg.addEntries("ID", ids);

        cfg.addEntry("AUTO_GENERATED_ID_PREFIX", "ABCDEFGH");

        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(), "", "UNKNOWN");// shortname is blank

        this.util = new DropOffUtil(cfg);
        id = this.util.getBestIdFrom(this.payload);

        assertTrue("an auto gen id parameter should have been set", "yes".equals(this.payload.getStringParameter("AUTO_GENERATED_ID")));
        assertFalse("the SHORTNAME parameter should NOT have been used", "testPath".equals(id));
        assertTrue("auto gen id should start with a (truncated to 4 char) prefix", id.startsWith("ABCD"));

        // Test force auto gen by specifying nothing
        // //////////////////////////////

        cfg = new ServiceConfigGuide();

        ids = new ArrayList<String>();
        cfg.addEntries("ID", ids);

        cfg.addEntry("AUTO_GENERATED_ID_PREFIX", "ABCDEFGH");

        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(), "", "UNKNOWN");// shortname is blank

        this.util = new DropOffUtil(cfg);
        id = this.util.getBestIdFrom(this.payload);

        assertTrue("an auto gen id parameter should have been set", "yes".equals(this.payload.getStringParameter("AUTO_GENERATED_ID")));
        assertFalse("the SHORTNAME parameter should NOT have been used", "testPath".equals(id));
        assertTrue("auto gen id should start with a (truncated to 4 char) prefix", id.startsWith("ABCD"));
    }

    @Test
    public void testGetExistingIds() {
        // Test get ID from parameter //////////////////////////////
        Configurator cfg = new ServiceConfigGuide();
        cfg.addEntry("AUTO_GENERATED_ID_PREFIX", "ABCDEFGH");

        List<String> ids = new ArrayList<String>();
        ids.add("MY_ID");
        ids.add("SHORTNAME");
        ids.add("AUTO_GENERATED_ID");
        cfg.addEntries("ID", ids);

        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(), "/eat/prefix/testPath", "UNKNOWN");
        this.payload.setParameter("MY_ID", "672317892139");
        this.util = new DropOffUtil(cfg);
        String[] values = this.util.getExistingIds(this.payload);

        assertTrue("an auto gen id parameter should not have been set", this.payload.getStringParameter("AUTO_GENERATED_ID") == null);
        assertTrue("the MY_ID parameter should have been used FIRST", "672317892139".equals(values[0]));
        assertTrue("the SHORTNAME should have been used SECOND", "testPath".equals(values[1]));
        assertTrue("the size of the return values is incorrect", values.length == 2);

        // Test empty return value by specifying a blank shortname

        cfg = new ServiceConfigGuide();

        ids = new ArrayList<String>();
        ids.add("SHORTNAME");
        cfg.addEntries("ID", ids);

        cfg.addEntry("AUTO_GENERATED_ID_PREFIX", "ABCDEFGH");

        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(), "", "UNKNOWN");// shortname is blank

        this.util = new DropOffUtil(cfg);
        values = this.util.getExistingIds(this.payload);

        assertTrue("the size of the return values is incorrect", values.length == 0);

        // Test empty values by specifying nothing

        cfg = new ServiceConfigGuide();

        ids = new ArrayList<String>();
        cfg.addEntries("ID", ids);

        cfg.addEntry("AUTO_GENERATED_ID_PREFIX", "ABCDEFGH");

        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(), "", "UNKNOWN");// shortname is blank

        this.util = new DropOffUtil(cfg);
        values = this.util.getExistingIds(this.payload);

        assertTrue("the size of the return values is incorrect", values.length == 0);
    }

    @Test
    public void testMetadataPreparation() {
        final List<IBaseDataObject> family = new ArrayList<IBaseDataObject>();

        final IBaseDataObject parent = DataObjectFactory.getInstance("This is a test".getBytes(), "item1", "PARENT_FORM", "PARENT_FTYPE");
        parent.putParameter("FOO", "PARENT_FOO");

        final IBaseDataObject child = DataObjectFactory.getInstance("This is a test".getBytes(), "item1-att-1", "CHILD_FORM", "CHILD_FTYPE");
        child.putParameter("FOO_FILETYPE", "myFoo");
        child.putParameter("BAR_FILETYPE", "myBar1");
        child.appendParameter("BAR_FILETYPE", "myBar2");
        child.putParameter("QUUX_FILETYPE", "myFoo");
        child.putParameter("FOO", "CHILD_FOO");

        family.add(parent);
        family.add(child);

        this.util.processMetadata(family);

        assertNull("Parent should not have extended filetype", parent.getParameter("EXTENDED_FILETYPE"));

        assertEquals("Child EXTENDED_FILETYPE should handle deduplication and collections", "CHILD_FTYPE//myFoo//myBar1//myBar2",
                child.getStringParameter("EXTENDED_FILETYPE"));


        assertNull("Parent should not get PARENT_* values", parent.getParameter("PARENT_FOO"));
        assertEquals("Child should get PARENT_FOO type", "PARENT_FOO", child.getStringParameter("PARENT_FOO"));
        assertEquals("Parent FOO should not be changed", "PARENT_FOO", parent.getStringParameter("FOO"));
        assertEquals("Child FOO should not be changed", "CHILD_FOO", child.getStringParameter("FOO"));
    }

    @Test
    public void testMetadataPreparationWithConfiguredValues() {
        final List<IBaseDataObject> family = new ArrayList<IBaseDataObject>();

        final IBaseDataObject parent = DataObjectFactory.getInstance(new byte[0], "item", "PARENT_FORM");
        parent.setFileType("PARENT_FTYPE");
        parent.putParameter("FOO", "FOO");

        final IBaseDataObject child = DataObjectFactory.getInstance(new byte[0], "item-att-1", "CHILD_FORM");
        child.putParameter("FOO", "CHILD_FOO");

        final List<IBaseDataObject> childRecords = new ArrayList<IBaseDataObject>();
        for (int i = 1; i < 5; i++) {
            childRecords.add(DataObjectFactory.getInstance(new byte[0], "item-att-1-att-" + i, "RECORD_FORM"));
        }
        child.addExtractedRecords(childRecords);

        final IBaseDataObject child2 = DataObjectFactory.getInstance(new byte[0], "item-att-2", "CHILD2_FORM");
        // child2 does not have a FOO param
        final IBaseDataObject grandchild = DataObjectFactory.getInstance(new byte[0], "item-att-2-att-1", "GRANDCHILD_FORM");

        final List<IBaseDataObject> gchildRecords = new ArrayList<IBaseDataObject>();
        for (int i = 1; i < 5; i++) {
            gchildRecords.add(DataObjectFactory.getInstance(new byte[0], "item-att-2-att-1-att-" + i, "GCHILD_RECORD_FORM"));
        }
        grandchild.addExtractedRecords(gchildRecords);

        family.add(parent);
        family.add(child);
        family.add(child2);
        family.add(grandchild);

        this.util.processMetadata(family);

        // Child 1 subtree has a FOO param
        assertEquals("Propagation of configured parent type must use closest available entry on child", "FOO",
                child.getStringParameter("PARENT_FOO"));
        assertEquals("Propagation of configured parent type must use closest available entry on child records", "CHILD_FOO", childRecords.get(0)
                .getStringParameter("PARENT_FOO"));

        // Child 2 subtree only has the TLD FOO param to use
        assertEquals("Propagation of configured parent type must fall back to TLD on child", "FOO", child2.getStringParameter("PARENT_FOO"));
        assertEquals("Propagation of configured parent type must fall back to TLD on grandchild", "FOO", grandchild.getStringParameter("PARENT_FOO"));
        assertEquals("Propagation of configured parent type must use closest available entry on grandchild records", "FOO", gchildRecords.get(0)
                .getStringParameter("PARENT_FOO"));
    }

    @Test
    public void testGetEventDate() {
        // populate the tld with all the event date options (note: year of 2016)
        IBaseDataObject tld = new BaseDataObject();
        tld.setParameter("EventDate", "2016-02-13 23:13:03");
        tld.setParameter("FILE_DATE", "2016-01-05 17:45:53");

        // populate the child with all the event date options (note: year of 2015)
        IBaseDataObject d = new BaseDataObject();
        d.setParameter("EventDate", "2015-02-13 23:13:03");
        d.setParameter("FILE_DATE", "2015-01-05 17:45:53");

        Date start = new Date(); // use this for relative comparison

        // hit on child EventDate
        assertEquals("2015-02-13 23:13:03", TimeUtil.getDateAsISO8601(this.util.getEventDate(d, tld).toInstant()));

        // removing child EventDate should hit on child FILE_DATE
        d.deleteParameter("EventDate");
        assertEquals("2015-01-05 17:45:53", TimeUtil.getDateAsISO8601(this.util.getEventDate(d, tld).toInstant()));

        // removing child FILE_DATE should hit on tld EventDate
        d.deleteParameter("FILE_DATE");
        assertEquals("2016-02-13 23:13:03", TimeUtil.getDateAsISO8601(this.util.getEventDate(d, tld).toInstant()));

        // removing tld EventDate should hit on tld FILE_DATE
        tld.deleteParameter("EventDate");
        assertEquals("2016-01-05 17:45:53", TimeUtil.getDateAsISO8601(this.util.getEventDate(d, tld).toInstant()));

        // removing tld FILE_DATE should default to now as configured by default
        tld.deleteParameter("FILE_DATE");
        assertNotNull(this.util.getEventDate(d, tld));
        assertNotEquals(-1, this.util.getEventDate(d, tld).compareTo(start));

        // changing the configuration to not default to now should return null
        Configurator cfg = new ServiceConfigGuide();
        final List<String> dates = new ArrayList<String>();
        dates.add("EventDate");
        dates.add("FILE_DATE");
        cfg.addEntries("DATE_PARAMETER", dates);
        cfg.addEntry("DEFAULT_EVENT_DATE_TO_NOW", "false");
        this.util = new DropOffUtil(cfg);

        assertNull(this.util.getEventDate(d, tld));
    }

    @Test
    public void testDeprecatedGetFileType() {
        Map<String, String> metadata = new HashMap<>();
        testDeprecatedFileType(metadata, "UNKNOWN", null);

        String poppedForms = "myPoppedForms";
        setupDeprecatedMetadata(metadata, poppedForms, DropOffUtil.FileTypeCheckParameter.POPPED_FORMS);
        testDeprecatedFileType(metadata, poppedForms, null);

        String formsArg = "myFile";
        setupDeprecatedMetadata(metadata, formsArg, DropOffUtil.FileTypeCheckParameter.FILETYPE);
        testDeprecatedFileType(metadata, formsArg, formsArg);

        String finalId = "myFinalId";
        setupDeprecatedMetadata(metadata, finalId, DropOffUtil.FileTypeCheckParameter.FINAL_ID);
        formsArg = "differentFileType";
        testDeprecatedFileType(metadata, finalId, formsArg);

        formsArg = "myFile  ";
        metadata.clear();
        testDeprecatedFileType(metadata, "myFile", formsArg);
        assertEquals(formsArg,
                metadata.get(DropOffUtil.FileTypeCheckParameter.COMPLETE_FILETYPE.getFieldName()));

        formsArg = "";
        String fontEncoding = "fontEncoding";
        setupDeprecatedMetadata(metadata, fontEncoding, DropOffUtil.FileTypeCheckParameter.FONT_ENCODING);
        testDeprecatedFileType(metadata, "TEXT", formsArg);

        metadata.clear();
        formsArg = " MSWORD";
        testDeprecatedFileType(metadata, "MSWORD_FRAGMENT", formsArg);

        metadata.clear();
        formsArg = "QUOTED-PRINTABLE";
        testDeprecatedFileType(metadata, "TEXT", formsArg);
    }

    private void setupDeprecatedMetadata(Map<String, String> metadata, String fieldValue, DropOffUtil.FileTypeCheckParameter fileTypeCheckParameter) {
        metadata.clear();
        metadata.put(fileTypeCheckParameter.getFieldName(), fieldValue);
    }

    private void testDeprecatedFileType(Map<String, String> metadata, String expectedResults, String formsArg) {
        String fileType;
        fileType = util.getFileType(metadata, formsArg);
        assertEquals(expectedResults, fileType);
    }

    @Test
    public void testGetFileType() {
        final IBaseDataObject bdo = new BaseDataObject();

        testFileType(bdo, null, "UNKNOWN", null);

        Map<String, String> metadata = new HashMap<>();
        testFileType(bdo, metadata, "UNKNOWN", null);

        String poppedForms = "myPoppedForms";
        setupMetadata(bdo, poppedForms, DropOffUtil.FileTypeCheckParameter.POPPED_FORMS);
        testFileType(bdo, metadata, poppedForms, null);

        String formsArg = "myFile";
        setupMetadata(bdo, formsArg, DropOffUtil.FileTypeCheckParameter.FILETYPE);
        testFileType(bdo, metadata, formsArg, formsArg);

        String finalId = "myFinalId";
        setupMetadata(bdo, finalId, DropOffUtil.FileTypeCheckParameter.FINAL_ID);
        formsArg = "differentFileType";
        testFileType(bdo, null, finalId, formsArg);
        testFileType(bdo, metadata, finalId, formsArg);

        formsArg = "myFile  ";
        bdo.clearParameters();
        metadata.clear();
        testFileType(bdo, null, "myFile", formsArg);
        testFileType(bdo, metadata, "myFile", formsArg);
        assertEquals(formsArg,
                metadata.get(DropOffUtil.FileTypeCheckParameter.COMPLETE_FILETYPE.getFieldName()));

        formsArg = "";
        String fontEncoding = "fontEncoding";
        setupMetadata(bdo, fontEncoding, DropOffUtil.FileTypeCheckParameter.FONT_ENCODING);
        testFileType(bdo, metadata, "TEXT", formsArg);

        bdo.clearParameters();
        metadata.clear();
        formsArg = " MSWORD";
        testFileType(bdo, metadata, "MSWORD_FRAGMENT", formsArg);

        metadata.clear();
        formsArg = "QUOTED-PRINTABLE";
        testFileType(bdo, metadata, "TEXT", formsArg);
    }

    private void setupMetadata(IBaseDataObject bdo, String fieldValue, DropOffUtil.FileTypeCheckParameter fileTypeCheckParameter) {
        bdo.clearParameters();
        bdo.putParameter(fileTypeCheckParameter.getFieldName(), fieldValue);
    }

    private void testFileType(IBaseDataObject bdo, Map<String, String> metadata, String expectedResults, String formsArg) {
        String fileType;
        fileType = DropOffUtil.getAndPutFileType(bdo, metadata, formsArg);
        assertEquals(expectedResults, fileType);
    }
}
