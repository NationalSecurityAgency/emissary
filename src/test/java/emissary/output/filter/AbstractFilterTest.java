package emissary.output.filter;

import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.core.DataObjectFactory;
import emissary.core.EmissaryRuntimeException;
import emissary.core.IBaseDataObject;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AbstractFilterTest extends UnitTest {

    AbstractFilter getAbstractFilterInstance() {
        return new AbstractFilter() {
            @Override
            public int filter(IBaseDataObject d, Map<String, Object> params) {
                return 0;
            }
        };
    }

    AbstractFilter getDenylistFilter(final List<String> denylist) {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("OUTPUT_SPEC_FOO", "SPEC");
        config.addEntry("OUTPUT_TYPE", "TYPE");
        denylist.forEach(entry -> config.addEntry("DENYLIST", entry));
        AbstractFilter f = getAbstractFilterInstance();
        f.initialize(new ServiceConfigGuide(), "FOO", config);
        return f;
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
    void testIncorrectConfigs() {
        List<String> invalidEntries = Arrays.asList(
                "type*.view", "*type.view", "ty*pe.view", "*.view", ".view",
                "type*.*view", "*type.*view", "ty*pe.*view", "*.*view", ".*view", "*view",
                "type*.vi*ew", "*type.vi*ew", "ty*pe.vi*ew", "*.vi*ew", ".vi*ew", "vi*ew",
                "type*.view*", "*type.view*", "ty*pe.view*", "*.view*", ".view*",
                "type.", "*", "type.*", ".", "type.view.", ".type.view", ".type.view.");
        for (String entry : invalidEntries) {
            final Configurator config = new ServiceConfigGuide();
            config.addEntry("DENYLIST", entry);
            AbstractFilter f = getAbstractFilterInstance();
            EmissaryRuntimeException e = assertThrows(
                    EmissaryRuntimeException.class,
                    () -> f.initialize(new ServiceConfigGuide(), "FOO", config));
            assertTrue(e.getMessage().contains(String.format("Invalid filter configuration: `DENYLIST = %s`", entry)));
        }
    }

    @Test
    void testDenyListContains() {
        AbstractFilter f = getDenylistFilter(Arrays.asList(
                "JSON_ML", "JSON_LANG_*",
                "JSON.GeoJSON", "JSON.JSON_1_*"));

        String filetype1 = "JSON";
        for (String accepted : Arrays.asList(
                "PrimaryView", "JSON_PRETTY", "JSON_LANG", "Geo", "JSON_1", "JSON_2_10", "JSON_2_10.1")) {
            assertFalse(f.denyListContains(filetype1, accepted), accepted + " should not be in denyList");
        }

        for (String denied : Arrays.asList(
                "JSON_ML", "JSON_LANG_ENG", "JSON_LANG_RUS", "JSON_LANG_FRE",
                "GeoJSON", "JSON_1_", "JSON_1_0", "JSON_1_2", "JSON_1_2.1")) {
            assertTrue(f.denyListContains(filetype1, denied), denied + " should be in denyList");
        }

        String filetype2 = "XML";
        for (String accepted : Arrays.asList(
                "PrimaryView", "JSON_PRETTY", "JSON_LANG", "Geo", "GeoJSON",
                "JSON_1_", "JSON_1", "JSON_1_0", "JSON_1_2", "JSON_1_2.1", "JSON_2_10", "JSON_2_10.1")) {
            assertFalse(f.denyListContains(filetype2, accepted), accepted + " should not be in denyList");
        }

        for (String denied : Arrays.asList(
                "JSON_ML", "JSON_LANG_ENG", "JSON_LANG_RUS", "JSON_LANG_FRE")) {
            assertTrue(f.denyListContains(filetype2, denied), denied + " should be in denyList");
        }

    }

    @Test
    void testGetTypesToCheck() {
        AbstractFilter f = getDenylistFilter(Arrays.asList(
                "JSON_ML", "JSON_LANG_*",
                "JSON.GeoJSON", "JSON.JSON_1_*"));

        IBaseDataObject payload = getTestPayload("JSON", Arrays.asList(
                "JSON_PRETTY", "JSON_ML", "Geo", "GeoJSON",
                "JSON_1_", "JSON_1", "JSON_1_0", "JSON_1_2", "JSON_1_2.1", "JSON_2_10", "JSON_2_10.1",
                "JSON_LANG", "JSON_LANG_ENG", "JSON_LANG_RUS", "JSON_LANG_FRE"));

        Set<String> checkTypes = f.getTypesToCheck(payload);

        for (String checked : Arrays.asList(
                "JSON.Geo", ".Geo", "*.Geo",
                "JSON.JSON_LANG", ".JSON_LANG", "*.JSON_LANG",
                "JSON.JSON_PRETTY", ".JSON_PRETTY", "*.JSON_PRETTY",
                "JSON.JSON_1", ".JSON_1", "*.JSON_1",
                "JSON.JSON_2_10", ".JSON_2_10", "*.JSON_2_10",
                "JSON.JSON_2_10.1", ".JSON_2_10.1", "*.JSON_2_10.1",
                "JSON.PrimaryView", ".PrimaryView", "*.PrimaryView",
                "JSON.Metadata", ".Metadata", "*.Metadata",
                "*.AlternateView", "NONE.Language", "*.Language", "JSON")) {
            assertTrue(checkTypes.contains(checked), checked + " should be a checked type");
        }

        for (String notChecked : Arrays.asList(
                "JSON.JSON_ML", ".JSON_ML", "*.JSON_ML",
                "JSON.GeoJSON", ".GeoJSON", "*.GeoJSON",
                "JSON.JSON_LANG_ENG", ".JSON_LANG_ENG", "*.JSON_LANG_ENG",
                "JSON.JSON_LANG_FRE", ".JSON_LANG_FRE", "*.JSON_LANG_FRE",
                "JSON.JSON_LANG_RUS", ".JSON_LANG_RUS", "*.JSON_LANG_RUS",
                "JSON.JSON_1_", ".JSON_1_", "*.JSON_1_",
                "JSON.JSON_1_0", ".JSON_1_0", "*.JSON_1_0",
                "JSON.JSON_1_2", ".JSON_1_2", "*.JSON_1_2",
                "JSON.JSON_1_2.1", ".JSON_1_2.1", "*.JSON_1_2.1")) {
            assertFalse(checkTypes.contains(notChecked), notChecked + " should not be a checked type");
        }
    }

}
