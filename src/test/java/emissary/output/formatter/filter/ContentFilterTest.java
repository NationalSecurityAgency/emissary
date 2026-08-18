package emissary.output.formatter.filter;

import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.core.DataObjectFactory;
import emissary.core.EmissaryRuntimeException;
import emissary.core.IBaseDataObject;
import emissary.output.formatter.filter.content.ContentAllowFilter;
import emissary.output.formatter.filter.content.ContentDenyFilter;
import emissary.output.formatter.filter.content.ContentFilter;
import emissary.output.formatter.filter.content.ContentFilterChain;
import emissary.output.formatter.filter.content.sample.ContentBinaryViewFilter;
import emissary.output.formatter.filter.content.sample.ContentMaxViewSizeFilter;
import emissary.output.formatter.filter.content.sample.ContentRequiredFieldFilter;
import emissary.output.formatter.filter.metadata.sample.MetadataFieldPatternFilter;
import emissary.output.formatter.filter.util.FilterClassFactory;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentFilterTest extends UnitTest {

    ContentFilter getFilter(final Configurator config) {
        return FilterClassFactory.createFilterChain(config, FilterConfigKeys.CONTENT_FILTER, ContentFilter.class,
                "ContentFilter", ContentFilterChain::new);
    }

    IBaseDataObject getTestPayload(final String filetype, final List<String> altViews) {
        IBaseDataObject payload = DataObjectFactory.getInstance();
        payload.setData("".getBytes(UTF_8));
        payload.setFileType(filetype);
        payload.setFilename("");
        altViews.forEach(viewName -> payload.addAlternateView(viewName, "".getBytes(UTF_8)));
        return payload;
    }

    @Test
    void testContentAcceptsEverythingByDefault() {
        ContentFilter filter = getFilter(new ServiceConfigGuide());
        assertInstanceOf(ContentFilterChain.class, filter);
        assertTrue(((ContentFilterChain) filter).getFilters().isEmpty(), "no filters should be configured by default");

        IBaseDataObject payload = getTestPayload("JSON", Arrays.asList("JSON_PRETTY", "Geo", "JSON_1_0"));
        assertTrue(filter.allows(payload, "PrimaryView"), "Primary view should be allowed by default");
        assertTrue(filter.allows(payload, "JSON_PRETTY"), "Alt view should be allowed by default");
        assertTrue(filter.allows(payload, "JSON_1_0"), "Alt view should be allowed by default");

        IBaseDataObject xml = getTestPayload("XML", Collections.emptyList());
        assertTrue(filter.allows(xml, "JSON_1_0"), "View not on deny list should be allowed");
    }

    @Test
    void testContentDeniesView() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("CONTENT_FILTER", ContentDenyFilter.class.getName());
        Arrays.asList("JSON_ML", "JSON.GeoJSON", "JSON_LANG_*").forEach(entry -> config.addEntry("DENYLIST", entry));
        ContentFilterChain filter = (ContentFilterChain) getFilter(config);
        assertInstanceOf(ContentDenyFilter.class, filter.getFilters().get(0), "declared deny filter should lead the chain");

        IBaseDataObject payload = getTestPayload("JSON", Arrays.asList("JSON_PRETTY", "JSON_ML", "Geo", "GeoJSON", "JSON_LANG_ENG"));

        assertFalse(filter.allows(payload, "JSON_ML"), "view on deny list should be denied");
        assertFalse(filter.allows(payload, "GeoJSON"), "filetype.view deny should apply for matching filetype");
        assertFalse(filter.allows(payload, "JSON_LANG_ENG"), "wildcard deny should apply");

        for (String allowed : Arrays.asList("PrimaryView", "JSON_PRETTY", "Geo")) {
            assertTrue(filter.allows(payload, allowed), allowed + " should be allowed");
        }

        assertTrue(filter.allows(getTestPayload("XML", Collections.emptyList()), "GeoJSON"),
                "filetype.view deny is filetype specific");
    }

    @Test
    void testContentAllowsOnlyListedViews() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("CONTENT_FILTER", ContentAllowFilter.class.getName());
        Arrays.asList("JSON_ML", "JSON.GeoJSON", "JSON_LANG_*", "XML.JSON_1_0").forEach(entry -> config.addEntry("ALLOWLIST", entry));
        ContentFilterChain filter = (ContentFilterChain) getFilter(config);
        assertInstanceOf(ContentAllowFilter.class, filter.getFilters().get(0), "declared allow filter should lead the chain");

        IBaseDataObject payload = getTestPayload("JSON", Arrays.asList("JSON_PRETTY", "JSON_ML", "Geo", "GeoJSON", "JSON_LANG_ENG"));

        assertTrue(filter.allows(payload, "JSON_ML"), "view on allow list should be allowed");
        assertTrue(filter.allows(payload, "GeoJSON"), "filetype.view allow should apply for matching filetype");
        assertTrue(filter.allows(payload, "JSON_LANG_ENG"), "wildcard allow should apply");

        for (String denied : Arrays.asList("PrimaryView", "JSON_PRETTY", "Geo")) {
            assertFalse(filter.allows(payload, denied), denied + " should be denied");
        }

        IBaseDataObject xml = getTestPayload("XML", Collections.emptyList());
        assertTrue(filter.allows(xml, "JSON_1_0"), "filetype.view allow should apply for matching filetype");
        assertFalse(filter.allows(xml, "JSON_PRETTY"), "unlisted view should be denied regardless of filetype");
    }

    @Test
    void testDenyAndAllowFiltersCanCoexist() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("CONTENT_FILTER", ContentDenyFilter.class.getName());
        config.addEntry("CONTENT_FILTER", ContentAllowFilter.class.getName());
        config.addEntry("DENYLIST", "JSON_ML");
        config.addEntry("ALLOWLIST", "JSON_*");

        ContentFilterChain filter = (ContentFilterChain) getFilter(config);
        assertInstanceOf(ContentDenyFilter.class, filter.getFilters().get(0), "deny filter should be first in declared order");
        assertInstanceOf(ContentAllowFilter.class, filter.getFilters().get(1), "allow filter should follow in declared order");

        IBaseDataObject payload = getTestPayload("JSON", Arrays.asList("JSON_PRETTY", "JSON_ML", "Geo"));
        assertFalse(filter.allows(payload, "JSON_ML"), "deny list denial should win");
        assertTrue(filter.allows(payload, "JSON_PRETTY"), "view allowed by both should pass");
        assertFalse(filter.allows(payload, "Geo"), "unlisted view should be denied by the allow filter");
    }

    @Test
    void testIncorrectConfigs() {
        List<String> invalidEntries = Arrays.asList(
                "*.view", ".view", "*.view*", "type.", "type.view.view.view", "view**");
        for (String entry : invalidEntries) {
            final Configurator config = new ServiceConfigGuide();
            config.addEntry("CONTENT_FILTER", ContentDenyFilter.class.getName());
            config.addEntry("DENYLIST", entry);
            EmissaryRuntimeException e = assertThrows(EmissaryRuntimeException.class, () -> getFilter(config));
            assertTrue(e.getMessage().contains("Invalid filter configuration"));
        }

        for (String entry : invalidEntries) {
            final Configurator config = new ServiceConfigGuide();
            config.addEntry("CONTENT_FILTER", ContentAllowFilter.class.getName());
            config.addEntry("ALLOWLIST", entry);
            EmissaryRuntimeException e = assertThrows(EmissaryRuntimeException.class, () -> getFilter(config));
            assertTrue(e.getMessage().contains("Invalid filter configuration"));
        }
    }

    @Test
    void testDefaultRegexPatterns() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("CONTENT_FILTER", ContentDenyFilter.class.getName());
        config.addEntry("DENYLIST", "JSON_ML");
        ContentFilter list = ((ContentFilterChain) getFilter(config)).getFilters().get(0);
        assertEquals("^[a-zA-Z0-9_\\-]+$", list.getFiletypeFormat());
        assertEquals("^[a-zA-Z0-9_\\-]+(\\.[a-zA-Z0-9_\\-]+)?\\*?$", list.getViewNameFormat());
    }

    @Test
    void testContentMaxViewSize() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("CONTENT_FILTER", ContentMaxViewSizeFilter.class.getName());
        config.addEntry("CONTENT_MAX_VIEW_SIZE", "5");
        ContentFilterChain filter = (ContentFilterChain) getFilter(config);
        assertInstanceOf(ContentMaxViewSizeFilter.class, filter.getFilters().get(0), "declared size filter should be layered on the chain");

        IBaseDataObject payload = getTestPayload("JSON", Arrays.asList("SMALL", "LARGE"));
        payload.addAlternateView("SMALL", "abc".getBytes(UTF_8));
        payload.addAlternateView("LARGE", "abcdefghijk".getBytes(UTF_8));

        assertTrue(filter.allows(payload, "SMALL"), "view within the size limit should be allowed");
        assertFalse(filter.allows(payload, "LARGE"), "oversized view should be denied");
        assertTrue(filter.allows(payload, "PrimaryView"), "missing alternate view should be allowed");
    }

    @Test
    void testContentRejectsBinaryViews() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("CONTENT_FILTER", ContentBinaryViewFilter.class.getName());
        config.addEntry("CONTENT_REJECT_BINARY_VIEWS", "true");
        ContentFilterChain filter = (ContentFilterChain) getFilter(config);
        assertInstanceOf(ContentBinaryViewFilter.class, filter.getFilters().get(0), "declared binary filter should be layered on the chain");

        IBaseDataObject payload = getTestPayload("JSON", Arrays.asList("TEXT", "BINARY"));
        payload.addAlternateView("TEXT", "hello world".getBytes(UTF_8));
        payload.addAlternateView("BINARY", new byte[] {0x01, 0x02, 0x03, 0x04, 0x05});

        assertTrue(filter.allows(payload, "TEXT"), "printable view should be allowed");
        assertFalse(filter.allows(payload, "BINARY"), "binary view should be denied");
        assertTrue(filter.allows(payload, "PrimaryView"), "missing alternate view should be allowed");
    }

    @Test
    void testRuntimeFiltersLayerOnDenyList() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("CONTENT_FILTER", ContentDenyFilter.class.getName());
        config.addEntry("DENYLIST", "JSON_ML");
        config.addEntry("CONTENT_FILTER", ContentMaxViewSizeFilter.class.getName());
        config.addEntry("CONTENT_MAX_VIEW_SIZE", "5");

        ContentFilterChain filter = (ContentFilterChain) getFilter(config);
        assertInstanceOf(ContentDenyFilter.class, filter.getFilters().get(0), "deny list should lead the chain");
        assertInstanceOf(ContentMaxViewSizeFilter.class, filter.getFilters().get(1), "declared filter should follow the deny list");

        IBaseDataObject payload = getTestPayload("JSON", Arrays.asList("JSON_ML", "SMALL", "LARGE"));
        payload.addAlternateView("SMALL", "abc".getBytes(UTF_8));
        payload.addAlternateView("LARGE", "abcdefghijk".getBytes(UTF_8));

        assertFalse(filter.allows(payload, "JSON_ML"), "deny list denial should still apply");
        assertFalse(filter.allows(payload, "LARGE"), "declared filter denial should apply");
        assertTrue(filter.allows(payload, "SMALL"), "view allowed by both filters should pass");
    }

    @Test
    void testRuntimeConfiguredContentFilterIsTypeChecked() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("CONTENT_FILTER", MetadataFieldPatternFilter.class.getName());
        EmissaryRuntimeException e = assertThrows(EmissaryRuntimeException.class, () -> getFilter(config));
        assertTrue(e.getMessage().contains("is not a ContentFilter"), "wrong filter type should be rejected");
    }

    @Test
    void testContentDenyAll() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("CONTENT_FILTER", ContentDenyFilter.class.getName());
        config.addEntry("DENYLIST", "*");
        ContentFilterChain filter = (ContentFilterChain) getFilter(config);
        assertInstanceOf(ContentDenyFilter.class, filter.getFilters().get(0), "deny list should lead the chain");

        IBaseDataObject payload = getTestPayload("JSON", List.of("JSON_PRETTY"));
        assertFalse(filter.allows(payload, "PrimaryView"), "primary view should be denied");
        assertFalse(filter.allows(payload, "JSON_PRETTY"), "alternate view should be denied");
    }

    @Test
    void testContentBinaryClueFromPayloadParameters() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("CONTENT_FILTER", ContentBinaryViewFilter.class.getName());
        config.addEntry("CONTENT_REJECT_BINARY_VIEWS", "true");
        config.addEntry("CONTENT_BINARY_CHECK_PARAMS", "MIME_TYPE");
        ContentFilterChain filter = (ContentFilterChain) getFilter(config);

        IBaseDataObject binaryClue = getTestPayload("JSON", Collections.emptyList());
        binaryClue.addAlternateView("VIEW", "printable text".getBytes(UTF_8));
        binaryClue.appendParameter("MIME_TYPE", "application/octet-stream");
        assertFalse(filter.allows(binaryClue, "VIEW"), "binary metadata clue should deny without scanning");

        IBaseDataObject textClue = getTestPayload("JSON", Collections.emptyList());
        textClue.addAlternateView("VIEW", new byte[] {0x01, 0x02});
        textClue.appendParameter("MIME_TYPE", "text/plain");
        assertTrue(filter.allows(textClue, "VIEW"), "text metadata clue should allow without scanning");

        IBaseDataObject noClue = getTestPayload("JSON", Collections.emptyList());
        noClue.addAlternateView("VIEW", new byte[] {0x01, 0x02});
        assertFalse(filter.allows(noClue, "VIEW"), "no metadata clue should fall back to a byte scan");
    }

    @Test
    void testContentRequiredFields() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("CONTENT_FILTER", ContentRequiredFieldFilter.class.getName());
        config.addEntry("CONTENT_REQUIRED_FIELDS", "RFC822_HEADER");
        config.addEntry("CONTENT_REQUIRED_FIELDS", "XSL_TITLE");
        ContentFilterChain filter = (ContentFilterChain) getFilter(config);
        assertInstanceOf(ContentRequiredFieldFilter.class, filter.getFilters().get(0),
                "declared field-gate filter should be layered on the chain");

        IBaseDataObject payload = getTestPayload("JSON", List.of("HTML_CLEANED"));
        payload.appendParameter("RFC822_HEADER", "from: someone");
        assertTrue(filter.allows(payload, "HTML_CLEANED"), "view should be emitted when any required field exists");

        IBaseDataObject other = getTestPayload("JSON", List.of("HTML_CLEANED"));
        other.appendParameter("XSL_TITLE", "title");
        assertTrue(filter.allows(other, "HTML_CLEANED"), "any configured field should satisfy the gate");

        IBaseDataObject none = getTestPayload("JSON", List.of("HTML_CLEANED"));
        none.appendParameter("UNRELATED", "value");
        assertFalse(filter.allows(none, "HTML_CLEANED"), "view should be withheld when no required field exists");
        assertTrue(filter.allows(none, "PrimaryView"), "missing alternate view should be allowed");
    }

    @Test
    void testFilterOrderFollowsDeclaredOrder() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("CONTENT_FILTER", ContentRequiredFieldFilter.class.getName());
        config.addEntry("CONTENT_FILTER", ContentDenyFilter.class.getName());
        config.addEntry("CONTENT_REQUIRED_FIELDS", "PRIORITY");
        config.addEntry("DENYLIST", "JSON_ML");

        ContentFilterChain filter = (ContentFilterChain) getFilter(config);
        assertInstanceOf(ContentRequiredFieldFilter.class, filter.getFilters().get(0),
                "filter declared before the deny filter should run first");
        assertInstanceOf(ContentDenyFilter.class, filter.getFilters().get(1), "deny filter should run second");

        IBaseDataObject payload = getTestPayload("JSON", Arrays.asList("PRIORITY", "JSON_ML"));
        payload.appendParameter("PRIORITY", "high");
        assertFalse(filter.allows(payload, "JSON_ML"), "deny list denial should still apply");
    }

    @Test
    void testChainShortCircuitsOnDenial() {
        AtomicInteger firstCalls = new AtomicInteger();
        AtomicInteger secondCalls = new AtomicInteger();
        ContentFilterChain chain = new ContentFilterChain(Arrays.asList(
                new ContentFilter() {
                    @Override
                    public boolean allows(final IBaseDataObject d, final String viewName) {
                        firstCalls.incrementAndGet();
                        return false;
                    }
                },
                new ContentFilter() {
                    @Override
                    public boolean allows(final IBaseDataObject d, final String viewName) {
                        secondCalls.incrementAndGet();
                        return true;
                    }
                }));

        IBaseDataObject payload = getTestPayload("JSON", Collections.emptyList());
        assertFalse(chain.allows(payload, "JSON_PRETTY"), "first filter denial should fail the chain");
        assertEquals(1, firstCalls.get(), "first filter should be consulted once");
        assertEquals(0, secondCalls.get(), "second filter must be skipped on short-circuit");
    }

    @Test
    void testChainAllowsWhenAllFiltersAllow() {
        ContentFilterChain chain = new ContentFilterChain(Arrays.asList(
                new ContentFilter() {
                    @Override
                    public boolean allows(final IBaseDataObject d, final String viewName) {
                        return true;
                    }
                },
                new ContentFilter() {
                    @Override
                    public boolean allows(final IBaseDataObject d, final String viewName) {
                        return true;
                    }
                }));

        IBaseDataObject payload = getTestPayload("JSON", Collections.emptyList());
        assertTrue(chain.allows(payload, "JSON_PRETTY"), "chain should allow when every filter allows");
    }
}
