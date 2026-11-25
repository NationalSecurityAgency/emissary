package emissary.server.api;

import emissary.config.ServiceConfigGuide;
import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.core.Namespace;
import emissary.output.OutputDispatchPlace;
import emissary.output.policy.Decision;
import emissary.output.policy.OutputFilter;
import emissary.output.policy.request.ViewRequest;
import emissary.output.sink.ISink.WriteStatus;
import emissary.output.sink.JsonSink;
import emissary.server.mvc.EndpointTestBase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputTraceTest extends EndpointTestBase {
    private final ObjectMapper mapper = new ObjectMapper();
    private JsonSink sink;

    @BeforeEach
    public void bindDispatcher() throws Exception {
        configure("rule deny payload outputable=false", "rule deny param SECRET", "rule deny value EMAIL=*@example.com");
    }

    private void configure(final String... stages) throws Exception {
        configureWithDefault(null, stages);
    }

    private void configureWithDefault(final String defaultOutcome, final String... stages) throws Exception {
        final ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry("ELIDE_CONTENT", "ELIDE");
        final OutputDispatchPlace place = new OutputDispatchPlace(config);
        final ServiceConfigGuide sinkConfig = new ServiceConfigGuide();
        sinkConfig.addEntry("STRIP_PARAM_PREFIX", "APP_");
        if (defaultOutcome != null) {
            sinkConfig.addEntry("POLICY_DEFAULT", defaultOutcome);
        }
        for (final String stage : stages) {
            sinkConfig.addEntry("POLICY_STAGE", stage);
        }
        this.sink = new JsonSink() {
            @Override
            public void initializePrepared() {
                initializeOutput();
            }
        };
        this.sink.initialize(config, "JSON", sinkConfig);
        place.addSink(this.sink);
        Namespace.bind("OutputDispatchPlace", place);
    }

    @AfterEach
    public void unbindDispatcher() {
        Namespace.unbind("OutputDispatchPlace");
    }

    private Response post(final String json) {
        return target("output/OutputDispatchPlace/sinks/JSON/trace").request().post(Entity.json(json));
    }

    @Test
    void livePolicyTraceMatchesActualParameterOutput() throws Exception {
        try (Response response = post("{\"target\":\"parameterValue\",\"name\":\"EMAIL\",\"value\":\"person@example.com\",\"fileType\":\"PDF\"}")) {
            assertEquals(200, response.getStatus());
            final JsonNode trace = this.mapper.readTree(response.readEntity(String.class));
            assertFalse(trace.get("wouldEmit").asBoolean());
            assertTrue(trace.get("explanation").asText().contains("deny value EMAIL=*@example.com"));
            assertTrue(trace.get("steps").size() > 3);
            assertTrue(trace.get("complete").asBoolean());
            assertEquals("denylist", trace.get("posture").asText());
        }
        final IBaseDataObject data = DataObjectFactory.getInstance("data".getBytes(UTF_8), "test", "PDF");
        data.appendParameter("EMAIL", "person@example.com");
        data.appendParameter("EMAIL", "keep@elsewhere.org");
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertEquals(WriteStatus.SUCCESS, this.sink.write(data, new HashMap<>(), out));
        final JsonNode values = this.mapper.readTree(out.toByteArray()).get(0).get("parameters").get("EMAIL");
        assertEquals(1, values.size());
        assertEquals("keep@elsewhere.org", values.get(0).asText());
    }

    @Test
    void payloadAndKeyDenialsShortCircuitComponentQuestions() throws Exception {
        try (Response response = post("{\"target\":\"view\",\"name\":\"TEXT\",\"outputable\":false}")) {
            final JsonNode trace = this.mapper.readTree(response.readEntity(String.class));
            assertEquals(200, response.getStatus());
            assertFalse(trace.get("wouldEmit").asBoolean());
            assertTrue(trace.get("steps").toString().contains("NOT_EVALUATED"));
        }
        try (Response response = post("{\"target\":\"parameterValue\",\"name\":\"SECRET\",\"value\":\"anything\"}")) {
            final JsonNode trace = this.mapper.readTree(response.readEntity(String.class));
            assertFalse(trace.get("wouldEmit").asBoolean());
            assertTrue(trace.get("explanation").asText().contains("deny param SECRET"));
        }
    }

    @Test
    void nestedDenialParticipatesInTrace() throws Exception {
        try (Response response = post("{\"target\":\"view\",\"name\":\"TEXT\",\"members\":[{\"shortName\":\"child\",\"outputable\":false}]}")) {
            assertEquals(200, response.getStatus());
            final JsonNode trace = this.mapper.readTree(response.readEntity(String.class));
            assertFalse(trace.get("wouldEmit").asBoolean());
            assertTrue(trace.get("steps").toString().contains("payload:child"));
        }
    }

    @Test
    void reportsOutputNameTransformationAndContentPreprocessing() throws Exception {
        try (Response response = post("{\"target\":\"parameter\",\"name\":\"app_my-key\",\"form\":\"ELIDE\"}")) {
            final JsonNode trace = this.mapper.readTree(response.readEntity(String.class));
            assertEquals("app_my-key", trace.get("originalName").asText());
            assertEquals("MY_KEY", trace.get("outputName").asText());
            assertTrue(trace.get("preprocessing").get(0).asText().contains("ELIDE_CONTENT"));
        }
    }

    @Test
    void rejectsInvalidRequestsAndUnknownDestinations() {
        for (final String body : new String[] {"{}", "{\"target\":\"unknown\"}", "{\"target\":\"view\"}",
                "{\"target\":\"parameterValue\",\"name\":\"EMAIL\"}"}) {
            try (Response response = post(body)) {
                assertEquals(400, response.getStatus());
            }
        }
        try (Response response = target("output/missing/sinks/JSON/trace").request().post(Entity.json("{\"target\":\"payload\"}"))) {
            assertEquals(404, response.getStatus());
        }
        try (Response response = target("output/OutputDispatchPlace/sinks/missing/trace").request()
                .post(Entity.json("{\"target\":\"payload\"}"))) {
            assertEquals(404, response.getStatus());
        }
    }

    @Test
    void explicitNullRemainsAValueQuestion() throws Exception {
        configure("rule deny value FOO=null");
        try (Response response = post("{\"target\":\"parameterValue\",\"name\":\"FOO\",\"value\":null}")) {
            assertEquals(200, response.getStatus());
            assertFalse(this.mapper.readTree(response.readEntity(String.class)).get("wouldEmit").asBoolean());
        }
    }

    @Test
    void reportsAllowlistPosture() throws Exception {
        configureWithDefault("deny", "rule allow payload *");
        try (Response response = post("{\"target\":\"payload\"}")) {
            assertEquals(200, response.getStatus());
            final JsonNode trace = this.mapper.readTree(response.readEntity(String.class));
            assertTrue(trace.get("wouldEmit").asBoolean());
            assertEquals("allowlist", trace.get("posture").asText());
        }
    }

    @Test
    void appliesElidePreprocessingToNestedMembers() throws Exception {
        try (Response response = post("{\"target\":\"payload\",\"members\":[{\"shortName\":\"child\",\"form\":\"ELIDE\"}]}")) {
            assertEquals(200, response.getStatus());
            final JsonNode trace = this.mapper.readTree(response.readEntity(String.class));
            assertTrue(trace.get("preprocessing").toString().contains("payload.members[0]"));
        }
    }

    @Test
    void requiresCustomFilterContextAndUsesSuppliedBytes() throws Exception {
        configure("filter " + ViewSizeFilter.class.getName());
        try (Response response = post("{\"target\":\"view\",\"name\":\"TEXT\"}")) {
            assertEquals(422, response.getStatus());
            final JsonNode result = this.mapper.readTree(response.readEntity(String.class));
            assertFalse(result.get("complete").asBoolean());
            assertEquals("payload.views", result.get("missingContext").get(0).asText());
            assertEquals("denylist", result.get("posture").asText());
            assertFalse(result.has("wouldEmit"));
        }
        try (Response response = post("{\"target\":\"view\",\"name\":\"TEXT\",\"views\":{\"TEXT\":\"YWJj\"}}")) {
            assertEquals(200, response.getStatus());
            assertFalse(this.mapper.readTree(response.readEntity(String.class)).get("wouldEmit").asBoolean());
        }
    }

    @Test
    void rejectsTooManyMembers() throws Exception {
        final StringBuilder json = new StringBuilder("{\"target\":\"payload\",\"members\":[");
        for (int i = 0; i < OutputTrace.MAX_PAYLOADS; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"shortName\":\"child\"}");
        }
        json.append("]}");
        try (Response response = post(json.toString())) {
            assertEquals(413, response.getStatus());
            assertTrue(response.readEntity(String.class).contains("payloads"));
        }
    }

    @Test
    void rejectsOversizedRequestBodies() throws Exception {
        final String json = "{\"target\":\"payload\",\"content\":\""
                + "A".repeat((int) RequestSizeLimitFilter.MAX_REQUEST_BYTES) + "\"}";
        try (Response response = post(json)) {
            assertEquals(413, response.getStatus());
        }
    }

    public static final class ViewSizeFilter implements OutputFilter {
        @Override
        public String name() {
            return "view-size";
        }

        @Override
        public Set<String> requiredContext() {
            return Set.of("views");
        }

        @Override
        public Decision decide(final ViewRequest request) {
            final byte[] bytes = request.data().getAlternateView(request.viewName());
            return bytes != null && bytes.length > 2 ? Decision.deny(name(), "More than two bytes") : Decision.abstain(name());
        }
    }
}
