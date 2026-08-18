package emissary.output.formatter;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentDenyListTest extends UnitTest {

    ContentDenyList getDenyList(final Configurator config) {
        ContentDenyList list = new ContentDenyList();
        list.configure(config);
        return list;
    }

    IBaseDataObject getTestPayload(final String filetype, final List<String> altViews) {
        IBaseDataObject payload = DataObjectFactory.getInstance();
        payload.setData("".getBytes());
        payload.setFileType(filetype);
        payload.setFilename("");
        altViews.forEach(viewName -> payload.addAlternateView(viewName, "".getBytes()));
        return payload;
    }

    @Test
    void testContentAcceptsEverythingByDefault() {
        ContentDenyList list = getDenyList(new ServiceConfigGuide());
        IBaseDataObject payload = getTestPayload("JSON", Arrays.asList("JSON_PRETTY", "Geo", "JSON_1_0"));
        assertTrue(list.isAllowed(payload, "PrimaryView"), "Primary view should be allowed by default");
        assertTrue(list.isAllowed(payload, "JSON_PRETTY"), "Alt view should be allowed by default");
        assertTrue(list.isAllowed(payload, "JSON_1_0"), "Alt view should be allowed by default");

        IBaseDataObject xml = getTestPayload("XML", Collections.emptyList());
        assertTrue(list.isAllowed(xml, "JSON_1_0"), "View not on deny list should be allowed");
    }

    @Test
    void testContentDeniesView() {
        Configurator config = new ServiceConfigGuide();
        Arrays.asList("JSON_ML", "JSON.GeoJSON", "JSON_LANG_*").forEach(entry -> config.addEntry("DENYLIST", entry));
        ContentDenyList list = getDenyList(config);

        IBaseDataObject payload = getTestPayload("JSON", Arrays.asList("JSON_PRETTY", "JSON_ML", "Geo", "GeoJSON", "JSON_LANG_ENG"));

        assertFalse(list.isAllowed(payload, "JSON_ML"), "view on deny list should be denied");
        assertFalse(list.isAllowed(payload, "GeoJSON"), "filetype.view deny should apply for matching filetype");
        assertFalse(list.isAllowed(payload, "JSON_LANG_ENG"), "wildcard deny should apply");

        for (String allowed : Arrays.asList("PrimaryView", "JSON_PRETTY", "Geo")) {
            assertTrue(list.isAllowed(payload, allowed), allowed + " should be allowed");
        }

        assertTrue(list.isAllowed(getTestPayload("XML", Collections.emptyList()), "GeoJSON"),
                "filetype.view deny is filetype specific");
    }

    @Test
    void testIncorrectConfigs() {
        List<String> invalidEntries = Arrays.asList(
                "*.view", ".view", "*.view*", "type.", "*", "type.view.view.view", "view**");
        for (String entry : invalidEntries) {
            final Configurator config = new ServiceConfigGuide();
            config.addEntry("DENYLIST", entry);
            EmissaryRuntimeException e = assertThrows(
                    EmissaryRuntimeException.class,
                    () -> getDenyList(config));
            assertTrue(e.getMessage().contains("Invalid filter configuration"));
        }
    }

    @Test
    void testDefaultRegexPatterns() {
        ContentDenyList list = getDenyList(new ServiceConfigGuide());
        assertEquals("^[a-zA-Z0-9_\\-]+$", list.getDenylistFiletypeFormat());
        assertEquals("^[a-zA-Z0-9_\\-]+(\\.[a-zA-Z0-9_\\-]+)?\\*?$", list.getDenylistViewNameFormat());
    }
}
