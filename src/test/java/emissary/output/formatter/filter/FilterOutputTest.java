package emissary.output.formatter.filter;

import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.core.DataObjectFactory;
import emissary.core.EmissaryRuntimeException;
import emissary.core.IBaseDataObject;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link FilterOutput}'s unified view/parameter list surface with one {@code FILTER_MODE} posture.
 */
class FilterOutputTest extends UnitTest {

    FilterOutput getFilter(final Configurator config) {
        FilterOutput filter = new FilterOutput();
        filter.configure(config);
        return filter;
    }

    IBaseDataObject payload(final String filetype, final List<String> altViews) {
        IBaseDataObject p = DataObjectFactory.getInstance();
        p.setData("".getBytes(UTF_8));
        p.setFileType(filetype);
        p.setFilename("");
        altViews.forEach(view -> p.addAlternateView(view, "".getBytes(UTF_8)));
        return p;
    }

    @Test
    void testAllowAllByDefault() {
        FilterOutput filter = getFilter(new ServiceConfigGuide());
        IBaseDataObject d = payload("JSON", Arrays.asList("JSON_PRETTY", "Geo"));

        assertTrue(filter.test(d, OutputItem.view("PrimaryView")), "primary view should be allowed by default");
        assertTrue(filter.test(d, OutputItem.view("JSON_PRETTY")), "alt view should be allowed by default");
        assertTrue(filter.test(null, OutputItem.parameter("KEEP")), "parameter should be allowed by default");
        assertTrue(filter.test(null, OutputItem.parameter("KEEP", "v")), "parameter value should be allowed by default");
    }

    @Test
    void testDeniesView() {
        Configurator config = new ServiceConfigGuide();
        Arrays.asList("JSON_ML", "JSON.GeoJSON", "JSON_LANG_*").forEach(entry -> config.addEntry("VIEWS", entry));

        FilterOutput filter = getFilter(config);
        IBaseDataObject d = payload("JSON", Arrays.asList("JSON_PRETTY", "JSON_ML", "Geo", "GeoJSON", "JSON_LANG_ENG"));

        assertFalse(filter.test(d, OutputItem.view("JSON_ML")), "view on the list should be denied");
        assertFalse(filter.test(d, OutputItem.view("GeoJSON")), "filetype.view deny should apply for matching filetype");
        assertFalse(filter.test(d, OutputItem.view("JSON_LANG_ENG")), "wildcard deny should apply");

        for (String allowed : Arrays.asList("PrimaryView", "JSON_PRETTY", "Geo")) {
            assertTrue(filter.test(d, OutputItem.view(allowed)), allowed + " should be allowed");
        }

        assertTrue(filter.test(payload("XML", Collections.emptyList()), OutputItem.view("GeoJSON")),
                "filetype.view deny is filetype specific");
    }

    @Test
    void testAllowsOnlyListedViews() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("FILTER_MODE", "allow");
        Arrays.asList("JSON_ML", "JSON.GeoJSON", "JSON_LANG_*", "XML.JSON_1_0").forEach(entry -> config.addEntry("VIEWS", entry));

        FilterOutput filter = getFilter(config);
        IBaseDataObject d = payload("JSON", Arrays.asList("JSON_PRETTY", "JSON_ML", "Geo", "GeoJSON", "JSON_LANG_ENG"));

        assertTrue(filter.test(d, OutputItem.view("JSON_ML")), "view on the list should be allowed");
        assertTrue(filter.test(d, OutputItem.view("GeoJSON")), "filetype.view allow should apply for matching filetype");
        assertTrue(filter.test(d, OutputItem.view("JSON_LANG_ENG")), "wildcard allow should apply");

        for (String denied : Arrays.asList("PrimaryView", "JSON_PRETTY", "Geo")) {
            assertFalse(filter.test(d, OutputItem.view(denied)), denied + " should be denied");
        }

