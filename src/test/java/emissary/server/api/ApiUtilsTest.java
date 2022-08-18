package emissary.server.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import emissary.core.EmissaryException;
import emissary.core.Namespace;
import emissary.directory.DirectoryPlace;
import emissary.directory.EmissaryNode;
import emissary.server.EmissaryServer;
import emissary.test.core.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiUtilsTest extends UnitTest {
    public static final String PEER = "*.*.*.http://somehost:8001/DirectoryPlace";
    public static final Set<String> PEERS = new HashSet<>(Collections.singletonList(PEER));
    private DirectoryPlace mockDirectory;

    @BeforeAll
    public static void setUpClass() {
        Namespace.clear();
    }

    @BeforeEach
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
    @AfterEach
    public void tearDown() {
        Namespace.clear();
    }

    @Test
    void lookupPeersNoDirectoryPlaceBound() {
        Namespace.unbind("DirectoryPlace");
        assertThrows(EmissaryException.class, ApiUtils::lookupPeers);
    }

    @Test
    void lookupPeers() throws Exception {
        // test
        Set<String> result = ApiUtils.lookupPeers();

        // verify
        assertEquals(PEERS, result);
    }

    @Test
    void stripPeerString() {
        // test
        String result = ApiUtils.stripPeerString(PEER);

        // verify
        assertEquals("http://somehost:8001/", result);
    }

    @Test
    void stripPeerStringBadPeer() {
        assertThrows(IndexOutOfBoundsException.class, () -> ApiUtils.stripPeerString("*.*.http://throwsException:8001"));
    }

    @Test
    void getHostAndPort() {
        // test
        String hostAndPort = ApiUtils.getHostAndPort();

        // verify
        assertEquals("localhost:8001", hostAndPort);
    }

    @Test
    void getHostAndPortNoEmissaryServer() {
        // setup
        Namespace.unbind("EmissaryServer");

        // test
        String result = ApiUtils.getHostAndPort();

        // verify
        assertEquals("Namespace lookup error, host unknown", result);
    }

}
