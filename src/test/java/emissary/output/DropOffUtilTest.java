package emissary.output;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static emissary.core.Form.TEXT;
import static emissary.core.Form.UNKNOWN;
import static emissary.core.constants.Parameters.EVENT_DATE;
import static emissary.core.constants.Parameters.FILE_DATE;
import static emissary.core.constants.Parameters.ORIGINAL_FILENAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        dates.add(EVENT_DATE);
        dates.add(FILE_DATE);
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
        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(), "/eat/prefix/testPath", "UNKNOWN");
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
        final IBaseDataObject tld = DataObjectFactory.getInstance("This is another test".getBytes(), "/eat/prefix/anotherTestPath", "UNKNOWN");
        Configurator cfg = new ServiceConfigGuide();
        final List<String> dates = new ArrayList<>();
        dates.add(EVENT_DATE);
        dates.add(FILE_DATE);
        cfg.addEntries("DATE_FORMAT", dates);

        List<String> ids = new ArrayList<>();
        ids.add("AUTO_GENERATED_ID");
        cfg.addEntries("ID", ids);

        cfg.addEntry("DATE_FORMAT", "E, d MMM yyyy HH:mm:ss Z");

        cfg.addEntry("AUTO_GENERATED_ID_PREFIX", "ABCDEFGH");

        this.util = new DropOffUtil(cfg);
        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(), "/eat/prefix/testPath", "UNKNOWN");
        String id = this.util.getBestId(this.payload, tld);
        assertTrue(id.startsWith("ABCD"), "auto gen id should start with a (truncated to 4 char) prefix");
        assertEquals("yes", this.payload.getStringParameter("AUTO_GENERATED_ID"), "an auto gen id parameter should have been set");

        // Test auto gen without prefix //////////////////////////////

        cfg.removeEntry("AUTO_GENERATED_ID_PREFIX", "ABCDEFGH");

        this.util = new DropOffUtil(cfg);
        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(), "/eat/prefix/testPath", "UNKNOWN");
        id = this.util.getBestId(this.payload, tld);
        assertEquals("yes", this.payload.getStringParameter("AUTO_GENERATED_ID"), "an auto gen id parameter should have been set");

        // Test get ID from parameter //////////////////////////////
        cfg = new ServiceConfigGuide();

        ids = new ArrayList<>();
        ids.add("MY_ID");
        ids.add("SHORTNAME");
        ids.add("AUTO_GENERATED_ID");
        cfg.addEntries("ID", ids);

        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(), "/eat/prefix/testPath", "UNKNOWN");
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

        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(), "/eat/prefix/testPath", "UNKNOWN");

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

        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(), "", "UNKNOWN");// shortname is blank

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

        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(), "", "UNKNOWN");// shortname is blank

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
        dates.add(EVENT_DATE);
        dates.add(FILE_DATE);
        cfg.addEntries("DATE_FORMAT", dates);

        List<String> ids = new ArrayList<>();
        ids.add("AUTO_GENERATED_ID");
        cfg.addEntries("ID", ids);

        cfg.addEntry("DATE_FORMAT", "E, d MMM yyyy HH:mm:ss Z");

        cfg.addEntry("AUTO_GENERATED_ID_PREFIX", "ABCDEFGH");

        this.util = new DropOffUtil(cfg);
        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(), "/eat/prefix/testPath", "UNKNOWN");
        String id = this.util.getBestIdFrom(this.payload);
        assertTrue(id.startsWith("ABCD"), "auto gen id should start with a (truncated to 4 char) prefix");
        assertEquals("yes", this.payload.getStringParameter("AUTO_GENERATED_ID"), "an auto gen id parameter should have been set");

        // Test auto gen without prefix //////////////////////////////

        cfg.removeEntry("AUTO_GENERATED_ID_PREFIX", "ABCDEFGH");

        this.util = new DropOffUtil(cfg);
        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(), "/eat/prefix/testPath", "UNKNOWN");
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

        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(), "/eat/prefix/testPath", "UNKNOWN");
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

        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(), "/eat/prefix/testPath", "UNKNOWN");

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

        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(), "", "UNKNOWN");// shortname is blank

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

        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(), "", "UNKNOWN");// shortname is blank

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

        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(), "/eat/prefix/testPath", "UNKNOWN");
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

        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(), "", "UNKNOWN");// shortname is blank

        this.util = new DropOffUtil(cfg);
        values = this.util.getExistingIds(this.payload);

        assertEquals(0, values.length, "the size of the return values is incorrect");

        // Test empty values by specifying nothing

        cfg = new ServiceConfigGuide();

        ids = new ArrayList<>();
        cfg.addEntries("ID", ids);

        cfg.addEntry("AUTO_GENERATED_ID_PREFIX", "ABCDEFGH");

        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(), "", "UNKNOWN");// shortname is blank

        this.util = new DropOffUtil(cfg);
        values = this.util.getExistingIds(this.payload);

        assertEquals(0, values.length, "the size of the return values is incorrect");
    }

    @Test
    void testMetadataPreparation() {
        final List<IBaseDataObject> family = new ArrayList<>();

        final IBaseDataObject parent = DataObjectFactory.getInstance("This is a test".getBytes(), "item1", "PARENT_FORM", "PARENT_FTYPE");
        parent.putParameter("FOO", "PARENT_FOO");
        parent.putParameter(ORIGINAL_FILENAME, "parent.tar.gz");

        final IBaseDataObject child = DataObjectFactory.getInstance("This is a test".getBytes(), "item1-att-1", "CHILD_FORM", "CHILD_FTYPE");
        child.putParameter(ORIGINAL_FILENAME, "child.docx");
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
        tld.setParameter(EVENT_DATE, "2016-02-13 23:13:03");
        tld.setParameter(FILE_DATE, "2016-01-05 17:45:53");

        // populate the child with all the event date options (note: year of 2015)
        IBaseDataObject d = new BaseDataObject();
        d.setParameter(EVENT_DATE, "2015-02-13 23:13:03");
        d.setParameter(FILE_DATE, "2015-01-05 17:45:53");

        Date start = new Date(); // use this for relative comparison

        // hit on child EventDate
        assertEquals("2015-02-13 23:13:03", TimeUtil.getDateAsISO8601(this.util.getEventDate(d, tld).toInstant()));

        // removing child EventDate should hit on child FILE_DATE
        d.deleteParameter(EVENT_DATE);
        assertEquals("2015-01-05 17:45:53", TimeUtil.getDateAsISO8601(this.util.getEventDate(d, tld).toInstant()));

        // removing child FILE_DATE should hit on tld EventDate
        d.deleteParameter(FILE_DATE);
        assertEquals("2016-02-13 23:13:03", TimeUtil.getDateAsISO8601(this.util.getEventDate(d, tld).toInstant()));

        // removing tld EventDate should hit on tld FILE_DATE
        tld.deleteParameter(EVENT_DATE);
        assertEquals("2016-01-05 17:45:53", TimeUtil.getDateAsISO8601(this.util.getEventDate(d, tld).toInstant()));

        // removing tld FILE_DATE should default to now as configured by default
        tld.deleteParameter(FILE_DATE);
        assertNotNull(this.util.getEventDate(d, tld));
        assertNotEquals(-1, this.util.getEventDate(d, tld).compareTo(start));

        // changing the configuration to not default to now should return null
        Configurator cfg = new ServiceConfigGuide();
        final List<String> dates = new ArrayList<>();
        dates.add(EVENT_DATE);
        dates.add(FILE_DATE);
        cfg.addEntries("DATE_PARAMETER", dates);
        cfg.addEntry("DEFAULT_EVENT_DATE_TO_NOW", "false");
        this.util = new DropOffUtil(cfg);

        assertNull(this.util.getEventDate(d, tld));
    }

    @Deprecated
    @Test
    void testDeprecatedGetFileType() {
        Map<String, String> metadata = new HashMap<>();
        testDeprecatedFileType(metadata, UNKNOWN, null);

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
        testDeprecatedFileType(metadata, TEXT, formsArg);

        metadata.clear();
        formsArg = " MSWORD";
        testDeprecatedFileType(metadata, "MSWORD_FRAGMENT", formsArg);

        metadata.clear();
        formsArg = "QUOTED-PRINTABLE";
        testDeprecatedFileType(metadata, TEXT, formsArg);
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
        testFileType(bdo, metadata, TEXT, formsArg);

        bdo.clearParameters();
        metadata.clear();
        formsArg = " MSWORD";
        testFileType(bdo, metadata, "MSWORD_FRAGMENT", formsArg);

        metadata.clear();
        formsArg = "QUOTED-PRINTABLE";
        testFileType(bdo, metadata, TEXT, formsArg);
    }

    @Test
    void testExtractUniqueFileExtensions() {
        // these should be constants
        final String FILEXT = "FILEXT";
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

    @Test
    void testCleanSpecPath() {
        assertEquals("thisisfine", util.cleanSpecPath("/this/is/fine"));
        assertEquals("this.isfine", util.cleanSpecPath("/this/../is/fine"));
        assertEquals("this.is.fine", util.cleanSpecPath("/this/../is/../fine"));
        assertEquals("this.isfine", util.cleanSpecPath("/this/....../../..//./is/fine"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"20230322160356", "20230322160357", "20230322160999"})
    void testGetPathFromSpecDTG(String input) {
        // %G% = DTG multi directory layout yyyy-mm-dd/hh/mi(div)10
        this.payload.putParameter("DTG", input);
        String actual = util.getPathFromSpec("%G%", this.payload);
        assertEquals("2023-03-22/16/00", actual);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"20230322160", "2023032216.356", "20230322../../../../../160356"})
    void testGetPathFromSpecDTGFallbackToNow(String input) {
        this.payload.putParameter("DTG", input);
        String actual = util.getPathFromSpec("%G%", this.payload);
        assertNotEquals("2023-03-22/16/00", actual);
        // defaults to now so check pattern
        assertTrue(Pattern.compile("\\d{4}-\\d{2}-\\d{2}/\\d{2}/\\d{2}").matcher(actual).matches());
    }

    @Test
    void testGetPathFromSpecBestId() {
        // %B% = ID for the payload depending on type (no -att-)
        final String spec = "%B%";

        this.payload.setFilename("/eat/prefix/testPath-att-1");

        String expected = "this.is.fine";
        this.payload.putParameter("MY_ID", expected);
        String actual = util.getPathFromSpec(spec, this.payload);
        assertEquals(expected, actual);
        this.payload.putParameter("MY_ID", "this/../../../is/../../.fine");
        actual = util.getPathFromSpec(spec, this.payload);
        assertEquals(expected, actual);
    }

    @Test
    void testGetPathFromSpecBestIdWithAtt() {
        // %b% = ID for the payload depending on type (with -att-)
        final String spec = "%b%";

        this.payload.setFilename("/eat/prefix/testPath-att-1");

        String expected = "this.is.fine-att-1";
        this.payload.putParameter("MY_ID", "this.is.fine");
        String actual = util.getPathFromSpec(spec, this.payload);
        assertEquals(expected, actual);
        this.payload.putParameter("MY_ID", "this/../../../is/../../.fine");
        actual = util.getPathFromSpec(spec, this.payload);
        assertEquals(expected, actual);
    }

    @Test
    void testGetPathFromSpecBestIdRandomUuid() {
        // %B% = ID for the payload depending on type (no -att-)
        final String spec = "%B%";

        this.payload.setFilename("/eat/prefix/testPath-att-1");

        // random generated uuid
        this.payload.setFilename("");
        String actual = util.getPathFromSpec(spec, this.payload);
        assertTrue(actual.startsWith("ABCD"));
        assertFalse(actual.endsWith("-att-1"));
    }

    @Test
    void testGetPathFromSpecBestIdWithAttRandomUuid() {
        // %b% = ID for the payload depending on type (with -att-)
        final String spec = "%b%";

        this.payload.setFilename("/eat/prefix/testPath-att-1");

        // random generated uuid
        this.payload.setFilename("");
        final IBaseDataObject ibdo = DataObjectFactory.getInstance("This is a test".getBytes(), "testPath-att-1", "UNKNOWN");
        String actual = util.getPathFromSpec(spec, ibdo, this.payload);
        assertTrue(actual.startsWith("ABCD"));
        assertTrue(actual.endsWith("-att-1"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "  "})
    void testGetPathFromSpecBestIdShortName(String input) {
        // %B% = ID for the payload depending on type (no -att-)
        final String spec = "%B%";
        this.payload.setFilename("/eat/prefix/testPath-att-1");

        // shortname
        this.payload.putParameter("MY_ID", "");
        String actual = util.getPathFromSpec(spec, this.payload);
        assertEquals("testPath-att-1", actual);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "  "})
    void testGetPathFromSpecBestIdNoAttShortName(String input) {
        // %B% = ID for the payload depending on type (no -att-)
        final String spec = "%b%";
        this.payload.setFilename("/eat/prefix/testPath-att-1");

        // shortname
        this.payload.putParameter("MY_ID", "");
        String actual = util.getPathFromSpec(spec, this.payload);
        assertEquals("testPath-att-1-att-1", actual);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN", "TEST_TXT", "SOME-ERROR"})
    void testGetPathFromSpecFileType(String expected) {
        this.payload.setFileType(expected);
        String actual = util.getPathFromSpec("%F%", this.payload);
        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void testGetPathFromSpecFileTypeNone(String input) {
        this.payload.setFileType(input);
        String actual = util.getPathFromSpec("%F%", this.payload);
        assertEquals("NONE", actual);
    }

    @Test
    void testGetPathFromSpecFileTypeClean() {
        this.payload.setFileType("this/is/../../fine");
        String actual = util.getPathFromSpec("%F%", this.payload);
        assertEquals("thisis.fine", actual);
    }

    @Test
    void testGetPathFromSpecMeta() {
        // @META{'token'} is to pull the named KEY from the MetaData
        final String key = "testing";
        final String spec = "@META{'" + key + "'}";

        this.payload.putParameter(key, "testPath");
        assertEquals("testPath", util.getPathFromSpec(spec, this.payload));

        this.payload.putParameter(key, "this/is/../../fine");
        assertEquals("thisis.fine", util.getPathFromSpec(spec, this.payload));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "  "})
    void testGetPathFromSpecMetaNoVal(String input) {
        // @META{'token'} is to pull the named KEY from the MetaData
        this.payload.putParameter("testing", null);
        assertEquals("NO-testing", util.getPathFromSpec("@META{'testing'}", this.payload));
    }

    @Test
    void testGetPathFromSpecTLDMeta() {
        // @TLD{'token'} is to pull the named KEY from the top level document Metadata
        final String key = "testing";
        final String spec = "@TLD{'" + key + "'}";
        final IBaseDataObject tld = DataObjectFactory.getInstance("This is a test".getBytes(), "testPath", "UNKNOWN");

        tld.putParameter(key, "testPath");
        this.payload.putParameter(key, "testPath-att-1");
        assertEquals("testPath", util.getPathFromSpec(spec, this.payload, tld));
        assertEquals("testPath-att-1", util.getPathFromSpec(spec, this.payload));

        tld.putParameter(key, "this/is/../fine");
        this.payload.putParameter(key, "this/is/fine");
        assertEquals("thisis.fine", util.getPathFromSpec(spec, this.payload, tld));
        assertEquals("thisisfine", util.getPathFromSpec(spec, this.payload));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "  "})
    void testGetPathFromSpecTLDMetaNoVal(String input) {
        // @TLD{'token'} is to pull the named KEY from the top level document Metadata
        final String key = "testing";
        final String spec = "@TLD{'" + key + "'}";
        final IBaseDataObject tld = DataObjectFactory.getInstance("This is a test".getBytes(), "testPath", "UNKNOWN");

        tld.putParameter(key, null);
        this.payload.putParameter(key, "not_empty");
        assertEquals("NO-" + key, util.getPathFromSpec(spec, this.payload, tld));
        assertEquals("not_empty", util.getPathFromSpec(spec, this.payload));
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
