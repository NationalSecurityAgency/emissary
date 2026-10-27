package emissary.config;

import emissary.command.PeersCommand;
import emissary.directory.EmissaryNode;
import emissary.test.core.junit5.UnitTest;

import com.google.common.net.HostAndPort;
import jakarta.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ConfigResolutionTest extends UnitTest {
    @TempDir
    Path dir;

    @Nullable
    private String originalDir;
    @Nullable
    private String originalFlavor;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        originalDir = System.getProperty(ConfigUtil.CONFIG_DIR_PROPERTY);
        originalFlavor = System.getProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY);
        System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, dir.toString());
        System.clearProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY);
        ConfigUtil.initialize();
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        restoreProperty(ConfigUtil.CONFIG_DIR_PROPERTY, originalDir);
        restoreProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY, originalFlavor);
        ConfigUtil.initialize();
        super.tearDown();
    }

    private static void restoreProperty(final String name, @Nullable final String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    private static void useFlavor() throws Exception {
        System.setProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY, "PROD");
        ConfigUtil.initialize();
    }

    @Test
    void discoveryRejectsCompetingFormatsButExplicitRequestsRemainExact() throws Exception {
        Files.writeString(dir.resolve("Alpha.cfg"), "FOO = legacy\n");
        Files.writeString(dir.resolve("Alpha.yaml"), "FOO: structured\n");
        final IOException failure = assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo("Alpha"));
        assertTrue(failure.getMessage().contains("Alpha.cfg"));
        assertTrue(failure.getMessage().contains("Alpha.yaml"));
        assertEquals("legacy", ConfigUtil.getConfigInfo("Alpha.cfg").findStringEntry("FOO"));
        assertEquals("structured", ConfigUtil.getConfigInfo("Alpha.yaml").findStringEntry("FOO"));
        assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo("Alpha.toml"));
    }

    @Test
    void discoveryHonorsDirectoryOrderAcrossFormats() throws Exception {
        final Path defaults = Files.createDirectory(dir.resolve("defaults"));
        Files.writeString(dir.resolve("Alpha.yaml"), "FOO: override\n");
        Files.writeString(defaults.resolve("Alpha.cfg"), "FOO = default\n");
        Files.writeString(defaults.resolve("Alpha.toml"), "FOO = \"other-default\"\n");
        System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, dir + "," + defaults);
        ConfigUtil.initialize();
        assertEquals("override", ConfigUtil.getConfigInfo("Alpha").findStringEntry("FOO"));
        assertEquals("default", ConfigUtil.getConfigInfo("Alpha.cfg").findStringEntry("FOO"));
    }

    @Test
    void classDiscoveryRejectsCompetingFormats() throws Exception {
        final String name = ConfigResolutionTest.class.getName();
        Files.writeString(dir.resolve(name + ".cfg"), "FOO = legacy\n");
        Files.writeString(dir.resolve(name + ".toml"), "FOO = \"structured\"\n");
        assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo(ConfigResolutionTest.class));
    }

    @Test
    void generatedNodeAndPeerCommandNamesDiscoverStructuredConfigs() throws Exception {
        final EmissaryNode node = new EmissaryNode(EmissaryNode.Mode.CLUSTER);
        final String preference = "peer-" + node.getNodeName() + "-" + node.getNodePort();
        Files.writeString(dir.resolve(preference + ".yaml"),
                "RENDEZVOUS_PEER: '*.*.*.http://specific.example:7001/DirectoryPlace'\n");
        Files.writeString(dir.resolve("peer.toml"),
                "RENDEZVOUS_PEER = \"*.*.*.http://generic.example:8001/DirectoryPlace\"\n");
        assertEquals("*.*.*.http://specific.example:7001/DirectoryPlace",
                node.getPeerConfigurator().findStringEntry("RENDEZVOUS_PEER"));
        assertEquals(Set.of("generic.example:8001"), PeersCommand.getPeers(HostAndPort.fromString("ignored.example"), true));
    }

    @Test
    void structuredDirectoriesAreNotAcceptedAsEmptyConfigs() throws Exception {
        Files.createDirectory(dir.resolve("Alpha.yaml"));
        final IOException failure = assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo("Alpha"));
        assertTrue(ConfigUtil.isStrictConfigFailure(failure));
    }

    @Test
    void discoveryCannotScanOutsideTheConfigDirectoryUsingADotName() throws Exception {
        final Path configs = Files.createDirectory(dir.resolve("configs"));
        Files.writeString(dir.resolve("configs.yaml"), "FOO: outside\n");
        System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, configs.toString());
        ConfigUtil.initialize();
        assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo("."));
        assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo("../configs"));
    }

    @Test
    void classpathDiscoveryRejectsCompetingFormats() {
        final IOException failure = assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo("emissary.config.Sample"));
        assertTrue(failure.getMessage().contains("Sample.yaml"));
        assertTrue(failure.getMessage().contains("Sample.toml"));
    }

    @Test
    void oldStyleDiscoveryAndNestedClassNamesStillWork() throws Exception {
        Files.writeString(dir.resolve("Alpha_Beta.yml"), "FOO: short\n");
        assertEquals("short", ConfigUtil.getConfigInfo("example.Alpha$Beta").findStringEntry("FOO"));
        Files.writeString(dir.resolve("Alpha_Beta.cfg"), "FOO = legacy\n");
        assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo("example.Alpha$Beta"));
        assertEquals("legacy", ConfigUtil.getConfigInfo("example.Alpha$Beta.cfg").findStringEntry("FOO"));
    }

    @Test
    void oldStyleBaseRetainsFullyQualifiedClasspathFlavorLookup() throws Exception {
        Files.writeString(dir.resolve("LegacyFlavorTarget.cfg"), "FOO = base\n");
        useFlavor();
        assertEquals("classpath-flavor", ConfigUtil.getConfigInfo("emissary.config.LegacyFlavorTarget").findStringEntry("FOO"));
    }

    @Test
    void exactAbsolutePathsCannotBeRedirectedToShortNames() throws Exception {
        final Path external = Files.createDirectory(dir.resolve("external"));
        final Path exact = external.resolve("example.Alpha.cfg");
        Files.writeString(exact, "FOO = absolute\n");
        Files.writeString(dir.resolve("Alpha.yaml"), "FOO: unrelated\n");
        assertEquals("absolute", ConfigUtil.getConfigInfo(exact.toString()).findStringEntry("FOO"));
        assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo(external.resolve("example.Alpha.yaml").toString()));
    }

    private void assumeCaseSensitiveFileSystem() {
        Path probe = null;
        try {
            probe = Files.createTempFile(dir, "CaseProbe", ".tmp");
            final String lower = probe.getFileName().toString().toLowerCase(Locale.ROOT);
            assumeTrue(!Files.exists(dir.resolve(lower)), "Requires a case-sensitive filesystem");
        } catch (IOException e) {
            assumeTrue(false, "Unable to probe filesystem case sensitivity");
        } finally {
            if (probe != null) {
                probe.toFile().delete();
            }
        }
    }

    @Test
    void exactSuffixCaseIsPreservedAndDiscoveryDetectsCaseCollisions() throws Exception {
        assumeCaseSensitiveFileSystem();
        Files.writeString(dir.resolve("Alpha.CFG"), "FOO = uppercase\n");
        assertEquals("uppercase", ConfigUtil.getConfigInfo("Alpha.CFG").findStringEntry("FOO"));
        assertEquals("uppercase", ConfigUtil.getConfigInfo("Alpha").findStringEntry("FOO"));
        Files.writeString(dir.resolve("Alpha.cfg"), "FOO = lowercase\n");
        assertEquals("uppercase", ConfigUtil.getConfigInfo("Alpha.CFG").findStringEntry("FOO"));
        assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo("Alpha"));
    }

    @Test
    void resolvedResourceIsNotRediscoveredWhenOpened() throws Exception {
        Files.writeString(dir.resolve("Alpha.yaml"), "FOO: selected\n");
        final ConfigUtil.ResolvedConfig source = ConfigUtil.resolveConfig("Alpha");
        Files.writeString(dir.resolve("Alpha.cfg"), "FOO = competitor\n");
        assertEquals("Alpha.yaml", source.getName());
        assertEquals("selected", ConfigUtil.getConfigInfo(source).findStringEntry("FOO"));
    }

    @Test
    void legacyPreferencesRetainParseErrorFallback() throws Exception {
        Files.writeString(dir.resolve("bad.cfg"), "FOO =\n");
        Files.writeString(dir.resolve("good.cfg"), "FOO = fallback\n");
        assertEquals("fallback", ConfigUtil.getConfigInfo(List.of("bad.cfg", "good.cfg")).findStringEntry("FOO"));
        assertEquals("fallback", ConfigUtil.getConfigInfo(List.of("bad", "good")).findStringEntry("FOO"));
    }

    @Test
    void ambiguityCannotFallThroughPreferences() throws Exception {
        Files.writeString(dir.resolve("Alpha.yaml"), "FOO: one\n");
        Files.writeString(dir.resolve("Alpha.toml"), "FOO = \"two\"\n");
        Files.writeString(dir.resolve("fallback.cfg"), "FOO = fallback\n");
        final IOException failure = assertThrows(IOException.class,
                () -> ConfigUtil.getConfigInfo(List.of("Alpha", "fallback.cfg")));
        assertTrue(ConfigUtil.isStrictConfigFailure(failure));
    }

    @Test
    void importsUseExactExtensionsOrUniqueLogicalDiscovery() throws Exception {
        Files.writeString(dir.resolve("shared.yaml"), "FOO: imported\n");
        Files.writeString(dir.resolve("main.cfg"), "IMPORT_FILE = shared\n");
        assertEquals("imported", ConfigUtil.getConfigInfo("main.cfg").findStringEntry("FOO"));
        Files.writeString(dir.resolve("main.cfg"), "IMPORT_FILE = shared.cfg\n");
        assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo("main.cfg"));
        Files.writeString(dir.resolve("main.cfg"), "OPT_IMPORT_FILE = shared\n");
        Files.writeString(dir.resolve("shared.toml"), "FOO = \"competitor\"\n");
        assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo("main.cfg"));
    }

    @Test
    void flavorsCanUseDifferentFormatsAndKeepMergeSemantics() throws Exception {
        Files.writeString(dir.resolve("Alpha.yaml"), "FOO: base\nLIST: [old, keep]\n");
        Files.writeString(dir.resolve("Alpha-PROD.toml"), "FOO = \"flavor\"\nLIST = [{\"!remove\" = \"old\"}, \"new\"]\n");
        useFlavor();
        final Configurator config = ConfigUtil.getConfigInfo("Alpha");
        assertEquals("flavor", config.findStringEntry("FOO"));
        assertEquals(List.of("new", "keep"), config.findEntries("LIST"));
    }

    @Test
    void absoluteConfigsAndImportsKeepTheirFlavorLayers() throws Exception {
        final Path external = Files.createDirectory(dir.resolve("external"));
        final Path base = external.resolve("Alpha.cfg");
        Files.writeString(base, "FOO = base\n");
        Files.writeString(external.resolve("Alpha-PROD.cfg"), "FOO = flavor\n");
        useFlavor();
        assertEquals("flavor", ConfigUtil.getConfigInfo(base.toString()).findStringEntry("FOO"));
        Files.writeString(dir.resolve("main.cfg"), "IMPORT_FILE = \"" + base + "\"\n");
        assertEquals(List.of("base", "flavor"), ConfigUtil.getConfigInfo("main.cfg").findEntries("FOO"));
    }

    @Test
    void competingFlavorFormatsAreFatal() throws Exception {
        Files.writeString(dir.resolve("Alpha.cfg"), "FOO = base\n");
        Files.writeString(dir.resolve("Alpha-PROD.yaml"), "FOO: one\n");
        Files.writeString(dir.resolve("Alpha-PROD.toml"), "FOO = \"two\"\n");
        useFlavor();
        assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo("Alpha.cfg"));
    }

    @ParameterizedTest
    @ValueSource(strings = {".yaml", ".toml"})
    void malformedStructuredFlavorsAndOptionalImportsAreFatal(final String suffix) throws Exception {
        final String bad = suffix.equals(".yaml") ? "FOO: [unclosed\n" : "FOO = [unclosed\n";
        Files.writeString(dir.resolve("Alpha.cfg"), "FOO = base\n");
        Files.writeString(dir.resolve("Alpha-PROD" + suffix), bad);
        useFlavor();
        assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo("Alpha.cfg"));
        System.clearProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY);
        ConfigUtil.initialize();
        Files.writeString(dir.resolve("broken" + suffix), bad);
        Files.writeString(dir.resolve("optional.cfg"), "OPT_IMPORT_FILE = broken" + suffix + "\nFOO = base\n");
        assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo("optional.cfg"));
        Files.writeString(dir.resolve("optional.cfg"), "OPT_IMPORT_FILE = missing" + suffix + "\nFOO = base\n");
        assertEquals("base", ConfigUtil.getConfigInfo("optional.cfg").findStringEntry("FOO"));
    }

    @Test
    void structuredShapeErrorsCannotBeSwallowedThroughLegacyImports() throws Exception {
        Files.writeString(dir.resolve("broken.yaml"), "FOO: [one, {BAD: mapping}]\n");
        Files.writeString(dir.resolve("inner.cfg"), "OPT_IMPORT_FILE = broken.yaml\n");
        Files.writeString(dir.resolve("outer.cfg"), "OPT_IMPORT_FILE = inner.cfg\n");
        final IOException failure = assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo("outer.cfg"));
        assertTrue(ConfigUtil.isStrictConfigFailure(failure));
        assertTrue(failure.getMessage().contains("FOO"));
    }

    @Test
    void legacyOptionalImportAndFlavorBehaviorIsPreserved() throws Exception {
        Files.writeString(dir.resolve("inner.cfg"), "IMPORT_FILE = missing.cfg\n");
        Files.writeString(dir.resolve("Alpha.cfg"), "OPT_IMPORT_FILE = inner.cfg\nFOO = base\n");
        Files.writeString(dir.resolve("Alpha-PROD.cfg"), "FOO =\n");
        useFlavor();
        assertEquals("base", ConfigUtil.getConfigInfo("Alpha.cfg").findStringEntry("FOO"));
        Files.writeString(dir.resolve("inner.cfg"), "FOO =\n");
        final IOException failure = assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo("Alpha.cfg"));
        assertFalse(ConfigUtil.isStrictConfigFailure(failure));
    }

    @Test
    void inventoryModulesStillMergeButSameModuleCannotHaveMultipleFormats() throws Exception {
        Files.writeString(dir.resolve("emissary.admin.ClassNameInventory.cfg"), "Alpha = example.Alpha\n");
        Files.writeString(dir.resolve("emissary.admin.ClassNameInventory-module.yaml"), "Beta: example.Beta\n");
        assertEquals(2, ConfigUtil.getClassNameInventory().entryKeys().size());
        Files.writeString(dir.resolve("emissary.admin.ClassNameInventory-module.toml"), "Gamma = \"example.Gamma\"\n");
        assertThrows(IOException.class, ConfigUtil::getClassNameInventory);
    }
}
