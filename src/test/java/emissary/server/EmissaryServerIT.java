package emissary.server;

import emissary.admin.Startup;
import emissary.client.EmissaryClient;
import emissary.client.response.MapResponseEntity;
import emissary.command.ServerCommand;
import emissary.core.EmissaryException;
import emissary.directory.EmissaryNode;
import emissary.test.core.junit5.UnitTest;
import emissary.util.Version;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class EmissaryServerIT extends UnitTest {

    @Test
    void testThreadPoolStuff() throws Exception {
        ServerCommand cmd = ServerCommand.parse(ServerCommand.class, "-h", "host1", "-p", "3001");
        EmissaryServer server = EmissaryServer.init(cmd);
        Server jettyServer = server.configureServer();
        QueuedThreadPool pool = (QueuedThreadPool) jettyServer.getThreadPool();
        assertEquals(10, pool.getMinThreads());
        assertEquals(250, pool.getMaxThreads());
        assertEquals(50, pool.getLowThreadsThreshold());
        assertEquals(Long.valueOf(TimeUnit.MINUTES.toMillis(15)).intValue(), pool.getIdleTimeout());
        assertEquals(9, pool.getThreadsPriority());
    }

    @Test
    void testSSLWorks() throws Exception {
        ServerCommand cmd = ServerCommand.parse(ServerCommand.class, "-p", "3443", "--ssl", "--disableSniHostCheck");
        EmissaryServer server = EmissaryServer.init(cmd);
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

    @Test
    void testInvisPlacesOnStrictStartUp() throws EmissaryException {
        ServerCommand cmd = ServerCommand.parse(ServerCommand.class, "--strict");
        EmissaryServer server = EmissaryServer.init(cmd);
        EmissaryNode node = new EmissaryNode();
        String location = "http://" + node.getNodeName() + ":" + node.getNodePort();
        Startup.getInvisPlaces().add(location + "/PlaceStartUnannouncedTest");
        server.startServer();
        // make sure server is shutdown due to invis places on strict startup
        assertFalse(server.isServerRunning());
    }
}
