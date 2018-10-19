package emissary.command;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.fail;

import java.nio.file.Paths;

import emissary.config.ConfigUtil;
import emissary.test.core.UnitTest;
import org.junit.Test;

public class ServerCommandIT extends UnitTest {
    private static final String PROJECT_BASE = System.getenv(ConfigUtil.PROJECT_BASE_ENV);
    private static final String PROJECT_BASE_SLASH = PROJECT_BASE + "/";

    @Test
    public void testGetConfig() throws Exception {
        ServerCommand cmd = ServerCommand.parse(ServerCommand.class, "-b", PROJECT_BASE, "-m", "standalone");
        assertThat(cmd.getConfig(), equalTo(Paths.get(PROJECT_BASE + "/config")));
    }

    @Test
    public void testGetConfigWithTrailingSlash() throws Exception {
        ServerCommand cmd = ServerCommand.parse(ServerCommand.class, "-b ", PROJECT_BASE_SLASH + "/", "-m", "standalone");
        assertThat(cmd.getConfig(), equalTo(Paths.get(PROJECT_BASE_SLASH + "/config")));
    }

    @Test
    public void testModeAddedToFlavors() throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        ServerCommand cmd = ServerCommand.parse(ServerCommand.class, "-b ", PROJECT_BASE_SLASH + "/", "-m", "standalone");
        assertThat(cmd.getFlavor(), equalTo("STANDALONE"));
    }

    @Test
    public void testStandaloneAndClusterError() throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        try {
            ServerCommand.parse(ServerCommand.class, "-b ", PROJECT_BASE_SLASH + "/", "-m", "standalone", "--flavor", "NORMAL,cluster");
            fail("Should have failed with standalone and cluster");
        } catch (Exception e) {
            assertThat(e.getMessage(), equalTo("Can not run a server in both STANDALONE and CLUSTER"));
        }
    }

    @Test
    public void testDeDupeFlavors() throws Exception {
        ServerCommand.parse(ServerCommand.class, "-b ", PROJECT_BASE + "/", "-m", "cluster", "--flavor", "NORMAL,cluster");
        assertThat(System.getProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY), equalTo("CLUSTER,NORMAL"));
    }

    // TODO: write a test for checking mode

}
