package emissary.output.filter;

import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

    AbstractFilter getJsonLangDenyFilter() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("OUTPUT_SPEC_FOO", "/tmp/%S%.%F%");
        config.addEntry("OUTPUT_TYPE", "JSON.PrimaryView");
        config.addEntry("DENYLIST", "JSON_LANG_*");

        AbstractFilter f = getAbstractFilterInstance();
        f.initialize(new ServiceConfigGuide(), "FOO", config);
        return f;
    }

    @Test
    void testDenyListContains() {
        AbstractFilter f = getJsonLangDenyFilter();

        for (String accepted : Arrays.asList(
                "JSON.PrimaryView", "PrimaryView",
                "JSON.JSON_PRETTY", "JSON_PRETTY",
                "JSON.JSON_LANG", "JSON_LANG")) {
            assertFalse(f.denyListContains(accepted), accepted + " should not be in denyList");
        }

        for (String denied : Arrays.asList(
                "JSON.JSON_LANG_", "JSON_LANG_",
                "JSON.JSON_LANG_ENG", "JSON_LANG_ENG",
                "JSON.JSON_LANG_RUS", "JSON_LANG_RUS",
                "JSON.JSON_LANG_FR", "JSON_LANG_FR")) {
            assertTrue(f.denyListContains(denied), denied + " should be in denyList");
        }
    }

    @Test
    void testGetTypesToCheck() {
        AbstractFilter f = getJsonLangDenyFilter();
        IBaseDataObject payload = DataObjectFactory.getInstance();
        payload.setData("abc".getBytes());
        payload.setFileType("JSON");
        payload.setFilename("/this/is/a/testfile");
        payload.addAlternateView("JSON_PRETTY", "def".getBytes());
        payload.addAlternateView("JSON_LANG", "ghi".getBytes());
        payload.addAlternateView("JSON_LANG_ENG", "jkl".getBytes());
        payload.addAlternateView("JSON_LANG_RUS", "mno".getBytes());
        payload.addAlternateView("JSON_LANG_FR", "pqr".getBytes());
        Set<String> checkTypes = f.getTypesToCheck(payload);

        for (String checked : Arrays.asList(
                "JSON.PrimaryView", ".PrimaryView", "*.PrimaryView",
                "JSON.JSON_PRETTY", ".JSON_PRETTY", "*.JSON_PRETTY",
                "JSON.JSON_LANG", ".JSON_LANG", "*.JSON_LANG")) {
            assertTrue(checkTypes.contains(checked), checked + " should be a checked type");
        }

        for (String notChecked : Arrays.asList(
                "JSON.JSON_LANG_", ".JSON_LANG_", "*.JSON_LANG_",
                "JSON.JSON_LANG_ENG", ".JSON_LANG_ENG", "*.JSON_LANG_ENG",
                "JSON.JSON_LANG_RUS", ".JSON_LANG_RUS", "*.JSON_LANG_RUS",
                "JSON.JSON_LANG_FR", ".JSON_LANG_FR", "*.JSON_LANG_FR")) {
            assertFalse(checkTypes.contains(notChecked), notChecked + " should not be a checked type");
        }
    }

}
