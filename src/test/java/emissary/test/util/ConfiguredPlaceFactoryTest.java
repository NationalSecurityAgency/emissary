package emissary.test.util;

import emissary.config.ConfigEntry;
import emissary.config.Configurator;
import emissary.place.ServiceProviderPlace;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfiguredPlaceFactoryTest extends UnitTest {
    private static final String CONSTANT_KEY = "CONSTANT_KEY";
    private static final String VARIABLE_KEY = "VARIABLE_KEY";
    private static final String CONSTANT_VALUE_1 = "CONSTANT_VALUE_1";
    private static final String CONSTANT_VALUE_2 = "CONSTANT_VALUE_2";
    private static final String CFG_FILE_VALUE_1 = "CFG_FILE_VALUE_1";
    private static final String CFG_FILE_VALUE_2 = "CFG_FILE_VALUE_2";
    private static final String FACTORY_CONSTRUCTOR_VALUE_1 = "FACTORY_CONSTRUCTOR_VALUE_1";
    private static final String FACTORY_CONSTRUCTOR_VALUE_2 = "FACTORY_CONSTRUCTOR_VALUE_2";
    private static final String BUILD_PARAMETER_VALUE_1 = "BUILD_PARAMETER_VALUE_1";
    private static final String BUILD_PARAMETER_VALUE_2 = "BUILD_PARAMETER_VALUE_2";

    static class TestConfigPlace extends ServiceProviderPlace {
        private final List<String> constantTestConfigs;
        private final List<String> variableTestConfigs;

        public TestConfigPlace(Configurator cfgInfo) throws IOException {
            super(TestConfigPlace.class.getName());
            configG = cfgInfo != null ? cfgInfo : configG;
            if (configG == null) {
                throw new IllegalStateException("No configurations found for " + getPlaceName());
            }
            constantTestConfigs = configG.findEntries(CONSTANT_KEY);
            variableTestConfigs = configG.findEntries(VARIABLE_KEY);
        }

        public List<String> getConstantTestConfigs() {
            return constantTestConfigs;
        }

        public List<String> getVariableTestConfigs() {
            return variableTestConfigs;
        }
    }

    /**
     * Same functionality as {@link TestConfigPlace} but without a .cfg file
     */
    static class TestConfigExtensionPlace extends TestConfigPlace {
        public TestConfigExtensionPlace(Configurator cfgInfo) throws IOException {
            super(cfgInfo);
        }
    }

    @Test
    void testCfgFile() {
        ConfiguredPlaceFactory<TestConfigPlace> factory = new ConfiguredPlaceFactory<>(TestConfigPlace.class);
        TestConfigPlace place = factory.buildPlace();

        assertIterableEquals(List.of(CONSTANT_VALUE_1, CONSTANT_VALUE_2), place.getConstantTestConfigs());
        assertIterableEquals(List.of(CFG_FILE_VALUE_1, CFG_FILE_VALUE_2), place.getVariableTestConfigs());
    }

    @Test
    void testNoCfgFile() {
        ConfiguredPlaceFactory<TestConfigExtensionPlace> factory = new ConfiguredPlaceFactory<>(TestConfigExtensionPlace.class);
        TestConfigPlace place = factory.buildPlace();

        assertTrue(place.getConstantTestConfigs().isEmpty());
        assertTrue(place.getVariableTestConfigs().isEmpty());
    }

    @Test
    void testFactoryConstructorOverridesCfgFile() {
        ConfiguredPlaceFactory<TestConfigPlace> factory = new ConfiguredPlaceFactory<>(TestConfigPlace.class,
                new ConfigEntry(VARIABLE_KEY, FACTORY_CONSTRUCTOR_VALUE_1),
                new ConfigEntry(VARIABLE_KEY, FACTORY_CONSTRUCTOR_VALUE_2));
        TestConfigPlace place = factory.buildPlace();

        assertIterableEquals(List.of(CONSTANT_VALUE_1, CONSTANT_VALUE_2), place.getConstantTestConfigs());
        assertIterableEquals(List.of(FACTORY_CONSTRUCTOR_VALUE_1, FACTORY_CONSTRUCTOR_VALUE_2), place.getVariableTestConfigs());
    }

    @Test
    void testFactoryConstructorWithoutCfgFile() {
        ConfiguredPlaceFactory<TestConfigExtensionPlace> factory = new ConfiguredPlaceFactory<>(TestConfigExtensionPlace.class,
                new ConfigEntry(VARIABLE_KEY, FACTORY_CONSTRUCTOR_VALUE_1),
                new ConfigEntry(VARIABLE_KEY, FACTORY_CONSTRUCTOR_VALUE_2));
        TestConfigExtensionPlace place = factory.buildPlace();

        assertTrue(place.getConstantTestConfigs().isEmpty());
        assertIterableEquals(List.of(FACTORY_CONSTRUCTOR_VALUE_1, FACTORY_CONSTRUCTOR_VALUE_2), place.getVariableTestConfigs());
    }

    @Test
    void testBuildParametersOverrideCfgFile() {
        ConfiguredPlaceFactory<TestConfigPlace> factory = new ConfiguredPlaceFactory<>(TestConfigPlace.class);
        TestConfigPlace place = factory.buildPlace(
                new ConfigEntry(VARIABLE_KEY, BUILD_PARAMETER_VALUE_1),
                new ConfigEntry(VARIABLE_KEY, BUILD_PARAMETER_VALUE_2));

        assertIterableEquals(List.of(CONSTANT_VALUE_1, CONSTANT_VALUE_2), place.getConstantTestConfigs());
        assertIterableEquals(List.of(BUILD_PARAMETER_VALUE_1, BUILD_PARAMETER_VALUE_2), place.getVariableTestConfigs());
    }

    @Test
    void testBuildParametersWithoutCfgFile() {
        ConfiguredPlaceFactory<TestConfigExtensionPlace> factory = new ConfiguredPlaceFactory<>(TestConfigExtensionPlace.class);
        TestConfigExtensionPlace place = factory.buildPlace(
                new ConfigEntry(VARIABLE_KEY, BUILD_PARAMETER_VALUE_1),
                new ConfigEntry(VARIABLE_KEY, BUILD_PARAMETER_VALUE_2));

        assertTrue(place.getConstantTestConfigs().isEmpty());
        assertIterableEquals(List.of(BUILD_PARAMETER_VALUE_1, BUILD_PARAMETER_VALUE_2), place.getVariableTestConfigs());
    }

    @Test
    void testBuildParametersOverrideFactoryConstructor() {
        ConfiguredPlaceFactory<TestConfigPlace> factory = new ConfiguredPlaceFactory<>(TestConfigPlace.class,
                new ConfigEntry(VARIABLE_KEY, FACTORY_CONSTRUCTOR_VALUE_1),
                new ConfigEntry(VARIABLE_KEY, FACTORY_CONSTRUCTOR_VALUE_2));
        TestConfigPlace place = factory.buildPlace(
                new ConfigEntry(VARIABLE_KEY, BUILD_PARAMETER_VALUE_1),
                new ConfigEntry(VARIABLE_KEY, BUILD_PARAMETER_VALUE_2));

        assertIterableEquals(List.of(CONSTANT_VALUE_1, CONSTANT_VALUE_2), place.getConstantTestConfigs());
        assertIterableEquals(List.of(BUILD_PARAMETER_VALUE_1, BUILD_PARAMETER_VALUE_2), place.getVariableTestConfigs());
    }

    @Test
    void testBuildParametersOverrideFactoryConstructorWithoutCfgFile() {
        ConfiguredPlaceFactory<TestConfigExtensionPlace> factory = new ConfiguredPlaceFactory<>(TestConfigExtensionPlace.class,
                new ConfigEntry(VARIABLE_KEY, FACTORY_CONSTRUCTOR_VALUE_1),
                new ConfigEntry(VARIABLE_KEY, FACTORY_CONSTRUCTOR_VALUE_2));
        TestConfigExtensionPlace place = factory.buildPlace(
                new ConfigEntry(VARIABLE_KEY, BUILD_PARAMETER_VALUE_1),
                new ConfigEntry(VARIABLE_KEY, BUILD_PARAMETER_VALUE_2));

        assertTrue(place.getConstantTestConfigs().isEmpty());
        assertIterableEquals(List.of(BUILD_PARAMETER_VALUE_1, BUILD_PARAMETER_VALUE_2), place.getVariableTestConfigs());
    }

    @Test
    void testRemoveConfigurations() {
        ConfiguredPlaceFactory<TestConfigPlace> factory = new ConfiguredPlaceFactory<>(TestConfigPlace.class,
                new ConfigEntry(VARIABLE_KEY, FACTORY_CONSTRUCTOR_VALUE_1),
                new ConfigEntry(VARIABLE_KEY, FACTORY_CONSTRUCTOR_VALUE_2));
        TestConfigPlace place = factory.buildPlace(Set.of(VARIABLE_KEY),
                new ConfigEntry(VARIABLE_KEY, BUILD_PARAMETER_VALUE_1),
                new ConfigEntry(VARIABLE_KEY, BUILD_PARAMETER_VALUE_2));

        assertIterableEquals(List.of(CONSTANT_VALUE_1, CONSTANT_VALUE_2), place.getConstantTestConfigs());
        assertTrue(place.getVariableTestConfigs().isEmpty());
    }
}
