package emissary.config;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceConfigGuideTomlTest extends UnitTest {

    private static ServiceConfigGuide parse(final String toml, final String name) throws IOException {
        return new ServiceConfigGuide(new ByteArrayInputStream(toml.getBytes(UTF_8)), name);
    }

    @Test
    void testFlattening() throws IOException {
        final String toml = "FOO = \"bar\"\nCOUNT = 42\n"
                + "RENDEZVOUS_PEER = [\"*.*.*.http://h1:7001/DirectoryPlace\", \"*.*.*.http://h2:8001/DirectoryPlace\"]\n"
                + "[NESTED]\nONE = \"AAA\"\nTWO = \"BBB\"\n";
        final ServiceConfigGuide scg = parse(toml, "test.toml");
        assertEquals("bar", scg.findStringEntry("FOO"));
        assertEquals("42", scg.findStringEntry("COUNT"));
        assertEquals(2, scg.findEntries("RENDEZVOUS_PEER").size());
        assertEquals("AAA", scg.findStringEntry("NESTED_ONE"));
        assertEquals("BBB", scg.findStringEntry("NESTED_TWO"));
    }

    @Test
    void testSubstitution() throws IOException {
        final ServiceConfigGuide scg = parse("BASE = \"hello\"\nGREETING = \"@{BASE} world\"\n", "test.toml");
        assertEquals("hello world", scg.findStringEntry("GREETING"));
    }

    @Test
    void testDottedKeysNest() throws IOException {
        final ServiceConfigGuide scg = parse("A.B = \"nested\"\n\"C.D\" = \"literal\"\n", "test.toml");
        assertEquals("nested", scg.findStringEntry("A_B"));
        assertEquals("literal", scg.findStringEntry("C.D"));
    }

    @Test
    void testBadToml() {
        final IOException e = assertThrows(IOException.class, () -> parse("FOO = [unclosed\n", "bad.toml"));
        assertTrue(e.getMessage().contains("bad.toml"), "Was: " + e.getMessage());
    }

    @Test
    void testDuplicateKeysAlwaysFail() {
        // The TOML spec forbids duplicates, so unlike YAML there is no warn-and-keep-last mode.
        final IOException e = assertThrows(IOException.class, () -> parse("FOO = \"a\"\nFOO = \"b\"\n", "test.toml"));
        assertTrue(e.getMessage().contains("Duplicate"), "Was: " + e.getMessage());
    }

    @Test
    void testMergeKeyRejected() {
        // '<<' has no merge meaning in TOML; reject it like YAML does for a consistent rule.
        final IOException e = assertThrows(IOException.class, () -> parse("\"<<\" = \"x\"\n", "test.toml"));
        assertTrue(e.getMessage().contains("merge key"), "Was: " + e.getMessage());
    }

    @Test
    void testIsTomlFile() {
        assertTrue(StructuredConfigParser.isTomlFile("foo.toml"));
        assertTrue(StructuredConfigParser.isTomlFile("foo.TOML"));
        assertFalse(StructuredConfigParser.isTomlFile("foo.cfg"));
        assertFalse(StructuredConfigParser.isTomlFile("foo.yaml"));
    }

    @Test
    void testBangRemove() throws IOException {
        final String toml = "FOO = [\"a\", \"b\"]\nGONE = [\"x\", \"y\"]\n[\"!remove\"]\nFOO = \"a\"\nGONE = \"*\"\n";
        final ServiceConfigGuide scg = parse(toml, "test.toml");
        assertEquals(List.of("b"), scg.findEntries("FOO"));
        assertTrue(scg.findEntries("GONE").isEmpty());
    }

    @Test
    void testPositionalRemove() throws IOException {
        final String toml = "FOO = [\"a\", {\"!remove\" = \"a\"}, \"b\"]\n";
        final ServiceConfigGuide scg = parse(toml, "test.toml");
        assertEquals(List.of("b"), scg.findEntries("FOO"));
    }

    @Test
    void testBangImport(@TempDir final Path dir) throws Exception {
        Files.writeString(dir.resolve("shared.toml"), "SHARED_KEY = \"shared-val\"\n", UTF_8);
        Files.writeString(dir.resolve("main.toml"), "\"!import\" = \"shared.toml\"\nOWN_KEY = \"own\"\n", UTF_8);
        withConfigDir(dir, () -> {
            final Configurator cfg = ConfigUtil.getConfigInfo("main.toml");
            assertEquals("own", cfg.findStringEntry("OWN_KEY"));
            assertEquals("shared-val", cfg.findStringEntry("SHARED_KEY"));
        });
    }

    @Test
    void testImportAcrossFormats(@TempDir final Path dir) throws Exception {
        // TOML importing legacy cfg and vice versa.
        Files.writeString(dir.resolve("legacy.cfg"), "FROM_CFG = \"yes\"\n", UTF_8);
        Files.writeString(dir.resolve("main.toml"), "\"!import\" = \"legacy.cfg\"\nFROM_TOML = \"yes\"\n", UTF_8);
        Files.writeString(dir.resolve("other.cfg"), "IMPORT_FILE = \"sided.toml\"\n", UTF_8);
        Files.writeString(dir.resolve("sided.toml"), "FROM_SIDE = \"yes\"\n", UTF_8);
        withConfigDir(dir, () -> {
            final Configurator fromToml = ConfigUtil.getConfigInfo("main.toml");
            assertEquals("yes", fromToml.findStringEntry("FROM_CFG"));
            final Configurator fromCfg = ConfigUtil.getConfigInfo("other.cfg");
            assertEquals("yes", fromCfg.findStringEntry("FROM_SIDE"));
        });
    }


    @Test
    void testLegacyNameFindsToml(@TempDir final Path dir) throws Exception {
        Files.writeString(dir.resolve("only.toml"), "FOO = \"from-toml\"\n", UTF_8);
        withConfigDir(dir, () -> {
            assertEquals("from-toml", ConfigUtil.getConfigInfo("only.toml").findStringEntry("FOO"));
            assertEquals("from-toml", ConfigUtil.getConfigInfo("only.cfg").findStringEntry("FOO"));
        });
    }

    @Test
    void testDirTomlBeatsClasspathCfg(@TempDir final Path dir) throws Exception {
        final String requested = "emissary.transform.HtmlEscapePlace.cfg";
        Files.writeString(dir.resolve("emissary.transform.HtmlEscapePlace.toml"), "MARKER = \"from-config-dir\"\n", UTF_8);
        withConfigDir(dir, () -> {
            final Configurator cfg = ConfigUtil.getConfigInfo(requested);
            assertEquals("from-config-dir", cfg.findStringEntry("MARKER"));
            assertNull(cfg.findStringEntry("SERVICE_KEY"),
                    "The shipped default's entries must not appear");
        });
    }

    @Test
    void testCfgBeatsToml(@TempDir final Path dir) throws Exception {
        Files.writeString(dir.resolve("contested.cfg"), "FOO = \"from-cfg\"\n", UTF_8);
        Files.writeString(dir.resolve("contested.toml"), "FOO = \"from-toml\"\n", UTF_8);
        withConfigDir(dir, () -> {
            assertEquals("from-cfg", ConfigUtil.getConfigInfo("contested.cfg").findStringEntry("FOO"));
        });
    }

    @Test
    void testClasspathToml() throws IOException {
        final Configurator cfg = ConfigUtil.getConfigInfo("emissary.config.ServiceConfigGuideTomlTest.toml");
        assertEquals("from-classpath", cfg.findStringEntry("CLASSPATH_KEY"));
        assertEquals("AAA", cfg.findStringEntry("NESTED_ONE"));
    }

    @Test
    void testSampleParses() throws IOException {
        final Configurator cfg = ConfigUtil.getConfigInfo("emissary.config.Sample.toml");
        assertEquals("SamplePlace", cfg.findStringEntry("PLACE_NAME"));
        assertEquals(2, cfg.findEntries("RENDEZVOUS_PEER").size());
        assertEquals("AAA", cfg.findStringEntry("MYPROPS_ONE"));
        assertEquals("hello world", cfg.findStringEntry("GREETING"));
        assertEquals("from-classpath", cfg.findStringEntry("CLASSPATH_KEY"));
    }

    @Test
    void testClassConfigFallsBackToToml() throws IOException {
        // Only the .toml resource exists for this class.
        final Configurator cfg = ConfigUtil.getConfigInfo(ServiceConfigGuideTomlTest.class);
        assertEquals("from-classpath", cfg.findStringEntry("CLASSPATH_KEY"));
    }

    @Test
    void testOldStyleShortName(@TempDir final Path dir) throws Exception {
        Files.writeString(dir.resolve("ShortPlace.toml"), "FOO = \"short\"\n", UTF_8);
        withConfigDir(dir, () -> {
            assertEquals("short", ConfigUtil.getConfigInfo("com.example.ShortPlace.toml").findStringEntry("FOO"));
        });
    }

    @Test
    void testPeerRoundTrip() throws IOException {
        final Path cfgPath = Path.of("src/main/config/peer.cfg");
        Assumptions.assumeTrue(Files.exists(cfgPath), "Needs repo checkout");
        final ServiceConfigGuide fromCfg;
        try (InputStream is = Files.newInputStream(cfgPath)) {
            fromCfg = new ServiceConfigGuide(is, "peer.cfg");
        }
        final String toml = "RENDEZVOUS_PEER = [\"*.*.*.http://@{emissary.node.name}:7001/DirectoryPlace\", "
                + "\"*.*.*.http://@{emissary.node.name}:8001/DirectoryPlace\", "
                + "\"*.*.*.http://@{emissary.node.name}:9001/DirectoryPlace\"]\n";
        final ServiceConfigGuide fromToml = parse(toml, "peer.toml");
        assertEquals(entriesAsStrings(fromCfg), entriesAsStrings(fromToml));
    }

    private static List<String> entriesAsStrings(final Configurator cfg) {
        final List<String> out = new ArrayList<>();
        for (final ConfigEntry e : cfg.getEntries()) {
            out.add(e.getKey() + "=" + e.getValue());
        }
        return out;
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static void withConfigDir(final Path dir, final ThrowingRunnable test) throws Exception {
        final String orig = System.getProperty(ConfigUtil.CONFIG_DIR_PROPERTY);
        System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, dir.toString());
        ConfigUtil.initialize();
        try {
            test.run();
        } finally {
            if (orig != null) {
                System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, orig);
            }
            ConfigUtil.initialize();
        }
    }
}
