package emissary.server.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import emissary.core.EmissaryException;
import emissary.core.Namespace;
import emissary.directory.DirectoryPlace;
import emissary.directory.EmissaryNode;
import emissary.server.EmissaryServer;
import emissary.test.core.UnitTest;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ApiUtilsTest extends UnitTest {
    @Rule
    public ExpectedException exception = ExpectedException.none();
    public static final String PEER = "*.*.*.http://somehost:8001/DirectoryPlace";
    public static final Set<String> PEERS = new HashSet<>(Arrays.asList(PEER));
    private DirectoryPlace mockDirectory;

    @BeforeClass
    public static void setUpClass() {
        Namespace.clear();
    }

    @Before
    @Override
    public void setUp() {
        mockDirectory = mock(DirectoryPlace.class);
        when(mockDirectory.getPeerDirectories()).thenReturn(PEERS);
        Namespace.bind("DirectoryPlace", mockDirectory);

        EmissaryServer mockServer = mock(EmissaryServer.class);
        EmissaryNode node = spy(new EmissaryNode());
        doReturn("localhost").when(node).getNodeName();
        doReturn(8001).when(node).getNodePort();
        when(mockServer.getNode()).thenReturn(node);
        Namespace.bind("EmissaryServer", mockServer);
    }

    @Override
    @After
    public void tearDown() {
        Namespace.clear();
    }

    @Test(expected = EmissaryException.class)
    public void lookupPeersNoDirectoryPlaceBound() throws Exception {
        // setup
        Namespace.unbind("DirectoryPlace");

        // test
        ApiUtils.lookupPeers();
    }

    @Test
    public void lookupPeers() throws Exception {
        // test
        Set<String> result = ApiUtils.lookupPeers();

        // verify
        assertEquals(PEERS, result);
    }

    @Test
    public void stripPeerString() throws Exception {
        // test
        String result = ApiUtils.stripPeerString(PEER);

        // verify
        assertEquals("http://somehost:8001/", result);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void stripPeerStringBadPeer() throws Exception {
        // test
        ApiUtils.stripPeerString("*.*.http://throwsException:8001");

    }

    @Test
    public void getHostAndPort() throws Exception {
        // test
        String hostAndPort = ApiUtils.getHostAndPort();

        // verify
        assertEquals("localhost:8001", hostAndPort);
    }

    @Test
    public void getHostAndPortNoEmissaryServer() {
        // setup
        Namespace.unbind("EmissaryServer");

        // test
        String result = ApiUtils.getHostAndPort();

        // verify
        assertEquals("Namespace lookup error, host unknown", result);
    }

}
