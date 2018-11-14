package emissary.server.api;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Response;

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
import org.hamcrest.collection.IsEmptyCollection;
import org.hamcrest.collection.IsIterableWithSize;
import org.hamcrest.junit.ExpectedException;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

public class PeersIT extends EndpointTestBase {
    @Rule
    public ExpectedException exception = ExpectedException.none();
    public static final String DIRNAME = "http://" + TestEmissaryNode.TEST_NODE_PORT + "/DirectoryPlace";
    public static final String PEER = "*.*.*.http://somehost:123456/DirectoryPlace";
    public static final Set<String> PEERS = new HashSet<>(Arrays.asList(PEER));

    @Before
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

    @After
    public void cleanup() {
        Namespace.unbind(DIRNAME);
        Namespace.unbind("EmissaryServer");
    }

    @Test
    public void peers() {
        // test
        Response response = target("peers").request().get();

        // verify
        assertThat(response.getStatus(), equalTo(200));
        PeersResponseEntity entity = response.readEntity(PeersResponseEntity.class);
        assertThat(entity.getErrors(), IsEmptyCollection.empty());
        assertThat(entity.getCluster(), equalTo(null));
        assertThat(entity.getLocal().getHost(), equalTo(TestEmissaryNode.TEST_NODE_PORT));
        assertThat(entity.getLocal().getPeers(), equalTo(PEERS));

    }

    @Test
    public void peersNoEmissaryServer() {
        // TODO Look at behavior here, we should probably throw the exception and not catch and return this value
        // setup
        Namespace.unbind("EmissaryServer");

        // test
        Response response = target("peers").request().get();

        // verify
        assertThat(response.getStatus(), equalTo(200));
        PeersResponseEntity entity = response.readEntity(PeersResponseEntity.class);
        assertThat(entity.getLocal().getHost(), equalTo("Namespace lookup error, host unknown"));
        assertThat(entity.getErrors(), IsIterableWithSize.iterableWithSize(0));
        assertThat(entity.getLocal().getPeers(), equalTo(PEERS));
    }

    @Test
    public void peersNoDirectoryPlace() throws NamespaceException {
        // TODO Look at behavior here, should we still set the host if we have it?
        // setup
        Namespace.lookup(DIRNAME);
        Namespace.unbind(DIRNAME);

        // test
        Response response = target("peers").request().get();

        // verify
        assertThat(response.getStatus(), equalTo(200));
        PeersResponseEntity entity = response.readEntity(PeersResponseEntity.class);
        assertThat(entity.getLocal(), equalTo(null));
        assertThat(entity.getErrors(), equalTo(new HashSet<>(Arrays.asList("Not found: DirectoryPlace"))));
    }

    @Ignore
    @Test
    public void clusterPeers() throws Exception {
        // TODO Look at putting this into an integration test with two real EmissaryServers stood up
    }

    class TestEmissaryNode extends EmissaryNode {
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

        public Configurator getPeerConfigurator() throws IOException {
            // just go get this from the src/test/resources directory
            return ConfigUtil.getConfigInfo("emissary.directory.peer-inoexist.cfg");
        }
    }
}
