package emissary.config;

import emissary.test.core.junit5.UnitTest;

import jakarta.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LegacyConfigLoadingTest extends UnitTest {
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

    /**
     * Skip scenarios that require two filenames differing only by case. They cannot be represented on a case-insensitive
     * filesystem such as the default macOS filesystem.
     */
    protected void assumeCaseSensitiveFileSystem() {
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
    void closesStreamsOnSyntaxAndImportFailures() throws Exception {
        for (final String content : List.of("FOO =\n", "IMPORT_FILE = missing.cfg\n")) {
            final TrackingStream stream = new TrackingStream(content);
            assertThrows(IOException.class, () -> new ServiceConfigGuide(stream, "broken.cfg"));
            assertEquals(1, stream.closeCount);
        }
        final TrackingStream valid = new TrackingStream("FOO = okay\n");
        assertEquals("okay", new ServiceConfigGuide(valid).findStringEntry("FOO"));
        assertEquals(1, valid.closeCount);
    }

    @Test
    void syntaxErrorsIncludeTheFilename() {
        final IOException failure = assertThrows(IOException.class,
                () -> new ServiceConfigGuide(new ByteArrayInputStream("FOO =\n".getBytes(UTF_8)), "broken.cfg"));
        assertTrue(failure.getMessage().contains("broken.cfg"));
    }

    @Test
    void missingExplicitPathsDoNotFallBackToUnrelatedRootFiles() throws Exception {
        Files.writeString(dir.resolve("Alpha.cfg"), "FOO = unrelated\n");
        assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo("missing/Alpha.cfg"));
        assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo(dir.resolve("missing/Alpha.cfg").toString()));
    }

    @Test
    void packageNamesStillUseOldStyleFallbackAndExactSuffixCase() throws Exception {
        assumeCaseSensitiveFileSystem();
        Files.writeString(dir.resolve("Alpha_Beta.cfg"), "FOO = short\n");
        assertEquals("short", ConfigUtil.getConfigInfo("example.Alpha$Beta.cfg").findStringEntry("FOO"));
        Files.writeString(dir.resolve("Upper.CFG"), "FOO = uppercase\n");
        Files.writeString(dir.resolve("Upper.cfg"), "FOO = lowercase\n");
        assertEquals("uppercase", ConfigUtil.getConfigInfo("Upper.CFG").findStringEntry("FOO"));
    }

    @Test
    void exactExtensionlessFilesRetainPrecedence() throws Exception {
        Files.writeString(dir.resolve("Raw"), "FOO = raw\n");
        Files.writeString(dir.resolve("Raw.cfg"), "FOO = suffixed\n");
        assertEquals("raw", ConfigUtil.getConfigInfo("Raw").findStringEntry("FOO"));
    }

    @Test
    void nonstandardImportExtensionsKeepTheirFlavorNames() throws Exception {
        Files.writeString(dir.resolve("shared.properties"), "FOO = base\n");
        Files.writeString(dir.resolve("shared-PROD.properties"), "OVERLAY = present\n");
        Files.writeString(dir.resolve("main.cfg"), "IMPORT_FILE = shared.properties\n");
        System.setProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY, "PROD");
        ConfigUtil.initialize();
        assertEquals("present", ConfigUtil.getConfigInfo("main.cfg").findStringEntry("OVERLAY"));
    }

    @Test
    void legacyPreferencesStillFallBackAfterParseErrors() throws Exception {
        Files.writeString(dir.resolve("bad.cfg"), "FOO =\n");
        Files.writeString(dir.resolve("good.cfg"), "FOO = fallback\n");
        assertEquals("fallback", ConfigUtil.getConfigInfo(List.of("bad.cfg", "good.cfg")).findStringEntry("FOO"));
    }

    @Test
    void relativeTraversalIsRejectedAndAbsolutePathsRemainSupported() throws Exception {
        Files.writeString(dir.resolve("Alpha.cfg"), "FOO = absolute\n");
        assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo("../Alpha.cfg"));
        assertEquals("absolute", ConfigUtil.getConfigInfo(dir.resolve("./Alpha.cfg").toString()).findStringEntry("FOO"));
    }

    private static final class TrackingStream extends ByteArrayInputStream {
        int closeCount;

        TrackingStream(final String content) {
            super(content.getBytes(UTF_8));
        }

        @Override
        public void close() throws IOException {
            closeCount++;
            super.close();
        }
    }
}
