package emissary.output.sink;

import emissary.config.ServiceConfigGuide;
import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.output.policy.Decision;
import emissary.output.policy.OutputFilter;
import emissary.output.policy.OutputPolicy;
import emissary.output.policy.request.PayloadRequest;
import emissary.output.sink.ISink.WriteStatus;
import emissary.test.core.junit5.UnitTest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputPipelineTest extends UnitTest {

    private static IBaseDataObject payload() {
        final IBaseDataObject d = DataObjectFactory.getInstance("This is the data".getBytes(UTF_8), "testfile", "FTYPE");
        d.appendParameter("FOO", "keep");
        d.appendParameter("FOO", "bad");
        d.appendParameter("SECRET", "hidden");
        d.addAlternateView("JSON_ML", "viewdata".getBytes(UTF_8));
        d.addAlternateView("KEEP_OUT", "other".getBytes(UTF_8));
        return d;
    }

    private static String format(final ServiceConfigGuide config) throws IOException {
        return format(config, payload());
    }

    private static String format(final ServiceConfigGuide config, final IBaseDataObject payload) throws IOException {
        final JsonWriter writer = new JsonWriter(config, config, OutputPolicy.from(config));
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.writeTo(out, Collections.singletonList(payload), new HashMap<>());
        return new String(out.toByteArray(), UTF_8);
    }

    private static ServiceConfigGuide config(final String... stages) {
        final ServiceConfigGuide config = new ServiceConfigGuide();
        for (final String stage : stages) {
            config.addEntry("POLICY_STAGE", stage);
        }
        return config;
    }

    @Test
    void emitsEverythingByDefault() throws IOException {
        final String json = format(config());

        assertTrue(json.contains("\"payload\""));
        assertTrue(json.contains("\"JSON_ML\""));
        assertTrue(json.contains("\"FOO\""));
        assertTrue(json.contains("\"SECRET\""));
    }

    @Test
    void emitContentOmitsParameters() throws IOException {
        final ServiceConfigGuide config = config();
        config.addEntry("EMIT", "content");
        final String json = format(config);

        assertTrue(json.contains("\"payload\""));
        assertTrue(json.contains("\"JSON_ML\""));
        assertFalse(json.contains("\"parameters\""));
        assertFalse(json.contains("\"FOO\""));
    }

    @Test
    void emitMetadataOmitsContent() throws IOException {
        final ServiceConfigGuide config = config();
        config.addEntry("EMIT", "metadata");
        final String json = format(config);

        assertFalse(json.contains("\"payload\""));
        assertFalse(json.contains("\"JSON_ML\""));
        assertTrue(json.contains("\"parameters\""));
        assertTrue(json.contains("\"FOO\""));
    }

    @Test
    void filtersViewsByPolicy() throws IOException {
        final String json = format(config("rule deny view KEEP_OUT"));

        assertTrue(json.contains("\"JSON_ML\""));
        assertFalse(json.contains("\"KEEP_OUT\""));
    }

    @Test
    void viewRulesUseOwningRecordFacts() throws IOException {
        final IBaseDataObject parent = payload();
        final IBaseDataObject child = DataObjectFactory.getInstance(new byte[0], "child", "CHILD");
        child.addAlternateView("KEEP_OUT", "childview".getBytes(UTF_8));
        parent.setExtractedRecords(Collections.singletonList(child));

        final ServiceConfigGuide config = config("rule deny view CHILD.KEEP_OUT");
        final String json = format(config, parent);

        assertEquals(1, StringUtils.countMatches(json, "\"KEEP_OUT\""));
    }

    @Test
    void filtersParameterKeysAndValuesByPolicy() throws IOException {
        final String json = format(config("rule deny param SECRET", "rule deny value FOO=bad"));
        final JsonNode parameters = new ObjectMapper().readTree(json).get(0).get("parameters");
        assertEquals(1, parameters.get("FOO").size());
        assertEquals("keep", parameters.get("FOO").get(0).asText());
        assertFalse(parameters.has("SECRET"));
    }

    @Test
    void sortsParameterValuesLikeLegacyOutput() throws IOException {
        final IBaseDataObject payload = DataObjectFactory.getInstance("data".getBytes(UTF_8), "testfile", "FTYPE");
        payload.appendParameter("FOO", "charlie");
        payload.appendParameter("FOO", "alpha");
        payload.appendParameter("FOO", "bravo");

        final ServiceConfigGuide config = config();
        final String json = format(config, payload);

        assertTrue(json.indexOf("alpha") < json.indexOf("bravo"), "values keep legacy sorted order");
        assertTrue(json.indexOf("bravo") < json.indexOf("charlie"), "values keep legacy sorted order");
    }

    @Test
    void sinkStillAcceptsRecordsWhenEmitIsMetadata() {
        final ServiceConfigGuide config = config("rule deny param SECRET");
        config.addEntry("EMIT", "metadata");
        final TestSink sink = new TestSink();
        sink.initialize(config, "JSON", config);

        assertTrue(sink.accept(payload()));
    }

    @Test
    void sinkExposesPolicyForInspection() {
        final TestSink sink = new TestSink();
        final ServiceConfigGuide config = config("rule deny param SECRET");
        sink.initialize(config, "JSON", config);

        final IBaseDataObject payload = payload();
        assertTrue(sink.getPolicy().traceParam(payload, "FOO").emitted());
        assertFalse(sink.getPolicy().traceParam(payload, "SECRET").emitted());
        assertTrue(sink.getPolicy().traceParam(payload, "SECRET").describe().contains("rule #1"));
    }

    @Test
    void sinkDeniesPayloadByPolicy() {
        final TestSink sink = new TestSink();
        final ServiceConfigGuide config = config("rule deny payload filetype=FTYPE");
        sink.initialize(config, "JSON", config);

        assertFalse(sink.accept(payload()));
        assertEquals(WriteStatus.SKIPPED,
                sink.write(Collections.singletonList(payload()), new HashMap<>(), new ByteArrayOutputStream()));
    }

    @Test
    void sinkSharesPolicyWithWriter() {
        final ServiceConfigGuide config = config("rule deny param SECRET");
        config.addEntry("EMIT", "metadata");
        final TestSink sink = new TestSink();
        sink.initialize(config, "JSON", config);

        final String json = new String(sink.writeTo(payload()), UTF_8);

        assertFalse(json.contains("\"payload\""));
        assertTrue(json.contains("\"FOO\""));
        assertFalse(json.contains("SECRET"));
    }

    @Test
    void nestedAndListedDenialsSkipTheWholeFamilyWithoutWriting() {
        final ServiceConfigGuide config = config("rule deny payload outputable=false");
        final TestSink sink = new TestSink();
        sink.initialize(config, "TEST", config);
        final IBaseDataObject parent = payload();
        final IBaseDataObject child = payload();
        child.setOutputable(false);
        parent.setExtractedRecords(List.of(child));
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertEquals(WriteStatus.SKIPPED, sink.write(parent, new HashMap<>(), out));
        assertEquals(0, out.size());
        parent.clearExtractedRecords();
        assertEquals(WriteStatus.SKIPPED, sink.write(List.of(parent, child), new HashMap<>(), out));
        assertEquals(0, out.size());
    }

    @Test
    void mixedAndNullParameterValuesRetainTypesAndOrder() throws IOException {
        final IBaseDataObject data = payload();
        data.putParameter("MIXED", Arrays.asList("text", 42, true, null));
        final JsonNode values = new ObjectMapper().readTree(format(config(), data)).get(0).get("parameters").get("MIXED");
        assertEquals("text", values.get(0).asText());
        assertEquals(42, values.get(1).asInt());
        assertTrue(values.get(2).asBoolean());
        assertTrue(values.get(3).isNull());
    }

    @Test
    void prefixStrippingUsesLongestPrefixDeterministically() throws IOException {
        final ServiceConfigGuide config = config();
        config.addEntry("STRIP_PARAM_PREFIX", "APP_");
        config.addEntry("STRIP_PARAM_PREFIX", "app_private_");
        final IBaseDataObject data = payload();
        data.appendParameter("app_private_my-key", "value");
        final JsonWriter writer = new JsonWriter(config, config, OutputPolicy.from(config));
        assertEquals("MY_KEY", writer.outputParameterName("app_private_my-key"));
        assertTrue(new ObjectMapper().readTree(format(config, data)).get(0).get("parameters").has("MY_KEY"));
    }

    @Test
    void eachRecordEligibilityRunsOncePerWrite() {
        final ServiceConfigGuide config = config("filter " + CountingFilter.class.getName());
        final TestSink sink = new TestSink();
        sink.initialize(config, "TEST", config);
        CountingFilter.COUNT.set(0);
        final IBaseDataObject parent = payload();
        parent.setExtractedRecords(List.of(payload()));
        assertEquals(WriteStatus.SUCCESS, sink.write(parent, new HashMap<>(), new ByteArrayOutputStream()));
        assertEquals(2, CountingFilter.COUNT.get());
    }

    @Test
    void emptyFamilyIsSkipped() {
        final TestSink sink = new TestSink();
        final ServiceConfigGuide config = config();
        sink.initialize(config, "TEST", config);
        assertEquals(WriteStatus.SKIPPED, sink.write(List.of(), new HashMap<>(), new ByteArrayOutputStream()));
    }

    public static final class CountingFilter implements OutputFilter {
        static final AtomicInteger COUNT = new AtomicInteger();

        @Override
        public String name() {
            return "counting";
        }

        @Override
        public Decision decide(final PayloadRequest request) {
            COUNT.incrementAndGet();
            return Decision.abstain(name());
        }
    }

    /** Stream-only sink so tests do not touch the filesystem. */
    private static final class TestSink extends AbstractSink {

        @Override
        protected String defaultName() {
            return "TEST";
        }

        private JsonWriter writer;

        @Override
        protected void initializeOutput() {
            this.writer = new JsonWriter(this.configG, this.sinkConfig, this.policy);
        }

        @Override
        protected void writePayloads(final OutputStream out, final List<IBaseDataObject> list,
                final Map<String, Object> params) throws IOException {
            this.writer.writeTo(out, list, params);
        }

        @Override
        public WriteStatus write(final List<IBaseDataObject> list, final Map<String, Object> params) {
            return write(list, params, new ByteArrayOutputStream());
        }

        byte[] writeTo(final IBaseDataObject d) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final Map<String, Object> params = new HashMap<>();
            final WriteStatus status = write(Collections.singletonList(d), params, out);
            assertEquals(WriteStatus.SUCCESS, status);
            return out.toByteArray();
        }
    }
}
