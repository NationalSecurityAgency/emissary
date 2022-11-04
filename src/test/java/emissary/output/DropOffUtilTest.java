package emissary.output;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import emissary.test.core.junit5.UnitTest;
import emissary.util.TimeUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for DropOffUtil
 */
class DropOffUtilTest extends UnitTest {
    private DropOffUtil util = null;
    private IBaseDataObject payload = null;

    @BeforeEach
    public void createUtil() {
        final Configurator cfg = new ServiceConfigGuide();
        final List<String> dates = new ArrayList<>();
        dates.add("EventDate");
        dates.add("FILE_DATE");
        cfg.addEntries("DATE_PARAMETER", dates);

        final List<String> ids = new ArrayList<>();
        ids.add("MY_ID");
        ids.add("SHORTNAME");
        ids.add("AUTO_GENERATED_ID");
        cfg.addEntries("ID", ids);

        cfg.addEntry("DATE_FORMAT", "E, d MMM yyyy HH:mm:ss Z");

        cfg.addEntry("AUTO_GENERATED_ID_PREFIX", "ABCDEFGH");
        cfg.addEntry("PARENT_PARAM", "FOO");

        this.util = new DropOffUtil(cfg);
        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(UTF_8), "/eat/prefix/testPath", "UNKNOWN");
    }

    @AfterEach
    public void destroyUtil() throws Exception {
        super.tearDown();
        this.util = null;
        this.payload = null;
    }

    @Test
    void testGeneratedFilenameUniqueness() {
        final Set<String> names = new HashSet<>();
        for (int i = 0; i < 10000; i++) {
            names.add(this.util.generateBuildFileName());
        }
        assertEquals(10000, names.size(), "Names must all be unique");
    }

    @Test
    void testGetBestId() {
        // Test auto gen //////////////////////////////
        final IBaseDataObject tld = DataObjectFactory.getInstance("This is another test".getBytes(UTF_8), "/eat/prefix/anotherTestPath", "UNKNOWN");
        Configurator cfg = new ServiceConfigGuide();
        final List<String> dates = new ArrayList<>();
        dates.add("EventDate");
        dates.add("FILE_DATE");
        cfg.addEntries("DATE_FORMAT", dates);

        List<String> ids = new ArrayList<>();
        ids.add("AUTO_GENERATED_ID");
        cfg.addEntries("ID", ids);

        cfg.addEntry("DATE_FORMAT", "E, d MMM yyyy HH:mm:ss Z");

        cfg.addEntry("AUTO_GENERATED_ID_PREFIX", "ABCDEFGH");

        this.util = new DropOffUtil(cfg);
        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(UTF_8), "/eat/prefix/testPath", "UNKNOWN");
        String id = this.util.getBestId(this.payload, tld);
        assertTrue(id.startsWith("ABCD"), "auto gen id should start with a (truncated to 4 char) prefix");
        assertEquals("yes", this.payload.getStringParameter("AUTO_GENERATED_ID"), "an auto gen id parameter should have been set");

        // Test auto gen without prefix //////////////////////////////

        cfg.removeEntry("AUTO_GENERATED_ID_PREFIX", "ABCDEFGH");

        this.util = new DropOffUtil(cfg);
        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(UTF_8), "/eat/prefix/testPath", "UNKNOWN");
        id = this.util.getBestId(this.payload, tld);
        assertEquals("yes", this.payload.getStringParameter("AUTO_GENERATED_ID"), "an auto gen id parameter should have been set");

        // Test get ID from parameter //////////////////////////////
        cfg = new ServiceConfigGuide();

        ids = new ArrayList<>();
        ids.add("MY_ID");
        ids.add("SHORTNAME");
        ids.add("AUTO_GENERATED_ID");
        cfg.addEntries("ID", ids);

        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(UTF_8), "/eat/prefix/testPath", "UNKNOWN");
        this.payload.setParameter("MY_ID", "672317892139");
        this.util = new DropOffUtil(cfg);
        id = this.util.getBestId(this.payload, tld);

        assertNull(this.payload.getStringParameter("AUTO_GENERATED_ID"), "an auto gen id parameter should NOT have been set");
        assertEquals("672317892139", id, "the MY_ID parameter should have been used");

        // Test shortname //////////////////////////////
        cfg = new ServiceConfigGuide();

        ids = new ArrayList<>();
        ids.add("SHORTNAME");
        ids.add("AUTO_GENERATED_ID");
        cfg.addEntries("ID", ids);

        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(UTF_8), "/eat/prefix/testPath", "UNKNOWN");

        this.util = new DropOffUtil(cfg);
        id = this.util.getBestId(this.payload, tld);

        assertNull(this.payload.getStringParameter("AUTO_GENERATED_ID"), "an auto gen id parameter should NOT have been set");
        assertEquals("testPath", id, "the SHORTNAME parameter should have been used");

        // Test force auto gen by specifying a blank shortname
        // //////////////////////////////

        cfg = new ServiceConfigGuide();

        ids = new ArrayList<>();
        ids.add("SHORTNAME");
        cfg.addEntries("ID", ids);

        cfg.addEntry("AUTO_GENERATED_ID_PREFIX", "ABCDEFGH");

        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(UTF_8), "", "UNKNOWN");// shortname is blank

        this.util = new DropOffUtil(cfg);
        id = this.util.getBestId(this.payload, tld);

        assertEquals("yes", this.payload.getStringParameter("AUTO_GENERATED_ID"), "an auto gen id parameter should have been set");
        assertNotEquals("testPath", id, "the SHORTNAME parameter should NOT have been used");
        assertTrue(id.startsWith("ABCD"), "auto gen id should start with a (truncated to 4 char) prefix");

        // Test force auto gen by specifying nothing
        // //////////////////////////////

        cfg = new ServiceConfigGuide();

        ids = new ArrayList<>();
        cfg.addEntries("ID", ids);

        cfg.addEntry("AUTO_GENERATED_ID_PREFIX", "ABCDEFGH");

        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(UTF_8), "", "UNKNOWN");// shortname is blank

        this.util = new DropOffUtil(cfg);
        id = this.util.getBestId(this.payload, tld);

        assertEquals("yes", this.payload.getStringParameter("AUTO_GENERATED_ID"), "an auto gen id parameter should have been set");
        assertNotEquals("testPath", id, "the SHORTNAME parameter should NOT have been used");
        assertTrue(id.startsWith("ABCD"), "auto gen id should start with a (truncated to 4 char) prefix");
    }

    @Test
    void testGetBestIdFrom() {
        // Test auto gen //////////////////////////////
        Configurator cfg = new ServiceConfigGuide();
        final List<String> dates = new ArrayList<>();
        dates.add("EventDate");
        dates.add("FILE_DATE");
        cfg.addEntries("DATE_FORMAT", dates);

        List<String> ids = new ArrayList<>();
        ids.add("AUTO_GENERATED_ID");
        cfg.addEntries("ID", ids);

        cfg.addEntry("DATE_FORMAT", "E, d MMM yyyy HH:mm:ss Z");

        cfg.addEntry("AUTO_GENERATED_ID_PREFIX", "ABCDEFGH");

        this.util = new DropOffUtil(cfg);
        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(UTF_8), "/eat/prefix/testPath", "UNKNOWN");
        String id = this.util.getBestIdFrom(this.payload);
        assertTrue(id.startsWith("ABCD"), "auto gen id should start with a (truncated to 4 char) prefix");
        assertEquals("yes", this.payload.getStringParameter("AUTO_GENERATED_ID"), "an auto gen id parameter should have been set");

        // Test auto gen without prefix //////////////////////////////

        cfg.removeEntry("AUTO_GENERATED_ID_PREFIX", "ABCDEFGH");

        this.util = new DropOffUtil(cfg);
        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(UTF_8), "/eat/prefix/testPath", "UNKNOWN");
        id = this.util.getBestIdFrom(this.payload);
        assertFalse(id.startsWith("ABCD"), "auto gen id should NOT start with a (truncated to 4 char) prefix");
        assertEquals("yes", this.payload.getStringParameter("AUTO_GENERATED_ID"), "an auto gen id parameter should have been set");

        // Test get ID from parameter //////////////////////////////
        cfg = new ServiceConfigGuide();

        ids = new ArrayList<>();
        ids.add("MY_ID");
        ids.add("SHORTNAME");
        ids.add("AUTO_GENERATED_ID");
        cfg.addEntries("ID", ids);

        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(UTF_8), "/eat/prefix/testPath", "UNKNOWN");
        this.payload.setParameter("MY_ID", "672317892139");
        this.util = new DropOffUtil(cfg);
        id = this.util.getBestIdFrom(this.payload);

        assertNull(this.payload.getStringParameter("AUTO_GENERATED_ID"), "an auto gen id parameter should NOT have been set");
        assertEquals("672317892139", id, "the MY_ID parameter should have been used");

        // Test shortname //////////////////////////////
        cfg = new ServiceConfigGuide();

        ids = new ArrayList<>();
        ids.add("SHORTNAME");
        ids.add("AUTO_GENERATED_ID");
        cfg.addEntries("ID", ids);

        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(UTF_8), "/eat/prefix/testPath", "UNKNOWN");

        this.util = new DropOffUtil(cfg);
        id = this.util.getBestIdFrom(this.payload);

        assertNull(this.payload.getStringParameter("AUTO_GENERATED_ID"), "an auto gen id parameter should NOT have been set");
        assertEquals("testPath", id, "the SHORTNAME parameter should have been used");

        // Test force auto gen by specifying a blank shortname
        // //////////////////////////////

        cfg = new ServiceConfigGuide();

        ids = new ArrayList<>();
        ids.add("SHORTNAME");
        cfg.addEntries("ID", ids);

        cfg.addEntry("AUTO_GENERATED_ID_PREFIX", "ABCDEFGH");

        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(UTF_8), "", "UNKNOWN");// shortname is blank

        this.util = new DropOffUtil(cfg);
        id = this.util.getBestIdFrom(this.payload);

        assertEquals("yes", this.payload.getStringParameter("AUTO_GENERATED_ID"), "an auto gen id parameter should have been set");
        assertNotEquals("testPath", id, "the SHORTNAME parameter should NOT have been used");
        assertTrue(id.startsWith("ABCD"), "auto gen id should start with a (truncated to 4 char) prefix");

        // Test force auto gen by specifying nothing
        // //////////////////////////////

        cfg = new ServiceConfigGuide();

        ids = new ArrayList<>();
        cfg.addEntries("ID", ids);

        cfg.addEntry("AUTO_GENERATED_ID_PREFIX", "ABCDEFGH");

        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(UTF_8), "", "UNKNOWN");// shortname is blank

        this.util = new DropOffUtil(cfg);
        id = this.util.getBestIdFrom(this.payload);

        assertEquals("yes", this.payload.getStringParameter("AUTO_GENERATED_ID"), "an auto gen id parameter should have been set");
        assertNotEquals("testPath", id, "the SHORTNAME parameter should NOT have been used");
        assertTrue(id.startsWith("ABCD"), "auto gen id should start with a (truncated to 4 char) prefix");
    }

    @Test
    void testGetExistingIds() {
        // Test get ID from parameter //////////////////////////////
        Configurator cfg = new ServiceConfigGuide();
        cfg.addEntry("AUTO_GENERATED_ID_PREFIX", "ABCDEFGH");

        List<String> ids = new ArrayList<>();
        ids.add("MY_ID");
        ids.add("SHORTNAME");
        ids.add("AUTO_GENERATED_ID");
        cfg.addEntries("ID", ids);

        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(UTF_8), "/eat/prefix/testPath", "UNKNOWN");
        this.payload.setParameter("MY_ID", "672317892139");
        this.util = new DropOffUtil(cfg);
        String[] values = this.util.getExistingIds(this.payload);

        assertNull(this.payload.getStringParameter("AUTO_GENERATED_ID"), "an auto gen id parameter should not have been set");
        assertEquals("672317892139", values[0], "the MY_ID parameter should have been used FIRST");
        assertEquals("testPath", values[1], "the SHORTNAME should have been used SECOND");
        assertEquals(2, values.length, "the size of the return values is incorrect");

        // Test empty return value by specifying a blank shortname

        cfg = new ServiceConfigGuide();

        ids = new ArrayList<>();
        ids.add("SHORTNAME");
        cfg.addEntries("ID", ids);

        cfg.addEntry("AUTO_GENERATED_ID_PREFIX", "ABCDEFGH");

        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(UTF_8), "", "UNKNOWN");// shortname is blank

        this.util = new DropOffUtil(cfg);
        values = this.util.getExistingIds(this.payload);

        assertEquals(0, values.length, "the size of the return values is incorrect");

        // Test empty values by specifying nothing

        cfg = new ServiceConfigGuide();

        ids = new ArrayList<>();
        cfg.addEntries("ID", ids);

        cfg.addEntry("AUTO_GENERATED_ID_PREFIX", "ABCDEFGH");

        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(UTF_8), "", "UNKNOWN");// shortname is blank

        this.util = new DropOffUtil(cfg);
        values = this.util.getExistingIds(this.payload);

        assertEquals(0, values.length, "the size of the return values is incorrect");
    }

    @Test
    void testMetadataPreparation() {
        final List<IBaseDataObject> family = new ArrayList<>();

        final IBaseDataObject parent = DataObjectFactory.getInstance("This is a test".getBytes(UTF_8), "item1", "PARENT_FORM", "PARENT_FTYPE");
        parent.putParameter("FOO", "PARENT_FOO");
        parent.putParameter("Original-Filename", "parent.tar.gz");

        final IBaseDataObject child = DataObjectFactory.getInstance("This is a test".getBytes(UTF_8), "item1-att-1", "CHILD_FORM", "CHILD_FTYPE");
        child.putParameter("Original-Filename", "child.docx");
        child.putParameter("FOO_FILETYPE", "myFoo");
        child.putParameter("BAR_FILETYPE", "myBar1");
        child.appendParameter("BAR_FILETYPE", "myBar2");
        child.putParameter("QUUX_FILETYPE", "myFoo");
        child.putParameter("FOO", "CHILD_FOO");

        family.add(parent);
        family.add(child);

        this.util.processMetadata(family);

        assertEquals("gz", parent.getStringParameter("FILEXT"), "Parent should have filext of gz");
        assertEquals("docx", child.getStringParameter("FILEXT"), "Child should have filext of docx");

        assertNull(parent.getParameter("EXTENDED_FILETYPE"), "Parent should not have extended filetype");

        assertEquals("CHILD_FTYPE//myFoo//myBar1//myBar2",
                child.getStringParameter("EXTENDED_FILETYPE"),
                "Child EXTENDED_FILETYPE should handle deduplication and collections");


        assertNull(parent.getParameter("PARENT_FOO"), "Parent should not get PARENT_* values");
        assertEquals("PARENT_FOO", child.getStringParameter("PARENT_FOO"), "Child should get PARENT_FOO type");
        assertEquals("PARENT_FOO", parent.getStringParameter("FOO"), "Parent FOO should not be changed");
        assertEquals("CHILD_FOO", child.getStringParameter("FOO"), "Child FOO should not be changed");
    }

    @Test
    void testMetadataPreparationWithConfiguredValues() {
        final List<IBaseDataObject> family = new ArrayList<>();

        final IBaseDataObject parent = DataObjectFactory.getInstance(new byte[0], "item", "PARENT_FORM");
        parent.setFileType("PARENT_FTYPE");
        parent.putParameter("FOO", "FOO");

        final IBaseDataObject child = DataObjectFactory.getInstance(new byte[0], "item-att-1", "CHILD_FORM");
        child.putParameter("FOO", "CHILD_FOO");

        final List<IBaseDataObject> childRecords = new ArrayList<>();
        for (int i = 1; i < 5; i++) {
            childRecords.add(DataObjectFactory.getInstance(new byte[0], "item-att-1-att-" + i, "RECORD_FORM"));
        }
        child.addExtractedRecords(childRecords);

        final IBaseDataObject child2 = DataObjectFactory.getInstance(new byte[0], "item-att-2", "CHILD2_FORM");
        // child2 does not have a FOO param
        final IBaseDataObject grandchild = DataObjectFactory.getInstance(new byte[0], "item-att-2-att-1", "GRANDCHILD_FORM");

        final List<IBaseDataObject> gchildRecords = new ArrayList<>();
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
        assertEquals("FOO",
                child.getStringParameter("PARENT_FOO"),
                "Propagation of configured parent type must use closest available entry on child");
        assertEquals("CHILD_FOO", childRecords.get(0)
                .getStringParameter("PARENT_FOO"), "Propagation of configured parent type must use closest available entry on child records");

        // Child 2 subtree only has the TLD FOO param to use
        assertEquals("FOO", child2.getStringParameter("PARENT_FOO"), "Propagation of configured parent type must fall back to TLD on child");
        assertEquals("FOO", grandchild.getStringParameter("PARENT_FOO"), "Propagation of configured parent type must fall back to TLD on grandchild");
        assertEquals("FOO", gchildRecords.get(0)
                .getStringParameter("PARENT_FOO"), "Propagation of configured parent type must use closest available entry on grandchild records");
    }

    @Test
    void testGetEventDate() {
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
        final List<String> dates = new ArrayList<>();
        dates.add("EventDate");
        dates.add("FILE_DATE");
        cfg.addEntries("DATE_PARAMETER", dates);
        cfg.addEntry("DEFAULT_EVENT_DATE_TO_NOW", "false");
        this.util = new DropOffUtil(cfg);

        assertNull(this.util.getEventDate(d, tld));
    }

    @Deprecated
    @Test
    void testDeprecatedGetFileType() {
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
    void testGetFileType() {
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

    @Test
    void testExtractUniqueFileExtensions() {
        // these should be constants
        final String FILEXT = "FILEXT";
        final String ORIGINAL_FILENAME = "Original-Filename";
        DropOffUtil util = new DropOffUtil();

        final IBaseDataObject bdo = new BaseDataObject();
        bdo.appendParameter(ORIGINAL_FILENAME, "name_with_no_period");

        util.extractUniqueFileExtensions(bdo);

        List<Object> fileExts = bdo.getParameter(FILEXT);
        assertNull(fileExts, "FILEXT value should be null if no Original-Filename contains a period");

        bdo.appendParameter(ORIGINAL_FILENAME, "lower.case.zip");
        bdo.appendParameter(ORIGINAL_FILENAME, "UPPER.CASE.MP3");
        bdo.appendParameter(ORIGINAL_FILENAME, "duplicated.mp3");
        assertEquals(4, bdo.getParameter(ORIGINAL_FILENAME).size(), "bdo should now have 4 Original-Filename values");

        util.extractUniqueFileExtensions(bdo);
        fileExts = bdo.getParameter(FILEXT);

        // validate extracted FILEXT values
        assertEquals(2, fileExts.size(), "bdo should now have 2 FILEXT values ");
        assertTrue(fileExts.contains("zip"), "FILEXT values should contain \"zip\"");
        assertTrue(fileExts.contains("mp3"), "FILEXT values should contain \"mp3\"");
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
