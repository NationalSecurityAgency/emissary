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

    static class ConfigFileTestPlace extends ServiceProviderPlace {
        private final List<String> constantTestConfigs;
        private final List<String> variableTestConfigs;

        public ConfigFileTestPlace(Configurator cfgInfo) throws IOException {
            super(ConfigFileTestPlace.class.getName());
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
     * Same functionality as {@link ConfigFileTestPlace} but without a .cfg file
     */
    static class NoConfigFileTestPlace extends ConfigFileTestPlace {
        public NoConfigFileTestPlace(Configurator cfgInfo) throws IOException {
            super(cfgInfo);
        }
    }

    /**
     * A place that always fails on startup
     */
    static class StartupFailureTestPlace extends ConfigFileTestPlace {
        static class SomeException extends RuntimeException {
            private static final long serialVersionUID = 123L;

            public SomeException(final String message) {
                super(message);
            }
        }

        public StartupFailureTestPlace(Configurator cfgInfo) throws IOException {
            super(cfgInfo);
            throw new SomeException("Some Exception message from Place instantiation");
        }
    }

    @Test
    void testCfgFile() {
        ConfiguredPlaceFactory<ConfigFileTestPlace> factory = new ConfiguredPlaceFactory<>(ConfigFileTestPlace.class);
        ConfigFileTestPlace place = factory.buildPlace();

        assertIterableEquals(List.of(CONSTANT_VALUE_1, CONSTANT_VALUE_2), place.getConstantTestConfigs());
        assertIterableEquals(List.of(CFG_FILE_VALUE_1, CFG_FILE_VALUE_2), place.getVariableTestConfigs());
    }

    @Test
    void testDoNotUseCfgFile() {
        ConfiguredPlaceFactory<ConfigFileTestPlace> factory = new ConfiguredPlaceFactory<>(ConfigFileTestPlace.class,
                new ServiceConfigGuide());
        ConfigFileTestPlace place = factory.buildPlace();

        assertTrue(place.getConstantTestConfigs().isEmpty());
        assertTrue(place.getVariableTestConfigs().isEmpty());
    }

    @Test
    void testCustomBaseConfigurator() {
        Configurator baseConfigurator = new ServiceConfigGuide();
        baseConfigurator.addEntry(CONSTANT_KEY, NEW_CONFIGURATOR_VALUE_1);
        baseConfigurator.addEntry(CONSTANT_KEY, NEW_CONFIGURATOR_VALUE_2);

        ConfiguredPlaceFactory<ConfigFileTestPlace> factory = new ConfiguredPlaceFactory<>(ConfigFileTestPlace.class, baseConfigurator);
        ConfigFileTestPlace place = factory.buildPlace();

        assertIterableEquals(List.of(NEW_CONFIGURATOR_VALUE_1, NEW_CONFIGURATOR_VALUE_2), place.getConstantTestConfigs());
        assertTrue(place.getVariableTestConfigs().isEmpty());
    }

    @Test
    void testExpectedCfgFileNotFound() {
        ConfiguredPlaceFactory<NoConfigFileTestPlace> factory = new ConfiguredPlaceFactory<>(NoConfigFileTestPlace.class);
        ConfigFileTestPlace place = factory.buildPlace();

        assertTrue(place.getConstantTestConfigs().isEmpty());
        assertTrue(place.getVariableTestConfigs().isEmpty());
    }

    @Test
    void testFactoryConstructorOverridesCfgFile() {
        ConfiguredPlaceFactory<ConfigFileTestPlace> factory = new ConfiguredPlaceFactory<>(ConfigFileTestPlace.class,
                new ConfigEntry(VARIABLE_KEY, FACTORY_CONSTRUCTOR_VALUE_1),
                new ConfigEntry(VARIABLE_KEY, FACTORY_CONSTRUCTOR_VALUE_2));
        ConfigFileTestPlace place = factory.buildPlace();

        assertIterableEquals(List.of(CONSTANT_VALUE_1, CONSTANT_VALUE_2), place.getConstantTestConfigs());
        assertIterableEquals(List.of(FACTORY_CONSTRUCTOR_VALUE_1, FACTORY_CONSTRUCTOR_VALUE_2), place.getVariableTestConfigs());
    }

    @Test
    void testFactoryConstructorWithoutCfgFile() {
        ConfiguredPlaceFactory<NoConfigFileTestPlace> factory = new ConfiguredPlaceFactory<>(NoConfigFileTestPlace.class,
                new ConfigEntry(VARIABLE_KEY, FACTORY_CONSTRUCTOR_VALUE_1),
                new ConfigEntry(VARIABLE_KEY, FACTORY_CONSTRUCTOR_VALUE_2));
        NoConfigFileTestPlace place = factory.buildPlace();

        assertTrue(place.getConstantTestConfigs().isEmpty());
        assertIterableEquals(List.of(FACTORY_CONSTRUCTOR_VALUE_1, FACTORY_CONSTRUCTOR_VALUE_2), place.getVariableTestConfigs());
    }

    @Test
    void testRemoveConfigurationsWithNullFactoryConstructorParameter() {
        ConfiguredPlaceFactory<ConfigFileTestPlace> factory = new ConfiguredPlaceFactory<>(ConfigFileTestPlace.class,
                new ConfigEntry(VARIABLE_KEY, null));
        ConfigFileTestPlace place = factory.buildPlace();

        assertIterableEquals(List.of(CONSTANT_VALUE_1, CONSTANT_VALUE_2), place.getConstantTestConfigs());
        assertTrue(place.getVariableTestConfigs().isEmpty());
    }

    @Test
    void testBuildParametersOverrideCfgFile() {
        ConfiguredPlaceFactory<ConfigFileTestPlace> factory = new ConfiguredPlaceFactory<>(ConfigFileTestPlace.class);
        ConfigFileTestPlace place = factory.buildPlace(
                new ConfigEntry(VARIABLE_KEY, BUILD_PARAMETER_VALUE_1),
                new ConfigEntry(VARIABLE_KEY, BUILD_PARAMETER_VALUE_2));

        assertIterableEquals(List.of(CONSTANT_VALUE_1, CONSTANT_VALUE_2), place.getConstantTestConfigs());
        assertIterableEquals(List.of(BUILD_PARAMETER_VALUE_1, BUILD_PARAMETER_VALUE_2), place.getVariableTestConfigs());
    }

    @Test
    void testBuildParametersWithoutCfgFile() {
        ConfiguredPlaceFactory<NoConfigFileTestPlace> factory = new ConfiguredPlaceFactory<>(NoConfigFileTestPlace.class);
        NoConfigFileTestPlace place = factory.buildPlace(
                new ConfigEntry(VARIABLE_KEY, BUILD_PARAMETER_VALUE_1),
                new ConfigEntry(VARIABLE_KEY, BUILD_PARAMETER_VALUE_2));

        assertTrue(place.getConstantTestConfigs().isEmpty());
        assertIterableEquals(List.of(BUILD_PARAMETER_VALUE_1, BUILD_PARAMETER_VALUE_2), place.getVariableTestConfigs());
    }

    @Test
    void testBuildParametersOverrideFactoryConstructor() {
        ConfiguredPlaceFactory<ConfigFileTestPlace> factory = new ConfiguredPlaceFactory<>(ConfigFileTestPlace.class,
                new ConfigEntry(VARIABLE_KEY, FACTORY_CONSTRUCTOR_VALUE_1),
                new ConfigEntry(VARIABLE_KEY, FACTORY_CONSTRUCTOR_VALUE_2));
        ConfigFileTestPlace place = factory.buildPlace(
                new ConfigEntry(VARIABLE_KEY, BUILD_PARAMETER_VALUE_1),
                new ConfigEntry(VARIABLE_KEY, BUILD_PARAMETER_VALUE_2));

        assertIterableEquals(List.of(CONSTANT_VALUE_1, CONSTANT_VALUE_2), place.getConstantTestConfigs());
        assertIterableEquals(List.of(BUILD_PARAMETER_VALUE_1, BUILD_PARAMETER_VALUE_2), place.getVariableTestConfigs());
    }

    @Test
    void testBuildParametersOverrideFactoryConstructorWithoutCfgFile() {
        ConfiguredPlaceFactory<NoConfigFileTestPlace> factory = new ConfiguredPlaceFactory<>(NoConfigFileTestPlace.class,
                new ConfigEntry(VARIABLE_KEY, FACTORY_CONSTRUCTOR_VALUE_1),
                new ConfigEntry(VARIABLE_KEY, FACTORY_CONSTRUCTOR_VALUE_2));
        NoConfigFileTestPlace place = factory.buildPlace(
                new ConfigEntry(VARIABLE_KEY, BUILD_PARAMETER_VALUE_1),
                new ConfigEntry(VARIABLE_KEY, BUILD_PARAMETER_VALUE_2));

        assertTrue(place.getConstantTestConfigs().isEmpty());
        assertIterableEquals(List.of(BUILD_PARAMETER_VALUE_1, BUILD_PARAMETER_VALUE_2), place.getVariableTestConfigs());
    }

    @Test
    void testRemoveConfigurationsWithNullBuildParameter() {
        ConfiguredPlaceFactory<ConfigFileTestPlace> factory = new ConfiguredPlaceFactory<>(ConfigFileTestPlace.class,
                new ConfigEntry(VARIABLE_KEY, FACTORY_CONSTRUCTOR_VALUE_1),
                new ConfigEntry(VARIABLE_KEY, FACTORY_CONSTRUCTOR_VALUE_2));
        ConfigFileTestPlace place = factory.buildPlace(new ConfigEntry(VARIABLE_KEY, null));

        assertIterableEquals(List.of(CONSTANT_VALUE_1, CONSTANT_VALUE_2), place.getConstantTestConfigs());
        assertTrue(place.getVariableTestConfigs().isEmpty());
    }


    @Test
    void testExpectedExceptionForBuildFailure() {
        ConfiguredPlaceFactory<StartupFailureTestPlace> factory = new ConfiguredPlaceFactory<>(StartupFailureTestPlace.class);
        Exception e = factory.getBuildPlaceException();

        assertInstanceOf(StartupFailureTestPlace.SomeException.class, e);
        assertEquals("Some Exception message from Place instantiation", e.getMessage());
    }

    @Test
    void testExpectedExceptionForBuildFailureWithProvidedType() {
        ConfiguredPlaceFactory<StartupFailureTestPlace> factory = new ConfiguredPlaceFactory<>(StartupFailureTestPlace.class);
        StartupFailureTestPlace.SomeException e = factory.getBuildPlaceException(StartupFailureTestPlace.SomeException.class);

        assertEquals("Some Exception message from Place instantiation", e.getMessage());
    }

    @Test
    void testUnexpectedExceptionTypeForBuildFailure() {
        String expectedExceptionName = "java.lang.NullPointerException";
        String actualExceptionName = "emissary.test.util.ConfiguredPlaceFactoryTest$StartupFailureTestPlace$SomeException";
        ConfiguredPlaceFactory<StartupFailureTestPlace> factory = new ConfiguredPlaceFactory<>(StartupFailureTestPlace.class);

        ClassCastException e = assertThrows(ClassCastException.class,
                () -> factory.getBuildPlaceException(NullPointerException.class));
        assertEquals(String.format("Cannot cast %s to %s", actualExceptionName, expectedExceptionName), e.getMessage());
    }

    @Test
    void testBuildSucceedsDespiteExpectedFailure() {
        String placeName = "emissary.test.util.ConfiguredPlaceFactoryTest$ConfigFileTestPlace";
        String exceptionName = "emissary.test.util.ConfiguredPlaceFactoryTest$StartupFailureTestPlace$SomeException";
        ConfiguredPlaceFactory<ConfigFileTestPlace> factory = new ConfiguredPlaceFactory<>(ConfigFileTestPlace.class);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> factory.getBuildPlaceException(StartupFailureTestPlace.SomeException.class));
        assertEquals(String.format("Succeeded building %s but expected to throw %s", placeName, exceptionName), e.getMessage());
    }
}
