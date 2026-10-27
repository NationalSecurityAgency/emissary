package emissary.server.api;

import emissary.client.response.Config;
import emissary.client.response.ConfigsResponseEntity;
import emissary.config.ConfigEntry;
import emissary.config.ConfigUtil;
import emissary.config.Configurator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigsTest {

    @Test
    void validate() {
        assertDoesNotThrow(() -> Configs.validate("some.random.config.PlaceConfig"));
        assertDoesNotThrow(() -> Configs.validate("some.random.config.PlaceConfig.yaml"));
        assertEquals("some.random.config.PlaceConfig.yaml", Configs.validate("some.random.config.PlaceConfig.yaml"));
        assertEquals("some.random.config.PlaceConfig.YAML", Configs.validate("some.random.config.PlaceConfig.YAML"));
        assertEquals("some.random.config.PlaceConfig", Configs.validate("some.random.config.PlaceConfig"));
        assertThrows(IllegalArgumentException.class, () -> Configs.validate("/dev/some.random.config.PlaceConfig"));
        assertThrows(IllegalArgumentException.class, () -> Configs.validate("https://dev/some.random.config.PlaceConfig"));
        assertThrows(IllegalArgumentException.class, () -> Configs.validate("..some.random.config.PlaceConfig"));
        assertThrows(IllegalArgumentException.class, () -> Configs.validate("."));
        assertThrows(IllegalArgumentException.class, () -> Configs.validate("%2e%2e%2fsome.random.config.PlaceConfig"));
        assertThrows(IllegalArgumentException.class, () -> Configs.validate("%252e%252e%252fsome.random.config.PlaceConfig"));
        assertThrows(IllegalArgumentException.class, () -> Configs.validate("U+002Fsome.random.config.PlaceConfigU+002F."));
    }

    @Test
    void redactsSensitiveValuesUnlessRequested() {
        final Configurator config = mock(Configurator.class);
        when(config.getEntries()).thenReturn(List.of(
                new ConfigEntry("DB_PASSWORD", "hunter2"),
                new ConfigEntry("NORMAL", "value")));

        assertEquals("***REDACTED***", valueOf(Configs.normalizeEntries(config, false), "DB_PASSWORD"));
        assertEquals("hunter2", valueOf(Configs.normalizeEntries(config, true), "DB_PASSWORD"));
        assertEquals("value", valueOf(Configs.normalizeEntries(config, false), "NORMAL"));
    }

    private static String valueOf(final List<ConfigEntry> entries, final String key) {
        return entries.stream().filter(entry -> key.equals(entry.getKey())).findFirst().orElseThrow().getValue();
    }

    @Test
    void testYamlConfig(@TempDir final Path dir) throws Exception {
        Files.writeString(dir.resolve("emissary.test.CmdPlace.yaml"), "PLACE_NAME: CmdYaml\n", UTF_8);
        final String orig = System.getProperty(ConfigUtil.CONFIG_DIR_PROPERTY);
        System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, dir.toString());
        ConfigUtil.initialize();
        try {
            final ConfigsResponseEntity plain = Configs.getConfigsResponse("emissary.test.CmdPlace.yaml", false);
            assertTrue(plain.getErrors().isEmpty(), "Non-detailed should load the explicit .yaml");
            assertTrue(containsEntry(plain.getLocal().getConfigs(), "PLACE_NAME", "CmdYaml"));

            final ConfigsResponseEntity detailed = Configs.getConfigsResponse("emissary.test.CmdPlace.yaml", true);
            assertTrue(detailed.getErrors().isEmpty(), "Detailed should load the explicit .yaml");
            assertTrue(containsEntry(detailed.getLocal().getConfigs(), "PLACE_NAME", "CmdYaml"));
        } finally {
            if (orig != null) {
                System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, orig);
            }
            ConfigUtil.initialize();
        }
    }

    @Test
    void testYamlNoSuffix(@TempDir final Path dir) throws Exception {
        Files.writeString(dir.resolve("emissary.test.SuffixlessCmdPlace.yaml"), "PLACE_NAME: SuffixlessYaml\n", UTF_8);
        final String orig = System.getProperty(ConfigUtil.CONFIG_DIR_PROPERTY);
        System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, dir.toString());
        ConfigUtil.initialize();
        try {
            final ConfigsResponseEntity plain = Configs.getConfigsResponse("emissary.test.SuffixlessCmdPlace", false);
            assertTrue(plain.getErrors().isEmpty(), "Plain should resolve the .yaml");
            assertTrue(containsEntry(plain.getLocal().getConfigs(), "PLACE_NAME", "SuffixlessYaml"));
            assertTrue(plain.getLocal().getConfigs().get(0).getConfigs().contains("emissary.test.SuffixlessCmdPlace.yaml"),
                    "Should report the resolved .yaml, Was: " + plain.getLocal().getConfigs().get(0).getConfigs());

            final ConfigsResponseEntity detailed = Configs.getConfigsResponse("emissary.test.SuffixlessCmdPlace", true);
            assertTrue(detailed.getErrors().isEmpty(), "Detailed should resolve the .yaml");
            assertTrue(containsEntry(detailed.getLocal().getConfigs(), "PLACE_NAME", "SuffixlessYaml"));
        } finally {
            if (orig != null) {
                System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, orig);
            }
            ConfigUtil.initialize();
        }
    }

    @Test
    void testYamlConfigFlavored(@TempDir final Path dir) throws Exception {
        Files.writeString(dir.resolve("emissary.test.FlavoredCmdPlace.yaml"), "FOO: base\n", UTF_8);
        Files.writeString(dir.resolve("emissary.test.FlavoredCmdPlace-CMD.yaml"), "FOO: flavored\n", UTF_8);
        final String origDir = System.getProperty(ConfigUtil.CONFIG_DIR_PROPERTY);
        final String origFlav = System.getProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY);
        System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, dir.toString());
        System.setProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY, "CMD");
        ConfigUtil.initialize();
        try {
            final ConfigsResponseEntity plain = Configs.getConfigsResponse("emissary.test.FlavoredCmdPlace.yaml", false);
            assertTrue(containsEntry(plain.getLocal().getConfigs(), "FOO", "flavored"));

            final ConfigsResponseEntity detailed = Configs.getConfigsResponse("emissary.test.FlavoredCmdPlace.yaml", true);
            // Base layer shows the unmerged base file, combined layer shows the flavor winning.
            assertTrue(containsEntry(detailed.getLocal().getConfigs(), "FOO", "base"));
            assertTrue(containsEntry(detailed.getLocal().getConfigs(), "FOO", "flavored"));
        } finally {
            if (origDir != null) {
                System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, origDir);
            }
            if (origFlav != null) {
                System.setProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY, origFlav);
            } else {
                System.clearProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY);
            }
            ConfigUtil.initialize();
        }
    }

    @Test
    void testDetailedBaseLayer(@TempDir final Path dir) throws Exception {
        Files.writeString(dir.resolve("emissary.test.DetailCmdPlace.yaml"), "FOO: base\n", UTF_8);
        Files.writeString(dir.resolve("emissary.test.DetailCmdPlace-CMD.yaml"), "FOO: inline\n", UTF_8);
        final String origDir = System.getProperty(ConfigUtil.CONFIG_DIR_PROPERTY);
        final String origFlav = System.getProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY);
        System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, dir.toString());
        System.setProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY, "CMD");
        ConfigUtil.initialize();
        try {
            final ConfigsResponseEntity detailed = Configs.getConfigsResponse("emissary.test.DetailCmdPlace.yaml", true);
            final List<Config> layers = detailed.getLocal().getConfigs();
            // Base layer is the unflavored file; the combined layer carries the file flavor.
            assertEquals(List.of("base"), entriesInLayer(layers, "emissary.test.DetailCmdPlace.yaml", "FOO"));
            assertTrue(containsEntry(layers, "FOO", "inline"));
        } finally {
            if (origDir != null) {
                System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, origDir);
            }
            if (origFlav != null) {
                System.setProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY, origFlav);
            } else {
                System.clearProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY);
            }
            ConfigUtil.initialize();
        }
    }

    @Test
    void testMixedFormatFlavorNamesAndStrictInspection(@TempDir final Path dir) throws Exception {
        final String name = "emissary.test.MixedCmdPlace";
        Files.writeString(dir.resolve(name + ".yaml"), "FOO: base\n", UTF_8);
        Files.writeString(dir.resolve(name + "-CMD.cfg"), "FOO = \"flavored\"\n", UTF_8);
        final String origDir = System.getProperty(ConfigUtil.CONFIG_DIR_PROPERTY);
        final String origFlav = System.getProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY);
        System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, dir.toString());
        System.setProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY, "CMD");
        ConfigUtil.initialize();
        try {
            final List<Config> layers = Configs.getConfigsResponse(name, true).getLocal().getConfigs();
            assertEquals(List.of("flavored"), entriesInLayer(layers, name + "-CMD.cfg", "FOO"));
            assertEquals(List.of(name + ".yaml", name + "-CMD.cfg"), layers.get(layers.size() - 1).getConfigs());
            Files.writeString(dir.resolve(name + "-CMD.yaml"), "FOO: [unclosed\n", UTF_8);
            assertThrows(IOException.class, () -> Configs.getConfigsResponse(name, true));
            assertThrows(IOException.class, () -> Configs.getConfigsResponse(name, false));
        } finally {
            if (origDir != null) {
                System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, origDir);
            } else {
                System.clearProperty(ConfigUtil.CONFIG_DIR_PROPERTY);
            }
            if (origFlav != null) {
                System.setProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY, origFlav);
            } else {
                System.clearProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY);
            }
            ConfigUtil.initialize();
        }
    }

    @Test
    void testDiscoveryAmbiguityAndExplicitExtensions(@TempDir final Path dir) throws Exception {
        final String name = "emissary.test.AmbiguousCmdPlace";
        Files.writeString(dir.resolve(name + ".cfg"), "FOO = legacy\n", UTF_8);
        Files.writeString(dir.resolve(name + ".yaml"), "FOO: structured\n", UTF_8);
        final String origDir = System.getProperty(ConfigUtil.CONFIG_DIR_PROPERTY);
        System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, dir.toString());
        ConfigUtil.initialize();
        try {
            assertThrows(IOException.class, () -> Configs.getConfigsResponse(name, false));
            assertThrows(IOException.class, () -> Configs.getConfigsResponse(name, true));
            assertTrue(containsEntry(Configs.getConfigsResponse(name + ".cfg", false).getLocal().getConfigs(), "FOO", "legacy"));
            assertTrue(containsEntry(Configs.getConfigsResponse(name + ".yaml", true).getLocal().getConfigs(), "FOO", "structured"));
        } finally {
            if (origDir != null) {
                System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, origDir);
            } else {
                System.clearProperty(ConfigUtil.CONFIG_DIR_PROPERTY);
            }
            ConfigUtil.initialize();
        }
    }

    private static boolean containsEntry(final List<Config> configs, final String key, final String value) {
        return configs.stream()
                .flatMap(c -> c.getEntries().stream())
                .anyMatch(e -> key.equals(e.getKey()) && value.equals(e.getValue()));
    }

    private static List<String> entriesInLayer(final List<Config> configs, final String configName, final String key) {
        final List<String> values = new ArrayList<>();
        for (final Config config : configs) {
            if (config.getConfigs() != null && config.getConfigs().equals(List.of(configName))) {
                for (final ConfigEntry entry : config.getEntries()) {
                    if (key.equals(entry.getKey())) {
                        values.add(entry.getValue());
                    }
                }
            }
        }
        return values;
    }
}
