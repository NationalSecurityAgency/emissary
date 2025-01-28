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

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

import static emissary.core.Form.TEXT;
import static emissary.core.constants.Parameters.EVENT_DATE;
import static emissary.core.constants.Parameters.FILEXT;
import static emissary.core.constants.Parameters.FILE_ABSOLUTEPATH;
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
    private static final Pattern SUBDIR_NAME_PATTERN = Pattern.compile("NO-CASE/\\d{4}-\\d{2}-\\d{2}/\\d{2}/\\d{2}");
    @Nullable
    private DropOffUtil util = null;
    @Nullable
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
        assertEquals("yes", this.payload.getParameterAsConcatString("AUTO_GENERATED_ID"), "an auto gen id parameter should have been set");
        assertEquals("yes", this.payload.getParameterAsString("AUTO_GENERATED_ID"), "an auto gen id parameter should have been set");

        // Test auto gen without prefix //////////////////////////////

        cfg.removeEntry("AUTO_GENERATED_ID_PREFIX", "ABCDEFGH");

        this.util = new DropOffUtil(cfg);
        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(), "/eat/prefix/testPath", "UNKNOWN");
        assertNotNull(this.util.getBestId(this.payload, tld));
        assertEquals("yes", this.payload.getStringParameter("AUTO_GENERATED_ID"), "an auto gen id parameter should have been set");
        assertEquals("yes", this.payload.getParameterAsConcatString("AUTO_GENERATED_ID"), "an auto gen id parameter should have been set");
        assertEquals("yes", this.payload.getParameterAsString("AUTO_GENERATED_ID"), "an auto gen id parameter should have been set");

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
        assertNull(this.payload.getParameterAsConcatString("AUTO_GENERATED_ID"), "an auto gen id parameter should NOT have been set");
        assertNull(this.payload.getParameterAsString("AUTO_GENERATED_ID"), "an auto gen id parameter should NOT have been set");
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
        assertNull(this.payload.getParameterAsConcatString("AUTO_GENERATED_ID"), "an auto gen id parameter should NOT have been set");
        assertNull(this.payload.getParameterAsString("AUTO_GENERATED_ID"), "an auto gen id parameter should NOT have been set");
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
        assertEquals("yes", this.payload.getParameterAsConcatString("AUTO_GENERATED_ID"), "an auto gen id parameter should have been set");
        assertEquals("yes", this.payload.getParameterAsString("AUTO_GENERATED_ID"), "an auto gen id parameter should have been set");
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
        assertEquals("yes", this.payload.getParameterAsConcatString("AUTO_GENERATED_ID"), "an auto gen id parameter should have been set");
        assertEquals("yes", this.payload.getParameterAsString("AUTO_GENERATED_ID"), "an auto gen id parameter should have been set");
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
        assertEquals("yes", this.payload.getParameterAsConcatString("AUTO_GENERATED_ID"), "an auto gen id parameter should have been set");
        assertEquals("yes", this.payload.getParameterAsString("AUTO_GENERATED_ID"), "an auto gen id parameter should have been set");

        // Test auto gen without prefix //////////////////////////////

        cfg.removeEntry("AUTO_GENERATED_ID_PREFIX", "ABCDEFGH");

        this.util = new DropOffUtil(cfg);
        this.payload = DataObjectFactory.getInstance("This is a test".getBytes(), "/eat/prefix/testPath", "UNKNOWN");
        id = this.util.getBestIdFrom(this.payload);
        assertFalse(id.startsWith("ABCD"), "auto gen id should NOT start with a (truncated to 4 char) prefix");
        assertEquals("yes", this.payload.getStringParameter("AUTO_GENERATED_ID"), "an auto gen id parameter should have been set");
        assertEquals("yes", this.payload.getParameterAsConcatString("AUTO_GENERATED_ID"), "an auto gen id parameter should have been set");
        assertEquals("yes", this.payload.getParameterAsString("AUTO_GENERATED_ID"), "an auto gen id parameter should have been set");

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
        assertNull(this.payload.getParameterAsConcatString("AUTO_GENERATED_ID"), "an auto gen id parameter should NOT have been set");
        assertNull(this.payload.getParameterAsString("AUTO_GENERATED_ID"), "an auto gen id parameter should NOT have been set");
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
        assertNull(this.payload.getParameterAsConcatString("AUTO_GENERATED_ID"), "an auto gen id parameter should NOT have been set");
        assertNull(this.payload.getParameterAsString("AUTO_GENERATED_ID"), "an auto gen id parameter should NOT have been set");
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
        assertEquals("yes", this.payload.getParameterAsConcatString("AUTO_GENERATED_ID"), "an auto gen id parameter should have been set");
        assertEquals("yes", this.payload.getParameterAsString("AUTO_GENERATED_ID"), "an auto gen id parameter should have been set");
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
        assertEquals("yes", this.payload.getParameterAsConcatString("AUTO_GENERATED_ID"), "an auto gen id parameter should have been set");
        assertEquals("yes", this.payload.getParameterAsString("AUTO_GENERATED_ID"), "an auto gen id parameter should have been set");
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
        assertNull(this.payload.getParameterAsConcatString("AUTO_GENERATED_ID"), "an auto gen id parameter should not have been set");
        assertNull(this.payload.getParameterAsString("AUTO_GENERATED_ID"), "an auto gen id parameter should not have been set");
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
        assertEquals("gz", parent.getParameterAsConcatString("FILEXT"), "Parent should have filext of gz");
        assertEquals("gz", parent.getParameterAsString("FILEXT"), "Parent should have filext of gz");
        assertEquals("docx", child.getStringParameter("FILEXT"), "Child should have filext of docx");
        assertEquals("docx", child.getParameterAsConcatString("FILEXT"), "Child should have filext of docx");
        assertEquals("docx", child.getParameterAsString("FILEXT"), "Child should have filext of docx");

        assertNull(parent.getParameter("EXTENDED_FILETYPE"), "Parent should not have extended filetype");

        assertEquals("CHILD_FTYPE//myFoo//myBar1//myBar2",
                child.getStringParameter("EXTENDED_FILETYPE"),
                "Child EXTENDED_FILETYPE should handle deduplication and collections");
        assertEquals("CHILD_FTYPE//myFoo//myBar1//myBar2",
                child.getParameterAsConcatString("EXTENDED_FILETYPE"),
                "Child EXTENDED_FILETYPE should handle deduplication and collections");
        assertEquals("CHILD_FTYPE//myFoo//myBar1//myBar2",
                child.getParameterAsString("EXTENDED_FILETYPE"),
                "Child EXTENDED_FILETYPE should handle deduplication and collections");


        assertNull(parent.getParameter("PARENT_FOO"), "Parent should not get PARENT_* values");
        assertEquals("PARENT_FOO", child.getStringParameter("PARENT_FOO"), "Child should get PARENT_FOO type");
        assertEquals("PARENT_FOO", child.getParameterAsConcatString("PARENT_FOO"), "Child should get PARENT_FOO type");
        assertEquals("PARENT_FOO", child.getParameterAsString("PARENT_FOO"), "Child should get PARENT_FOO type");
        assertEquals("PARENT_FOO", parent.getStringParameter("FOO"), "Parent FOO should not be changed");
        assertEquals("PARENT_FOO", parent.getParameterAsConcatString("FOO"), "Parent FOO should not be changed");
        assertEquals("PARENT_FOO", parent.getParameterAsString("FOO"), "Parent FOO should not be changed");
        assertEquals("CHILD_FOO", child.getStringParameter("FOO"), "Child FOO should not be changed");
        assertEquals("CHILD_FOO", child.getParameterAsConcatString("FOO"), "Child FOO should not be changed");
        assertEquals("CHILD_FOO", child.getParameterAsString("FOO"), "Child FOO should not be changed");
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
        assertEquals("FOO",
                child.getParameterAsConcatString("PARENT_FOO"),
                "Propagation of configured parent type must use closest available entry on child");
        assertEquals("FOO",
                child.getParameterAsString("PARENT_FOO"),
                "Propagation of configured parent type must use closest available entry on child");
        assertEquals("CHILD_FOO", childRecords.get(0)
                .getStringParameter("PARENT_FOO"), "Propagation of configured parent type must use closest available entry on child records");
        assertEquals("CHILD_FOO", childRecords.get(0)
                .getParameterAsConcatString("PARENT_FOO"), "Propagation of configured parent type must use closest available entry on child records");
        assertEquals("CHILD_FOO", childRecords.get(0)
                .getParameterAsString("PARENT_FOO"), "Propagation of configured parent type must use closest available entry on child records");

        // Child 2 subtree only has the TLD FOO param to use
        assertEquals("FOO", child2.getStringParameter("PARENT_FOO"), "Propagation of configured parent type must fall back to TLD on child");
        assertEquals("FOO", child2.getParameterAsConcatString("PARENT_FOO"), "Propagation of configured parent type must fall back to TLD on child");
        assertEquals("FOO", child2.getParameterAsString("PARENT_FOO"), "Propagation of configured parent type must fall back to TLD on child");
        assertEquals("FOO", grandchild.getStringParameter("PARENT_FOO"), "Propagation of configured parent type must fall back to TLD on grandchild");
        assertEquals("FOO", grandchild.getParameterAsConcatString("PARENT_FOO"),
                "Propagation of configured parent type must fall back to TLD on grandchild");
        assertEquals("FOO", grandchild.getParameterAsString("PARENT_FOO"),
                "Propagation of configured parent type must fall back to TLD on grandchild");
        assertEquals("FOO", gchildRecords.get(0)
                .getStringParameter("PARENT_FOO"), "Propagation of configured parent type must use closest available entry on grandchild records");
        assertEquals("FOO", gchildRecords.get(0).getParameterAsConcatString("PARENT_FOO"),
                "Propagation of configured parent type must use closest available entry on grandchild records");
        assertEquals("FOO", gchildRecords.get(0).getParameterAsString("PARENT_FOO"),
                "Propagation of configured parent type must use closest available entry on grandchild records");
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

        long start = Instant.now().getEpochSecond() - 5; // use this for relative comparison

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
        assertTrue(this.util.getEventDate(d, tld).toInstant().isAfter(Instant.ofEpochSecond(start)));

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

    @Test
    void testGetSubdirName() {
        String subdirName = this.util.getSubDirName(new BaseDataObject(), null, new BaseDataObject());
        assertTrue(SUBDIR_NAME_PATTERN.matcher(subdirName).matches());
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
    void testExtractFileExtensionsWithFullFilepaths() {
        DropOffUtil util = new DropOffUtil();

        // tests a combination of either FILE_ABSOLUTEPATH and Original-Filename, neither, and both set at once
        String[] fileAbsolutepaths = {"D:\\Users\\jdoe\\Documents\\Taxes 2023.csv", "", "/paper.abc.zzz",
                "/home/jdoe/SHARED_D.IR/cat.mov", "/home/user/.bashrc", ""};
        String[] originalFilenames = {"", "D:\\Users\\jdoe\\interesting.folder\\a.table", "flowers.456.123",
                "/home/jdoe/SHARED_D.IR/cat", "taxes.thisfileextensionistoolong", ""};

        String[][] extensions = {{"csv"}, {"table"}, {"zzz", "123"}, {"mov"}, {"bashrc"}, {}};

        final IBaseDataObject ibdo = new BaseDataObject();

        for (int i = 0; i < fileAbsolutepaths.length; i++) {
            ibdo.setParameter(FILE_ABSOLUTEPATH, fileAbsolutepaths[i]);
            ibdo.setParameter(ORIGINAL_FILENAME, originalFilenames[i]);
            util.extractUniqueFileExtensions(ibdo);
            if (extensions[i].length == 0) {
                assertFalse(ibdo.hasParameter(FILEXT));
            }
            for (String extension : extensions[i]) {
                assertEquals(extensions[i].length, ibdo.getParameter(FILEXT).size(), "Only "
                        + extensions[i].length + " file extensions should have been extracted");
                assertTrue(ibdo.getParameter(FILEXT).contains(extension), "FILEXT should be extracted");
            }
            // reset for the next test
            ibdo.clearParameters();
        }
    }

    @Test
    void testCleanSpecPath() {
        assertEquals("/this/is/fine", util.cleanSpecPath("/this/is/fine"));
        assertEquals("/this/./is/fine", util.cleanSpecPath("/this/../is/fine"));
        assertEquals("/this/./is/./fine", util.cleanSpecPath("/this/../is/../fine"));
        assertEquals("/this/./././/./is/fine", util.cleanSpecPath("/this/....../../..//./is/fine"));
    }

    @Test
    void testGetFullFilepathsFromParams() {
        IBaseDataObject ibdo = new BaseDataObject();
        List<String> bestFilenames;

        ibdo.setParameter(ORIGINAL_FILENAME, "");
        ibdo.setParameter(FILE_ABSOLUTEPATH, "");
        bestFilenames = DropOffUtil.getFullFilepathsFromParams(ibdo);
        assertEquals(0, bestFilenames.size(), "No filename should have been found");

        ibdo.setParameter(FILE_ABSOLUTEPATH, "theOtherFile.csv");
        bestFilenames = DropOffUtil.getFullFilepathsFromParams(ibdo);
        assertEquals(1, bestFilenames.size(), "There should be one filename extracted");
        assertEquals("theOtherFile.csv", bestFilenames.get(0), "The FILE_ABSOLUTEPATH should have been extracted");

        ibdo.setParameter(ORIGINAL_FILENAME, "file.docx");
        bestFilenames = DropOffUtil.getFullFilepathsFromParams(ibdo);
        assertEquals(2, bestFilenames.size(), "There should be two filenames extracted");
        assertEquals("file.docx", bestFilenames.get(0), "The Original-Filename should have been extracted");
        assertEquals("theOtherFile.csv", bestFilenames.get(1), "The Original-Filename should have been extracted");
    }

    @Test
    void getFullFilepathsFromParamsCustomFields() {
        IBaseDataObject ibdo = new BaseDataObject();
        ibdo.setParameter("CustomField", "customName.txt");
        ibdo.setParameter(ORIGINAL_FILENAME, "groceries.xml");
        List<String> bestFilenames = DropOffUtil.getFullFilepathsFromParams(ibdo, new String[] {"CustomField"});

        assertEquals(1, bestFilenames.size(), "Only one filename should have been extracted");
        assertEquals("customName.txt", bestFilenames.get(0), "Only the value in CustomField should have been extracted");
    }

    private static void setupMetadata(IBaseDataObject bdo, String fieldValue, DropOffUtil.FileTypeCheckParameter fileTypeCheckParameter) {
        bdo.clearParameters();
        bdo.putParameter(fileTypeCheckParameter.getFieldName(), fieldValue);
    }

    private static void testFileType(IBaseDataObject bdo, @Nullable Map<String, String> metadata, String expectedResults, @Nullable String formsArg) {
        String fileType = DropOffUtil.getAndPutFileType(bdo, metadata, formsArg);
        assertEquals(expectedResults, fileType);
    }
}
