package emissary.command;

import emissary.config.ConfigUtil;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerCommandIT extends UnitTest {
    private static final String PROJECT_BASE = System.getenv(ConfigUtil.PROJECT_BASE_ENV);
    private static final String PROJECT_BASE_SLASH = PROJECT_BASE + "/";

    @Test
    void testGetConfig() throws Exception {
        ServerCommand cmd = ServerCommand.parse(ServerCommand.class, "-b", PROJECT_BASE, "-m", "standalone");
        assertEquals(Paths.get(PROJECT_BASE + "/config"), cmd.getConfig());
    }

    @Test
    void testGetConfigWithTrailingSlash() throws Exception {
        ServerCommand cmd = ServerCommand.parse(ServerCommand.class, "-b ", PROJECT_BASE_SLASH + "/", "-m", "standalone");
        assertEquals(Paths.get(PROJECT_BASE_SLASH + "/config"), cmd.getConfig());
    }

    @Test
    void testModeAddedToFlavors() throws InstantiationException, IllegalAccessException, ClassNotFoundException {
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
