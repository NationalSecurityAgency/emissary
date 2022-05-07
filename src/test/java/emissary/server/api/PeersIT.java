package emissary.server.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Response;

import com.google.common.collect.Sets;
import emissary.client.response.PeersResponseEntity;
import emissary.command.ServerCommand;
import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.directory.DirectoryPlace;
import emissary.directory.EmissaryNode;
import emissary.server.EmissaryServer;
import emissary.server.mvc.EndpointTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class PeersIT extends EndpointTestBase {

    public static final String DIRNAME = "http://" + TestEmissaryNode.TEST_NODE_PORT + "/DirectoryPlace";
    public static final String SELF = "*.*.*.http://localhost:9999/DirectoryPlace";
    public static final String PEER1 = "*.*.*.http://remoteHost:8888/DirectoryPlace";
    public static final String PEER2 = "*.*.*.http://remoteHost2:8888/DirectoryPlace";
    public static final Set<String> PEERS = new HashSet<>(Arrays.asList(SELF, PEER1, PEER2));

    @BeforeEach
    public void setup() throws Exception {
        EmissaryNode emissaryNode = new TestEmissaryNode();
        DirectoryPlace directoryPlace = new DirectoryPlace(DIRNAME, emissaryNode);
        directoryPlace.addPeerDirectories(PEERS, false);
        Namespace.bind(DIRNAME, directoryPlace);

        String projectBase = System.getenv(ConfigUtil.PROJECT_BASE_ENV);
        ServerCommand cmd = ServerCommand.parse(ServerCommand.class, "-b ", projectBase, "-m", "cluster");
        cmd.setupServer();
        EmissaryServer server = new EmissaryServer(cmd, emissaryNode);
        Namespace.bind("EmissaryServer", server);
    }

    @AfterEach
    public void cleanup() {
        Namespace.unbind(DIRNAME);
        Namespace.unbind("EmissaryServer");
    }

    @Test
    void peers() {
        // test
        Response response = target("peers").request().get();

        // verify
        assertEquals(200, response.getStatus());
        PeersResponseEntity entity = response.readEntity(PeersResponseEntity.class);
        assertEquals(0, entity.getErrors().size());
        assertNull(entity.getCluster());
        assertEquals(TestEmissaryNode.TEST_NODE_PORT, entity.getLocal().getHost());
        assertTrue(entity.getLocal().getPeers().containsAll(PEERS));
    }

    @Test
    void peersNoEmissaryServer() {
        // TODO Look at behavior here, we should probably throw the exception and not catch and return this value
        // setup
        Namespace.unbind("EmissaryServer");

        // test
        Response response = target("peers").request().get();

        // verify
        assertEquals(200, response.getStatus());
        PeersResponseEntity entity = response.readEntity(PeersResponseEntity.class);
        assertEquals("Namespace lookup error, host unknown", entity.getLocal().getHost());
        assertEquals(0, entity.getErrors().size());
        assertTrue(entity.getLocal().getPeers().containsAll(PEERS));
    }

    @Test
    void peersNoDirectoryPlace() throws NamespaceException {
        // TODO Look at behavior here, should we still set the host if we have it?
        // setup
        Namespace.lookup(DIRNAME);
        Namespace.unbind(DIRNAME);

        // test
        Response response = target("peers").request().get();

        // verify
        assertEquals(200, response.getStatus());
        PeersResponseEntity entity = response.readEntity(PeersResponseEntity.class);
        assertNull(entity.getLocal());
        assertIterableEquals(Sets.newHashSet(Collections.singletonList("Not found: DirectoryPlace")), entity.getErrors());
    }

    @Test
    @Disabled("make this an integration test")
    void clusterPeers() {
        // TODO Look at putting this into an integration test with two real EmissaryServers stood up
    }

    static class TestEmissaryNode extends EmissaryNode {
        public static final int TEST_PORT = 123456;
        public static final String TEST_NODE = "localhost";
        public static final String TEST_NODE_PORT = TEST_NODE + ":" + TEST_PORT;

        public TestEmissaryNode() {
            nodeNameIsDefault = true;
        }

        @Override
        public int getNodePort() {
            return TEST_PORT;
        }

        @Override
        public String getNodeName() {
            return TEST_NODE;
        }

        @Override
        public boolean isStandalone() {
            return false;
        }

        @Override
        public Configurator getPeerConfigurator() throws IOException {
            // just go get this from the src/test/resources directory
            return ConfigUtil.getConfigInfo("peer-TESTING.cfg");
        }
    }
}
