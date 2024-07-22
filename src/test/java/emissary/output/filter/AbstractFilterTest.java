package emissary.output.filter;

import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.core.DataObjectFactory;
import emissary.core.EmissaryRuntimeException;
import emissary.core.IBaseDataObject;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
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

    AbstractFilter getDenyFilter(final String outputType) {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("OUTPUT_SPEC_FOO", "/tmp/%S%.%F%");
        config.addEntry("OUTPUT_TYPE", outputType);
        config.addEntry("DENYLIST", "JSON.BSON.V_1.*");
        config.addEntry("DENYLIST", "JSON.JSON_ONE_LINE");
        config.addEntry("DENYLIST", "JSON.JSON_LANG_*");
        config.addEntry("DENYLIST", "*.PLAINTEXT");
        config.addEntry("DENYLIST", "*.MEDIA_*");

        AbstractFilter f = getAbstractFilterInstance();
        f.initialize(new ServiceConfigGuide(), "FOO", config);
        return f;
    }

    @Test
    void testIncorrectConfigs() {
        AbstractFilter f = getAbstractFilterInstance();
        String[] invalidEntries = {
                "type*", "*type", "ty*pe", "type*.view", "*type.view", "ty*pe.view",
                "type*.view*", "*type.view*", "ty*pe.view*", "type*.*", "*type.*", "ty*pe.*",
                "type.*view", "type.vi*ew", "*.*view", "*.vi*ew",
                "type.", ".view", ".", "type.view.", ".type.view", ".type.view."};
        for (String entry : invalidEntries) {
            final Configurator config = new ServiceConfigGuide();
            config.addEntry("DENYLIST", entry);
            EmissaryRuntimeException e = assertThrows(
                    EmissaryRuntimeException.class,
                    () -> f.initialize(new ServiceConfigGuide(), "FOO", config));
            assertTrue(e.getMessage().contains(String.format("Invalid filter configuration: `DENYLIST = %s`", entry)));
        }
    }

    @Test
    void testDenyListContainsSamefiletype() {
        String filetype = "JSON";
        AbstractFilter f = getDenyFilter(filetype);

        for (String accepted : Arrays.asList(
                "BSON.V_2.0.0",
                "BSON.V_6.7.0",
                "JSON_LANG",
                "JSON_PRETTY",
                "PLAIN",
                "PLAINTXT",
                "PrimaryView")) {
            assertFalse(f.denyListContains(filetype, accepted), accepted + " should not be in denyList");
        }

        for (String denied : Arrays.asList(
                "BSON.V_1.0.0",
                "BSON.V_1.2.0",
                "JSON_ONE_LINE",
                "JSON_LANG_",
                "JSON_LANG_ENG",
                "JSON_LANG_RUS",
                "JSON_LANG_FR",
                "PLAINTEXT",
                "MEDIA_AUDIO",
                "MEDIA_")) {
            assertTrue(f.denyListContains(filetype, denied), denied + " should be in denyList");
        }
    }

    @Test
    void testDenyListContainsDifferentFiletype() {
        String filetype = "XML";
        AbstractFilter f = getDenyFilter(filetype);

        for (String accepted : Arrays.asList(
                "BSON.V_1.0.0",
                "BSON.V_1.2.0",
                "BSON.V_2.0.0",
                "BSON.V_6.7.0",
                "JSON_LANG",
                "JSON_LANG_",
                "JSON_LANG_ENG",
                "JSON_LANG_RUS",
                "JSON_LANG_FR",
                "JSON_ONE_LINE",
                "JSON_PRETTY",
                "PrimaryView")) {
            assertFalse(f.denyListContains(filetype, accepted), accepted + " should not be in denyList");
        }
    }

    @Test
    void testDenyListContainsWildCardFiletype() {
        String filetype = "MP4";
        AbstractFilter f = getDenyFilter(filetype);

        for (String accepted : Arrays.asList(
                "PLAIN",
                "PLAINTEXT_",
                "PrimaryView")) {
            assertFalse(f.denyListContains(filetype, accepted), accepted + " should not be in denyList");
        }

        for (String denied : Arrays.asList(
                "PLAINTEXT",
                "MEDIA_",
                "MEDIA_VIDEO")) {
            assertTrue(f.denyListContains(filetype, denied), denied + " should be in denyList");
        }
    }


    @Test
    void testGetTypesToCheck() {
        String filetype = "JSON";
        AbstractFilter f = getDenyFilter(filetype);

        IBaseDataObject payload = DataObjectFactory.getInstance();
        payload.setData("".getBytes());
        payload.setFileType(filetype);
        payload.setFilename("/this/is/a/testfile");
        payload.addAlternateView("BSON.V_1.1", "".getBytes());
        payload.addAlternateView("BSON.V_6.0.1", "".getBytes());
        payload.addAlternateView("JSON_ONE_LINE", "".getBytes());
        payload.addAlternateView("JSON_PRETTY", "".getBytes());
        payload.addAlternateView("JSON_LANG", "".getBytes());
        payload.addAlternateView("JSON_LANG_ENG", "".getBytes());
        payload.addAlternateView("JSON_LANG_RUS", "".getBytes());
        payload.addAlternateView("JSON_LANG_FR", "".getBytes());
        payload.addAlternateView("PLAIN", "".getBytes());
        payload.addAlternateView("PLAINTEXT", "".getBytes());
        payload.addAlternateView("PLAINTEXT_", "".getBytes());
        payload.addAlternateView("MEDIA", "".getBytes());
        payload.addAlternateView("MEDIA_", "".getBytes());
        payload.addAlternateView("MEDIA_AUDIO", "".getBytes());
        payload.addAlternateView("MEDIA_VIDEO", "".getBytes());
        Set<String> checkTypes = f.getTypesToCheck(payload);

        for (String checked : Arrays.asList(
                "JSON.BSON.V_6.0.1", ".BSON.V_6.0.1", "*.BSON.V_6.0.1",
                "JSON.JSON_PRETTY", ".JSON_PRETTY", "*.JSON_PRETTY",
                "JSON.JSON_LANG", ".JSON_LANG", "*.JSON_LANG",
                "JSON.MEDIA", ".MEDIA", "*.MEDIA",
                "JSON.PLAIN", ".PLAIN", "*.PLAIN",
                "JSON.PLAINTEXT_", ".PLAINTEXT_", "*.PLAINTEXT_",
                "JSON.PrimaryView", ".PrimaryView", "*.PrimaryView")) {
            assertTrue(checkTypes.contains(checked), checked + " should be a checked type");
        }

        for (String notChecked : Arrays.asList(
                "JSON.BSON.V_1.1", ".BSON.V_1.1", "*.BSON.V_1.1",
                "JSON.JSON_LANG_", ".JSON_LANG_", "*.JSON_LANG_",
                "JSON.JSON_LANG_ENG", ".JSON_LANG_ENG", "*.JSON_LANG_ENG",
                "JSON.JSON_LANG_RUS", ".JSON_LANG_RUS", "*.JSON_LANG_RUS",
                "JSON.JSON_LANG_FR", ".JSON_LANG_FR", "*.JSON_LANG_FR",
                "JSON.JSON_ONE_LINE", ".JSON_ONE_LINE", "*.JSON_ONE_LINE",
                "JSON.PLAINTEXT", ".PLAINTEXT", "*.PLAINTEXT",
                "JSON.MEDIA_", ".MEDIA_", "*.MEDIA_",
                "JSON.MEDIA_AUDIO", ".MEDIA_AUDIO", "*.MEDIA_AUDIO",
                "JSON.MEDIA_VIDEO", ".MEDIA_VIDEO", "*.MEDIA_VIDEO")) {
            assertFalse(checkTypes.contains(notChecked), notChecked + " should not be a checked type");
        }
    }

}
