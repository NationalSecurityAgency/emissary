package emissary.output.formatter;

import emissary.config.ServiceConfigGuide;
import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Output filters combine with short-circuit AND semantics: filters run in order and stop at the first deny, and the
 * {@code EMIT} dimension veto (a {@code FilterEmit} installed first) denies a whole dimension like any other filter.
 */
class OutputFilteringTest extends UnitTest {

    private ServiceConfigGuide config;
    private AbstractFormatter f;
    private IBaseDataObject payload;

    @BeforeEach
    public void setup() {
        config = new ServiceConfigGuide();
        f = new AbstractFormatter() {
            @Override
            public void writeTo(final OutputStream out, final List<IBaseDataObject> list,
                    final Map<String, Object> params) {
                // filtering only; no serialization needed
            }
        };
        payload = DataObjectFactory.getInstance();
        payload.setData("data".getBytes(UTF_8));
        payload.setFileType("FTYPE");
    }

    @Test
    void testAllowAllByDefault() {
        f.initialize(config, "TEST", config);
        assertTrue(f.isMetadataAllowed("KEEP"), "parameter should be allowed");
        assertTrue(f.isContentAllowed(payload, "PrimaryView"), "view should be allowed");
    }

    @Test
    void testEmitMetadata() {
        config.addEntry("EMIT", "metadata");
        f.initialize(config, "TEST", config);
        assertTrue(f.isMetadataAllowed("KEEP"), "parameters should pass the metadata gate");
        assertFalse(f.isContentAllowed(payload, "PrimaryView"), "views should be denied by the gate");
    }

    @Test
    void testEmitContent() {
        config.addEntry("EMIT", "content");
        f.initialize(config, "TEST", config);
        assertFalse(f.isMetadataAllowed("KEEP"), "parameters should be denied by the gate");
        assertTrue(f.isContentAllowed(payload, "PrimaryView"), "views should pass the content gate");
    }

    @Test
    void testAllowlist() {
        config.addEntry("OUTPUT_FILTER", "emissary.output.formatter.filter.FilterOutput");
        config.addEntry("FILTER_MODE", "allow");
        config.addEntry("VIEWS", "JSON_*");
        config.addEntry("PARAMS", "FOO");
        f.initialize(config, "TEST", config);
        payload.addAlternateView("JSON_PRETTY", "".getBytes(UTF_8));

        assertTrue(f.isContentAllowed(payload, "JSON_PRETTY"), "listed view should be allowed");
        assertFalse(f.isContentAllowed(payload, "PrimaryView"), "unlisted view should be denied");
        assertTrue(f.isMetadataAllowed("FOO"), "listed parameter should be allowed");
        assertFalse(f.isMetadataAllowed("BAR"), "unlisted parameter should be denied");
    }

    @Test
    void testShortCircuit() {
        config.addEntry("OUTPUT_FILTER", "emissary.output.formatter.filter.FilterOutput");
        config.addEntry("PARAMS", "DROP");
        config.addEntry("OUTPUT_FILTER", CountingOutputFilter.class.getName());
        f.initialize(config, "TEST", config);

        CountingOutputFilter.calls.set(0);
        assertFalse(f.isMetadataAllowed("DROP"), "denied parameter should be dropped");
        assertEquals(0, CountingOutputFilter.calls.get(), "filters after the denying one should be skipped");

        CountingOutputFilter.calls.set(0);
        assertTrue(f.isMetadataAllowed("KEEP"), "non-denied parameter should pass");
        assertEquals(1, CountingOutputFilter.calls.get(), "a non-denied parameter should reach the filter after the list");
    }
}
