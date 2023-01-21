package emissary.output.filter;

import emissary.config.ServiceConfigGuide;
import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.test.core.junit5.UnitTest;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonOutputFilterTest extends UnitTest {

    private ServiceConfigGuide config;
    private IBaseDataObject payload;
    private IDropOffFilter f;
    private Path tmpDir;

    @BeforeEach
    public void setup(@TempDir final Path tmpDir) throws IOException {
        this.tmpDir = tmpDir;

        config = new ServiceConfigGuide();
        config.removeAllEntries("OUTPUT_PATH");
        config.addEntry("OUTPUT_PATH", tmpDir.toAbsolutePath().toString());

        f = new JsonOutputFilter();

        payload = DataObjectFactory.getInstance();
        payload.setData("This is the data".getBytes(UTF_8));
        payload.setFileType("FTYPE");
        payload.setFilename("/this/is/a/testfile");
        payload.appendParameter("FOO", "bar");
        payload.appendParameter("FOO", "stuff");
    }

    @AfterEach
    public void teardown() throws IOException {
        Files.deleteIfExists(tmpDir);
        config = null;
    }

    @Test
    void testFilterSetup() {
        f.initialize(config, "FOO", config);
        assertEquals("FOO", f.getFilterName(), "Filter name should be set");
    }

    @Test
    void testOutputFromFilter() {
        f.initialize(config, "FOO", config);

        List<IBaseDataObject> payloadList = new ArrayList<>();
        payloadList.add(payload);

        Map<String, Object> params = new HashMap<>();

        assertTrue(f.isOutputtable(payload, params), "Payload should be outputtable");
        assertTrue(f.isOutputtable(payloadList, params), "Payload list should be outputtable");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int status = f.filter(payloadList, params, output);

        assertEquals(IDropOffFilter.STATUS_SUCCESS, status, "Filter should return success");
        assertTrue(output.toString().contains("\"FILETYPE\":[\"FTYPE\"]"), "Filter output should have file type");
        assertTrue(output.toString().contains("\"payload\":\"VGhpcyBpcyB0aGUgZGF0YQ==\""), "Filter should have payload");
    }

    @Test
    void testNoPayloadOutputFromFilter() {
        config.addEntry("EMIT_PAYLOAD", "false");
        f.initialize(config, "FOO", config);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int status = f.filter(Collections.singletonList(payload), new HashMap<>(), output);

        assertEquals(IDropOffFilter.STATUS_SUCCESS, status, "Filter should return success");
        assertFalse(output.toString().contains("\"payload\":"), "Filter should not have payload");
    }


    @Test
    void testMetadataRecordOutput() {
        config.addEntry("EXTRA_PARAM", "*");
        f.initialize(config, "FOO", config);

        IBaseDataObject payload = DataObjectFactory.getInstance();
        payload.setData("This is the data".getBytes(UTF_8));
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

        assertTrue(f.isOutputtable(payload, params), "Payload should be outputtable");
        assertTrue(f.isOutputtable(payloadList, params), "Payload list should be outputtable");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int status = f.filter(payloadList, params, output);

        assertEquals(IDropOffFilter.STATUS_SUCCESS, status, "Filter should return success");
        String s = output.toString();

        assertEquals(10, StringUtils.countMatches(s, "creationTimestamp"), "Should emit line for parent and extracted records");

    }

    @Test
    void testWhitelistFields() {
        config.addEntry("EXTRA_PARAM", "BAR");
        f.initialize(config, "FOO", config);

        payload.appendParameter("BAR", "foo");

        List<IBaseDataObject> payloadList = new ArrayList<>();
        payloadList.add(payload);

        Map<String, Object> params = new HashMap<>();

        assertTrue(f.isOutputtable(payload, params), "Payload should be outputtable");
        assertTrue(f.isOutputtable(payloadList, params), "Payload list should be outputtable");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int status = f.filter(payloadList, params, output);
        String s = output.toString();

        System.out.println(s);

        assertEquals(IDropOffFilter.STATUS_SUCCESS, status, "Filter should return success");
        assertTrue(s.contains("\"BAR\":[\"foo\"]"), "Filter output should contain field BAR");
        assertFalse(s.contains("\"FOO\":"), "Filter output should not contain field FOO");
    }

    @Test
    void testBlacklistedFields() {
        config.addEntry("BLACKLIST_FIELD", "FOO");
        config.addEntry("BLACKLIST_PREFIX", "BAR_");
        config.addEntry("EXTRA_PARAM", "*");
        f.initialize(config, "FOO", config);

        IBaseDataObject payload = DataObjectFactory.getInstance();
        payload.setData("This is the data".getBytes(UTF_8));
        payload.setFileType("FTYPE");
        payload.setFilename("/this/is/a/testfile");
        payload.putParameter("FOO", "myFoo");
        payload.putParameter("BAR", "myBar");
        payload.putParameter("BAR_AS_PREFIX", "myBarPrefix");
        payload.putParameter("QUUX", "myQuux");
        List<IBaseDataObject> payloadList = new ArrayList<>();
        payloadList.add(payload);

        Map<String, Object> params = new HashMap<>();

        assertTrue(f.isOutputtable(payload, params), "Payload should be outputtable");
        assertTrue(f.isOutputtable(payloadList, params), "Payload list should be outputtable");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int status = f.filter(payloadList, params, output);
        String s = output.toString();

        assertEquals(IDropOffFilter.STATUS_SUCCESS, status, "Filter should return success");
        assertTrue(s.contains("\"QUUX\":[\"myQuux\"]"), "Filter output should have whitelist field");
        assertTrue(s.contains("\"BAR\":[\"myBar\"]"), "Filter output should have prefix no-match field BAR");
        assertFalse(s.contains("\"FOO\":"), "Filter output should not have blacklist field FOO");
        assertFalse(s.contains("\"BAR_AS_PREFIX\":"), "Filter output should not have blacklist prefix field BAR_");
    }

    @Test
    void testBlacklistedFieldsNoWhitelist() {
        config.addEntry("BLACKLIST_FIELD", "FOO");
        config.addEntry("BLACKLIST_PREFIX", "BAR_");
        f.initialize(config, "FOO", config);

        IBaseDataObject payload = DataObjectFactory.getInstance();
        payload.setData("This is the data".getBytes(UTF_8));
        payload.setFileType("FTYPE");
        payload.setFilename("/this/is/a/testfile");
        payload.putParameter("FOO", "myFoo");
        payload.putParameter("BAR", "myBar");
        payload.putParameter("BAR_AS_PREFIX", "myBarPrefix");
        payload.putParameter("QUUX", "myQuux");
        List<IBaseDataObject> payloadList = new ArrayList<>();
        payloadList.add(payload);

        Map<String, Object> params = new HashMap<>();

        assertTrue(f.isOutputtable(payload, params), "Payload should be outputtable");
        assertTrue(f.isOutputtable(payloadList, params), "Payload list should be outputtable");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int status = f.filter(payloadList, params, output);
        String s = output.toString();

        assertEquals(IDropOffFilter.STATUS_SUCCESS, status, "Filter should return success");
        assertTrue(s.contains("\"QUUX\":[\"myQuux\"]"), "Filter output should have whitelist field");
        assertTrue(s.contains("\"BAR\":[\"myBar\"]"), "Filter output should have prefix no-match field");
        assertFalse(s.contains("\"FOO\":"), "Filter output should not have blacklist field");
        assertFalse(s.contains("\"BAR_AS_PREFIX\":"), "Filter output should not have blacklist prefix field");
    }

    @Test
    void testBlacklistAll() {
        config.addEntry("BLACKLIST_FIELD", "*");
        f.initialize(config, "FOO", config);

        List<IBaseDataObject> payloadList = new ArrayList<>();
        payloadList.add(payload);

        Map<String, Object> params = new HashMap<>();

        assertTrue(f.isOutputtable(payload, params), "Payload should be outputtable");
        assertTrue(f.isOutputtable(payloadList, params), "Payload list should be outputtable");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int status = f.filter(payloadList, params, output);
        String s = output.toString();

        System.out.println(s);

        assertEquals(IDropOffFilter.STATUS_SUCCESS, status, "Filter should return success");
        assertTrue(s.contains("\"parameters\":{}"), "Filter output should have no parameters");
    }

    @Test
    void testBlacklistedPrefixWhitelistField() {
        config.addEntry("BLACKLIST_PREFIX", "BAR");
        config.addEntry("EXTRA_PARAM", "BAR_BAZ");
        f.initialize(config, "FOO", config);

        // setup ibdo
        IBaseDataObject payload = DataObjectFactory.getInstance();
        payload.setData("This is the dBAR_BARata".getBytes(UTF_8));
        payload.appendParameter("BAR_BAR", "bar");
        payload.appendParameter("BAR_BAZ", "baz");
        List<IBaseDataObject> payloadList = new ArrayList<>();
        payloadList.add(payload);

        // run filter
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        f.filter(payloadList, new HashMap<>(), output);
        String s = output.toString();

        // assert
        assertFalse(s.contains("\"BAR_BAR\":[\"bar\"]"), "Filter output should not have blacklist field BAR_BAR with value bar");
        assertTrue(s.contains("\"BAR_BAZ\":[\"baz\"]"), "Filter output should have field BAR_BAZ with value baz");
    }

    @Test
    void testWhitelistedPrefixBlacklistField() {
        config.addEntry("EXTRA_PARAM_PREFIX", "BAR");
        config.addEntry("BLACKLIST_FIELD", "BAR_BAZ");
        f.initialize(config, "FOO", config);

        // setup ibdo
        IBaseDataObject payload = DataObjectFactory.getInstance();
        payload.setData("This is the dBAR_BARata".getBytes(UTF_8));
        payload.appendParameter("BAR_BAR", "bar");
        payload.appendParameter("BAR_BAZ", "baz");
        List<IBaseDataObject> payloadList = new ArrayList<>();
        payloadList.add(payload);

        // run filter
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        f.filter(payloadList, new HashMap<>(), output);
        String s = output.toString();

        // assert
        assertTrue(s.contains("\"BAR_BAR\":[\"bar\"]"), "Filter output should have field BAR_BAR with value bar");
        assertFalse(s.contains("\"BAR_BAZ\":[\"baz\"]"), "Filter output should not have field BAR_BAZ with value baz");
    }

    @Test
    void testBlacklistValue() {
        config.addEntry("EXTRA_PARAM", "*");
        config.addEntry("BLACKLIST_VALUE_BAR", "baz");
        f.initialize(config, "FOO", config);

        // setup ibdo
        IBaseDataObject payload = DataObjectFactory.getInstance();
        payload.setData("This is the dBAR_BARata".getBytes(UTF_8));
        payload.appendParameter("BAR", "bar");
        payload.appendParameter("BAR", "baz");
        List<IBaseDataObject> payloadList = new ArrayList<>();
        payloadList.add(payload);

        // run filter
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        f.filter(payloadList, new HashMap<>(), output);
        String s = output.toString();

        System.out.println(s);

        // assert
        assertTrue(s.contains("\"BAR\":[\"bar\"]"), "Filter output should field BAR with value bar");
        assertFalse(s.contains("\"BAR\":[\"baz\"]"), "Filter output should not have field BAR with value baz");
    }

    @Test
    void testTotalDescendantCountMultiChildren() {
        // setup
        IBaseDataObject parent = DataObjectFactory.getInstance();
        parent.setFilename("parent");
        parent.setData("some data".getBytes(UTF_8));
        IBaseDataObject child = DataObjectFactory.getInstance();
        child.setFilename("parent-att-1");
        child.setData("some child data".getBytes(UTF_8));
        IBaseDataObject child2 = DataObjectFactory.getInstance();
        child2.setFilename("parent-att-2");
        child2.setData("some child data".getBytes(UTF_8));

        config.addEntry("EXTRA_PARAM", "DESCENDANT_COUNT");
        f.initialize(config, "FOO", config);

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // test
        f.filter(Arrays.asList(parent, child, child2), new HashMap<>(), output);

        // verify
        String s = output.toString();

        assertTrue(s.contains("\"DESCENDANT_COUNT\":[2]"), "Filter output should have had DESCENDANT_COUNT");
        assertEquals(1, StringUtils.countMatches(s, "DESCENDANT_COUNT"), "Filter output should have only had DESCENDANT_COUNT once");
    }

    @Test
    void testStripPrefix() {
        config.addEntry("STRIP_PARAM_PREFIX", "IGNORE_");
        config.addEntry("EXTRA_PREFIX", "IGNORE_");
        config.addEntry("EXTRA_PARAM", "QUUX");
        f.initialize(config, "FOO", config);

        IBaseDataObject payload = DataObjectFactory.getInstance();
        payload.setData("This is the data".getBytes(UTF_8));
        payload.setFileType("FTYPE");
        payload.putParameter("IGNORE_FOO", "ONE");
        payload.putParameter("IGNORE_BAR", "TWO");
        payload.putParameter("QUUX", "THREE");
        payload.setFilename("/this/is/a/testfile");
        List<IBaseDataObject> payloadList = new ArrayList<>();
        payloadList.add(payload);

        Map<String, Object> params = new HashMap<>();

        assertTrue(f.isOutputtable(payload, params), "Payload should be outputtable");
        assertTrue(f.isOutputtable(payloadList, params), "Payload list should be outputtable");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int status = f.filter(payloadList, params, output);

        assertEquals(IDropOffFilter.STATUS_SUCCESS, status, "Filter should return success");

        String s = output.toString();

        assertTrue(s.contains("\"QUUX\":[\"THREE\"]"), "Output should have non-prefix parameter as normal " + s);
        assertTrue(s.contains("\"FOO\":[\"ONE\"]"), "Output should have prefix stripped parameter " + s);
        assertTrue(s.contains("\"BAR\":[\"TWO\"]"), "Output should have prefix stripped parameter " + s);
    }
}
