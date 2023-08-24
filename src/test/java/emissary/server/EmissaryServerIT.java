package emissary.server;

import emissary.client.EmissaryClient;
import emissary.client.response.MapResponseEntity;
import emissary.command.ServerCommand;
import emissary.test.core.junit5.UnitTest;
import emissary.util.Version;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmissaryServerIT extends UnitTest {

    @Test
    void testThreadPoolStuff() throws Exception {
        ServerCommand cmd = ServerCommand.parse(ServerCommand.class, "-h", "host1", "-p", "3001");
        EmissaryServer server = new EmissaryServer(cmd);
        Server jettyServer = server.configureServer();
        QueuedThreadPool pool = (QueuedThreadPool) jettyServer.getThreadPool();
        assertEquals(10, pool.getMinThreads());
        assertEquals(250, pool.getMaxThreads());
        assertEquals(50, pool.getLowThreadsThreshold());
        assertEquals(new Long(TimeUnit.MINUTES.toMillis(15)).intValue(), pool.getIdleTimeout());
        assertEquals(9, pool.getThreadsPriority());
    }

    @Test
    void testSSLWorks() throws Exception {
        ServerCommand cmd = ServerCommand.parse(ServerCommand.class, "-p", "3443", "--ssl");
        EmissaryServer server = new EmissaryServer(cmd);
        try {
            server.startServer();
            EmissaryClient client = new EmissaryClient();
            String hostPort = cmd.getHost() + ":" + cmd.getPort(); // will be key in response
            String endpoint = cmd.getScheme() + "://" + hostPort + "/api/version";
            MapResponseEntity versionMap = client.send(new HttpGet(endpoint)).getContent(MapResponseEntity.class);
            Map<String, String> response = versionMap.getResponse();
            assertEquals(new Version().getVersion(), response.get(hostPort));
        } finally {
            // throws some stopping warning, good place to start when fixing the stop
            server.stop();
        }
    }

}
