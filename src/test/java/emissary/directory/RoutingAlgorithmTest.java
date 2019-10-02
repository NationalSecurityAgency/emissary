package emissary.directory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import emissary.core.DataObjectFactory;
import emissary.core.HDMobileAgent;
import emissary.core.IBaseDataObject;
import emissary.place.IServiceProviderPlace;
import emissary.test.core.UnitTest;
import emissary.util.io.ResourceReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RoutingAlgorithmTest extends UnitTest {
    private MyDirectoryPlace dir;
    private IBaseDataObject payload;
    private MyMobileAgent agent;
    private final List<DirectoryEntry> unknowns = createUnknownEntries();
    private final List<DirectoryEntry> transforms = createTransformEntries();
    private final List<DirectoryEntry> analyzers = createAnalyzeEntries();

    String unknownDataId = "UNKNOWN::ID";
    String transformDataId = "XFORM::TRANSFORM";
    String analyzeDataId = "ANALYZE::ANALYZE";

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        this.payload = DataObjectFactory.getInstance();
        this.payload.setFilename("testpayload");
        this.dir = new MyDirectoryPlace("http://example.com:8001/MyDirectoryPlace");
        this.agent = new MyMobileAgent();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        this.dir.clearAllEntries();
        this.dir.shutDown();
        this.agent.killAgent();
    }

    private static List<DirectoryEntry> createUnknownEntries() {
        final List<DirectoryEntry> unknowns = new ArrayList<DirectoryEntry>();
        unknowns.add(new DirectoryEntry("UNKNOWN.s1.ID.http://example.com:8001/U$5050"));
        unknowns.add(new DirectoryEntry("UNKNOWN.s2.ID.http://example.com:8001/U$5060"));
        unknowns.add(new DirectoryEntry("UNKNOWN.s3.ID.http://example.com:8001/U$6050"));
        unknowns.add(new DirectoryEntry("UNKNOWN.s4.ID.http://example.com:8001/U$7050"));
        return unknowns;
    }

    private static List<DirectoryEntry> createTransformEntries() {
        final List<DirectoryEntry> transforms = new ArrayList<DirectoryEntry>();
        transforms.add(new DirectoryEntry("XFORM.s1.TRANSFORM.http://example.com:8001/T$5050"));
        transforms.add(new DirectoryEntry("XFORM.s2.TRANSFORM.http://example.com:8001/T$5060"));
        transforms.add(new DirectoryEntry("XFORM.s3.TRANSFORM.http://example.com:8001/T$6050"));
        transforms.add(new DirectoryEntry("XFORM.s4.TRANSFORM.http://example.com:8001/T$7050"));
        return transforms;
    }

    private static List<DirectoryEntry> createAnalyzeEntries() {
        final List<DirectoryEntry> analyzers = new ArrayList<DirectoryEntry>();
        analyzers.add(new DirectoryEntry("ANALYZE.s1.ANALYZE.http://example.com:8001/A$5050"));
        analyzers.add(new DirectoryEntry("ANALYZE.s2.ANALYZE.http://example.com:8001/A$5060"));
        analyzers.add(new DirectoryEntry("ANALYZE.s3.ANALYZE.http://example.com:8001/A$6050"));
        analyzers.add(new DirectoryEntry("ANALYZE.s4.ANALYZE.http://example.com:8001/A$7050"));
        return analyzers;
    }

    private void loadAllTestEntries() {
        this.dir.addTestEntries(this.unknowns);
        this.dir.addTestEntries(this.transforms);
        this.dir.addTestEntries(this.analyzers);
    }

    @Test
    public void testFindsBuriedCurrentFormAndPullsToTop() {
        this.dir.addTestEntries(this.unknowns);
        this.payload.pushCurrentForm("UNKNOWN");
        this.payload.pushCurrentForm("FOO");
        this.payload.pushCurrentForm("BAR");
        final int oldSize = this.payload.currentFormSize();
        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertEquals("Next keys returns lowest cost", this.unknowns.get(0).getKey(), result.getKey());
        assertEquals("Form stack must have same size", oldSize, this.payload.currentFormSize());
        assertEquals("Payload form used pulled to top", "UNKNOWN", this.payload.currentForm());
    }

    @Test
    public void testIdsInOrderWithNullLastPlace() {
        this.dir.addTestEntries(this.unknowns);
        this.payload.pushCurrentForm("UNKNOWN");
        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertEquals("Next keys returns lowest cost", this.unknowns.get(0).getKey(), result.getKey());
    }

    @Test
    public void testIdsInOrderWithCheaperLastPlace() {
        this.dir.addTestEntries(this.unknowns);
        this.payload.pushCurrentForm("UNKNOWN");
        this.payload.appendTransformHistory(this.unknowns.get(0).getFullKey());
        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertEquals("Next keys should return next in cost/quality order", this.unknowns.get(1).getKey(), result.getKey());
    }

    @Test
    public void testIdsInOrderGettingLastPlaceOnList() {
        this.dir.addTestEntries(this.unknowns);
        this.payload.pushCurrentForm("UNKNOWN");
        this.payload.appendTransformHistory(this.unknowns.get(this.unknowns.size() - 2).getFullKey());
        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertEquals("Next keys returns next cost", this.unknowns.get(this.unknowns.size() - 1).getKey(), result.getKey());
    }

    @Test
    public void testIdsInOrderGettingNoResultAtEndOfList() {
        this.dir.addTestEntries(this.unknowns);
        this.payload.pushCurrentForm("UNKNOWN");
        this.payload.appendTransformHistory(this.unknowns.get(this.unknowns.size() - 1).getFullKey());
        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertNull("Result must be null with all unknown entries loaded not " + result, result);
    }

    @Test
    public void testHighestIdWithAllEntriesLoaded() {
        loadAllTestEntries();
        this.unknowns.get(this.unknowns.size() - 1);
        this.payload.pushCurrentForm("UNKNOWN");
        this.payload.appendTransformHistory(this.unknowns.get(this.unknowns.size() - 1).getFullKey());
        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertNull("Result must be null with all entries loaded not " + result, result);
    }

    @Test
    public void testBackToIdPhaseAfterTransform() {
        final DirectoryEntry lastPlace = new DirectoryEntry("FOO.s2.TRANSFORM.http://example.com:8001/T$7050");
        this.dir.addTestEntry(new DirectoryEntry("FOO.s1.ID.http://example.com:8001/I$7050"));
        this.dir.addTestEntry(lastPlace);
        this.dir.addTestEntry(new DirectoryEntry("FOO.s3.ANALYZE.http://example.com:8001/A$7050"));

        this.payload.pushCurrentForm("FOO");
        this.payload.appendTransformHistory(lastPlace.getFullKey());
        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertEquals("Should go to ID place after transform", "ID", result.getServiceType());
    }

    @Test
    public void testNotBackToIdPhaseAfterAnalyze() {
        final DirectoryEntry lastPlace = new DirectoryEntry("FOO.s3.ANALYZE.http://example.com:8001/T$7050");
        this.dir.addTestEntry(new DirectoryEntry("FOO.s1.ID.http://example.com:8001/I$7050"));
        this.dir.addTestEntry(new DirectoryEntry("FOO.s2.TRANSFORM.http://example.com:8001/A$7050"));
        this.dir.addTestEntry(lastPlace);

        this.payload.pushCurrentForm("FOO");
        this.payload.appendTransformHistory(lastPlace.getFullKey());
        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertNull("Should not return to ID place after analyze", result);
    }

    @Test
    public void testCheckTransformProxyWithTwoFormsDoesNotRepeat() {
        loadAllTestEntries();

        // Same place, two service proxy values
        final DirectoryEntry foo = new DirectoryEntry("FOO.s2.TRANSFORM.http://example.com:8001/T$7050");
        final DirectoryEntry bar = new DirectoryEntry("BAR.s2.TRANSFORM.http://example.com:8001/T$7050");
        this.dir.addTestEntry(foo);
        this.dir.addTestEntry(bar);

        this.payload.pushCurrentForm("FOO");
        this.payload.pushCurrentForm("BAR");
        this.payload.appendTransformHistory(this.unknowns.get(0).getFullKey());
        this.payload.appendTransformHistory(foo.getFullKey());

        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertNull("No place remains without looping error but we got " + result + " on " + this.payload.getAllCurrentForms() + " lastPlace="
                + this.payload.getLastPlaceVisited(), result);
    }

    @Test
    public void testCannotProceedPastMaxItinerarySteps() {
        loadAllTestEntries();
        this.payload.pushCurrentForm("UNKNOWN");
        for (int i = 0; i <= this.agent.getMaxItinerarySteps(); i++) {
            this.payload.appendTransformHistory(this.unknowns.get(0).getFullKey());
        }
        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertNull("Must not proceed past MAX steps but we got " + result, result);
    }

    @Test
    public void testCannotProceedPastMaxItineraryStepsWithSetValue() {
        loadAllTestEntries();
        this.payload.pushCurrentForm("UNKNOWN");
        this.agent.setMaxItinerarySteps(10);
        for (int i = 0; i <= this.agent.getMaxItinerarySteps(); i++) {
            this.payload.appendTransformHistory(this.unknowns.get(0).getFullKey());
        }
        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertNull("Must not proceed past MAX steps but we got " + result, result);
    }

    @Test
    public void testContractWithNextKeysPlace() {
        loadAllTestEntries();
        this.payload.pushCurrentForm("UNKNOWN");
        final DirectoryEntry result = this.agent.getNextKeyAccess(null, this.payload);
        assertNull("Must not return result with null place", result);
    }

    @Test
    public void testContractWithNextKeysPayload() {
        loadAllTestEntries();
        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, null);
        assertNull("Must not return result with null payload", result);
    }

    @Test
    public void testPayloadWithNoCurrentForm() {
        loadAllTestEntries();
        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertNull("Must not return result when payload has no current form", result);
    }

    @Test
    public void testPayloadWithDoneForm() {
        loadAllTestEntries();
        this.dir.addTestEntry(new DirectoryEntry("DONE.d1.IO.http://example.com:8001/D$5050"));
        this.payload.pushCurrentForm(emissary.core.Form.DONE);
        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertNull("Must not return result when payload has DONE form", result);
    }

    @Test
    public void testErrorHandlingPopsCurrentForms() {
        loadAllTestEntries();
        final DirectoryEntry eplace = new DirectoryEntry("ERROR.e1.IO.http://example.com:8001/E$5050");
        this.dir.addTestEntry(eplace);
        this.payload.pushCurrentForm("FOO");
        this.payload.pushCurrentForm("BAR");
        this.payload.pushCurrentForm("BAZ");
        this.payload.pushCurrentForm(emissary.core.Form.ERROR);
        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertEquals("Routing to error handling place should occur", eplace.getKey(), result.getKey());
        assertEquals("Routing to error handling place removes other forms", 1, this.payload.currentFormSize());
    }

    @Test
    public void testErrorHandlingErrorPopsAllCurrentForms() {
        loadAllTestEntries();
        final DirectoryEntry eplace = new DirectoryEntry("ERROR.e1.IO.http://example.com:8001/E$5050");
        this.dir.addTestEntry(eplace);
        this.payload.pushCurrentForm(emissary.core.Form.ERROR);
        this.payload.pushCurrentForm(emissary.core.Form.ERROR);
        this.payload.pushCurrentForm(emissary.core.Form.ERROR);
        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertEquals("Error in error handling place removes all forms", 0, this.payload.currentFormSize());
        assertNull("Error in Error handling must not re-route to error handler but we got " + result, result);
    }

    @Test
    public void testNoRepeatWhenTransformAddsFormButDoesNotRemoveOwnProxyAndIsntEvenOnTop() {
        loadAllTestEntries();
        final DirectoryEntry foo = new DirectoryEntry("FOO.s2.TRANSFORM.http://example.com:8001/T$7050");
        final DirectoryEntry bar = new DirectoryEntry("BAR.s2.ANALYZE.http://example.com:8001/A$1050");
        this.dir.addTestEntry(foo);
        this.dir.addTestEntry(bar);

        this.payload.pushCurrentForm("BAR");
        this.payload.pushCurrentForm("FOO");
        this.payload.appendTransformHistory(this.unknowns.get(0).getFullKey());
        this.payload.appendTransformHistory(foo.getFullKey());

        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertNotNull("Must move along to analyze with current form of xform still on stack", result);
        assertEquals("Must move along to analyze even with current form of xform place still on stack", bar.getKey(), result.getKey());
    }

    @Test
    public void testVisitedPlaceNoRepeatListForAnalyzeStage() {
        loadAllTestEntries();
        final DirectoryEntry foo = new DirectoryEntry("FOO.s2.ANALYZE.http://example.com:8001/A$7050");
        final DirectoryEntry bar = new DirectoryEntry("BAR.s2.ANALYZE.http://example.com:8001/A$1050");
        this.dir.addTestEntry(foo);
        this.dir.addTestEntry(bar);

        this.payload.pushCurrentForm("BAR");
        this.payload.pushCurrentForm("FOO");
        this.payload.appendTransformHistory(this.unknowns.get(0).getFullKey());
        this.payload.appendTransformHistory(foo.getFullKey());

        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertNull("Must not repeat analyze place even with two forms but we got " + result, result);
    }

    @Test
    public void testNoRepeatWhenTransformAddsFormButDoesNotRemoveOwnProxy() {
        loadAllTestEntries();
        final DirectoryEntry foo = new DirectoryEntry("FOO.s2.TRANSFORM.http://example.com:8001/T$7050");
        final DirectoryEntry bar = new DirectoryEntry("BAR.s2.ANALYZE.http://example.com:8001/A$1050");
        this.dir.addTestEntry(foo);
        this.dir.addTestEntry(bar);

        this.payload.pushCurrentForm("FOO");
        this.payload.pushCurrentForm("BAR");
        this.payload.appendTransformHistory(this.unknowns.get(0).getFullKey());
        this.payload.appendTransformHistory(foo.getFullKey());

        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertNotNull("Must move along to analyze with current form of xform still on stack", result);
        assertEquals("Must move along to analyze even with current form of xform place still on stack", bar.getKey(), result.getKey());
    }

    @Test
    public void testNextKeyFromQueue() {
        loadAllTestEntries();
        final DirectoryEntry foo = new DirectoryEntry("FOO.s2.TRANSFORM.http://example.com:8001/T$7050");
        this.agent.addEntryToQueue(foo);
        final int sz = this.agent.queueSize();
        this.payload.pushCurrentForm("BAR");
        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertEquals("Next key must be from queue when queue non-empty", foo.getKey(), result.getKey());
        assertEquals("Queue should drain by one", sz - 1, this.agent.queueSize());
    }

    @Test
    public void testCompleteKeyRouting() {
        loadAllTestEntries();
        final DirectoryEntry foo1 = new DirectoryEntry("FOO.s2.TRANSFORM.http://example.com:8001/T$7050");
        final DirectoryEntry foo2 = new DirectoryEntry("FOO.s3.TRANSFORM.http://example.com:9999/T$7050");
        this.dir.addTestEntry(foo1);
        this.payload.pushCurrentForm(foo2.getKey());
        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertEquals("Routing must take place to fully qualified key", foo2.getKey(), result.getKey());
    }

    /**
     * Extend directory place to allow us to access the entryMap
     */
    class MyDirectoryPlace extends DirectoryPlace {
        public MyDirectoryPlace(final String placeLoc) throws IOException {
            super(new ResourceReader().getConfigDataAsStream(thisPackage + ".MyDirectoryPlace.cfg"), placeLoc, new EmissaryNode());
        }

        public void clearAllEntries() {
            entryMap.clear();
        }

        public void addTestEntry(final DirectoryEntry newEntry) {
            addEntry(newEntry);
        }

        public void addTestEntries(final List<DirectoryEntry> newEntryList) {
            addEntries(newEntryList);
        }
    }

    private static final class MyMobileAgent extends HDMobileAgent {
        /**
         * provide uid for serialization
         */
        private static final long serialVersionUID = 6667669555504467253L;

        public DirectoryEntry getNextKeyAccess(final IServiceProviderPlace place, final IBaseDataObject payload) {
            return getNextKey(place, payload);
        }

        public void addEntryToQueue(final DirectoryEntry entry) {
            nextKeyQueue.add(entry);
        }

        public int queueSize() {
            return nextKeyQueue.size();
        }
    }
}
