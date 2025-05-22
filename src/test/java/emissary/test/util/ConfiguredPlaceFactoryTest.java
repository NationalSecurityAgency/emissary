package emissary.test.util;

import emissary.config.ConfigEntry;
import emissary.config.Configurator;
import emissary.place.ServiceProviderPlace;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfiguredPlaceFactoryTest extends UnitTest {
    private static final String PLACE_LOCATION = "emissary.test.util.ConfiguredPlaceFactoryTest$TestConfigPlace";
    private static final String CONST_KEY = "CONST_KEY";
    private static final String CONST_VALUE = "CONST_VALUE";
    private static final String VAR_KEY = "VAR_KEY";
    private static final String CFG_FILE_VALUE_1 = "CFG_FILE_VALUE_1";
    private static final String CFG_FILE_VALUE_2 = "CFG_FILE_VALUE_2";
    private static final String FACTORY_CONSTRUCTOR_VALUE_1 = "FACTORY_CONSTRUCTOR_VALUE_1";
    private static final String FACTORY_CONSTRUCTOR_VALUE_2 = "FACTORY_CONSTRUCTOR_VALUE_2";
    private static final String BUILD_PARAMETER_VALUE_1 = "BUILD_PARAMETER_VALUE_1";
    private static final String BUILD_PARAMETER_VALUE_2 = "BUILD_PARAMETER_VALUE_2";

    static class TestConfigPlace extends ServiceProviderPlace {
        private final String constTestConfig;
        private final List<String> varTestConfigs;

        public TestConfigPlace(Configurator cfgInfo) throws IOException {
            super(PLACE_LOCATION);
            configG = cfgInfo != null ? cfgInfo : configG;
            if (configG == null) {
                throw new IllegalStateException("No configurations found for " + getPlaceName());
            }
            constTestConfig = configG.findStringEntry(CONST_KEY);
            varTestConfigs = configG.findEntries(VAR_KEY);
        }

        public String getConstTestConfig() {
            return constTestConfig;
        }

        public List<String> getVarTestConfigs() {
            return varTestConfigs;
        }
    }

    static class TestConfigExtensionPlace extends TestConfigPlace {

        public TestConfigExtensionPlace(Configurator cfgInfo) throws IOException {
            super(cfgInfo);
        }
    }

    @Test
    void testCfgFile() {
        ConfiguredPlaceFactory<TestConfigPlace> factory = new ConfiguredPlaceFactory<>(TestConfigPlace.class);
        TestConfigPlace place = factory.buildPlace();

        assertEquals(CONST_VALUE, place.getConstTestConfig());
        assertIterableEquals(List.of(CFG_FILE_VALUE_1, CFG_FILE_VALUE_2), place.getVarTestConfigs());
    }

    @Test
    void testNoCfgFile() {
        ConfiguredPlaceFactory<TestConfigExtensionPlace> factory = new ConfiguredPlaceFactory<>(TestConfigExtensionPlace.class);
        TestConfigPlace place = factory.buildPlace();

        assertNull(place.getConstTestConfig());
        assertTrue(place.getVarTestConfigs().isEmpty());
    }

    @Test
    void testFactoryConstructorOverridesCfgFile() {
        ConfiguredPlaceFactory<TestConfigPlace> factory = new ConfiguredPlaceFactory<>(TestConfigPlace.class,
                new ConfigEntry(VAR_KEY, FACTORY_CONSTRUCTOR_VALUE_1),
                new ConfigEntry(VAR_KEY, FACTORY_CONSTRUCTOR_VALUE_2));
        TestConfigPlace place = factory.buildPlace();

        assertEquals(CONST_VALUE, place.getConstTestConfig());
        assertIterableEquals(List.of(FACTORY_CONSTRUCTOR_VALUE_1, FACTORY_CONSTRUCTOR_VALUE_2), place.getVarTestConfigs());
    }

    @Test
    void testFactoryConstructorWithoutCfgFile() {
        ConfiguredPlaceFactory<TestConfigExtensionPlace> factory = new ConfiguredPlaceFactory<>(TestConfigExtensionPlace.class,
                new ConfigEntry(VAR_KEY, FACTORY_CONSTRUCTOR_VALUE_1),
                new ConfigEntry(VAR_KEY, FACTORY_CONSTRUCTOR_VALUE_2));
        TestConfigExtensionPlace place = factory.buildPlace();

        assertNull(place.getConstTestConfig());
        assertIterableEquals(List.of(FACTORY_CONSTRUCTOR_VALUE_1, FACTORY_CONSTRUCTOR_VALUE_2), place.getVarTestConfigs());
    }

    @Test
    void testBuildParametersOverrideCfgFile() {
        ConfiguredPlaceFactory<TestConfigPlace> factory = new ConfiguredPlaceFactory<>(TestConfigPlace.class);
        TestConfigPlace place = factory.buildPlace(
                new ConfigEntry(VAR_KEY, BUILD_PARAMETER_VALUE_1),
                new ConfigEntry(VAR_KEY, BUILD_PARAMETER_VALUE_2));

        assertEquals(CONST_VALUE, place.getConstTestConfig());
        assertIterableEquals(List.of(BUILD_PARAMETER_VALUE_1, BUILD_PARAMETER_VALUE_2), place.getVarTestConfigs());
    }

    @Test
    void testBuildParametersWithoutCfgFile() {
        ConfiguredPlaceFactory<TestConfigExtensionPlace> factory = new ConfiguredPlaceFactory<>(TestConfigExtensionPlace.class);
        TestConfigExtensionPlace place = factory.buildPlace(
                new ConfigEntry(VAR_KEY, BUILD_PARAMETER_VALUE_1),
                new ConfigEntry(VAR_KEY, BUILD_PARAMETER_VALUE_2));

        assertNull(place.getConstTestConfig());
        assertIterableEquals(List.of(BUILD_PARAMETER_VALUE_1, BUILD_PARAMETER_VALUE_2), place.getVarTestConfigs());
    }

    @Test
    void testBuildParametersOverrideFactoryConstructor() {
        ConfiguredPlaceFactory<TestConfigPlace> factory = new ConfiguredPlaceFactory<>(TestConfigPlace.class,
                new ConfigEntry(VAR_KEY, FACTORY_CONSTRUCTOR_VALUE_1),
                new ConfigEntry(VAR_KEY, FACTORY_CONSTRUCTOR_VALUE_2));
        TestConfigPlace place = factory.buildPlace(
                new ConfigEntry(VAR_KEY, BUILD_PARAMETER_VALUE_1),
                new ConfigEntry(VAR_KEY, BUILD_PARAMETER_VALUE_2));

        assertEquals(CONST_VALUE, place.getConstTestConfig());
        assertIterableEquals(List.of(BUILD_PARAMETER_VALUE_1, BUILD_PARAMETER_VALUE_2), place.getVarTestConfigs());
    }

    @Test
    void testBuildParametersOverrideFactoryConstructorWithoutCfgFile() {
        ConfiguredPlaceFactory<TestConfigExtensionPlace> factory = new ConfiguredPlaceFactory<>(TestConfigExtensionPlace.class,
                new ConfigEntry(VAR_KEY, FACTORY_CONSTRUCTOR_VALUE_1),
                new ConfigEntry(VAR_KEY, FACTORY_CONSTRUCTOR_VALUE_2));
        TestConfigExtensionPlace place = factory.buildPlace(
                new ConfigEntry(VAR_KEY, BUILD_PARAMETER_VALUE_1),
                new ConfigEntry(VAR_KEY, BUILD_PARAMETER_VALUE_2));

        assertNull(place.getConstTestConfig());
        assertIterableEquals(List.of(BUILD_PARAMETER_VALUE_1, BUILD_PARAMETER_VALUE_2), place.getVarTestConfigs());
    }
}
