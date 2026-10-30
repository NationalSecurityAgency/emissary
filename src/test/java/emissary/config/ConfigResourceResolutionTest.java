package emissary.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigResourceResolutionTest extends LegacyConfigLoadingTest {
    @Test
    void selectedLocationDoesNotChangeWhenOtherFilesAppear() throws Exception {
        final Path first = Files.createDirectory(dir.resolve("first"));
        final Path second = Files.createDirectory(dir.resolve("second"));
        Files.writeString(second.resolve("Alpha.cfg"), "FOO = selected\n");
        System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, first + "," + second);
        ConfigUtil.initialize();
        final ConfigUtil.ResolvedConfig source = ConfigUtil.resolveLogicalConfig("Alpha");
        Files.writeString(first.resolve("Alpha.cfg"), "FOO = later\n");
        assertEquals("selected", ConfigUtil.getConfigInfo(source).findStringEntry("FOO"));
        assertEquals("later", ConfigUtil.getConfigInfo("Alpha.cfg").findStringEntry("FOO"));
    }

    @Test
    void logicalDiscoveryDetectsCaseCollisionsButExplicitNamesStayExact() throws Exception {
        assumeCaseSensitiveFileSystem();
        Files.writeString(dir.resolve("Alpha.cfg"), "FOO = lower\n");
        Files.writeString(dir.resolve("Alpha.CFG"), "FOO = upper\n");
        assertThrows(IOException.class, () -> ConfigUtil.resolveLogicalConfig("Alpha"));
        assertEquals("lower", ConfigUtil.getConfigInfo("Alpha.cfg").findStringEntry("FOO"));
        assertEquals("upper", ConfigUtil.getConfigInfo("Alpha.CFG").findStringEntry("FOO"));
    }
}
