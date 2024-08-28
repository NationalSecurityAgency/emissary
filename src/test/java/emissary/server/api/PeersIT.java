package emissary.server.api;

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

import com.google.common.collect.Sets;
import jakarta.ws.rs.core.Response;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeersIT extends EndpointTestBase {

    public static final int TEST_PORT = 123456;
    public static final String TEST_NODE = "localhost";
    public static final String TEST_NODE_PORT = TEST_NODE + ":" + TEST_PORT;
    public static final String DIRNAME = "http://" + TEST_NODE_PORT + "/DirectoryPlace";
    public static final String SELF = "*.*.*.http://localhost:9999/DirectoryPlace";
    public static final String PEER1 = "*.*.*.http://remoteHost:8888/DirectoryPlace";
    public static final String PEER2 = "*.*.*.http://remoteHost2:8888/DirectoryPlace";
    public static final Set<String> PEERS = new HashSet<>(Arrays.asList(SELF, PEER1, PEER2));

    @BeforeEach
    public void setup() throws Exception {
        String projectBase = System.getenv(ConfigUtil.PROJECT_BASE_ENV);
        ServerCommand cmd = ServerCommand.parse(ServerCommand.class, "-b ", projectBase, "-m", "cluster", "-p", "123456");
        cmd.setupServer();
        EmissaryServer server = EmissaryServer.init(cmd, new TestEmissaryNode(EmissaryNode.EmissaryMode.CLUSTER));
        Namespace.bind("EmissaryServer", server);

        DirectoryPlace directoryPlace = new DirectoryPlace(DIRNAME, server.getNode());
        directoryPlace.addPeerDirectories(PEERS, false);
        Namespace.bind(DIRNAME, directoryPlace);
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
        assertTrue(CollectionUtils.isEmpty(entity.getCluster()));
        assertEquals(TEST_NODE_PORT, entity.getLocal().getHost());
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
        assertTrue(CollectionUtils.isEmpty(entity.getLocal().getPeers()));
        assertIterableEquals(Sets.newHashSet(Collections.singletonList("Not found: DirectoryPlace")), entity.getErrors());
    }

    static class TestEmissaryNode extends EmissaryNode {

        public TestEmissaryNode(EmissaryNode.EmissaryMode mode) {
            super(mode);
            nodeNameIsDefault = true;
        }

        @Override
        public Configurator getPeerConfigurator() throws IOException {
            // just go get this from the src/test/resources directory
            return ConfigUtil.getConfigInfo("peer-TESTING.cfg");
        }
    }
}
