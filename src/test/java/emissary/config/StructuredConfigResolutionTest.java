package emissary.config;

import emissary.parser.ParserFactory;
import emissary.util.TypeEngine;
import emissary.util.io.ResourceReader;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredConfigResolutionTest extends LegacyConfigLoadingTest {
    @Test
    void discoveryRejectsCompetingFormatsAndExplicitRequestsRemainExact() throws Exception {
        Files.writeString(dir.resolve("Alpha.cfg"), "FOO = legacy\n");
        Files.writeString(dir.resolve("Alpha.yaml"), "FOO: yaml\n");
        assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo("Alpha"));
        assertEquals("legacy", ConfigUtil.getConfigInfo("Alpha.cfg").findStringEntry("FOO"));
        assertEquals("yaml", ConfigUtil.getConfigInfo("Alpha.yaml").findStringEntry("FOO"));
        assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo("missing/Alpha.yaml"));
    }

    @Test
    void rawLegacyFilesStillWinOverNewFormats() throws Exception {
        Files.writeString(dir.resolve("Raw"), "FOO = raw\n");
        Files.writeString(dir.resolve("Raw.yaml"), "FOO: yaml\n");
        assertEquals("raw", ConfigUtil.getConfigInfo("Raw").findStringEntry("FOO"));
    }

    @Test
    void directoryOrderingWinsAcrossFormats() throws Exception {
        final Path defaults = Files.createDirectory(dir.resolve("defaults"));
        Files.writeString(dir.resolve("Alpha.yaml"), "FOO: override\n");
        Files.writeString(defaults.resolve("Alpha.cfg"), "FOO = default\n");
        System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, dir + "," + defaults);
        ConfigUtil.initialize();
        assertEquals("override", ConfigUtil.getConfigInfo("Alpha").findStringEntry("FOO"));
    }

    @Test
    void malformedStructuredConfigsReachConsumersAndLegacyDefaultsRemainAllowed() throws Exception {
        Files.writeString(dir.resolve("emissary.parser.ParserFactory.yaml"), "FOO: [unclosed\n");
        assertThrows(IllegalStateException.class, ParserFactory::new);
        Files.writeString(dir.resolve("broken.yaml"), "FOO: [unclosed\n");
        assertThrows(IllegalStateException.class, () -> new TypeEngine(List.of("broken.yaml")));
        Files.writeString(dir.resolve("broken.cfg"), "FOO =\n");
        assertDoesNotThrow(() -> new TypeEngine(List.of("broken.cfg")));
    }

    @Test
    void optionalImportsAndFlavorsDoNotHideStructuredErrors() throws Exception {
        Files.writeString(dir.resolve("broken.yaml"), "FOO: [one, {BAD: mapping}]\n");
        Files.writeString(dir.resolve("Alpha.cfg"), "OPT_IMPORT_FILE = broken.yaml\n");
        assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo("Alpha.cfg"));
        Files.writeString(dir.resolve("Alpha.cfg"), "FOO = base\n");
        Files.writeString(dir.resolve("Alpha-PROD.yaml"), "FOO: [unclosed\n");
        System.setProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY, "PROD");
        ConfigUtil.initialize();
        assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo("Alpha.cfg"));
    }

    @Test
    void logicalImportsGenerateFlavorsFromTheSelectedName() throws Exception {
        Files.writeString(dir.resolve("example.Shared.yaml"), "FOO: base\n");
        Files.writeString(dir.resolve("example.Shared-PROD.cfg"), "OVERLAY = present\n");
        Files.writeString(dir.resolve("main.cfg"), "IMPORT_FILE = example.Shared\n");
        System.setProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY, "PROD");
        ConfigUtil.initialize();
        assertEquals("present", ConfigUtil.getConfigInfo("main.cfg").findStringEntry("OVERLAY"));
    }

    @Test
    void readerAndCentralDiscoveryAgreeOnClasspathAmbiguity() {
        assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo(StructuredConfigResolutionTest.class));
        assertThrows(IllegalStateException.class, () -> new ResourceReader().findConfigDataName(StructuredConfigResolutionTest.class));
    }

    @Test
    void inventoryModulesMergeButCompetingModuleFormatsFail() throws Exception {
        Files.writeString(dir.resolve("emissary.admin.ClassNameInventory.cfg"), "Alpha = example.Alpha\n");
        Files.writeString(dir.resolve("emissary.admin.ClassNameInventory-module.yaml"), "Beta: example.Beta\n");
        assertEquals(2, ConfigUtil.getClassNameInventory().entryKeys().size());
        Files.writeString(dir.resolve("emissary.admin.ClassNameInventory-module.yml"), "Gamma: example.Gamma\n");
        final IOException failure = assertThrows(IOException.class, ConfigUtil::getClassNameInventory);
        assertTrue(ConfigUtil.isStrictConfigFailure(failure));
    }
}
