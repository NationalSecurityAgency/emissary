package emissary.config;

import emissary.test.core.junit5.UnitTest;
import emissary.util.io.ResourceReader;
import emissary.util.io.fixtures.YamlOnlyFixture;

import jakarta.annotation.Nullable;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceConfigGuideYamlTest extends UnitTest {

    private static final String BOM = "\uFEFF";

    private static ServiceConfigGuide parse(final String yaml, final String name) throws IOException {
        return new ServiceConfigGuide(new ByteArrayInputStream(yaml.getBytes(UTF_8)), name);
    }

    @Test
    void testFlattening() throws IOException {
        final String yaml = "FOO: bar\nCOUNT: 42\nRENDEZVOUS_PEER:\n"
                + "  - \"*.*.*.http://h1:7001/DirectoryPlace\"\n"
                + "  - \"*.*.*.http://h2:8001/DirectoryPlace\"\n"
                + "NESTED:\n  ONE: AAA\n  TWO: BBB\n";
        final ServiceConfigGuide scg = parse(yaml, "test.yaml");
        assertEquals("bar", scg.findStringEntry("FOO"));
        assertEquals("42", scg.findStringEntry("COUNT"));
        assertEquals(2, scg.findEntries("RENDEZVOUS_PEER").size());
        assertEquals("AAA", scg.findStringEntry("NESTED_ONE"));
        assertEquals("BBB", scg.findStringEntry("NESTED_TWO"));
    }

    @Test
    void testSubstitution() throws IOException {
        final String yaml = "BASE: hello\nGREETING: \"@{BASE} world\"\n";
        final ServiceConfigGuide scg = parse(yaml, "test.yaml");
        assertEquals("hello world", scg.findStringEntry("GREETING"));
    }

    @Test
    void testValuelessKey() {
        final IOException e = assertThrows(IOException.class, () -> parse("FOO:\nBAR: x\n", "test.yaml"));
        assertTrue(e.getMessage().contains("FOO") && e.getMessage().contains("no value"),
                "Should name the key and say it has no value, was: " + e.getMessage());
    }

    @Test
    void testValuelessKeyInSequence() {
        final IOException e = assertThrows(IOException.class, () -> parse("FOO:\n  - a\n  -\n", "test.yaml"));
        assertTrue(e.getMessage().contains("FOO") && e.getMessage().contains("no value"),
                "A blank sequence item has no value either, was: " + e.getMessage());
    }


    @Test
    void testQuotedBlank() throws IOException {
        // A quoted empty string is a real value, unlike a valueless key.
        final ServiceConfigGuide scg = parse("FOO: \"\"\nBAR: x\n", "test.yaml");
        assertEquals("", scg.findStringEntry("FOO"));
    }

    @Test
    void testNullSentinel() throws IOException {
        final ServiceConfigGuide scg = parse("FOO: \"<null>\"\n", "test.yaml");
        assertNull(scg.findStringEntry("FOO"));
    }

    @Test
    void testUnnamedStream() throws IOException {
        // Unnamed streams go through the legacy tokenizer, exactly as before; a named stream selects the YAML parser.
        assertThrows(IOException.class,
                () -> new ServiceConfigGuide(new ByteArrayInputStream("FOO: bar\n".getBytes(UTF_8))));
        final ServiceConfigGuide named =
                new ServiceConfigGuide(new ByteArrayInputStream("FOO: bar\n".getBytes(UTF_8)), "test.yaml");
        assertEquals("bar", named.findStringEntry("FOO"));
    }

    @Test
    void testBadYaml() {
        final String bad = "FOO: [unclosed\n";
        final IOException e = assertThrows(IOException.class, () -> parse(bad, "bad.yaml"));
        assertTrue(e.getMessage().contains("bad.yaml:1:"),
                "Syntax error should carry file:line:col, was: " + e.getMessage());
    }

    @Test
    void testNonMappingTopLevel() {
        final IOException e = assertThrows(IOException.class, () -> parse("- a\n- b\n", "list.yaml"));
        assertTrue(e.getMessage().contains("list.yaml") && e.getMessage().contains("sequence"),
                "Should name the file and the offending kind, was: " + e.getMessage());
    }

    @Test
    void testBangEqualsRejected() {
        final IOException e = assertThrows(IOException.class, () -> parse("\"!=\": x\n", "test.yaml"));
        assertTrue(e.getMessage().contains("operator"), "Was: " + e.getMessage());
    }

    @Test
    void testNonStringKeysCoerced() throws IOException {
        // Scalar mapping keys arrive as strings, matching legacy .cfg where every key is literal text.
        final ServiceConfigGuide scg = parse("42: num\n\"on\": word\n", "test.yaml");
        assertEquals("num", scg.findStringEntry("42"));
        assertEquals("word", scg.findStringEntry("on"));
    }

    @Test
    void testSuffixDetection() {
        assertTrue(StructuredConfigParser.isYamlFile("foo.yaml"));
        assertTrue(StructuredConfigParser.isYamlFile("foo.yml"));
        assertTrue(StructuredConfigParser.isYamlFile("foo.YAML"));
        assertFalse(StructuredConfigParser.isYamlFile("foo.cfg"));
        assertFalse(StructuredConfigParser.isYamlFile("foo.ycfg"));
    }

    @Test
    void testLegacyNameFindsYaml(@TempDir final Path dir) throws Exception {
        final String base = "emissary.test.YamlFallbackPlace";
        Files.writeString(dir.resolve(base + ".yaml"), "FOO: from-yaml\n", UTF_8);
        final String orig = System.getProperty(ConfigUtil.CONFIG_DIR_PROPERTY);
        System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, dir.toString());
        ConfigUtil.initialize();
        try {
            assertEquals("from-yaml", ConfigUtil.getConfigInfo(base + ".yaml").findStringEntry("FOO"));
            // A legacy .cfg request resolves the deployment's .yaml file.
            assertEquals("from-yaml", ConfigUtil.getConfigInfo(base + ".cfg").findStringEntry("FOO"));
            // A structured name for a missing file stays exact.
            assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo(base + ".yml"));
        } finally {
            if (orig != null) {
                System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, orig);
            }
            ConfigUtil.initialize();
        }
    }

    @Test
    void testImportYamlFromCfg(@TempDir final Path dir) throws Exception {
        Files.writeString(dir.resolve("shared.yaml"), "SHARED_KEY: shared-val\n", UTF_8);
        Files.writeString(dir.resolve("main.cfg"), "IMPORT_FILE = shared.yaml\nOWN_KEY = own\n", UTF_8);
        final String orig = System.getProperty(ConfigUtil.CONFIG_DIR_PROPERTY);
        System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, dir.toString());
        ConfigUtil.initialize();
        try {
            final Configurator cfg = ConfigUtil.getConfigInfo("main.cfg");
            assertEquals("own", cfg.findStringEntry("OWN_KEY"));
            assertEquals("shared-val", cfg.findStringEntry("SHARED_KEY"));
            // IMPORT_FILE names its file with the suffix.
            Files.writeString(dir.resolve("aliased.yaml"), "ALIASED: \"yes\"\n", UTF_8);
            Files.writeString(dir.resolve("uses-alias.cfg"), "IMPORT_FILE = aliased.yaml\n", UTF_8);
            final Configurator cfg2 = ConfigUtil.getConfigInfo("uses-alias.cfg");
            assertEquals("yes", cfg2.findStringEntry("ALIASED"));
        } finally {
            if (orig != null) {
                System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, orig);
            }
            ConfigUtil.initialize();
        }
    }


    @Test
    void testOptImportBrokenNested(@TempDir final Path dir) throws Exception {
        // An optional import that exists but cannot be read is still optional, so it must not abort startup.
        Files.writeString(dir.resolve("opt-inner.cfg"), "IMPORT_FILE = \"missing-somewhere.cfg\"\n", UTF_8);
        Files.writeString(dir.resolve("main.cfg"),
                "FOO = \"BAR\"\nOPT_IMPORT_FILE = \"" + dir.resolve("opt-inner.cfg") + "\"\n", UTF_8);
        withConfigDirAndFlavor(dir, null, () -> assertEquals("BAR", ConfigUtil.getConfigInfo("main.cfg").findStringEntry("FOO")));
    }

    @Test
    void testImportBrokenNestedFails(@TempDir final Path dir) throws Exception {
        // The same problem in a required IMPORT_FILE is still fatal, so a broken overlay is never silently skipped.
        Files.writeString(dir.resolve("req-inner.cfg"), "IMPORT_FILE = \"missing-somewhere.cfg\"\n", UTF_8);
        Files.writeString(dir.resolve("required.cfg"),
                "FOO = \"BAR\"\nIMPORT_FILE = \"" + dir.resolve("req-inner.cfg") + "\"\n", UTF_8);
        withConfigDirAndFlavor(dir, null, () -> assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo("required.cfg")));
    }


    @Test
    void testClasspathDefault(@TempDir final Path dir) throws Exception {
        // With no override the shipped default is still used, as before.
        withConfigDirAndFlavor(dir, null, () -> {
            assertNotNull(ConfigUtil.getConfigInfo("emissary.transform.HtmlEscapePlace.cfg")
                    .findStringEntry("SERVICE_KEY"));
        });
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static void withConfigDirAndFlavor(final Path dir, @Nullable final String flavor, final ThrowingRunnable test)
            throws Exception {
        final String origDir = System.getProperty(ConfigUtil.CONFIG_DIR_PROPERTY);
        final String origFlav = System.getProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY);
        System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, dir.toString());
        if (flavor != null) {
            System.setProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY, flavor);
        } else {
            System.clearProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY);
        }
        ConfigUtil.initialize();
        try {
            test.run();
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
    void testNestedOperatorKey() {
        // Operator keys only mean something at the top level; nested ones would otherwise flatten into a junk key.
        for (final String op : List.of("\"!=\"", "\"!remove\"", "\"!import\"", "\"!opt-import\"")) {
            final IOException e = assertThrows(IOException.class,
                    () -> new ServiceConfigGuide(new ByteArrayInputStream(
                            ("FOO: base\nBAR:\n  " + op + ": keep\n").getBytes(UTF_8)), "app.yaml"));
            assertTrue(e.getMessage().contains("only allowed at the top level"), e.getMessage());
        }
    }


    @Test
    void testDuplicateKeysKeepLast() throws IOException {
        // Duplicates are a startup error, like TOML. Multi-valued entries must use sequences.
        final IOException e = assertThrows(IOException.class, () -> parse("FOO: a\nFOO: b\n", "test.yaml"));
        assertTrue(e.getMessage().contains("Duplicate"), "Was: " + e.getMessage());
    }

    @Test
    void testNestedDuplicateKeysFail() throws IOException {
        final IOException e = assertThrows(IOException.class,
                () -> parse("NESTED:\n  ONE: a\n  ONE: b\n", "test.yaml"));
        assertTrue(e.getMessage().contains("Duplicate"), "Was: " + e.getMessage());
    }

    @Test
    void testMergeKeyRejected() throws IOException {
        // A YAML merge key would otherwise flatten to a bogus '<<' entry, so fail loudly instead.
        final IOException e = assertThrows(IOException.class,
                () -> parse("b: &b\n  X: 9\nderived:\n  <<: *b\n  C: 3\n", "test.yaml"));
        assertTrue(e.getMessage().contains("merge key"), "Was: " + e.getMessage());
    }

    @Test
    void testOpInRemove() throws IOException {
        // Operator keys directly inside !remove would otherwise run as top-level operators.
        final IOException e = assertThrows(IOException.class,
                () -> parse("\"!remove\": {\"!import\": \"other.yaml\"}\n", "test.yaml"));
        assertTrue(e.getMessage().contains("not allowed inside a !remove block"), "Was: " + e.getMessage());
    }

    @Test
    void testCollisionKeepsBoth() throws IOException {
        // NESTED: {B_C} flattens to the same key as the literal NESTED_B_C: both are kept,
        // lookups return the first.
        final ServiceConfigGuide scg = parse("NESTED:\n  B_C: nested\nNESTED_B_C: literal\n", "test.yaml");
        assertEquals(List.of("nested", "literal"), scg.findEntries("NESTED_B_C"));
        assertEquals("nested", scg.findStringEntry("NESTED_B_C"));
    }

    @Test
    void testSequenceItemsKeepBoth() throws IOException {
        final ServiceConfigGuide scg = parse("FOO:\n  - a\n  - b\n", "test.yaml");
        assertEquals(List.of("a", "b"), scg.findEntries("FOO"));
    }

    @Test
    void testCreateDirectives(@TempDir final Path dir) throws IOException {
        final Path subdir = dir.resolve("sub/dir");
        final Path file = dir.resolve("sub/file.txt");
        final String yaml = "CREATE_DIRECTORY: \"" + subdir + "\"\nCREATE_FILE: \"" + file + "\"\n";
        parse(yaml, "test.yaml");
        assertTrue(Files.isDirectory(subdir), "CREATE_DIRECTORY should create " + subdir);
        assertTrue(Files.isRegularFile(file), "CREATE_FILE should create " + file);
    }


    @Test
    void testPreferenceFallback(@TempDir final Path dir) throws Exception {
        // Node-style prefs keep their legacy .cfg names; a migrated .yaml still wins.
        Files.writeString(dir.resolve("peer-myhost-8001.yaml"), "RENDEZVOUS_PEER: peer-one\n", UTF_8);
        Files.writeString(dir.resolve("peer.cfg"), "RENDEZVOUS_PEER = peer-generic\n", UTF_8);
        withConfigDirAndFlavor(dir, null, () -> {
            final Configurator nodeCfg = ConfigUtil
                    .getConfigInfo(List.of("peer-myhost-8001.cfg", "peer-myhost.cfg", "peer.cfg"));
            assertEquals("peer-one", nodeCfg.findStringEntry("RENDEZVOUS_PEER"));

            final Configurator generic = ConfigUtil
                    .getConfigInfo(List.of("peer-unknown.cfg", "peer.cfg"));
            assertEquals("peer-generic", generic.findStringEntry("RENDEZVOUS_PEER"));
        });
    }

    @Test
    void testDirYamlBeatsClasspathCfg(@TempDir final Path dir) throws Exception {
        // A deployment override wins over a shipped default even across formats.
        final String requested = "emissary.transform.HtmlEscapePlace.cfg";
        Files.writeString(dir.resolve("emissary.transform.HtmlEscapePlace.yaml"), "MARKER: from-config-dir\n", UTF_8);
        withConfigDirAndFlavor(dir, null, () -> {
            final Configurator cfg = ConfigUtil.getConfigInfo(requested);
            assertEquals("from-config-dir", cfg.findStringEntry("MARKER"));
            assertNull(cfg.findStringEntry("SERVICE_KEY"),
                    "The shipped default's entries must not appear");
        });
    }

    @Test
    void testCfgPrecedence(@TempDir final Path dir) throws Exception {
        // Compatibility guarantee: a legacy .cfg is never shadowed by a same-named .yaml.
        Files.writeString(dir.resolve("contested.cfg"), "FOO = from-cfg\n", UTF_8);
        Files.writeString(dir.resolve("contested.yaml"), "FOO: from-yaml\n", UTF_8);
        withConfigDirAndFlavor(dir, null, () -> {
            assertEquals("from-cfg", ConfigUtil.getConfigInfo("contested.cfg").findStringEntry("FOO"));
        });
    }

    @Test
    void testPeerRoundTrip() throws IOException {
        // The real peer.cfg translated to YAML must load an identical entry multiset.
        final Path cfgPath = Path.of("src/main/config/peer.cfg");
        Assumptions.assumeTrue(Files.exists(cfgPath), "Needs repo checkout");
        final ServiceConfigGuide fromCfg;
        try (InputStream is = Files.newInputStream(cfgPath)) {
            fromCfg = new ServiceConfigGuide(is, "peer.cfg");
        }
        final String yaml = "RENDEZVOUS_PEER:\n"
                + "  - \"*.*.*.http://@{emissary.node.name}:7001/DirectoryPlace\"\n"
                + "  - \"*.*.*.http://@{emissary.node.name}:8001/DirectoryPlace\"\n"
                + "  - \"*.*.*.http://@{emissary.node.name}:9001/DirectoryPlace\"\n";
        final ServiceConfigGuide fromYaml = parse(yaml, "peer.yaml");
        assertEquals(entriesAsStrings(fromCfg), entriesAsStrings(fromYaml));
    }

    private static List<String> entriesAsStrings(final Configurator cfg) {
        final List<String> out = new ArrayList<>();
        for (final ConfigEntry e : cfg.getEntries()) {
            out.add(e.getKey() + "=" + e.getValue());
        }
        return out;
    }


    @Test
    void testClasspathYaml() throws IOException {
        final Configurator cfg = ConfigUtil.getConfigInfo("emissary.config.TestYamlClasspath.yaml");
        assertEquals("from-classpath", cfg.findStringEntry("CLASSPATH_KEY"));
        assertEquals("AAA", cfg.findStringEntry("NESTED_ONE"));
    }

    @Test
    void testClasspathYml() throws IOException {
        final Configurator cfg = ConfigUtil.getConfigInfo("emissary.config.TestYmlClasspath.yml");
        assertEquals("from-yml-classpath", cfg.findStringEntry("YML_KEY"));
    }

    @Test
    void testSampleParses() throws IOException {
        final Configurator cfg = ConfigUtil.getConfigInfo("emissary.config.Sample.yaml");
        assertEquals("SamplePlace", cfg.findStringEntry("PLACE_NAME"));
        assertEquals(2, cfg.findEntries("RENDEZVOUS_PEER").size());
        assertEquals("AAA", cfg.findStringEntry("MYPROPS_ONE"));
        assertEquals("hello world", cfg.findStringEntry("GREETING"));
        assertEquals("from-classpath", cfg.findStringEntry("CLASSPATH_KEY"));
    }

    @Test
    void testClasspathYamlExplicit() throws IOException {
        final Configurator cfg = ConfigUtil.getConfigInfo("emissary.config.TestYamlClasspath.yaml");
        assertEquals("from-classpath", cfg.findStringEntry("CLASSPATH_KEY"));
    }

    @Test
    void testBangRemove() throws IOException {
        final String yaml = "FOO:\n  - a\n  - b\nGONE:\n  - x\n  - y\n\"!remove\":\n  FOO: a\n  GONE: \"*\"\n";
        final ServiceConfigGuide scg = parse(yaml, "test.yaml");
        assertEquals(List.of("b"), scg.findEntries("FOO"));
        assertTrue(scg.findEntries("GONE").isEmpty());
    }

    @Test
    void testRemoveInSequence() throws IOException {
        // Add, remove, re-add in one sequence evaluates in order, like the legacy lines would.
        final String yaml = "FOO:\n  - a\n  - {\"!remove\": a}\n  - b\n";
        final ServiceConfigGuide scg = parse(yaml, "test.yaml");
        assertEquals(List.of("b"), scg.findEntries("FOO"));
    }

    @Test
    void testRemoveWildcardFirst() throws IOException {
        // Flavor-file pattern: clear inherited entries, then add new ones.
        final String yaml = "FOO:\n  - {\"!remove\": \"*\"}\n  - b\n  - c\n";
        final ServiceConfigGuide scg = parse(yaml, "test.yaml");
        assertEquals(List.of("b", "c"), scg.findEntries("FOO"));
    }

    @Test
    void testSequenceMapRemoveThrows() {
        final IOException e = assertThrows(IOException.class,
                () -> parse("FOO:\n  - {BAR: baz}\n", "test.yaml"));
        assertTrue(e.getMessage().contains("single-entry {\"!remove\""), "Was: " + e.getMessage());
    }

    @Test
    void testBangRemoveBadValue() {
        final IOException e = assertThrows(IOException.class, () -> parse("\"!remove\": [a]\n", "test.yaml"));
        assertTrue(e.getMessage().contains("\"!remove\"") && e.getMessage().contains("test.yaml"),
                "Was: " + e.getMessage());
    }

    @Test
    void testNestedCollectionError() {
        final IOException e = assertThrows(IOException.class,
                () -> parse("TOP:\n  NESTED:\n    - {A: b}\n", "test.yaml"));
        assertTrue(e.getMessage().contains("TOP_NESTED") && e.getMessage().contains("$.TOP.NESTED[0]"),
                "Was: " + e.getMessage());
    }

    @Test
    void testMissingImportError() {
        final IOException e = assertThrows(IOException.class,
                () -> parse("\"!import\": no-such-file.yaml\n", "test.yaml"));
        assertTrue(e.getMessage().contains("IMPORT_FILE") && e.getMessage().contains("$.!import"),
                "Was: " + e.getMessage());
    }

    @Test
    void testBangImport(@TempDir final Path dir) throws Exception {
        Files.writeString(dir.resolve("shared.yaml"), "SHARED_KEY: shared-val\n", UTF_8);
        Files.writeString(dir.resolve("main.yaml"), "\"!import\": shared.yaml\nOWN_KEY: own\n", UTF_8);
        final String orig = System.getProperty(ConfigUtil.CONFIG_DIR_PROPERTY);
        System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, dir.toString());
        ConfigUtil.initialize();
        try {
            final Configurator cfg = ConfigUtil.getConfigInfo("main.yaml");
            assertEquals("own", cfg.findStringEntry("OWN_KEY"));
            assertEquals("shared-val", cfg.findStringEntry("SHARED_KEY"));
        } finally {
            if (orig != null) {
                System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, orig);
            }
            ConfigUtil.initialize();
        }
    }

    @Test
    void testOptImportMissingSilent(@TempDir final Path dir) throws Exception {
        Files.writeString(dir.resolve("main.yaml"), "\"!opt-import\": no-such-file.yaml\nOWN_KEY: own\n", UTF_8);
        final String orig = System.getProperty(ConfigUtil.CONFIG_DIR_PROPERTY);
        System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, dir.toString());
        ConfigUtil.initialize();
        try {
            final Configurator cfg = ConfigUtil.getConfigInfo("main.yaml");
            assertEquals("own", cfg.findStringEntry("OWN_KEY"));
        } finally {
            if (orig != null) {
                System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, orig);
            }
            ConfigUtil.initialize();
        }
    }

    @Test
    void testFileFlavors(@TempDir final Path dir) throws Exception {
        Files.writeString(dir.resolve("emissary.test.FlavoredPlace.yaml"), "FOO: base\nONLY_BASE: 1\n", UTF_8);
        Files.writeString(dir.resolve("emissary.test.FlavoredPlace-MYFLAV.yaml"), "FOO: flavored\nONLY_FLAV: 2\n", UTF_8);
        final String origDir = System.getProperty(ConfigUtil.CONFIG_DIR_PROPERTY);
        final String origFlav = System.getProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY);
        System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, dir.toString());
        System.setProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY, "MYFLAV");
        ConfigUtil.initialize();
        try {
            final Configurator cfg = ConfigUtil.getConfigInfo("emissary.test.FlavoredPlace.yaml");
            // flavor merge prepends, so the flavored value wins findStringEntry
            assertEquals("flavored", cfg.findStringEntry("FOO"));
            assertEquals("1", cfg.findStringEntry("ONLY_BASE"));
            assertEquals("2", cfg.findStringEntry("ONLY_FLAV"));
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
    void testShortName(@TempDir final Path dir) throws Exception {
        Files.writeString(dir.resolve("ShortPlace.yml"), "FOO: short\n", UTF_8);
        final String orig = System.getProperty(ConfigUtil.CONFIG_DIR_PROPERTY);
        System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, dir.toString());
        ConfigUtil.initialize();
        try {
            final Configurator cfg = ConfigUtil.getConfigInfo("com.example.ShortPlace.yml");
            assertEquals("short", cfg.findStringEntry("FOO"));
        } finally {
            if (orig != null) {
                System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, orig);
            }
            ConfigUtil.initialize();
        }
    }

    @Test
    void testNamedStream(@TempDir final Path dir) throws Exception {
        // A YAML stream parses when loaded with its name; the legacy getConfigDataAsStream stays .cfg-only.
        withConfigDirAndFlavor(dir, null, () -> {
            final ResourceReader rr = new ResourceReader();
            assertNull(rr.getConfigDataAsStream(YamlOnlyFixture.class));
            final String name = rr.findConfigDataName(YamlOnlyFixture.class);
            assertEquals("emissary/util/io/fixtures/YamlOnlyFixture.yaml", name);
            try (InputStream is = rr.getResourceAsStream(name)) {
                assertNotNull(is);
                final Configurator cfg = ConfigUtil.getConfigInfo(is, name);
                assertEquals("bar", cfg.findStringEntry("FOO"));
                assertEquals("qux", cfg.findStringEntry("BAZ"));
            }
        });
    }

    @Test
    void testEmptyFilesAreValid() throws IOException {
        assertEquals(0, parse("", "test.yaml").getEntries().size());
        assertEquals(0, parse("\n\n   \n", "test.yaml").getEntries().size());
        assertEquals(0, parse("# just a comment\n#  and another\n\n", "test.yaml").getEntries().size());
        assertEquals(0, parse("   # indented comment\n", "test.yaml").getEntries().size());
        assertEquals(0, parse(BOM + "# bom then comment\n", "test.yaml").getEntries().size());
        assertEquals(0, parse("", "test.toml").getEntries().size());
        assertEquals(0, parse("# nothing here\n\n", "test.toml").getEntries().size());
    }

    @Test
    void testHashInValue() throws IOException {
        assertEquals("#nope", parse("FOO: \"#nope\"\n", "test.yaml").findStringEntry("FOO"));
        assertEquals("//", parse("FOO: \"//\"\n", "test.yaml").findStringEntry("FOO"));
        assertEquals("1", parse("\"a#b\" = 1\n", "test.toml").findStringEntry("a#b"));
    }

    @Test
    void testMultiDocumentRejected() {
        final IOException e = assertThrows(IOException.class, () -> parse("FOO: one\n---\nFOO: two\n", "test.yaml"));
        assertTrue(e.getMessage().contains("Trailing token"),
                "Should report the trailing document, was: " + e.getMessage());
        assertThrows(IOException.class, () -> parse("FOO: one\n---\n", "test.yaml"));
    }

    @Test
    void testLeadingDocumentMarker() throws IOException {
        assertEquals("one", parse("---\nFOO: one\n", "test.yaml").findStringEntry("FOO"));
        assertEquals("one", parse("FOO: one # trailing comment\n", "test.yaml").findStringEntry("FOO"));
    }

    @Test
    void testNonScalarRejected() {
        final IOException e = assertThrows(IOException.class, () -> parse("FOO: !!binary aGk=\n", "test.yaml"));
        assertTrue(e.getMessage().contains("must be a scalar"),
                "Should say the value must be a scalar, was: " + e.getMessage());
    }


    @Test
    void testInventoryYaml(@TempDir final Path dir) throws Exception {
        Files.writeString(dir.resolve("emissary.admin.ClassNameInventory.yaml"),
                "TestPlaceA: emissary.place.TestPlaceA\n", UTF_8);
        Files.writeString(dir.resolve("emissary.admin.ClassNameInventory-extra.yaml"),
                "TestPlaceB: emissary.place.TestPlaceB\n", UTF_8);
        final String orig = System.getProperty(ConfigUtil.CONFIG_DIR_PROPERTY);
        System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, dir.toString());
        ConfigUtil.initialize();
        try {
            final Configurator cfg = ConfigUtil.getClassNameInventory();
            assertEquals("emissary.place.TestPlaceA", cfg.findStringEntry("TestPlaceA"));
            assertEquals("emissary.place.TestPlaceB", cfg.findStringEntry("TestPlaceB"));
        } finally {
            if (orig != null) {
                System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, orig);
            }
            ConfigUtil.initialize();
        }
    }
}
