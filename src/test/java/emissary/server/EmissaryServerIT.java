package emissary.server;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import emissary.client.EmissaryClient;
import emissary.client.response.MapResponseEntity;
import emissary.command.ServerCommand;
import emissary.test.core.UnitTest;
import emissary.util.Version;
import org.apache.http.client.methods.HttpGet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.Test;

public class EmissaryServerIT extends UnitTest {

    @Test
    public void testThreadPoolStuff() throws Exception {
        ServerCommand cmd = ServerCommand.parse(ServerCommand.class, "-h", "host1", "-p", "3001");
        EmissaryServer server = new EmissaryServer(cmd);
        Server jettyServer = server.configureServer();
        QueuedThreadPool pool = (QueuedThreadPool) jettyServer.getThreadPool();
        assertThat(pool.getMinThreads(), equalTo(10));
        assertThat(pool.getMaxThreads(), equalTo(250));
        assertThat(pool.getLowThreadsThreshold(), equalTo(50));
        assertThat(pool.getIdleTimeout(), equalTo(new Long(TimeUnit.MINUTES.toMillis(15)).intValue()));
        assertThat(pool.getThreadsPriority(), equalTo(9));
    }

    @Test
    public void testSSLWorks() throws Exception {
        ServerCommand cmd = ServerCommand.parse(ServerCommand.class, "-p", "3443", "--ssl");
        EmissaryServer server = new EmissaryServer(cmd);
        try {
            server.startServer();
            EmissaryClient client = new EmissaryClient();
            String hostPort = cmd.getHost() + ":" + cmd.getPort(); // will be key in response
            String endpoint = cmd.getScheme() + "://" + hostPort + "/api/version";
            MapResponseEntity versionMap = client.send(new HttpGet(endpoint)).getContent(MapResponseEntity.class);
            Map<String, String> response = versionMap.getResponse();
            assertThat(response.get(hostPort), equalTo(new Version().getVersion()));
        } catch (Exception e) {
            System.err.println("Problem here");
            e.printStackTrace();
        } finally {
            // throws some stopping warning, good place to start when fixing the stop
            server.stop();
        }
    }

}
