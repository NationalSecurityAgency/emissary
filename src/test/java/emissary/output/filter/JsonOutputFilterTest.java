package emissary.output.filter;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.io.Files;
import emissary.config.ServiceConfigGuide;
import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.test.core.UnitTest;
import org.apache.commons.lang.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class JsonOutputFilterTest extends UnitTest {

    private ServiceConfigGuide config;
    private IBaseDataObject payload;
    private IDropOffFilter f;

    @Before
    public void setup() {
        config = new ServiceConfigGuide();
        config.removeAllEntries("OUTPUT_PATH");
        config.addEntry("OUTPUT_PATH", Files.createTempDir().getAbsolutePath());

        f = new JsonOutputFilter();

        payload = DataObjectFactory.getInstance();
        payload.setData("This is the data".getBytes());
        payload.setFileType("FTYPE");
        payload.setFilename("/this/is/a/testfile");
        payload.appendParameter("FOO", "bar");
        payload.appendParameter("FOO", "stuff");
    }

    @After
    public void teardown() {
        config = null;
    }

    @Test
    public void testFilterSetup() {
        f.initialize(config, "FOO", config);
        assertEquals("Filter name should be set", "FOO", f.getFilterName());
    }

    @Test
    public void testOutputFromFilter() {
        f.initialize(config, "FOO", config);

        List<IBaseDataObject> payloadList = new ArrayList<>();
        payloadList.add(payload);

        Map<String, Object> params = new HashMap<>();

        assertTrue("Payload should be outputtable", f.isOutputtable(payload, params));
        assertTrue("Payload list should be outputtable", f.isOutputtable(payloadList, params));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int status = f.filter(payloadList, params, output);

        assertEquals("Filter should return success", IDropOffFilter.STATUS_SUCCESS, status);
        assertTrue("Filter output should have file type", output.toString().contains("\"FILETYPE\":[\"FTYPE\"]"));
    }

    @Test
    public void testMetadataRecordOutput() {
        config.addEntry("EXTRA_PARAM", "*");
        f.initialize(config, "FOO", config);

        IBaseDataObject payload = DataObjectFactory.getInstance();
        payload.setData("This is the data".getBytes());
        payload.setFileType("FTYPE");
        payload.setFilename("/this/is/a/testfile");

        List<IBaseDataObject> nested = new ArrayList<>();
        for (int i = 1; i < 10; i++) {
            IBaseDataObject record = DataObjectFactory.getInstance();
            record.setFilename("/this/is/a/testfile-att-" + i);
            record.setFileType("FTYPE-" + i);
            record.putParameter("FOO_" + i, "BAR_" + i);
            nested.add(record);
        }
        payload.setExtractedRecords(nested);

        List<IBaseDataObject> payloadList = new ArrayList<>();
        payloadList.add(payload);

        Map<String, Object> params = new HashMap<>();
        params.put(IDropOffFilter.TLD_PARAM, payload);

        assertTrue("Payload should be outputtable", f.isOutputtable(payload, params));
        assertTrue("Payload list should be outputtable", f.isOutputtable(payloadList, params));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int status = f.filter(payloadList, params, output);

        assertEquals("Filter should return success", IDropOffFilter.STATUS_SUCCESS, status);
        String s = output.toString();

        assertEquals("Should emit line for parent and extracted records", 10, StringUtils.countMatches(s, "creationTimestamp"));

    }

    @Test
    public void testWhitelistFields() {
        config.addEntry("EXTRA_PARAM", "BAR");
        f.initialize(config, "FOO", config);

        payload.appendParameter("BAR", "foo");

        List<IBaseDataObject> payloadList = new ArrayList<>();
        payloadList.add(payload);

        Map<String, Object> params = new HashMap<>();

        assertTrue("Payload should be outputtable", f.isOutputtable(payload, params));
        assertTrue("Payload list should be outputtable", f.isOutputtable(payloadList, params));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int status = f.filter(payloadList, params, output);
        String s = output.toString();

        System.out.println(s);

        assertEquals("Filter should return success", IDropOffFilter.STATUS_SUCCESS, status);
        assertTrue("Filter output should contain field BAR", s.contains("\"BAR\":[\"foo\"]"));
        assertFalse("Filter output should not contain field FOO", s.contains("\"FOO\":"));
    }

    @Test
    public void testBlacklistedFields() {
        config.addEntry("BLACKLIST_FIELD", "FOO");
        config.addEntry("BLACKLIST_PREFIX", "BAR_");
        config.addEntry("EXTRA_PARAM", "*");
        f.initialize(config, "FOO", config);

        IBaseDataObject payload = DataObjectFactory.getInstance();
        payload.setData("This is the data".getBytes());
        payload.setFileType("FTYPE");
        payload.setFilename("/this/is/a/testfile");
        payload.putParameter("FOO", "myFoo");
        payload.putParameter("BAR", "myBar");
        payload.putParameter("BAR_AS_PREFIX", "myBarPrefix");
        payload.putParameter("QUUX", "myQuux");
        List<IBaseDataObject> payloadList = new ArrayList<>();
        payloadList.add(payload);

        Map<String, Object> params = new HashMap<>();

        assertTrue("Payload should be outputtable", f.isOutputtable(payload, params));
        assertTrue("Payload list should be outputtable", f.isOutputtable(payloadList, params));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int status = f.filter(payloadList, params, output);
        String s = output.toString();

        assertEquals("Filter should return success", IDropOffFilter.STATUS_SUCCESS, status);
        assertTrue("Filter output should have whitelist field", s.contains("\"QUUX\":[\"myQuux\"]"));
        assertTrue("Filter output should have prefix no-match field BAR", s.contains("\"BAR\":[\"myBar\"]"));
        assertFalse("Filter output should not have blacklist field FOO", s.contains("\"FOO\":"));
        assertFalse("Filter output should not have blacklist prefix field BAR_", s.contains("\"BAR_AS_PREFIX\":"));
    }

    @Test
    public void testBlacklistedFieldsNoWhitelist() {
        config.addEntry("BLACKLIST_FIELD", "FOO");
        config.addEntry("BLACKLIST_PREFIX", "BAR_");
        f.initialize(config, "FOO", config);

        IBaseDataObject payload = DataObjectFactory.getInstance();
        payload.setData("This is the data".getBytes());
        payload.setFileType("FTYPE");
        payload.setFilename("/this/is/a/testfile");
        payload.putParameter("FOO", "myFoo");
        payload.putParameter("BAR", "myBar");
        payload.putParameter("BAR_AS_PREFIX", "myBarPrefix");
        payload.putParameter("QUUX", "myQuux");
        List<IBaseDataObject> payloadList = new ArrayList<>();
        payloadList.add(payload);

        Map<String, Object> params = new HashMap<>();

        assertTrue("Payload should be outputtable", f.isOutputtable(payload, params));
        assertTrue("Payload list should be outputtable", f.isOutputtable(payloadList, params));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int status = f.filter(payloadList, params, output);
        String s = output.toString();

        assertEquals("Filter should return success", IDropOffFilter.STATUS_SUCCESS, status);
        assertTrue("Filter output should have whitelist field", s.contains("\"QUUX\":[\"myQuux\"]"));
        assertTrue("Filter output should have prefix no-match field", s.contains("\"BAR\":[\"myBar\"]"));
        assertFalse("Filter output should not have blacklist field", s.contains("\"FOO\":"));
        assertFalse("Filter output should not have blacklist prefix field", s.contains("\"BAR_AS_PREFIX\":"));
    }

    @Test
    public void testBlacklistAll() {
        config.addEntry("BLACKLIST_FIELD", "*");
        f.initialize(config, "FOO", config);

        List<IBaseDataObject> payloadList = new ArrayList<>();
        payloadList.add(payload);

        Map<String, Object> params = new HashMap<>();

        assertTrue("Payload should be outputtable", f.isOutputtable(payload, params));
        assertTrue("Payload list should be outputtable", f.isOutputtable(payloadList, params));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int status = f.filter(payloadList, params, output);
        String s = output.toString();

        System.out.println(s);

        assertEquals("Filter should return success", IDropOffFilter.STATUS_SUCCESS, status);
        assertTrue("Filter output should have no parameters", s.contains("\"parameters\":{}"));
    }

    @Test
    public void testBlacklistedPrefixWhitelistField() {
        config.addEntry("BLACKLIST_PREFIX", "BAR");
        config.addEntry("EXTRA_PARAM", "BAR_BAZ");
        f.initialize(config, "FOO", config);

        // setup ibdo
        IBaseDataObject payload = DataObjectFactory.getInstance();
        payload.setData("This is the dBAR_BARata".getBytes());
        payload.appendParameter("BAR_BAR", "bar");
        payload.appendParameter("BAR_BAZ", "baz");
        List<IBaseDataObject> payloadList = new ArrayList<>();
        payloadList.add(payload);

        // run filter
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        f.filter(payloadList, new HashMap<String, Object>(), output);
        String s = output.toString();

        // assert
        assertFalse("Filter output should not have blacklist field BAR_BAR with value bar", s.contains("\"BAR_BAR\":[\"bar\"]"));
        assertTrue("Filter output should have field BAR_BAZ with value baz", s.contains("\"BAR_BAZ\":[\"baz\"]"));
    }

    @Test
    public void testWhitelistedPrefixBlacklistField() {
        config.addEntry("EXTRA_PARAM_PREFIX", "BAR");
        config.addEntry("BLACKLIST_FIELD", "BAR_BAZ");
        f.initialize(config, "FOO", config);

        // setup ibdo
        IBaseDataObject payload = DataObjectFactory.getInstance();
        payload.setData("This is the dBAR_BARata".getBytes());
        payload.appendParameter("BAR_BAR", "bar");
        payload.appendParameter("BAR_BAZ", "baz");
        List<IBaseDataObject> payloadList = new ArrayList<>();
        payloadList.add(payload);

        // run filter
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        f.filter(payloadList, new HashMap<String, Object>(), output);
        String s = output.toString();

        // assert
        assertTrue("Filter output should have field BAR_BAR with value bar", s.contains("\"BAR_BAR\":[\"bar\"]"));
        assertFalse("Filter output should not have field BAR_BAZ with value baz", s.contains("\"BAR_BAZ\":[\"baz\"]"));
    }

    @Test
    public void testBlacklistValue() {
        config.addEntry("EXTRA_PARAM", "*");
        config.addEntry("BLACKLIST_VALUE_BAR", "baz");
        f.initialize(config, "FOO", config);

        // setup ibdo
        IBaseDataObject payload = DataObjectFactory.getInstance();
        payload.setData("This is the dBAR_BARata".getBytes());
        payload.appendParameter("BAR", "bar");
        payload.appendParameter("BAR", "baz");
        List<IBaseDataObject> payloadList = new ArrayList<>();
        payloadList.add(payload);

        // run filter
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        f.filter(payloadList, new HashMap<String, Object>(), output);
        String s = output.toString();

        System.out.println(s);

        // assert
        assertTrue("Filter output should field BAR with value bar", s.contains("\"BAR\":[\"bar\"]"));
        assertFalse("Filter output should not have field BAR with value baz", s.contains("\"BAR\":[\"baz\"]"));
    }

    @Test
    public void testTotalDescendantCountMultiChildren() {
        // setup
        IBaseDataObject parent = DataObjectFactory.getInstance();
        parent.setFilename("parent");
        parent.setData("some data".getBytes());
        IBaseDataObject child = DataObjectFactory.getInstance();
        child.setFilename("parent-att-1");
        child.setData("some child data".getBytes());
        IBaseDataObject child2 = DataObjectFactory.getInstance();
        child2.setFilename("parent-att-2");
        child2.setData("some child data".getBytes());

        config.addEntry("EXTRA_PARAM", "DESCENDANT_COUNT");
        f.initialize(config, "FOO", config);

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // test
        f.filter(Arrays.asList(parent, child, child2), new HashMap<String, Object>(), output);

        // verify
        String s = output.toString();

        assertTrue("Filter output should have had DESCENDANT_COUNT", s.contains("\"DESCENDANT_COUNT\":[2]"));
        assertTrue("Filter output should have only had DESCENDANT_COUNT once", StringUtils.countMatches(s, "DESCENDANT_COUNT") == 1);
    }

    @Test
    public void testStripPrefix() {
        config.addEntry("STRIP_PARAM_PREFIX", "IGNORE_");
        config.addEntry("EXTRA_PREFIX", "IGNORE_");
        config.addEntry("EXTRA_PARAM", "QUUX");
        f.initialize(config, "FOO", config);

        IBaseDataObject payload = DataObjectFactory.getInstance();
        payload.setData("This is the data".getBytes());
        payload.setFileType("FTYPE");
        payload.putParameter("IGNORE_FOO", "ONE");
        payload.putParameter("IGNORE_BAR", "TWO");
        payload.putParameter("QUUX", "THREE");
        payload.setFilename("/this/is/a/testfile");
        List<IBaseDataObject> payloadList = new ArrayList<>();
        payloadList.add(payload);

        Map<String, Object> params = new HashMap<>();

        assertTrue("Payload should be outputtable", f.isOutputtable(payload, params));
        assertTrue("Payload list should be outputtable", f.isOutputtable(payloadList, params));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int status = f.filter(payloadList, params, output);

        assertEquals("Filter should return success", IDropOffFilter.STATUS_SUCCESS, status);

        String s = output.toString();

        assertTrue("Output should have non-prefix parameter as normal " + s, s.contains("\"QUUX\":[\"THREE\"]"));
        assertTrue("Output should have prefix stripped parameter " + s, s.contains("\"FOO\":[\"ONE\"]"));
        assertTrue("Output should have prefix stripped parameter " + s, s.contains("\"BAR\":[\"TWO\"]"));
    }
}
