package emissary.directory;

import emissary.core.BaseDataObject;
import emissary.core.IBaseDataObject;
import emissary.place.IServiceProviderPlace;
import emissary.test.core.junit5.FunctionalTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FTestPeerDirectoryPlace extends FunctionalTest {

    private static final Logger logger = LoggerFactory.getLogger(FTestPeerDirectoryPlace.class);

    private IDirectoryPlace peer1 = null;
    private IDirectoryPlace peer2 = null;
    private IDirectoryPlace peer3 = null;

    private final DOHitCounter hcpeer1 = new DOHitCounter();
    private final DOHitCounter hcpeer2 = new DOHitCounter();
    private final DOHitCounter hcpeer3 = new DOHitCounter();

    private long SPEED_FACTOR = 1L; // slow down all the pauses if needed

    /*
     * peer1(8005) -- peer2(9005) -- peer3(7005) | + ToLower | | | / \ | | / \ child1(8006) child2(9006) child3 child4 +
     * ToUpper | (7006) (7007) | + TestPlace2 grandchild2(9007) + DevNullPlace
     */

    private boolean allTestsCompleted = false;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        startJetty(8005, "jettypeer");

        this.peer1 = directory; // from super class
        this.peer1.addObserver(this.hcpeer1);
        logger.debug("Setup phase finished");
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {

        if (logger.isDebugEnabled() && !this.allTestsCompleted) {
            logger.debug("Sleeping 5 min disable debug logging to stop this");
            try {
                Thread.sleep(300000);
            } catch (Exception ex) {
                // empty catch block
            }
            logger.debug("Sleep over");
        }

        // Shutdown directory places
        if (this.peer3 != null) {
            this.peer3.shutDown();
            this.peer3 = null;
        }

        if (this.peer2 != null) {
            this.peer2.shutDown();
            this.peer2 = null;
        }

        demolishServer();
        restoreConfig();

    }

    @Disabled("UnsupportedOperationException: We no longer use a tmp directory, fix this")
    @Test
    void testDirectories() throws Exception {
        // Check things about peer1 on 8005, a statically configured peer
        logger.debug("STARTING PEER1 on 8005");
        assertNotNull(this.peer1, "Peer1 directory found");
        assertTrue(this.peer1.getKey().contains("8005"), "Peer1 key has port");
        Set<String> peers = this.peer1.getPeerDirectories();
        assertNotNull(peers, "Peer1 has peers");
        assertTrue(peers.size() > 0, "Peer1 has peers");
        boolean foundother = false;
        for (final String other : peers) {
            if (other.contains("localhost:9005")) {
                foundother = true;
                break;
            }
        }
        assertTrue(foundother, "Peer1 knows about peer2");

        // Start peer2 on 9005, a second statically configured peer
        logger.debug("STARTING PEER2 on 9005");
        this.peer2 = startDirectory(9005);
        this.peer2.addObserver(this.hcpeer2);
        this.peer2.heartbeatRemoteDirectory(this.peer1.getKey());
        this.peer1.heartbeatRemoteDirectory(this.peer2.getKey());
        pause(this.SPEED_FACTOR * 250);
        assertNotNull(this.peer2, "Peer2 directory found");
        assertNotSame(this.peer1, this.peer2, "Peer1 and peer2 are distinct");
        assertTrue(this.peer2.getKey().contains("9005"), "Peer2 key has port");

        // See the the directories have peered with each other
        logger.debug("Checking peering between peer1 and peer2");
        peers = this.peer2.getPeerDirectories();
        foundother = false;
        assertNotNull(peers, "Peer2 has peers");
        assertTrue(peers.size() > 0, "Peer2 has peers");
        for (final String other : peers) {
            if (other.contains("localhost:8005")) {
                foundother = true;
                break;
            }
        }
        assertTrue(foundother, "Peer2 knows about peer2");

        // See the the directories have correct costs for each other
        String dataId = KeyManipulator.getDataID(this.peer1.getKey());
        DirectoryEntryList dl1 = this.peer1.getEntryList(dataId);
        final DirectoryEntryList dl2 = this.peer2.getEntryList(dataId);
        assertNotNull(dl1, "Directory entries in peer1");
        assertNotNull(dl2, "Directory entries in peer2");
        assertEquals(2, dl1.size(), "Have entries for both peers");
        assertEquals(2, dl2.size(), "Have entries for both peers");

        DirectoryEntry dl1d0 = dl1.getEntry(0);
        DirectoryEntry dl1d1 = dl1.getEntry(1);
        assertEquals(50, dl1d0.getCost(), "Cost of local in peer1");
        assertEquals(50 + IDirectoryPlace.REMOTE_COST_OVERHEAD, dl1d1.getCost(), "Cost of remote in peer1");

        final DirectoryEntry dl2d0 = dl2.getEntry(0);
        final DirectoryEntry dl2d1 = dl2.getEntry(1);
        assertEquals(50, dl2d0.getCost(), "Cost of local in peer2");
        assertEquals(50 + IDirectoryPlace.REMOTE_COST_OVERHEAD, dl2d1.getCost(), "Cost of remote in peer2");

        // Test path weight
        final int pathWeight = dl1d1.getPathWeight();
        assertTrue(pathWeight > 0, "Path weight is filled in in peer entry");

        // Try to issue a transient failure to affect the path weight
        logger.debug("Forcing a failure of peer2 as seen by peer1");
        ((IRemoteDirectory) this.peer1).irdFailDirectory(this.peer2.getKey(), false);

        // Get the directory entry again and recheck the weight
        dl1 = this.peer1.getEntryList(dataId);
        dl1d0 = dl1.getEntry(0);
        dl1d1 = dl1.getEntry(1);

        final int newPathWeight = dl1d1.getPathWeight();

        // It should go down due to the failure, but the increment is not
        // part of the public api, so just check that it is lower
        assertTrue(newPathWeight < pathWeight, "Path weight should decrease on failure, had " + pathWeight + " and now " + newPathWeight);

        // Start peer3 on 7005 a non-statically configured peer
        logger.debug("STARTING PEER3 on 7005");
        this.peer3 = startDirectory(7005);
        this.peer3.addObserver(this.hcpeer3);
        this.peer3.heartbeatRemoteDirectory(this.peer2.getKey());
        this.peer3.heartbeatRemoteDirectory(this.peer1.getKey());
        this.peer1.heartbeatRemoteDirectory(this.peer3.getKey());
        this.peer2.heartbeatRemoteDirectory(this.peer3.getKey());
        pause(this.SPEED_FACTOR * 250);
        assertNotNull(this.peer3, "Peer3 directory found");
        assertNotSame(this.peer1, this.peer3, "Peer1 and peer3 are distinct");
        assertNotSame(this.peer2, this.peer3, "Peer2 and peer3 are distinct");
        assertTrue(this.peer3.getKey().contains("7005"), "Peer3 key has port");
        assertEquals(2, this.peer3.getPeerDirectories().size(), "Peer3 has 2 peers");
        assertEquals(2, this.peer2.getPeerDirectories().size(), "Peer2 has 2 peers");
        assertEquals(2, this.peer1.getPeerDirectories().size(), "Peer1 has 2 peers");
        assertEquals(1, this.hcpeer2.peerUpdate, "Peer observer called");
        assertEquals(2, this.hcpeer2.lastPeerSetSize, "Peer observer set size");

        assertEquals(1, this.hcpeer2.peerUpdate, "Peer observer no change");
        assertEquals(2, this.hcpeer2.lastPeerSetSize, "Peer observer set size no change");

        // Add a place to a peer, check costs
        logger.debug("Starring ToLower place on peer2/9005");
        final IServiceProviderPlace toLower =
                addPlace("http://localhost:9005/ToLowerPlace", "emissary.place.sample.ToLowerPlace", this.peer2.getKey());

        assertNotNull(toLower, "ToLower created on peer2");
        DirectoryEntry dehome = toLower.getDirectoryEntry();
        assertNotNull(dehome, "ToLower directory entry");

        dataId = KeyManipulator.getDataID(toLower.getKey());
        logger.debug("ToLower data id is " + dataId);
        DirectoryEntryList dlpeer1 = this.peer1.getEntryList(dataId);
        assertNotNull(dlpeer1, "Tolower dataid list from peer1");
        assertEquals(1, dlpeer1.size(), "Size of dataid list from peer1");
        assertEquals(dehome.getExpense() + IDirectoryPlace.REMOTE_EXPENSE_OVERHEAD, dlpeer1.getEntry(0).getExpense(), "Peer1 cost one hop");
        DirectoryEntryList dlpeer2 = this.peer2.getEntryList(dataId);
        assertNotNull(dlpeer2, "Tolower dataid list from peer2");
        assertEquals(1, dlpeer2.size(), "Size of dataid list from peer2");
        assertEquals(dehome.getExpense(), dlpeer2.getEntry(0).getExpense(), "Home cost no change in peer2");
        DirectoryEntryList dlpeer3 = this.peer3.getEntryList(dataId);
        assertNotNull(dlpeer3, "Tolower dataid list from peer3");
        assertEquals(1, dlpeer3.size(), "Size of dataid list from peer3");
        assertEquals(dehome.getExpense() + IDirectoryPlace.REMOTE_EXPENSE_OVERHEAD, dlpeer3.getEntry(0).getExpense(), "Peer3 cost one hop");

        final IServiceProviderPlace toUpper =
                addPlace("http://localhost:8006/ToUpperPlace", "emissary.place.sample.ToUpperPlace", this.peer3.getKey());

        assertNotNull(toUpper, "ToUpper created on peer3");
        dehome = toUpper.getDirectoryEntry();
        assertNotNull(dehome, "ToUpper directory entry");

        dataId = KeyManipulator.getDataID(toUpper.getKey());
        logger.debug("ToUpper data id is " + dataId);
        dlpeer1 = this.peer1.getEntryList(dataId);
        logger.debug("From peer1 we have " + dlpeer1);
        assertNotNull(dlpeer1, "ToUpper dataid list from peer1");
        assertEquals(1, dlpeer1.size(), "Size of dataid list from peer1");
        assertEquals(dehome.getExpense() + IDirectoryPlace.REMOTE_EXPENSE_OVERHEAD, dlpeer1.getEntry(0).getExpense(), "Peer1 cost one hop");
        dlpeer2 = this.peer2.getEntryList(dataId);
        logger.debug("From peer2 we have " + dlpeer2);
        assertNotNull(dlpeer2, "Toupper dataid list from peer2");
        assertEquals(1, dlpeer2.size(), "Size of dataid list from peer2");
        assertEquals(dehome.getExpense() + (IDirectoryPlace.REMOTE_EXPENSE_OVERHEAD * 2), dlpeer2.getEntry(0).getExpense(), "Peer2 cost two hops");
        dlpeer3 = this.peer3.getEntryList(dataId);
        logger.debug("From peer3 we have " + dlpeer3);
        assertNotNull(dlpeer3, "ToUpper dataid list from peer3");
        assertEquals(1, dlpeer3.size(), "Size of dataid list from peer3");
        assertEquals(dehome.getExpense() + (IDirectoryPlace.REMOTE_EXPENSE_OVERHEAD * 2), dlpeer3.getEntry(0).getExpense(), "Peer3 cost two hops");

        // Add a proxy to a place
        logger.debug("Adding the FOOBAR proxy to the ToUpper on child1/8006");
        final int obsRegCountBeforeFoobar = this.hcpeer2.placeRegistered;
        final int obsDeregCountBeforeFoobar = this.hcpeer2.placeDeregistered;
        toUpper.addServiceProxy("FOOBAR");
        pause(this.SPEED_FACTOR * 100);
        assertEquals(obsRegCountBeforeFoobar + 1, this.hcpeer2.placeRegistered, "New proxy type propagated");
        assertEquals(obsDeregCountBeforeFoobar, this.hcpeer2.placeDeregistered, "Nothing deregistered");
        DirectoryEntryList foodl = this.peer3.getEntryList("FOOBAR::TRANSFORM");
        assertNotNull(foodl, "Data entry list for new proxy");
        assertEquals(1, foodl.size(), "Entry made in list");

        // Remove the proxy from the place
        logger.debug("Removing the FOOBAR proxy from ToUpper on child1/8006");
        toUpper.removeServiceProxy("FOOBAR");
        pause(this.SPEED_FACTOR * 100);
        assertEquals(obsRegCountBeforeFoobar + 1, this.hcpeer2.placeRegistered, "Nothing new registered");
        assertEquals(obsDeregCountBeforeFoobar + 1, this.hcpeer2.placeDeregistered, "Proxy deregistered notification");
        foodl = this.peer3.getEntryList("FOOBAR::TRANSFORM");
        assertNotNull(foodl, "Data entry list for new proxy");
        assertEquals(0, foodl.size(), "Entry should be removed from list");
        dataId = KeyManipulator.getDataID(toUpper.getKey());
        foodl = this.peer2.getEntryList(dataId);
        assertNotNull(foodl, "Primary proxy for ToUpper should still work");
        assertEquals(1, foodl.size(), "Entry still in list");

        // Build a payload and verify proper keys selected on peer1
        logger.debug("STARTING PAYLOAD RELAY FROM PEER TO CHILD");
        IBaseDataObject payload = new BaseDataObject("test data".getBytes(UTF_8), "test object", "LOWER_CASE");
        payload.appendTransformHistory("UNKNOWN.UNIXFILE.ID.http://localhost:9005/UnixFilePlace");
        payload.appendTransformHistory("LOWER_CASE.TO_UPPER.TRANSFORM.http://localhost:8005/DirectoryPlace");
        this.peer1.process(payload);
        assertEquals("LOWER_CASE.TO_UPPER.TRANSFORM.http://localhost:8006/ToUpperPlace", payload.currentForm(), "Child key selected for payload");

        logger.debug("STARTING PAYLOAD RELAY FROM CHILD TO PEER");
        payload = new BaseDataObject("test data".getBytes(UTF_8), "test object", "UPPER_CASE");
        payload.appendTransformHistory("UNKNOWN.UNIXFILE.ID.http://localhost:8006/UnixFilePlace");
        payload.appendTransformHistory("UPPER_CASE.TO_LOWER.TRANSFORM.http://localhost:8005/DirectoryPlace");
        this.peer1.process(payload);
        assertEquals("UPPER_CASE.TO_LOWER.TRANSFORM.http://localhost:9005/ToLowerPlace", payload.currentForm(), "Peer key selected for payload");

        assertFalse(this.peer2.isRemoteDirectoryAvailable("DIRECTORY.EMISSARY_DIRECTORY_SERVICES.STUDY.http://foo.example.com:12345/DirectoryPlace"),
                "Peer2 should not be in sync with bogus remote");

        pause(this.SPEED_FACTOR * 250);

        // Add a place to a grandchild2
        final IServiceProviderPlace devnull =
                addPlace("http://localhost:9007/DevNullPlace", "emissary.place.sample.DevNullPlace", this.peer2.getKey());
        assertNotNull(devnull, "DevNull created on grandchild2");
        final DirectoryEntry dnde = devnull.getDirectoryEntry();
        assertNotNull(dnde, "DevNull directory entry");

        assertEquals(
                1,
                this.peer2.getMatchingEntries(
                        dnde.getDataType() + ".*." + dnde.getServiceType() + "." + KeyManipulator.getServiceLocation(this.peer2.getKey())).size(),
                "DevNull place advertised on peer2(grandparent)");
        assertEquals(
                1,
                this.peer1.getMatchingEntries(
                        dnde.getDataType() + ".*." + dnde.getServiceType() + "." + KeyManipulator.getServiceLocation(this.peer2.getKey())).size(),
                "DevNull place advertised on peer1(peer of grandparent)");

        // Verify the routing from DevNull to ToUpper
        // Should go from grandchild2 > child2 > peer2 > peer1 > child1
        final DirectoryEntry upperde = toUpper.getDirectoryEntry();
        payload = new BaseDataObject("test data".getBytes(UTF_8), "test oject", upperde.getDataType());
        payload.appendTransformHistory(devnull.getKey());

        // Should be proxy key on peer2
        assertEquals("LOWER_CASE.TO_UPPER.TRANSFORM.http://localhost:9005/DirectoryPlace",
                payload.currentForm(),
                "Grand parent key should have been selected for payload");
        // Simulate Move to peer2
        payload.appendTransformHistory(KeyManipulator.addExpense(payload.currentForm(), upperde.getExpense()
                + (IDirectoryPlace.REMOTE_EXPENSE_OVERHEAD * 3)));

        // Process on peer2
        logger.debug("Simulating a payload relay move on peer2");
        this.peer2.process(payload);

        // Should be proxy key on peer1
        assertEquals("LOWER_CASE.TO_UPPER.TRANSFORM.http://localhost:8005/DirectoryPlace",
                payload.currentForm(),
                "Peer1 proxy key should have been selected for payload");
        // Simulate Move to peer1
        payload.appendTransformHistory(KeyManipulator.addExpense(payload.currentForm(), upperde.getExpense()
                + (IDirectoryPlace.REMOTE_EXPENSE_OVERHEAD * 2)));

        // Process on peer1
        logger.debug("Simulating a payload relay move on peer1");
        this.peer1.process(payload);

        // Should be actual key on child1
        assertEquals(toUpper.getKey(), payload.currentForm(), "Child1 key should have been selected for payload");

        // Simulate Move to child1
        payload.appendTransformHistory(KeyManipulator.addExpense(payload.currentForm(), upperde.getExpense()
                + IDirectoryPlace.REMOTE_EXPENSE_OVERHEAD));

        // ======================== SHUTTING DOWN ==============================

        // Remove the observer
        final boolean obsDeleted = this.peer2.deleteObserver(this.hcpeer2);
        assertTrue(obsDeleted, "Observer was deleted");

        this.allTestsCompleted = true;
    }

    public void verifyObserver(final DOHitCounter h, final String msg) {
        assertEquals(h.exp_placeRegistered, h.placeRegistered, "Obs reg count " + msg);
        assertEquals(h.exp_placeDeregistered, h.placeDeregistered, "Obs dereg count " + msg);
        assertEquals(h.exp_childAdded, h.childAdded, "Obs chadd count " + msg);
        assertEquals(h.exp_childRemoved, h.childRemoved, "Obs chrm count " + msg);
        assertEquals(h.exp_placeCostChanged, h.placeCostChanged, "Obs cce count " + msg);

        assertEquals(h.exp_peerUpdate, h.peerUpdate, "Obs peer count " + msg);
        assertEquals(h.exp_lastPeerSetSize, h.lastPeerSetSize, "Obs peer size " + msg);
    }

    /**
     * Just count how many times each method was called Calling super in each method allows the super class to show as
     * tested and gives logging output if that is configured.
     */
    static class DOHitCounter extends DirectoryAdapter {
        public int placeRegistered = 0;
        public int placeDeregistered = 0;
        public int childAdded = 0;
        public int childRemoved = 0;
        public int placeCostChanged = 0;
        public int peerUpdate = 0;
        public int lastPeerSetSize = 0;

        public int exp_placeRegistered = 0;
        public int exp_placeDeregistered = 0;
        public int exp_childAdded = 0;
        public int exp_childRemoved = 0;
        public int exp_placeCostChanged = 0;
        public int exp_peerUpdate = 0;
        public int exp_lastPeerSetSize = 0;

        public void clear() {
            this.placeRegistered = 0;
            this.placeDeregistered = 0;
            this.childAdded = 0;
            this.childRemoved = 0;
            this.placeCostChanged = 0;
            this.peerUpdate = 0;
            this.lastPeerSetSize = 0;

            this.exp_placeRegistered = 0;
            this.exp_placeDeregistered = 0;
            this.exp_childAdded = 0;
            this.exp_childRemoved = 0;
            this.exp_placeCostChanged = 0;
            this.exp_peerUpdate = 0;
            this.exp_lastPeerSetSize = 0;
        }

        public void expect(final int pr, final int pd, final int ca, final int cr, final int cce, final int pu, final int ps) {
            this.exp_placeRegistered = pr;
            this.exp_placeDeregistered = pd;
            this.exp_childAdded = ca;
            this.exp_childRemoved = cr;
            this.exp_placeCostChanged = cce;
            this.exp_peerUpdate = pu;
            this.exp_lastPeerSetSize = ps;
        }

        @Override
        public void placeRegistered(final String observableKey, final String placeKey) {
            super.placeRegistered(observableKey, placeKey);
            this.placeRegistered++;
        }

        @Override
        public void placeDeregistered(final String observableKey, final String placeKey) {
            super.placeDeregistered(observableKey, placeKey);
            this.placeDeregistered++;
        }

        @Override
        public void placeCostChanged(final String observableKey, final String placeKey) {
            super.placeCostChanged(observableKey, placeKey);
            this.placeCostChanged++;
        }

        @Override
        public void peerUpdate(final String observableKey, final Set<DirectoryEntry> peers) {
            super.peerUpdate(observableKey, peers);
            this.peerUpdate++;
            this.lastPeerSetSize = peers.size();
        }
    }
}