        IBaseDataObject xml = payload("XML", Collections.emptyList());
        assertTrue(filter.test(xml, OutputItem.view("JSON_1_0")), "filetype.view allow should apply for matching filetype");
        assertFalse(filter.test(xml, OutputItem.view("JSON_PRETTY")), "unlisted view should be denied regardless of filetype");
    }

    @Test
    void testDenyAllViews() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("VIEWS", "*");

        FilterOutput filter = getFilter(config);
        IBaseDataObject d = payload("JSON", List.of("JSON_PRETTY"));

        assertFalse(filter.test(d, OutputItem.view("PrimaryView")), "primary view should be denied");
        assertFalse(filter.test(d, OutputItem.view("JSON_PRETTY")), "alternate view should be denied");
        assertTrue(filter.test(null, OutputItem.parameter("KEEP")), "parameters should still pass");
    }

    @Test
    void testParameterDeny() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("PARAMS", "DROP");
        config.addEntry("PARAMS", "DROP_*");

        FilterOutput filter = getFilter(config);

        assertFalse(filter.test(null, OutputItem.parameter("DROP")), "listed field should be denied");
        assertFalse(filter.test(null, OutputItem.parameter("DROP_ME")), "wildcard field should be denied");
        assertTrue(filter.test(null, OutputItem.parameter("KEEP")), "unlisted field should be allowed");
        assertTrue(filter.test(null, OutputItem.parameter("KEEP_ME")), "unlisted wildcard field should be allowed");
    }

    @Test
    void testParameterValueDeny() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("PARAM_VALUE_KEY", "SECRET");

        FilterOutput filter = getFilter(config);

        assertTrue(filter.test(null, OutputItem.parameter("KEY", "public")), "non-denied value should be allowed");
        assertFalse(filter.test(null, OutputItem.parameter("KEY", "SECRET")), "denied value should be rejected");
        assertTrue(filter.test(null, OutputItem.parameter("KEY")), "key-level decision should be unaffected");
    }

    @Test
    void testParameterAllowOnly() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("FILTER_MODE", "allow");
        config.addEntry("PARAMS", "KEEP");
        config.addEntry("PARAMS", "KEEP_*");

        FilterOutput filter = getFilter(config);

        assertTrue(filter.test(null, OutputItem.parameter("KEEP")), "listed field should be allowed");
        assertTrue(filter.test(null, OutputItem.parameter("KEEP_ME")), "wildcard field should be allowed");
        assertFalse(filter.test(null, OutputItem.parameter("DROP")), "unlisted field should be denied");
        assertFalse(filter.test(null, OutputItem.parameter("DROP_ME")), "unlisted wildcard field should be denied");
    }

    @Test
    void testParameterAllowStar() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("FILTER_MODE", "allow");
        config.addEntry("PARAMS", "*");

        FilterOutput filter = getFilter(config);

        assertTrue(filter.test(null, OutputItem.parameter("KEEP")), "all fields should be allowed when '*' is listed");
        assertTrue(filter.test(null, OutputItem.parameter("DROP")), "all fields should be allowed when '*' is listed");
    }

    @Test
    void testDenyAllParams() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("PARAMS", "*");

        FilterOutput filter = getFilter(config);

        assertFalse(filter.test(null, OutputItem.parameter("ANY")), "every field should be denied");
        assertFalse(filter.test(null, OutputItem.parameter("ANY", "value")), "every field should be denied");
        assertTrue(filter.test(payload("JSON", List.of("TXT")), OutputItem.view("TXT")), "views should still pass");
    }

    @Test
    void testStripPrefix() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("STRIP_PARAM_PREFIX", "APP_");
        config.addEntry("PARAMS", "FOO");

        FilterOutput filter = getFilter(config);

        assertFalse(filter.test(null, OutputItem.parameter("APP_FOO")), "prefixed field should be matched after stripping");
        assertTrue(filter.test(null, OutputItem.parameter("APP_KEEP")), "non-matching prefixed field should be allowed");
    }

    @Test
    void testBadViewConfigs() {
        List<String> invalidEntries = Arrays.asList(
                "*.view", ".view", "*.view*", "type.", "type.view.view.view", "view**");
        for (String entry : invalidEntries) {
            final Configurator config = new ServiceConfigGuide();
            config.addEntry("VIEWS", entry);
            EmissaryRuntimeException e = assertThrows(EmissaryRuntimeException.class, () -> getFilter(config));
            assertTrue(e.getMessage().contains("Invalid filter configuration"));
        }
    }

    @Test
    void testDefaultPatterns() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("VIEWS", "JSON_ML");
        FilterOutput filter = getFilter(config);
        assertEquals("^[a-zA-Z0-9_\\-]+$", filter.getFiletypeFormat());
        assertEquals("^[a-zA-Z0-9_\\-]+(\\.[a-zA-Z0-9_\\-]+)?\\*?$", filter.getViewNameFormat());
    }

    @Test
    void testDescribe() {
        Configurator config = new ServiceConfigGuide();
        Arrays.asList("JSON_ML", "JSON_LANG_*", "XML.JSON_1_0").forEach(entry -> config.addEntry("VIEWS", entry));
        config.addEntry("PARAMS", "FOO");
        config.addEntry("PARAMS", "PRIVATE_*");
        config.addEntry("STRIP_PARAM_PREFIX", "APP_");

        String detail = getFilter(config).describe(true);
        assertTrue(detail.contains("FILTER_MODE = deny"), "detail should render the polarity");
        assertTrue(detail.contains("JSON_ML"), "detail should render configured view entries");
        assertTrue(detail.contains("JSON_LANG_*"), "detail should render configured wildcard view entries");
        assertTrue(detail.contains("XML.JSON_1_0"), "detail should render filetype.view entries");
        assertTrue(detail.contains("PARAMS = FOO"), "detail should render configured parameter entries");
        assertTrue(detail.contains("PARAMS = PRIVATE_*"), "detail should render wildcard parameter entries");
        assertTrue(detail.contains("STRIP_PARAM_PREFIX = APP_"), "detail should render configured strip prefixes");
    }
}
