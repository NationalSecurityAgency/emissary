package emissary.directory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import emissary.core.BaseDataObject;
import emissary.core.IBaseDataObject;
import emissary.place.IServiceProviderPlace;
import emissary.test.core.FunctionalTest;
import org.junit.After;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FTestPeerDirectoryPlace extends FunctionalTest {

    private static final Logger logger = LoggerFactory.getLogger(FTestPeerDirectoryPlace.class);

    private IDirectoryPlace peer1 = null;
    private IDirectoryPlace peer2 = null;
    private IDirectoryPlace peer3 = null;

    private DOHitCounter hcpeer1 = new DOHitCounter();
    private DOHitCounter hcpeer2 = new DOHitCounter();
    private DOHitCounter hcpeer3 = new DOHitCounter();

    private long SPEED_FACTOR = 1L; // slow down all the pauses if needed

    /*
     * peer1(8005) -- peer2(9005) -- peer3(7005) | + ToLower | | | / \ | | / \ child1(8006) child2(9006) child3 child4 +
     * ToUpper | (7006) (7007) | + TestPlace2 grandchild2(9007) + DevNullPlace
     */

    private boolean allTestsCompleted = false;

    @Override
    @Before
    public void setUp() throws Exception {
        setConfig(System.getProperty("java.io.tmpdir", "."), true);

        startJetty(8005, "jettypeer");

        this.peer1 = directory; // from super class
        this.peer1.addObserver(this.hcpeer1);
        logger.debug("Setup phase finished");
    }

    @Override
    @After
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

    @org.junit.Test
    public void testDirectories() throws Exception {
        // Check things about peer1 on 8005, a statically configured peer
        logger.debug("STARTING PEER1 on 8005");
        assertNotNull("Peer1 directory found", this.peer1);
        assertTrue("Peer1 key has port", this.peer1.getKey().indexOf("8005") > -1);
        Set<String> peers = this.peer1.getPeerDirectories();
        assertNotNull("Peer1 has peers", peers);
        assertTrue("Peer1 has peers", peers.size() > 0);
        boolean foundother = false;
        for (final String other : peers) {
            if (other.indexOf("localhost:9005") > -1) {
                foundother = true;
                break;
            }
        }
        assertTrue("Peer1 knows about peer2", foundother);

        // Start peer2 on 9005, a second statically configured peer
        logger.debug("STARTING PEER2 on 9005");
        this.peer2 = startDirectory(9005);
        this.peer2.addObserver(this.hcpeer2);
        this.peer2.heartbeatRemoteDirectory(this.peer1.getKey());
        this.peer1.heartbeatRemoteDirectory(this.peer2.getKey());
        pause(this.SPEED_FACTOR * 250);
        assertNotNull("Peer2 directory found", this.peer2);
        assertNotSame("Peer1 and peer2 are distinct", this.peer1, this.peer2);
        assertTrue("Peer2 key has port", this.peer2.getKey().indexOf("9005") > -1);

        // See the the directories have peered with each other
        logger.debug("Checking peering between peer1 and peer2");
        peers = this.peer2.getPeerDirectories();
        foundother = false;
        assertNotNull("Peer2 has peers", peers);
        assertTrue("Peer2 has peers", peers.size() > 0);
        for (final String other : peers) {
            if (other.indexOf("localhost:8005") > -1) {
                foundother = true;
                break;
            }
        }
        assertTrue("Peer2 knows about peer2", foundother);

        // See the the directories have correct costs for each other
        String dataId = KeyManipulator.getDataID(this.peer1.getKey());
        DirectoryEntryList dl1 = this.peer1.getEntryList(dataId);
        final DirectoryEntryList dl2 = this.peer2.getEntryList(dataId);
        assertNotNull("Directory entries in peer1", dl1);
        assertNotNull("Directory entries in peer2", dl2);
        assertEquals("Have entries for both peers", 2, dl1.size());
        assertEquals("Have entries for both peers", 2, dl2.size());

        DirectoryEntry dl1d0 = dl1.getEntry(0);
        DirectoryEntry dl1d1 = dl1.getEntry(1);
        assertEquals("Cost of local in peer1", 50, dl1d0.getCost());
        assertEquals("Cost of remote in peer1", 50 + IDirectoryPlace.REMOTE_COST_OVERHEAD, dl1d1.getCost());

        final DirectoryEntry dl2d0 = dl2.getEntry(0);
        final DirectoryEntry dl2d1 = dl2.getEntry(1);
        assertEquals("Cost of local in peer2", 50, dl2d0.getCost());
        assertEquals("Cost of remote in peer2", 50 + IDirectoryPlace.REMOTE_COST_OVERHEAD, dl2d1.getCost());

        // Test path weight
        final int pathWeight = dl1d1.getPathWeight();
        assertTrue("Path weight is filled in in peer entry", pathWeight > 0);

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
        assertTrue("Path weight should decrease on failure, had " + pathWeight + " and now " + newPathWeight, newPathWeight < pathWeight);

        // Start peer3 on 7005 a non-statically configured peer
        logger.debug("STARTING PEER3 on 7005");
        this.peer3 = startDirectory(7005);
        this.peer3.addObserver(this.hcpeer3);
        this.peer3.heartbeatRemoteDirectory(this.peer2.getKey());
        this.peer3.heartbeatRemoteDirectory(this.peer1.getKey());
        this.peer1.heartbeatRemoteDirectory(this.peer3.getKey());
        this.peer2.heartbeatRemoteDirectory(this.peer3.getKey());
        pause(this.SPEED_FACTOR * 250);
        assertNotNull("Peer3 directory found", this.peer3);
        assertNotSame("Peer1 and peer3 are distinct", this.peer1, this.peer3);
        assertNotSame("Peer2 and peer3 are distinct", this.peer2, this.peer3);
        assertTrue("Peer3 key has port", this.peer3.getKey().indexOf("7005") > -1);
        assertEquals("Peer3 has 2 peers", 2, this.peer3.getPeerDirectories().size());
        assertEquals("Peer2 has 2 peers", 2, this.peer2.getPeerDirectories().size());
        assertEquals("Peer1 has 2 peers", 2, this.peer1.getPeerDirectories().size());
        assertEquals("Peer observer called", 1, this.hcpeer2.peerUpdate);
        assertEquals("Peer observer set size", 2, this.hcpeer2.lastPeerSetSize);

        assertEquals("Peer observer no change", 1, this.hcpeer2.peerUpdate);
        assertEquals("Peer observer set size no change", 2, this.hcpeer2.lastPeerSetSize);

        // Add a place to a peer, check costs
        logger.debug("Starring ToLower place on peer2/9005");
        final IServiceProviderPlace toLower =
                addPlace("http://localhost:9005/ToLowerPlace", "emissary.place.sample.ToLowerPlace", this.peer2.getKey());

        assertNotNull("ToLower created on peer2", toLower);
        DirectoryEntry dehome = toLower.getDirectoryEntry();
        assertNotNull("ToLower directory entry", dehome);

        dataId = KeyManipulator.getDataID(toLower.getKey());
        logger.debug("ToLower data id is " + dataId);
        DirectoryEntryList dlpeer1 = this.peer1.getEntryList(dataId);
        assertNotNull("Tolower dataid list from peer1", dlpeer1);
        assertEquals("Size of dataid list from peer1", 1, dlpeer1.size());
        assertEquals("Peer1 cost one hop", dehome.getExpense() + IDirectoryPlace.REMOTE_EXPENSE_OVERHEAD, dlpeer1.getEntry(0).getExpense());
        DirectoryEntryList dlpeer2 = this.peer2.getEntryList(dataId);
        assertNotNull("Tolower dataid list from peer2", dlpeer2);
        assertEquals("Size of dataid list from peer2", 1, dlpeer2.size());
        assertEquals("Home cost no change in peer2", dehome.getExpense(), dlpeer2.getEntry(0).getExpense());
        DirectoryEntryList dlpeer3 = this.peer3.getEntryList(dataId);
        assertNotNull("Tolower dataid list from peer3", dlpeer3);
        assertEquals("Size of dataid list from peer3", 1, dlpeer3.size());
        assertEquals("Peer3 cost one hop", dehome.getExpense() + IDirectoryPlace.REMOTE_EXPENSE_OVERHEAD, dlpeer3.getEntry(0).getExpense());

        final IServiceProviderPlace toUpper =
                addPlace("http://localhost:8006/ToUpperPlace", "emissary.place.sample.ToUpperPlace", this.peer3.getKey());

        assertNotNull("ToUpper created on peer3", toUpper);
        dehome = toUpper.getDirectoryEntry();
        assertNotNull("ToUpper directory entry", dehome);

        dataId = KeyManipulator.getDataID(toUpper.getKey());
        logger.debug("ToUpper data id is " + dataId);
        dlpeer1 = this.peer1.getEntryList(dataId);
        logger.debug("From peer1 we have " + dlpeer1);
        assertNotNull("ToUpper dataid list from peer1", dlpeer1);
        assertEquals("Size of dataid list from peer1", 1, dlpeer1.size());
        assertEquals("Peer1 cost one hop", dehome.getExpense() + IDirectoryPlace.REMOTE_EXPENSE_OVERHEAD, dlpeer1.getEntry(0).getExpense());
        dlpeer2 = this.peer2.getEntryList(dataId);
        logger.debug("From peer2 we have " + dlpeer2);
        assertNotNull("Toupper dataid list from peer2", dlpeer2);
        assertEquals("Size of dataid list from peer2", 1, dlpeer2.size());
        assertEquals("Peer2 cost two hops", dehome.getExpense() + (IDirectoryPlace.REMOTE_EXPENSE_OVERHEAD * 2), dlpeer2.getEntry(0).getExpense());
        dlpeer3 = this.peer3.getEntryList(dataId);
        logger.debug("From peer3 we have " + dlpeer3);
        assertNotNull("ToUpper dataid list from peer3", dlpeer3);
        assertEquals("Size of dataid list from peer3", 1, dlpeer3.size());
        assertEquals("Peer3 cost two hops", dehome.getExpense() + (IDirectoryPlace.REMOTE_EXPENSE_OVERHEAD * 2), dlpeer3.getEntry(0).getExpense());

        // Add a proxy to a place
        logger.debug("Adding the FOOBAR proxy to the ToUpper on child1/8006");
        final int obsRegCountBeforeFoobar = this.hcpeer2.placeRegistered;
        final int obsDeregCountBeforeFoobar = this.hcpeer2.placeDeregistered;
        toUpper.addServiceProxy("FOOBAR");
        pause(this.SPEED_FACTOR * 100);
        assertEquals("New proxy type propagated", obsRegCountBeforeFoobar + 1, this.hcpeer2.placeRegistered);
        assertEquals("Nothing deregistered", obsDeregCountBeforeFoobar, this.hcpeer2.placeDeregistered);
        DirectoryEntryList foodl = this.peer3.getEntryList("FOOBAR::TRANSFORM");
        assertNotNull("Data entry list for new proxy", foodl);
        assertEquals("Entry made in list", 1, foodl.size());

        // Remove the proxy from the place
        logger.debug("Removing the FOOBAR proxy from ToUpper on child1/8006");
        toUpper.removeServiceProxy("FOOBAR");
        pause(this.SPEED_FACTOR * 100);
        assertEquals("Nothing new registered", obsRegCountBeforeFoobar + 1, this.hcpeer2.placeRegistered);
        assertEquals("Proxy deregistered notification", obsDeregCountBeforeFoobar + 1, this.hcpeer2.placeDeregistered);
        foodl = this.peer3.getEntryList("FOOBAR::TRANSFORM");
        assertNotNull("Data entry list for new proxy", foodl);
        assertEquals("Entry should be removed from list", 0, foodl.size());
        dataId = KeyManipulator.getDataID(toUpper.getKey());
        foodl = this.peer2.getEntryList(dataId);
        assertNotNull("Primary proxy for ToUpper should still work", foodl);
        assertEquals("Entry still in list", 1, foodl.size());

        // Build a payload and verify proper keys selected on peer1
        logger.debug("STARTING PAYLOAD RELAY FROM PEER TO CHILD");
        IBaseDataObject payload = new BaseDataObject("test data".getBytes(), "test oject", "LOWER_CASE");
        payload.appendTransformHistory("UNKNOWN.UNIXFILE.ID.http://localhost:9005/UnixFilePlace");
        payload.appendTransformHistory("LOWER_CASE.TO_UPPER.TRANSFORM.http://localhost:8005/DirectoryPlace");
        this.peer1.process(payload);
        assertEquals("Child key selected for payload", "LOWER_CASE.TO_UPPER.TRANSFORM.http://localhost:8006/ToUpperPlace", payload.currentForm());

        logger.debug("STARTING PAYLOAD RELAY FROM CHILD TO PEER");
        payload = new BaseDataObject("test data".getBytes(), "test object", "UPPER_CASE");
        payload.appendTransformHistory("UNKNOWN.UNIXFILE.ID.http://localhost:8006/UnixFilePlace");
        payload.appendTransformHistory("UPPER_CASE.TO_LOWER.TRANSFORM.http://localhost:8005/DirectoryPlace");
        this.peer1.process(payload);
        assertEquals("Peer key selected for payload", "UPPER_CASE.TO_LOWER.TRANSFORM.http://localhost:9005/ToLowerPlace", payload.currentForm());

        assertFalse("Peer2 should not be in sync with bogus remote",
                this.peer2.isRemoteDirectoryAvailable("DIRECTORY.EMISSARY_DIRECTORY_SERVICES.STUDY.http://foo.example.com:12345/DirectoryPlace"));

        pause(this.SPEED_FACTOR * 250);

        // Add a place to a grandchild2
        final IServiceProviderPlace devnull =
                addPlace("http://localhost:9007/DevNullPlace", "emissary.place.sample.DevNullPlace", this.peer2.getKey());
        assertNotNull("DevNull created on grandchild2", devnull);
        final DirectoryEntry dnde = devnull.getDirectoryEntry();
        assertNotNull("DevNull directory entry", dnde);

        assertEquals(
                "DevNull place advertised on peer2(grandparent)",
                1,
                this.peer2.getMatchingEntries(
                        dnde.getDataType() + ".*." + dnde.getServiceType() + "." + KeyManipulator.getServiceLocation(this.peer2.getKey())).size());
        assertEquals(
                "DevNull place advertised on peer1(peer of grandparent)",
                1,
                this.peer1.getMatchingEntries(
                        dnde.getDataType() + ".*." + dnde.getServiceType() + "." + KeyManipulator.getServiceLocation(this.peer2.getKey())).size());

        // Verify the routing from DevNull to ToUpper
        // Should go from grandchild2 > child2 > peer2 > peer1 > child1
        final DirectoryEntry upperde = toUpper.getDirectoryEntry();
        payload = new BaseDataObject("test data".getBytes(), "test oject", upperde.getDataType());
        payload.appendTransformHistory(devnull.getKey());

        // Should be proxy key on peer2
        assertEquals("Grand parent key should have been selected for payload", "LOWER_CASE.TO_UPPER.TRANSFORM.http://localhost:9005/DirectoryPlace",
                payload.currentForm());
        // Simulate Move to peer2
        payload.appendTransformHistory(KeyManipulator.addExpense(payload.currentForm(), upperde.getExpense()
                + (IDirectoryPlace.REMOTE_EXPENSE_OVERHEAD * 3)));

        // Process on peer2
        logger.debug("Simulating a payload relay move on peer2");
        this.peer2.process(payload);

        // Should be proxy key on peer1
        assertEquals("Peer1 proxy key should have been selected for payload", "LOWER_CASE.TO_UPPER.TRANSFORM.http://localhost:8005/DirectoryPlace",
                payload.currentForm());
        // Simulate Move to peer1
        payload.appendTransformHistory(KeyManipulator.addExpense(payload.currentForm(), upperde.getExpense()
                + (IDirectoryPlace.REMOTE_EXPENSE_OVERHEAD * 2)));

        // Process on peer1
        logger.debug("Simulating a payload relay move on peer1");
        this.peer1.process(payload);

        // Should be actual key on child1
        assertEquals("Child1 key should have been selected for payload", toUpper.getKey(), payload.currentForm());

        // Simulate Move to child1
        payload.appendTransformHistory(KeyManipulator.addExpense(payload.currentForm(), upperde.getExpense()
                + (IDirectoryPlace.REMOTE_EXPENSE_OVERHEAD * 1)));

        // ======================== SHUTTING DOWN ==============================

        // Remove the observer
        final boolean obsDeleted = this.peer2.deleteObserver(this.hcpeer2);
        assertTrue("Observer was deleted", obsDeleted);

        this.allTestsCompleted = true;
    }

    public void verifyObserver(final DOHitCounter h, final String msg) {
        assertEquals("Obs reg count " + msg, h.exp_placeRegistered, h.placeRegistered);
        assertEquals("Obs dereg count " + msg, h.exp_placeDeregistered, h.placeDeregistered);
        assertEquals("Obs chadd count " + msg, h.exp_childAdded, h.childAdded);
        assertEquals("Obs chrm count " + msg, h.exp_childRemoved, h.childRemoved);
        assertEquals("Obs cce count " + msg, h.exp_placeCostChanged, h.placeCostChanged);

        assertEquals("Obs peer count " + msg, h.exp_peerUpdate, h.peerUpdate);
        assertEquals("Obs peer size " + msg, h.exp_lastPeerSetSize, h.lastPeerSetSize);
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

        @Override
        public void childAdded(final String observableKey, final DirectoryEntry child) {
            super.childAdded(observableKey, child);
            this.childAdded++;
        }

        @Override
        public void childRemoved(final String observableKey, final DirectoryEntry child) {
            super.childRemoved(observableKey, child);
            this.childRemoved++;
        }
    }
}
