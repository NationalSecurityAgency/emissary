package emissary.config;

import emissary.client.response.Config;
import emissary.parser.ParserFactory;
import emissary.server.api.Configs;
import emissary.util.TypeEngine;
import emissary.util.io.ResourceReader;
import emissary.util.io.fixtures.JsonOnlyFixture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceConfigGuideJsonTest extends LegacyConfigLoadingTest {
    private static ServiceConfigGuide parse(final String json) throws IOException {
        return new ServiceConfigGuide(new ByteArrayInputStream(json.getBytes(UTF_8)), "test.json");
    }

    private static void useFlavor() throws Exception {
        System.setProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY, "PROD");
        ConfigUtil.initialize();
    }

    @Test
    void flattensObjectsArraysAndScalarsWithoutRoundingDecimals() throws Exception {
        final Configurator config = parse("{\"FOO\":\"bar\",\"COUNT\":42,\"ENABLED\":true,"
                + "\"DECIMAL\":1.234567890123456789,\"LARGE\":123456789012345678901234567890,"
                + "\"LIST\":[\"a\",\"b\"],\"NESTED\":{\"ONE\":\"AAA\"},\"C.D\":\"literal\"}");
        assertEquals("bar", config.findStringEntry("FOO"));
        assertEquals("42", config.findStringEntry("COUNT"));
        assertEquals("true", config.findStringEntry("ENABLED"));
        assertEquals("1.234567890123456789", config.findStringEntry("DECIMAL"));
        assertEquals("123456789012345678901234567890", config.findStringEntry("LARGE"));
        assertEquals(List.of("a", "b"), config.findEntries("LIST"));
        assertEquals("AAA", config.findStringEntry("NESTED_ONE"));
        assertEquals("literal", config.findStringEntry("C.D"));
    }

    @Test
    void substitutionsBlankStringsAndNullSentinelUseLegacyHandling() throws Exception {
        final Configurator config = parse("{\"BASE\":\"hello\",\"GREETING\":\"@{BASE} world\","
                + "\"EMPTY\":\"\",\"NULL_ENTRY\":\"<null>\",\"HASH\":\"#literal\"}");
        assertEquals("hello world", config.findStringEntry("GREETING"));
        assertEquals("", config.findStringEntry("EMPTY"));
        assertNull(config.findStringEntry("NULL_ENTRY"));
        assertEquals("#literal", config.findStringEntry("HASH"));
    }

    @Test
    void positionalAndTopLevelRemovalsPreserveOrdering() throws Exception {
        final Configurator config = parse("{\"LIST\":[\"a\",{\"!remove\":\"a\"},\"b\"],"
                + "\"GONE\":[\"x\",\"y\"],\"!remove\":{\"GONE\":\"*\"}}");
        assertEquals(List.of("b"), config.findEntries("LIST"));
        assertTrue(config.findEntries("GONE").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"KEY\":1,\"KEY\":2}", "{\"NESTED\":{\"KEY\":1,\"KEY\":2}}"})
    void duplicateKeysAreFatal(final String json) {
        final IOException failure = assertThrows(IOException.class, () -> parse(json));
        assertTrue(failure.getMessage().contains("Duplicate"), failure.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"# comment\n", "// comment\n", "/* comment */", "{\"KEY\":1,}", "{KEY:1}",
            "{} {}", "{\"KEY\":NaN}", "{\"KEY\":01}", "\f"})
    void invalidJsonCannotBeSilentlyAccepted(final String json) {
        final IOException failure = assertThrows(IOException.class, () -> parse(json));
        assertTrue(failure.getMessage().contains("test.json"), failure.getMessage());
        assertTrue(ConfigUtil.isStrictConfigFailure(failure));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[1,2]", "true", "42", "\"scalar\""})
    void topLevelMustBeAnObject(final String json) {
        final IOException failure = assertThrows(IOException.class, () -> parse(json));
        assertTrue(failure.getMessage().contains("top level"), failure.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"KEY\":null}", "{\"LIST\":[null]}", "{\"LIST\":[[1]]}", "{\"LIST\":[{\"BAD\":1}]}",
            "{\"!remove\":[\"KEY\"]}", "{\"NESTED\":{\"!import\":\"missing.cfg\"}}"})
    void unsupportedShapesAndNullLeavesAreFatal(final String json) {
        assertThrows(IOException.class, () -> parse(json));
    }

    @Test
    void emptyFilesAndEmptyObjectsAreValid() throws Exception {
        for (final String json : List.of("", " \t\r\n", "{}", "\uFEFF \n")) {
            assertTrue(parse(json).getEntries().isEmpty());
        }
    }

    @Test
    void suffixesSelectTheParserWithoutSniffingLegacyStreams() throws Exception {
        assertTrue(StructuredConfigParser.isJsonFile("Alpha.JSON"));
        assertTrue(StructuredConfigParser.isStructuredFile("Alpha.json"));
        assertFalse(StructuredConfigParser.isJsonFile("Alpha.cfg"));
        assertEquals(".json", ConfigUtil.configFileSuffix("Alpha.JSON"));
        assertThrows(IOException.class,
                () -> new ServiceConfigGuide(new ByteArrayInputStream("{\"FOO\":\"bar\"}".getBytes(UTF_8)), "Alpha.cfg"));
        assertEquals("legacy", new ServiceConfigGuide(new ByteArrayInputStream("FOO = legacy\n".getBytes(UTF_8))).findStringEntry("FOO"));
    }

    @Test
    void discoveryIsUniqueButExplicitExtensionsRemainExact() throws Exception {
        Files.writeString(dir.resolve("Alpha.json"), "{\"FOO\":\"json\"}");
        assertEquals("json", ConfigUtil.getConfigInfo("Alpha").findStringEntry("FOO"));
        assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo("Alpha.cfg"));
        Files.writeString(dir.resolve("Alpha.cfg"), "FOO = legacy\n");
        assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo("Alpha"));
        assertEquals("legacy", ConfigUtil.getConfigInfo("Alpha.cfg").findStringEntry("FOO"));
        assertEquals("json", ConfigUtil.getConfigInfo("Alpha.json").findStringEntry("FOO"));
        assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo("missing/Alpha.json"));
    }

    @Test
    void earlierJsonDirectoryOverridesLaterCfgAndClasspathDefaults() throws Exception {
        final Path defaults = Files.createDirectory(dir.resolve("defaults"));
        Files.writeString(dir.resolve("Alpha.json"), "{\"FOO\":\"override\"}");
        Files.writeString(defaults.resolve("Alpha.cfg"), "FOO = default\n");
        System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, dir + "," + defaults);
        ConfigUtil.initialize();
        assertEquals("override", ConfigUtil.getConfigInfo("Alpha").findStringEntry("FOO"));
        Files.writeString(dir.resolve("emissary.transform.HtmlEscapePlace.json"), "{\"MARKER\":\"json\"}");
        assertEquals("json", ConfigUtil.getConfigInfo("emissary.transform.HtmlEscapePlace").findStringEntry("MARKER"));
    }

    @Test
    void classpathDiscoveryReaderAndNamedStreamsSupportJson() throws Exception {
        assertEquals("from-json-classpath", ConfigUtil.getConfigInfo(JsonOnlyFixture.class).findStringEntry("CLASSPATH_KEY"));
        assertEquals("from-json-classpath",
                ConfigUtil.getConfigInfo("emissary.util.io.fixtures.JsonOnlyFixture.JSON").findStringEntry("CLASSPATH_KEY"));
        final ResourceReader reader = new ResourceReader();
        final String name = reader.findConfigDataName(JsonOnlyFixture.class);
        assertEquals("emissary/util/io/fixtures/JsonOnlyFixture.json", name);
        assertEquals(List.of(name), reader.findConfigResourcesFor(JsonOnlyFixture.class));
        assertNull(reader.getConfigDataAsStream(JsonOnlyFixture.class));
        try (InputStream stream = reader.getResourceAsStream(name)) {
            assertEquals("AAA", ConfigUtil.getConfigInfo(stream, name).findStringEntry("NESTED_ONE"));
        }
        assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo(ServiceConfigGuideJsonTest.class));
        assertThrows(IllegalStateException.class, () -> reader.findConfigDataName(ServiceConfigGuideJsonTest.class));
    }

    @Test
    void importsCanCrossEveryFormatInBothDirections() throws Exception {
        Files.writeString(dir.resolve("legacy.cfg"), "FROM_CFG = yes\nIMPORT_FILE = shared.json\n");
        Files.writeString(dir.resolve("shared.json"), "{\"FROM_JSON\":\"yes\"}");
        Files.writeString(dir.resolve("shared.yaml"), "FROM_YAML: 'yes'\n\"!import\": shared.json\n");
        Files.writeString(dir.resolve("shared.toml"), "FROM_TOML = \"yes\"\n\"!import\" = \"shared.json\"\n");
        Files.writeString(dir.resolve("main.json"), "{\"!import\":[\"legacy.cfg\",\"shared.yaml\",\"shared.toml\"]}");
        final Configurator config = ConfigUtil.getConfigInfo("main.json");
        for (final String key : List.of("FROM_CFG", "FROM_JSON", "FROM_YAML", "FROM_TOML")) {
            assertEquals("yes", config.findStringEntry(key));
        }
    }

    @Test
    void logicalJsonImportsUseTheSelectedNameForMixedFormatFlavors() throws Exception {
        Files.writeString(dir.resolve("example.Shared.json"), "{\"FOO\":\"base\"}");
        Files.writeString(dir.resolve("example.Shared-PROD.toml"), "OVERLAY = \"present\"\n");
        Files.writeString(dir.resolve("main.cfg"), "IMPORT_FILE = example.Shared\n");
        useFlavor();
        assertEquals("present", ConfigUtil.getConfigInfo("main.cfg").findStringEntry("OVERLAY"));
    }

    @Test
    void jsonFlavorsApplyRemovalsAndCompetingFormatsFail() throws Exception {
        Files.writeString(dir.resolve("Alpha.cfg"), "FOO = base\nLIST = old\nLIST = keep\n");
        Files.writeString(dir.resolve("Alpha-PROD.json"), "{\"FOO\":\"flavor\",\"LIST\":[{\"!remove\":\"old\"},\"new\"]}");
        useFlavor();
        final Configurator config = ConfigUtil.getConfigInfo("Alpha.cfg");
        assertEquals("flavor", config.findStringEntry("FOO"));
        assertEquals(List.of("new", "keep"), config.findEntries("LIST"));
        Files.writeString(dir.resolve("Alpha-PROD.yaml"), "FOO: competitor\n");
        assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo("Alpha.cfg"));
    }

    @Test
    void jsonBaseCanUseACfgFlavor() throws Exception {
        Files.writeString(dir.resolve("Alpha.json"), "{\"FOO\":\"base\"}");
        Files.writeString(dir.resolve("Alpha-PROD.cfg"), "FOO = flavor\n");
        useFlavor();
        assertEquals("flavor", ConfigUtil.getConfigInfo("Alpha.json").findStringEntry("FOO"));
    }

    @Test
    void optionalImportsSkipMissingJsonButPropagateMalformedJson() throws Exception {
        Files.writeString(dir.resolve("main.cfg"), "OPT_IMPORT_FILE = missing.json\nFOO = base\n");
        assertEquals("base", ConfigUtil.getConfigInfo("main.cfg").findStringEntry("FOO"));
        Files.writeString(dir.resolve("broken.json"), "{\"FOO\":null}");
        Files.writeString(dir.resolve("main.cfg"), "OPT_IMPORT_FILE = broken.json\n");
        final IOException failure = assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo("main.cfg"));
        assertTrue(ConfigUtil.isStrictConfigFailure(failure));
        Files.writeString(dir.resolve("Alpha.cfg"), "FOO = base\n");
        Files.writeString(dir.resolve("Alpha-PROD.json"), "# invalid JSON comment\n");
        useFlavor();
        assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo("Alpha.cfg"));
    }

    @Test
    void malformedJsonPropagatesThroughPreferencesAndConsumers() throws Exception {
        Files.writeString(dir.resolve("broken.json"), "{\"FOO\":");
        Files.writeString(dir.resolve("good.cfg"), "FOO = fallback\n");
        assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo(List.of("broken", "good.cfg")));
        assertThrows(IllegalStateException.class, () -> new TypeEngine(List.of("broken.json")));
        Files.writeString(dir.resolve("emissary.parser.ParserFactory.json"), "{\"FOO\":");
        assertThrows(IllegalStateException.class, ParserFactory::new);
    }

    @Test
    void inventoryModulesMergeAndSameModuleFormatsAreAmbiguous() throws Exception {
        Files.writeString(dir.resolve("emissary.admin.ClassNameInventory.cfg"), "Alpha = example.Alpha\n");
        Files.writeString(dir.resolve("emissary.admin.ClassNameInventory-extra.json"), "{\"Beta\":\"example.Beta\"}");
        assertEquals(2, ConfigUtil.getClassNameInventory().entryKeys().size());
        Files.writeString(dir.resolve("emissary.admin.ClassNameInventory-extra.toml"), "Gamma = \"example.Gamma\"\n");
        assertThrows(IOException.class, ConfigUtil::getClassNameInventory);
    }

    @Test
    void inspectionReportsJsonBaseAndMixedFormatFlavorNames() throws Exception {
        Files.writeString(dir.resolve("example.Alpha.json"), "{\"FOO\":\"base\"}");
        Files.writeString(dir.resolve("example.Alpha-PROD.yaml"), "FOO: flavor\n");
        useFlavor();
        final List<Config> layers = Configs.getConfigsResponse("example.Alpha", true).getLocal().getConfigs();
        assertEquals(List.of("example.Alpha.json"), layers.get(0).getConfigs());
        assertEquals(List.of("example.Alpha.json", "example.Alpha-PROD.yaml"), layers.get(layers.size() - 1).getConfigs());
        assertTrue(Configs.getConfigsResponse("example.Alpha.json", false).getLocal().getConfigs().get(0).getEntries().stream()
                .anyMatch(entry -> "FOO".equals(entry.getKey()) && "flavor".equals(entry.getValue())));
    }

    @Test
    void extractionPreservesJsonContentAndSuffix() throws Exception {
        final String json = "{\"FOO\":\"json\"}\n";
        Files.writeString(dir.resolve("Alpha.json"), json);
        final Path output = Files.createDirectory(dir.resolve("output"));
        final ExtractResource extractor = new ExtractResource();
        extractor.setOutputDirectory(output.toString());
        assertEquals(json, extractor.getResource("Alpha.json"));
        extractor.writeResource("Alpha.json");
        assertEquals(json, Files.readString(output.resolve("Alpha.json")));
    }

    @Test
    void sampleUsesSubstitutionImportsAndPositionalRemoval() throws Exception {
        final Configurator config = ConfigUtil.getConfigInfo("emissary.config.Sample.json");
        assertEquals("SamplePlace", config.findStringEntry("PLACE_NAME"));
        assertEquals("hello world", config.findStringEntry("GREETING"));
        assertEquals("from-classpath", config.findStringEntry("CLASSPATH_KEY"));
        assertEquals(List.of("blue"), config.findEntries("COLORS"));
        assertEquals(2, config.findEntries("RENDEZVOUS_PEER").size());
    }
}
