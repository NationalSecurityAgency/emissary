package emissary.output.sink;

import emissary.config.ServiceConfigGuide;
import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.test.core.junit5.UnitTest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sink and formatter filters configured from one shared config file coexist without clobbering each other: the sink
 * owns whole-payload eligibility while the formatter selects views and parameters.
 */
class SinkAndFormatterFiltersTest extends UnitTest {

    private static IBaseDataObject payload(final String fileType, final int children) {
        IBaseDataObject p = DataObjectFactory.getInstance();
        p.setFileType(fileType);
        p.setNumChildren(children);
        p.setData("primary data".getBytes(UTF_8));
        p.addAlternateView("KEEPME", "keepdata".getBytes(UTF_8));
        p.addAlternateView("UNWANTED", "junkdata".getBytes(UTF_8));
        p.appendParameter("GOOD_FIELD", "kept");
        p.appendParameter("BAD_FIELD", "dropped");
        return p;
    }

    private static ServiceConfigGuide config(final Path tmpDir) {
        ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry("OUTPUT_PATH", tmpDir.toAbsolutePath().toString());
        config.addEntry(AbstractSink.PAYLOAD_FILTER, "emissary.output.sink.filter.PayloadFilterCondition");
        config.addEntry(AbstractSink.PAYLOAD_FILTER, "emissary.output.sink.filter.MaxChildrenFilter");
        config.addEntry("DENY_FILETYPES", "RARE");
        config.addEntry("MAX_CHILDREN", "1");
        config.addEntry("OUTPUT_FILTER", "emissary.output.formatter.filter.FilterOutput");
        config.addEntry("VIEWS", "UNWANTED");
        config.addEntry("PARAMS", "BAD_FIELD");
        return config;
    }

    private static String writeString(final JsonSink sink, final IBaseDataObject p) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        sink.write(p, new HashMap<>(), buf);
        return buf.toString(UTF_8);
    }

    private static JsonNode writeThrough(final JsonSink sink, final IBaseDataObject p) throws Exception {
        return new ObjectMapper().readTree(writeString(sink, p)).get(0);
    }

    @Test
    void testLayersCoexist(@TempDir final Path tmpDir) throws Exception {
        JsonSink sink = new JsonSink();
        sink.initialize(config(tmpDir), null, config(tmpDir));

        IBaseDataObject eligible = payload("COMMON", 0);
        assertTrue(sink.accept(eligible), "payload on no sink deny list should pass the sink");
        JsonNode json = writeThrough(sink, eligible);
        assertTrue(json.has("payload"), "primary view should be allowed by the content filter");
        assertTrue(json.has("views"), "allowed alternate views should be emitted");
        assertTrue(json.get("views").has("KEEPME"), "view not on the content deny list should be emitted");
        assertFalse(json.get("views").has("UNWANTED"), "denied view should be stripped, not the whole payload");
        assertTrue(json.get("parameters").has("GOOD_FIELD"), "parameter not on the metadata deny list should be emitted");
        assertFalse(json.get("parameters").has("BAD_FIELD"), "denied parameter should be stripped");

        IBaseDataObject deniedType = payload("RARE", 0);
        assertFalse(sink.accept(deniedType), "sink filetype deny list should reject the payload");
        assertEquals("", writeString(sink, deniedType), "nothing of a sink-denied payload should reach the formatter");

        IBaseDataObject deniedFamily = payload("COMMON", 2);
        assertFalse(sink.accept(deniedFamily), "children over the sink threshold should reject the payload");
    }

    @Test
    void testFamilyEligibility(@TempDir final Path tmpDir) {
        JsonSink sink = new JsonSink();
        sink.initialize(config(tmpDir), null, config(tmpDir));

        IBaseDataObject tld = DataObjectFactory.getInstance();
        tld.setFileType("COMMON");
        tld.setNumChildren(1);
        IBaseDataObject child = DataObjectFactory.getInstance();
        child.setFileType("COMMON");
        assertTrue(sink.accept(List.of(tld, child)), "family within the child threshold should be allowed");

        IBaseDataObject secondChild = DataObjectFactory.getInstance();
        secondChild.setFileType("COMMON");
        assertFalse(sink.accept(List.of(tld, child, secondChild)), "family over the child threshold should be denied");
    }
}
