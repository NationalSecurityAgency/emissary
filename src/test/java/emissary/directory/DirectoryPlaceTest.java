package emissary.directory;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.EmissaryException;
import emissary.core.Namespace;
import emissary.place.IServiceProviderPlace;
import emissary.test.core.junit5.UnitTest;
import emissary.util.io.ResourceReader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;

class DirectoryPlaceTest extends UnitTest {

    // Both on same machine so we don't have to test
    // with jetty
    private final String primaryloc = "http://localhost:8001/TestPrimaryDirectoryPlace";
    private final String clientloc = "http://localhost:8001/DirectoryPlace";
    private DirectoryPlace primary = null;
    private DirectoryPlace client = null;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        // primary directory
        InputStream configStream = new ResourceReader().getConfigDataAsStream(this);

        this.primary = spy(new DirectoryPlace(configStream, this.primaryloc, new EmissaryNode()));
        configStream.close();
        Namespace.bind(this.primaryloc, this.primary);

        // non-primary directory
        configStream = new ResourceReader().getConfigDataAsStream(this);
        this.client = spy(new DirectoryPlace(configStream, this.primary.getDirectoryEntry().getKey(), this.clientloc, new EmissaryNode()));
        configStream.close();
        Namespace.bind(this.clientloc, this.client);
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        this.primary.shutDown();
        this.primary = null;
        this.client.shutDown();
        this.client = null;
        for (String s : Namespace.keySet()) {
            Namespace.unbind(s);
        }

    }

    @SuppressWarnings("unchecked")
    @Test
    void testAddEntryInPrimaryUsingFullKeys() {
        final List<String> keys = new ArrayList<>();
        keys.add("DUMDUM.THISPLACE.ID.http://host.domain.com:8001/thePlace$5050");
        keys.add("DUMDUM.THATPLACE.ID.http://host.domain.com:8001/thePlace$5050");
        // TODO look for a better solution than spying (needed because of old STANDALONE configuration in emissary
        // client
        doNothing().when(this.primary).addPeerDirectories(any(Set.class), any(Boolean.class));
        this.primary.addPlaces(keys);
        final List<DirectoryEntry> allEntries = this.primary.getEntries();
        // Two for the keys, one primary and client directories shadowing
        assertEquals(3, allEntries.size(), "Entries made " + allEntries);
        final DirectoryEntry de = allEntries.get(0);
        assertNotNull(de, "Entry produced");
        assertEquals(5050, de.getExpense(), "Expense computation");
    }

    @SuppressWarnings("unchecked")
    @Test
    void testAddEntryInClientUsingFullKeys() {
        final List<String> keys = new ArrayList<>();
        keys.add("DUMDUM.THISPLACE.ID.http://host.domain.com:8001/thePlace$5050");
        keys.add("DUMDUM.THATPLACE.ID.http://host.domain.com:8001/thePlace$5050");
        // TODO look for a better solution than spying (needed because of old STANDALONE configuration in emissary
        // client
        doNothing().when(this.client).irdAddPlaces(any(List.class), any(Boolean.class));
        this.client.addPlaces(keys);
        final List<DirectoryEntry> allEntries = this.primary.getEntries();
        assertTrue(allEntries.size() > 0, "Entry made");
        final DirectoryEntry de = allEntries.get(0);
        assertNotNull(de, "Entry produced");
        assertEquals(5050, de.getExpense(), "Expense computation");
    }

    @Test
    void testContactThroughDirectoryEntry() {
        final DirectoryEntry d = new DirectoryEntry(this.client.getKey());
        assertEquals(this.client.getKey(), d.getKey(), "Entry produced");
        assertTrue(d.isLocal(), "Entry points to local place");
        final IServiceProviderPlace p = d.getLocalPlace();
        assertNotNull(p, "Entry points to local place");
        assertTrue(p instanceof IDirectoryPlace, "Entry is directory");
    }

    @Test
    void testEmissaryNodeImpl() throws IOException, EmissaryException {
        try {
            final String c2loc = "http://stupidnode.example.com:3700/DirectoryPlace";
            final InputStream configStream = new ResourceReader().getConfigDataAsStream(this);
            final EmissaryNode myEmissaryNode = new TestEmissaryNode();
            assertTrue(myEmissaryNode.isValid(), "Custom emissary node should be valid");

            final DirectoryPlace client3 = spy(new DirectoryPlace(configStream, c2loc, myEmissaryNode));
            // we are faking a cluster, so we really don't want to wait for the timeout to hit
            // when sending messages
            doNothing().when(client3).sendFailMessage(any(DirectoryEntry.class), any(String.class), any(Boolean.class));
            // The emissary node should cause it to pick up the stupidnode config file
            assertNotNull(client3, "Directory created with custom EmissaryNode");
            final Set<String> peerKeys = client3.getPeerDirectories();
            assertEquals(1, peerKeys.size(), "Should find one peer directory");

            // Shut it down
            client3.shutDown();
            assertEquals(0, client3.getPeerDirectories().size(), "All peer directories removed");

            // Try to add another peer after the shutdown
            final Set<String> s = new HashSet<>();
            s.add("http://stupidnode.example.com:3900/StupidDirectoryPlace");
            client3.addPeerDirectories(s, true);
            assertEquals(0, client3.getPeerDirectories().size(), "Should not add peer during shutdown");
        } finally {
            restoreConfig();
        }
    }

    static class TestEmissaryNode extends EmissaryNode {
        public TestEmissaryNode() {
            nodeNameIsDefault = true;
        }

        @Override
        public int getNodePort() {
            return 3700;
        }

        @Override
        public String getNodeName() {
            return "stupidnode.example.com";
        }

        @Override
        public String getNodeType() {
            return "trs80";
        }

        @Override
        public boolean isStandalone() {
            return false;
        }

        @Override
        public Configurator getPeerConfigurator() throws IOException {
            // just go get this from the src/test/resources directory
            return ConfigUtil.getConfigInfo("emissary.directory.peer-stupidnode_example_com-3700.cfg");
        }
    }
}
