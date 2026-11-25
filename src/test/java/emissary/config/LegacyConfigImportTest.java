package emissary.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LegacyConfigImportTest extends LegacyConfigLoadingTest {
    private static void useFlavor() throws Exception {
        System.setProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY, "PROD");
        ConfigUtil.initialize();
    }

    @Test
    void flavorDiscoveryDoesNotHideAmbiguousCfgNames() throws Exception {
        assumeCaseSensitiveFileSystem();
        Files.writeString(dir.resolve("Alpha.cfg"), "FOO = base\n");
        Files.writeString(dir.resolve("Alpha-PROD.cfg"), "FOO = lower\n");
        Files.writeString(dir.resolve("Alpha-PROD.CFG"), "FOO = upper\n");
        useFlavor();
        assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo("Alpha.cfg"));
        Files.writeString(dir.resolve("main.cfg"), "IMPORT_FILE = Alpha.cfg\n");
        assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo("main.cfg"));
    }

    @Test
    void missingOptionalBaseStillAllowsAnExistingFlavor() throws Exception {
        Files.writeString(dir.resolve("shared-PROD.cfg"), "OVERLAY = present\n");
        Files.writeString(dir.resolve("main.cfg"), "OPT_IMPORT_FILE = shared.cfg\n");
        useFlavor();
        assertEquals("present", ConfigUtil.getConfigInfo("main.cfg").findStringEntry("OVERLAY"));
    }

    @Test
    void oldStyleBaseKeepsItsFullyQualifiedFlavorLookup() throws Exception {
        Files.writeString(dir.resolve("LegacyFlavorTarget.cfg"), "FOO = base\n");
        useFlavor();
        assertEquals("classpath-flavor", ConfigUtil.getConfigInfo("emissary.config.LegacyFlavorTarget.cfg").findStringEntry("FOO"));
    }
}
