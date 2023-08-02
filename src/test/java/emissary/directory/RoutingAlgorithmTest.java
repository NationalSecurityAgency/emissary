package emissary.directory;

import emissary.core.DataObjectFactory;
import emissary.core.Form;
import emissary.core.HDMobileAgent;
import emissary.core.IBaseDataObject;
import emissary.place.IServiceProviderPlace;
import emissary.place.sample.DelayPlace;
import emissary.test.core.junit5.UnitTest;
import emissary.util.io.ResourceReader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RoutingAlgorithmTest extends UnitTest {
    private MyDirectoryPlace dir;
    private IBaseDataObject payload;
    private MyMobileAgent agent;
    private final List<DirectoryEntry> unknowns = createUnknownEntries();
    private final List<DirectoryEntry> transforms = createTransformEntries();
    private final List<DirectoryEntry> analyzers = createAnalyzeEntries();

    // String unknownDataId = "UNKNOWN::ID";
    // String transformDataId = "XFORM::TRANSFORM";
    // String analyzeDataId = "ANALYZE::ANALYZE";

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        this.payload = DataObjectFactory.getInstance();
        this.payload.setFilename("testpayload");
        this.dir = new MyDirectoryPlace("http://example.com:8001/MyDirectoryPlace");
        this.agent = new MyMobileAgent();
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        this.dir.clearAllEntries();
        this.dir.shutDown();
        this.agent.killAgent();
    }

    private static List<DirectoryEntry> createUnknownEntries() {
        final List<DirectoryEntry> unknowns = new ArrayList<>();
        unknowns.add(new DirectoryEntry("UNKNOWN.s1.ID.http://example.com:8001/U$5050"));
        unknowns.add(new DirectoryEntry("UNKNOWN.s2.ID.http://example.com:8001/U$5060"));
        unknowns.add(new DirectoryEntry("UNKNOWN.s3.ID.http://example.com:8001/U$6050"));
        unknowns.add(new DirectoryEntry("UNKNOWN.s4.ID.http://example.com:8001/U$7050"));
        return unknowns;
    }

    private static List<DirectoryEntry> createTransformEntries() {
        final List<DirectoryEntry> transforms = new ArrayList<>();
        transforms.add(new DirectoryEntry("XFORM.s1.TRANSFORM.http://example.com:8001/T$5050"));
        transforms.add(new DirectoryEntry("XFORM.s2.TRANSFORM.http://example.com:8001/T$5060"));
        transforms.add(new DirectoryEntry("XFORM.s3.TRANSFORM.http://example.com:8001/T$6050"));
        transforms.add(new DirectoryEntry("XFORM.s4.TRANSFORM.http://example.com:8001/T$7050"));
        return transforms;
    }

    private static List<DirectoryEntry> createAnalyzeEntries() {
        final List<DirectoryEntry> analyzers = new ArrayList<>();
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
    void testFindsBuriedCurrentFormAndPullsToTop() {
        this.dir.addTestEntries(this.unknowns);
        this.payload.pushCurrentForm("UNKNOWN");
        this.payload.pushCurrentForm("FOO");
        this.payload.pushCurrentForm("BAR");
        final int oldSize = this.payload.currentFormSize();
        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertEquals(this.unknowns.get(0).getKey(), result.getKey(), "Next keys returns lowest cost");
        assertEquals(oldSize, this.payload.currentFormSize(), "Form stack must have same size");
        assertEquals("UNKNOWN", this.payload.currentForm(), "Payload form used pulled to top");
    }

    @Test
    void testIdsInOrderWithNullLastPlace() {
        this.dir.addTestEntries(this.unknowns);
        this.payload.pushCurrentForm("UNKNOWN");
        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertEquals(this.unknowns.get(0).getKey(), result.getKey(), "Next keys returns lowest cost");
    }

    @Test
    void testIdsInOrderWithCheaperLastPlace() {
        this.dir.addTestEntries(this.unknowns);
        this.payload.pushCurrentForm("UNKNOWN");
        this.payload.appendTransformHistory(this.unknowns.get(0).getFullKey());
        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertEquals(this.unknowns.get(1).getKey(), result.getKey(), "Next keys should return next in cost/quality order");
    }

    @Test
    void testIdsInOrderGettingLastPlaceOnList() {
        this.dir.addTestEntries(this.unknowns);
        this.payload.pushCurrentForm("UNKNOWN");
        this.payload.appendTransformHistory(this.unknowns.get(this.unknowns.size() - 2).getFullKey());
        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertEquals(this.unknowns.get(this.unknowns.size() - 1).getKey(), result.getKey(), "Next keys returns next cost");
    }

    @Test
    void testIdsInOrderGettingNoResultAtEndOfList() {
        this.dir.addTestEntries(this.unknowns);
        this.payload.pushCurrentForm("UNKNOWN");
        this.payload.appendTransformHistory(this.unknowns.get(this.unknowns.size() - 1).getFullKey());
        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertNull(result, "Result must be null with all unknown entries loaded not " + result);
    }

    @Test
    void testHighestIdWithAllEntriesLoaded() {
        loadAllTestEntries();
        this.unknowns.get(this.unknowns.size() - 1);
        this.payload.pushCurrentForm("UNKNOWN");
        this.payload.appendTransformHistory(this.unknowns.get(this.unknowns.size() - 1).getFullKey());
        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertNull(result, "Result must be null with all entries loaded not " + result);
    }

    @Test
    void testBackToIdPhaseAfterTransform() {
        final DirectoryEntry lastPlace = new DirectoryEntry("FOO.s2.TRANSFORM.http://example.com:8001/T$7050");
        this.dir.addTestEntry(new DirectoryEntry("FOO.s1.ID.http://example.com:8001/I$7050"));
        this.dir.addTestEntry(lastPlace);
        this.dir.addTestEntry(new DirectoryEntry("FOO.s3.ANALYZE.http://example.com:8001/A$7050"));

        this.payload.pushCurrentForm("FOO");
        this.payload.appendTransformHistory(lastPlace.getFullKey());
        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertEquals("ID", result.getServiceType(), "Should go to ID place after transform");
    }

    @Test
    void testNotBackToIdPhaseAfterAnalyze() {
        final DirectoryEntry lastPlace = new DirectoryEntry("FOO.s3.ANALYZE.http://example.com:8001/T$7050");
        this.dir.addTestEntry(new DirectoryEntry("FOO.s1.ID.http://example.com:8001/I$7050"));
        this.dir.addTestEntry(new DirectoryEntry("FOO.s2.TRANSFORM.http://example.com:8001/A$7050"));
        this.dir.addTestEntry(lastPlace);

        this.payload.pushCurrentForm("FOO");
        this.payload.appendTransformHistory(lastPlace.getFullKey());
        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertNull(result, "Should not return to ID place after analyze");
    }

    @Test
    void testBackToIdPhaseAfterCoordinate() {
        final DirectoryEntry lastPlace = new DirectoryEntry("UNKNOWN.s1.ID.http://example.com:8001/I$1010");
        this.dir.addTestEntry(lastPlace);
        this.dir.addTestEntry(new DirectoryEntry("UNKNOWN.s3.ID.http://example.com:8001/A$3030"));
        this.dir.addTestEntry(new DirectoryEntry("UNKNOWN.s4.ANALYZE.http://example.com:8001/A$4040"));

        // after appending a key w/ coordinated=true to the transform history, we should be in the ID phase
        this.payload.pushCurrentForm("UNKNOWN");
        this.payload.appendTransformHistory("UNKNOWN.s1.ID.http://example.com:8001/I$1010");
        this.payload.appendTransformHistory("UNKNOWN.s2.ANALYZE.http://example.com:8001/T$2020", true);
        assertEquals("ID", this.agent.getNextKeyAccess(this.dir, this.payload).getServiceType(), "Should go to ID place after coordinate");

        // after appending a key w/ coordinated=false to the transform history, we should be in the ANALYZE phase
        this.payload.clearTransformHistory();
        this.payload.appendTransformHistory("UNKNOWN.s1.ID.http://example.com:8001/I$1010");
        this.payload.appendTransformHistory("UNKNOWN.s2.ANALYZE.http://example.com:8001/T$2020", false);
        assertEquals("ANALYZE", this.agent.getNextKeyAccess(this.dir, this.payload).getServiceType(), "Should not return to ID place after analyze");
    }

    @Test
    void testCheckTransformProxyWithTwoFormsDoesNotRepeat() {
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
        assertNull(result, "No place remains without looping error but we got " + result + " on " + this.payload.getAllCurrentForms() + " lastPlace="
                + this.payload.getLastPlaceVisited());
    }

    @Test
    void testCannotProceedPastMaxItinerarySteps() {
        loadAllTestEntries();
        this.payload.pushCurrentForm("UNKNOWN");
        for (int i = 0; i <= this.agent.getMaxItinerarySteps(); i++) {
            this.payload.appendTransformHistory(this.unknowns.get(0).getFullKey());
        }
        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertNull(result, "Must not proceed past MAX steps but we got " + result);
    }

    @Test
    void testCannotProceedPastMaxItineraryStepsWithSetValue() {
        loadAllTestEntries();
        this.payload.pushCurrentForm("UNKNOWN");
        this.agent.setMaxItinerarySteps(10);
        for (int i = 0; i <= this.agent.getMaxItinerarySteps(); i++) {
            this.payload.appendTransformHistory(this.unknowns.get(0).getFullKey());
        }
        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertNull(result, "Must not proceed past MAX steps but we got " + result);
    }

    @Test
    void testContractWithNextKeysPlace() {
        loadAllTestEntries();
        this.payload.pushCurrentForm("UNKNOWN");
        final DirectoryEntry result = this.agent.getNextKeyAccess(null, this.payload);
        assertNull(result, "Must not return result with null place");
    }

    @Test
    void testContractWithNextKeysPayload() {
        loadAllTestEntries();
        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, null);
        assertNull(result, "Must not return result with null payload");
    }

    @Test
    void testPayloadWithNoCurrentForm() {
        loadAllTestEntries();
        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertNull(result, "Must not return result when payload has no current form");
    }

    @Test
    void testPayloadWithDoneForm() {
        loadAllTestEntries();
        this.dir.addTestEntry(new DirectoryEntry("DONE.d1.IO.http://example.com:8001/D$5050"));
        this.payload.pushCurrentForm(Form.DONE);
        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertNull(result, "Must not return result when payload has DONE form");
    }

    @Test
    void testErrorHandlingPopsCurrentForms() {
        loadAllTestEntries();
        final DirectoryEntry eplace = new DirectoryEntry("ERROR.e1.IO.http://example.com:8001/E$5050");
        this.dir.addTestEntry(eplace);
        this.payload.pushCurrentForm("FOO");
        this.payload.pushCurrentForm("BAR");
        this.payload.pushCurrentForm("BAZ");
        this.payload.pushCurrentForm(Form.ERROR);
        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertEquals(eplace.getKey(), result.getKey(), "Routing to error handling place should occur");
        assertEquals(1, this.payload.currentFormSize(), "Routing to error handling place removes other forms");
    }

    @Test
    void testErrorHandlingErrorPopsAllCurrentForms() {
        loadAllTestEntries();
        final DirectoryEntry eplace = new DirectoryEntry("ERROR.e1.IO.http://example.com:8001/E$5050");
        this.dir.addTestEntry(eplace);
        this.payload.pushCurrentForm(Form.ERROR);
        this.payload.pushCurrentForm(Form.ERROR);
        this.payload.pushCurrentForm(Form.ERROR);
        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertEquals(0, this.payload.currentFormSize(), "Error in error handling place removes all forms");
        assertNull(result, "Error in Error handling must not re-route to error handler but we got " + result);
    }

    @Test
    void testNoRepeatWhenTransformAddsFormButDoesNotRemoveOwnProxyAndIsntEvenOnTop() {
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
        assertNotNull(result, "Must move along to analyze with current form of xform still on stack");
        assertEquals(bar.getKey(), result.getKey(), "Must move along to analyze even with current form of xform place still on stack");
    }

    @Test
    void testVisitedPlaceNoRepeatListForAnalyzeStage() {
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
        assertNull(result, "Must not repeat analyze place even with two forms but we got " + result);
    }

    @Test
    void testNoRepeatWhenTransformAddsFormButDoesNotRemoveOwnProxy() {
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
        assertNotNull(result, "Must move along to analyze with current form of xform still on stack");
        assertEquals(bar.getKey(), result.getKey(), "Must move along to analyze even with current form of xform place still on stack");
    }

    @Test
    void testNextKeyFromQueue() {
        loadAllTestEntries();
        final DirectoryEntry foo = new DirectoryEntry("FOO.s2.TRANSFORM.http://example.com:8001/T$7050");
        this.agent.addEntryToQueue(foo);
        final int sz = this.agent.queueSize();
        this.payload.pushCurrentForm("BAR");
        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertEquals(foo.getKey(), result.getKey(), "Next key must be from queue when queue non-empty");
        assertEquals(sz - 1, this.agent.queueSize(), "Queue should drain by one");
    }

    @Test
    void testCompleteKeyRouting() {
        loadAllTestEntries();
        final DirectoryEntry foo1 = new DirectoryEntry("FOO.s2.TRANSFORM.http://example.com:8001/T$7050");
        final DirectoryEntry foo2 = new DirectoryEntry("FOO.s3.TRANSFORM.http://example.com:9999/T$7050");
        this.dir.addTestEntry(foo1);
        this.payload.pushCurrentForm(foo2.getKey());
        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertEquals(foo2.getKey(), result.getKey(), "Routing must take place to fully qualified key");
    }

    @Test
    void testWildCardProxyHonorsDenyList() throws IOException {
        loadAllTestEntries();

        this.payload.pushCurrentForm("MYFORM");

        // create our place and add it to the directory. This place proxies for "*" but explicitly denies "MYFORM"
        DelayPlace deniedWildcardPlace = new DelayPlace(new ResourceReader().getConfigDataName(DelayPlace.class).replace("/main/", "/test/"));
        this.dir.addTestEntry(deniedWildcardPlace.getDirectoryEntry());

        // Add another entry that proxies for "MYFORM".
        // Doesn't need an actual place, but does need a higher expense than deniedWildcardPlace
        DirectoryEntry expected = new DirectoryEntry("MYFORM.s4.ANALYZE.http://example.com:8001/A$9999");
        this.dir.addTestEntry(expected);

        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertEquals(expected, result, "After a denied entry, should get next matching entry for the same stage");
    }

    @Test
    void testWildCardProxyWithDeniedEntry() throws IOException {
        loadAllTestEntries();

        this.payload.pushCurrentForm("OTHERFORM");

        // create our place and add it to the directory. This place proxies for "*" but explicitly denies "MYFORM"
        DelayPlace deniedWildcardPlace = new DelayPlace(new ResourceReader().getConfigDataName(DelayPlace.class).replace("/main/", "/test/"));
        this.dir.addTestEntry(deniedWildcardPlace.getDirectoryEntry());

        // OTHERFORM is not denied so we expect non-null result
        final DirectoryEntry result = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertEquals(deniedWildcardPlace.getKey(), result.getKey(), "Should get matching entry for wildcard place");

        this.payload.pushCurrentForm("MYFORM");

        // MYFORM is denied so null should be returned
        final DirectoryEntry nullResult = this.agent.getNextKeyAccess(this.dir, this.payload);
        assertNull(nullResult, "MYFORM should be denied");
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
