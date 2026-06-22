package emissary.command;

import emissary.config.ConfigUtil;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerCommandIT extends UnitTest {
    private static final String PROJECT_BASE = System.getenv(ConfigUtil.PROJECT_BASE_ENV);
    private static final String PROJECT_BASE_SLASH = PROJECT_BASE + "/";

    @Test
    void testGetConfig() throws Exception {
        ServerCommand cmd = ServerCommand.parse(ServerCommand.class, "-b", PROJECT_BASE, "-m", "standalone");
        assertEquals(Path.of(PROJECT_BASE + "/config"), cmd.getConfig());
    }

    @Test
    void testGetConfigWithTrailingSlash() throws Exception {
        ServerCommand cmd = ServerCommand.parse(ServerCommand.class, "-b ", PROJECT_BASE_SLASH + "/", "-m", "standalone");
        assertEquals(Path.of(PROJECT_BASE_SLASH + "/config"), cmd.getConfig());
    }

    @Test
    void testGetMultipleConfigDirs(@TempDir Path extraConfig) throws Exception {
        String firstConfig = PROJECT_BASE + "/config";
        ServerCommand cmd = ServerCommand.parse(ServerCommand.class, "-b", PROJECT_BASE, "-m", "standalone",
                "-c", firstConfig + "," + extraConfig);
        Path pathFirstConfig = Path.of(firstConfig);
        assertEquals(List.of(pathFirstConfig, extraConfig), cmd.getConfigDirs());
        // first/primary dir is still returned by getConfig()
        assertEquals(pathFirstConfig, cmd.getConfig());
        // property is propagated as the comma-joined absolute paths
        assertEquals(pathFirstConfig.toAbsolutePath() + "," + extraConfig.toAbsolutePath(),
                System.getProperty(ConfigUtil.CONFIG_DIR_PROPERTY));
    }

    @Test
    void testNonExistentConfigDirInListRejected() {
        String firstConfig = PROJECT_BASE + "/config";
        assertThrows(Exception.class,
                () -> ServerCommand.parse(ServerCommand.class, "-b", PROJECT_BASE, "-m", "standalone",
                        "-c", firstConfig + ",/path/does/not/exist"));
    }

    @Test
    void testBlankConfigDirInListRejected() {
        String firstConfig = PROJECT_BASE + "/config";
        // an empty token (a,,b) must not silently resolve to the current working directory
        assertThrows(Exception.class,
                () -> ServerCommand.parse(ServerCommand.class, "-b", PROJECT_BASE, "-m", "standalone",
                        "-c", firstConfig + ",," + firstConfig));
    }

    @Test
    void testConfigDirWhitespaceTrimmed(@TempDir Path extraConfig) throws Exception {
        String firstConfig = PROJECT_BASE + "/config";
        ServerCommand cmd = ServerCommand.parse(ServerCommand.class, "-b", PROJECT_BASE, "-m", "standalone",
                "-c", firstConfig + " , " + extraConfig);
        assertEquals(List.of(Path.of(firstConfig), extraConfig), cmd.getConfigDirs());
    }

    @Test
    void testModeAddedToFlavors() throws Exception {
        ServerCommand cmd = ServerCommand.parse(ServerCommand.class, "-b ", PROJECT_BASE_SLASH + "/", "-m", "standalone");
        assertEquals("STANDALONE", cmd.getFlavor());
    }

    @Test
    void testStandaloneAndClusterError() {
        Exception e = assertThrows(Exception.class,
                () -> ServerCommand.parse(ServerCommand.class, "-b ", PROJECT_BASE_SLASH + "/", "-m", "standalone", "--flavor", "NORMAL,cluster"));
        assertEquals("Can not run a server in both STANDALONE and CLUSTER", e.getMessage());
    }

    @Test
    void testDeDupeFlavors() throws Exception {
        ServerCommand.parse(ServerCommand.class, "-b ", PROJECT_BASE + "/", "-m", "cluster", "--flavor", "NORMAL,cluster");
        assertEquals("CLUSTER,NORMAL", System.getProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY));
    }

    // TODO: write a test for checking mode

}
