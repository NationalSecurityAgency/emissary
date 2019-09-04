package emissary.directory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.EmissaryException;
import emissary.core.Namespace;
import emissary.test.core.UnitTest;
import emissary.util.io.ResourceReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DirectoryPlaceTest extends UnitTest {

    // Both on same machine so we don't have to test
    // with jetty
    private String masterloc = "http://localhost:8001/TestMasterDirectoryPlace";
    private String clientloc = "http://localhost:8001/DirectoryPlace";
    private DirectoryPlace master = null;
    private DirectoryPlace client = null;

    @Override
    @Before
    public void setUp() throws Exception {
        // master directory
        InputStream configStream = new ResourceReader().getConfigDataAsStream(this);

        this.master = spy(new DirectoryPlace(configStream, this.masterloc, new EmissaryNode()));
        configStream.close();
        Namespace.bind(this.masterloc, this.master);

        // non-master directory
        configStream = new ResourceReader().getConfigDataAsStream(this);
        this.client = spy(new DirectoryPlace(configStream, this.master.getDirectoryEntry().getKey(), this.clientloc, new EmissaryNode()));
        configStream.close();
        Namespace.bind(this.clientloc, this.client);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        this.master.shutDown();
        this.master = null;
        this.client.shutDown();
        this.client = null;
        for (Iterator<String> i = Namespace.keySet().iterator(); i.hasNext();) {
            Namespace.unbind(i.next());
        }

    }

    @SuppressWarnings("unchecked")
    @Test
    public void testAddEntryInMasterUsingFullKeys() {
        final List<String> keys = new ArrayList<String>();
        keys.add("DUMDUM.THISPLACE.ID.http://host.domain.com:8001/thePlace$5050");
        keys.add("DUMDUM.THATPLACE.ID.http://host.domain.com:8001/thePlace$5050");
        // TODO look for a better solution than spying (needed because of old STANDALONE configuration in emissary
        // client
        doNothing().when(this.master).addPeerDirectories(any(Set.class), any(Boolean.class));
        this.master.addPlaces(keys);
        final List<DirectoryEntry> allEntries = this.master.getEntries();
        // Two for the keys, one master and client directories shadowing
        assertEquals("Entries made " + allEntries, 3, allEntries.size());
        final DirectoryEntry de = allEntries.get(0);
        assertNotNull("Entry produced", de);
        assertEquals("Expense computation", 5050, de.getExpense());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testAddEntryInClientUsingFullKeys() {
        final List<String> keys = new ArrayList<String>();
        keys.add("DUMDUM.THISPLACE.ID.http://host.domain.com:8001/thePlace$5050");
        keys.add("DUMDUM.THATPLACE.ID.http://host.domain.com:8001/thePlace$5050");
        // TODO look for a better solution than spying (needed because of old STANDALONE configuration in emissary
        // client
        doNothing().when(this.client).irdAddPlaces(any(List.class), any(Boolean.class));
        this.client.addPlaces(keys);
        final List<DirectoryEntry> allEntries = this.master.getEntries();
        assertTrue("Entry made", allEntries.size() > 0);
        final DirectoryEntry de = allEntries.get(0);
        assertNotNull("Entry produced", de);
        assertEquals("Expense computation", 5050, de.getExpense());
    }

    @Test
    public void testContactThroughDirectoryEntry() {
        final DirectoryEntry d = new DirectoryEntry(this.client.getKey());
        assertEquals("Entry produced", this.client.getKey(), d.getKey());
        assertTrue("Entry points to local place", d.isLocal());
        final emissary.place.IServiceProviderPlace p = d.getLocalPlace();
        assertNotNull("Entry points to local place", p);
        assertTrue("Entry is directory", p instanceof IDirectoryPlace);
    }

    @Test
    public void testEmissaryNodeImpl() throws IOException, EmissaryException {
        try {
            final String c2loc = "http://stupidnode.example.com:3700/DirectoryPlace";
            final InputStream configStream = new ResourceReader().getConfigDataAsStream(this);
            final EmissaryNode myEmissaryNode = new TestEmissaryNode();
            assertTrue("Custom emissary node should be valid", myEmissaryNode.isValid());

            final DirectoryPlace client3 = spy(new DirectoryPlace(configStream, c2loc, myEmissaryNode));
            // we are faking a cluster, so we really don't want to wait for the timeout to hit
            // when sending messages
            doNothing().when(client3).sendFailMessage(any(DirectoryEntry.class), any(String.class), any(Boolean.class));
            // The emissary node should cause it to pick up the stupidnode config file
            assertNotNull("Directory created with custom EmissaryNode", client3);
            final Set<String> peerKeys = client3.getPeerDirectories();
            assertEquals("Should find one peer directory", 1, peerKeys.size());

            // Shut it down
            client3.shutDown();
            assertEquals("All peer directories removed", 0, client3.getPeerDirectories().size());

            // Try to add another peer after the shutdown
            final Set<String> s = new HashSet<String>();
            s.add("http://stupidnode.example.com:3900/StupidDirectoryPlace");
            client3.addPeerDirectories(s, true);
            assertEquals("Should not add peer during shutdown", 0, client3.getPeerDirectories().size());
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

        public Configurator getPeerConfigurator() throws IOException {
            // just go get this from the src/test/resources directory
            return ConfigUtil.getConfigInfo("emissary.directory.peer-stupidnode_example_com-3700.cfg");
        }
    }
}
