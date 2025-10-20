package emissary.test.util;

import emissary.config.ConfigEntry;
import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.place.ServiceProviderPlace;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfiguredPlaceFactoryTest extends UnitTest {
    private static final String CONSTANT_KEY = "CONSTANT_KEY";
    private static final String VARIABLE_KEY = "VARIABLE_KEY";
    private static final String CONSTANT_VALUE_1 = "CONSTANT_VALUE_1";
    private static final String CONSTANT_VALUE_2 = "CONSTANT_VALUE_2";
    private static final String CFG_FILE_VALUE_1 = "CFG_FILE_VALUE_1";
    private static final String CFG_FILE_VALUE_2 = "CFG_FILE_VALUE_2";
    private static final String NEW_CONFIGURATOR_VALUE_1 = "NEW_CONFIGURATOR_VALUE_1";
    private static final String NEW_CONFIGURATOR_VALUE_2 = "NEW_CONFIGURATOR_VALUE_2";
    private static final String FACTORY_CONSTRUCTOR_VALUE_1 = "FACTORY_CONSTRUCTOR_VALUE_1";
    private static final String FACTORY_CONSTRUCTOR_VALUE_2 = "FACTORY_CONSTRUCTOR_VALUE_2";
    private static final String BUILD_PARAMETER_VALUE_1 = "BUILD_PARAMETER_VALUE_1";
    private static final String BUILD_PARAMETER_VALUE_2 = "BUILD_PARAMETER_VALUE_2";

    static class ConfigFilePlace extends ServiceProviderPlace {
        private final List<String> constantTestConfigs;
        private final List<String> variableTestConfigs;

        public ConfigFilePlace(Configurator cfgInfo) throws IOException {
            super(ConfigFilePlace.class.getName());
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
     * Same functionality as {@link ConfigFilePlace} but without a .cfg file
     */
    static class NoConfigFilePlace extends ConfigFilePlace {
        public NoConfigFilePlace(Configurator cfgInfo) throws IOException {
            super(cfgInfo);
        }
    }

    /**
     * A place that always fails on startup
     */
    static class StartupFailurePlace extends ConfigFilePlace {
        static class SomeException extends RuntimeException {
            private static final long serialVersionUID = 123L;

            public SomeException(final String message) {
                super(message);
            }
        }

        public StartupFailurePlace(Configurator cfgInfo) throws IOException {
            super(cfgInfo);
            throw new SomeException("Some Exception message from Place instantiation");
        }
    }

    @Test
    void testCfgFile() {
        ConfiguredPlaceFactory<ConfigFilePlace> factory = new ConfiguredPlaceFactory<>(ConfigFilePlace.class);
        ConfigFilePlace place = factory.buildPlace();

        assertIterableEquals(List.of(CONSTANT_VALUE_1, CONSTANT_VALUE_2), place.getConstantTestConfigs());
        assertIterableEquals(List.of(CFG_FILE_VALUE_1, CFG_FILE_VALUE_2), place.getVariableTestConfigs());
    }

    @Test
    void testDoNotUseCfgFile() {
        ConfiguredPlaceFactory<ConfigFilePlace> factory = new ConfiguredPlaceFactory<>(ConfigFilePlace.class,
                new ServiceConfigGuide());
        ConfigFilePlace place = factory.buildPlace();

        assertTrue(place.getConstantTestConfigs().isEmpty());
        assertTrue(place.getVariableTestConfigs().isEmpty());
    }

    @Test
    void testCustomBaseConfigurator() {
        Configurator baseConfigurator = new ServiceConfigGuide();
        baseConfigurator.addEntry(CONSTANT_KEY, NEW_CONFIGURATOR_VALUE_1);
        baseConfigurator.addEntry(CONSTANT_KEY, NEW_CONFIGURATOR_VALUE_2);

        ConfiguredPlaceFactory<ConfigFilePlace> factory = new ConfiguredPlaceFactory<>(ConfigFilePlace.class, baseConfigurator);
        ConfigFilePlace place = factory.buildPlace();

        assertIterableEquals(List.of(NEW_CONFIGURATOR_VALUE_1, NEW_CONFIGURATOR_VALUE_2), place.getConstantTestConfigs());
        assertTrue(place.getVariableTestConfigs().isEmpty());
    }

    @Test
    void testExpectedCfgFileNotFound() {
        ConfiguredPlaceFactory<NoConfigFilePlace> factory = new ConfiguredPlaceFactory<>(NoConfigFilePlace.class);
        ConfigFilePlace place = factory.buildPlace();

        assertTrue(place.getConstantTestConfigs().isEmpty());
        assertTrue(place.getVariableTestConfigs().isEmpty());
    }

    @Test
    void testFactoryConstructorOverridesCfgFile() {
        ConfiguredPlaceFactory<ConfigFilePlace> factory = new ConfiguredPlaceFactory<>(ConfigFilePlace.class,
                new ConfigEntry(VARIABLE_KEY, FACTORY_CONSTRUCTOR_VALUE_1),
                new ConfigEntry(VARIABLE_KEY, FACTORY_CONSTRUCTOR_VALUE_2));
        ConfigFilePlace place = factory.buildPlace();

        assertIterableEquals(List.of(CONSTANT_VALUE_1, CONSTANT_VALUE_2), place.getConstantTestConfigs());
        assertIterableEquals(List.of(FACTORY_CONSTRUCTOR_VALUE_1, FACTORY_CONSTRUCTOR_VALUE_2), place.getVariableTestConfigs());
    }

    @Test
    void testFactoryConstructorWithoutCfgFile() {
        ConfiguredPlaceFactory<NoConfigFilePlace> factory = new ConfiguredPlaceFactory<>(NoConfigFilePlace.class,
                new ConfigEntry(VARIABLE_KEY, FACTORY_CONSTRUCTOR_VALUE_1),
                new ConfigEntry(VARIABLE_KEY, FACTORY_CONSTRUCTOR_VALUE_2));
        NoConfigFilePlace place = factory.buildPlace();

        assertTrue(place.getConstantTestConfigs().isEmpty());
        assertIterableEquals(List.of(FACTORY_CONSTRUCTOR_VALUE_1, FACTORY_CONSTRUCTOR_VALUE_2), place.getVariableTestConfigs());
    }

    @Test
    void testRemoveConfigurationsWithNullFactoryConstructorParameter() {
        ConfiguredPlaceFactory<ConfigFilePlace> factory = new ConfiguredPlaceFactory<>(ConfigFilePlace.class,
                new ConfigEntry(VARIABLE_KEY, null));
        ConfigFilePlace place = factory.buildPlace();

        assertIterableEquals(List.of(CONSTANT_VALUE_1, CONSTANT_VALUE_2), place.getConstantTestConfigs());
        assertTrue(place.getVariableTestConfigs().isEmpty());
    }

    @Test
    void testBuildParametersOverrideCfgFile() {
        ConfiguredPlaceFactory<ConfigFilePlace> factory = new ConfiguredPlaceFactory<>(ConfigFilePlace.class);
        ConfigFilePlace place = factory.buildPlace(
                new ConfigEntry(VARIABLE_KEY, BUILD_PARAMETER_VALUE_1),
                new ConfigEntry(VARIABLE_KEY, BUILD_PARAMETER_VALUE_2));

        assertIterableEquals(List.of(CONSTANT_VALUE_1, CONSTANT_VALUE_2), place.getConstantTestConfigs());
        assertIterableEquals(List.of(BUILD_PARAMETER_VALUE_1, BUILD_PARAMETER_VALUE_2), place.getVariableTestConfigs());
    }

    @Test
    void testBuildParametersWithoutCfgFile() {
        ConfiguredPlaceFactory<NoConfigFilePlace> factory = new ConfiguredPlaceFactory<>(NoConfigFilePlace.class);
        NoConfigFilePlace place = factory.buildPlace(
                new ConfigEntry(VARIABLE_KEY, BUILD_PARAMETER_VALUE_1),
                new ConfigEntry(VARIABLE_KEY, BUILD_PARAMETER_VALUE_2));

        assertTrue(place.getConstantTestConfigs().isEmpty());
        assertIterableEquals(List.of(BUILD_PARAMETER_VALUE_1, BUILD_PARAMETER_VALUE_2), place.getVariableTestConfigs());
    }

    @Test
    void testBuildParametersOverrideFactoryConstructor() {
        ConfiguredPlaceFactory<ConfigFilePlace> factory = new ConfiguredPlaceFactory<>(ConfigFilePlace.class,
                new ConfigEntry(VARIABLE_KEY, FACTORY_CONSTRUCTOR_VALUE_1),
                new ConfigEntry(VARIABLE_KEY, FACTORY_CONSTRUCTOR_VALUE_2));
        ConfigFilePlace place = factory.buildPlace(
                new ConfigEntry(VARIABLE_KEY, BUILD_PARAMETER_VALUE_1),
                new ConfigEntry(VARIABLE_KEY, BUILD_PARAMETER_VALUE_2));

        assertIterableEquals(List.of(CONSTANT_VALUE_1, CONSTANT_VALUE_2), place.getConstantTestConfigs());
        assertIterableEquals(List.of(BUILD_PARAMETER_VALUE_1, BUILD_PARAMETER_VALUE_2), place.getVariableTestConfigs());
    }

    @Test
    void testBuildParametersOverrideFactoryConstructorWithoutCfgFile() {
        ConfiguredPlaceFactory<NoConfigFilePlace> factory = new ConfiguredPlaceFactory<>(NoConfigFilePlace.class,
                new ConfigEntry(VARIABLE_KEY, FACTORY_CONSTRUCTOR_VALUE_1),
                new ConfigEntry(VARIABLE_KEY, FACTORY_CONSTRUCTOR_VALUE_2));
        NoConfigFilePlace place = factory.buildPlace(
                new ConfigEntry(VARIABLE_KEY, BUILD_PARAMETER_VALUE_1),
                new ConfigEntry(VARIABLE_KEY, BUILD_PARAMETER_VALUE_2));

        assertTrue(place.getConstantTestConfigs().isEmpty());
        assertIterableEquals(List.of(BUILD_PARAMETER_VALUE_1, BUILD_PARAMETER_VALUE_2), place.getVariableTestConfigs());
    }

    @Test
    void testRemoveConfigurationsWithNullBuildParameter() {
        ConfiguredPlaceFactory<ConfigFilePlace> factory = new ConfiguredPlaceFactory<>(ConfigFilePlace.class,
                new ConfigEntry(VARIABLE_KEY, FACTORY_CONSTRUCTOR_VALUE_1),
                new ConfigEntry(VARIABLE_KEY, FACTORY_CONSTRUCTOR_VALUE_2));
        ConfigFilePlace place = factory.buildPlace(new ConfigEntry(VARIABLE_KEY, null));

        assertIterableEquals(List.of(CONSTANT_VALUE_1, CONSTANT_VALUE_2), place.getConstantTestConfigs());
        assertTrue(place.getVariableTestConfigs().isEmpty());
    }


    @Test
    void testExpectedExceptionForBuildFailure() {
        ConfiguredPlaceFactory<StartupFailurePlace> factory = new ConfiguredPlaceFactory<>(StartupFailurePlace.class);
        Exception e = factory.getBuildPlaceException();

        assertInstanceOf(StartupFailurePlace.SomeException.class, e);
        assertEquals("Some Exception message from Place instantiation", e.getMessage());
    }

    @Test
    void testExpectedExceptionForBuildFailureWithProvidedType() {
        ConfiguredPlaceFactory<StartupFailurePlace> factory = new ConfiguredPlaceFactory<>(StartupFailurePlace.class);
        StartupFailurePlace.SomeException e = factory.getBuildPlaceException(StartupFailurePlace.SomeException.class);

        assertEquals("Some Exception message from Place instantiation", e.getMessage());
    }

    @Test
    void testUnexpectedExceptionTypeForBuildFailure() {
        String expectedExceptionName = "java.lang.NullPointerException";
        String actualExceptionName = "emissary.test.util.ConfiguredPlaceFactoryTest$StartupFailurePlace$SomeException";
        ConfiguredPlaceFactory<StartupFailurePlace> factory = new ConfiguredPlaceFactory<>(StartupFailurePlace.class);

        ClassCastException e = assertThrows(ClassCastException.class,
                () -> factory.getBuildPlaceException(NullPointerException.class));
        assertEquals(String.format("Cannot cast %s to %s", actualExceptionName, expectedExceptionName), e.getMessage());
    }

    @Test
    void testBuildSucceedsDespiteExpectedFailure() {
        String placeName = "emissary.test.util.ConfiguredPlaceFactoryTest$ConfigFilePlace";
        ConfiguredPlaceFactory<ConfigFilePlace> factory = new ConfiguredPlaceFactory<>(ConfigFilePlace.class);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> factory.getBuildPlaceException(StartupFailurePlace.SomeException.class));
        assertEquals(String.format("Succeeded building %s but expected failure", placeName), e.getMessage());
    }
}
