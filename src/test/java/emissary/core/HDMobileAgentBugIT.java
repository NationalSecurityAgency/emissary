package emissary.core;

import emissary.admin.PlaceStarter;
import emissary.directory.DirectoryPlace;
import emissary.directory.EmissaryNode;
import emissary.place.IServiceProviderPlace;
import emissary.place.ServiceProviderPlace;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.spy;

/**
 * This test class only exists to highlight a bug in the HDMobileAgent
 */
class HDMobileAgentBugIT extends UnitTest {

    private static final String FAKE_ANALYZE_HISTORY = "FRM-PROCESSED-alternative.FAKE_ANALYZE.ANALYZE.http://localhost:8001/FakeAnalyzePlace$105050";
    private static final String FAKE_VERIFY_HISTORY = "FRM-PROCESSED-alternative.FAKE_VERIFY.VERIFY.http://localhost:8001/FakeVerifyPlace$105050";
    private static final String FAKE_SKIPPED_HISTORY = "FRM-PROCESSED-OTHER.FAKE_SKIPPED.ANALYZE.http://localhost:8001/FakeSkippedPlace$106050";
    private static final String FAKE_VERIFY_OTHER_HISTORY = "FRM-PROCESSED-OTHER.FAKE_VERIFY.VERIFY.http://localhost:8001/FakeVerifyPlace$105050";

    private IServiceProviderPlace directory;
    private IServiceProviderPlace analyze;
    private IServiceProviderPlace skipped;
    private IServiceProviderPlace validate;
    private HDMobileAgent ma;

    @BeforeEach
    void setup() {
        directory = addDir("http://localhost:8543/DirectoryPlace", DirectoryPlace.class.getName());
        analyze = addPlace("http://localhost:8543/FakeAnalyzePlace", FakeAnalyzePlace.class.getName());
        skipped = addPlace("http://localhost:8543/FakeSkippedPlace", FakeSkippedPlace.class.getName());
        validate = addPlace("http://localhost:8543/FakeVerifyPlace", FakeVerifyPlace.class.getName());

        ma = spy(HDMobileAgent.class);
        ma.arrivalPlace = analyze;
    }

    @AfterEach
    void testTearDown() {
        directory.shutDown();
        analyze.shutDown();
        skipped.shutDown();
        validate.shutDown();
    }

    private IServiceProviderPlace addDir(String key, String clsName) {
        return PlaceStarter.createPlace(key, null, clsName, null, new EmissaryNode());
    }

    private IServiceProviderPlace addPlace(String key, String clsName) {
        return PlaceStarter.createPlace(key, null, clsName, null);
    }

    private IBaseDataObject generateIbdo(int index, String form) {
        IBaseDataObject ibdo = DataObjectFactory.getInstance(new byte[] {}, "testing_file" + Family.getSep(index), form, "TST");
        ibdo.pushCurrentForm("FRM-PROCESSED-alternative");
        ibdo.appendTransformHistory("*.FAKE_ANALYZE.ANALYZE.http://localhost:8543/VisitedHereFirstPlace");
        return ibdo;
    }

    //@formatter:off
    @Test
    void testAgentControl() {

        String form1 = "FRM-PROCESSED";
        String form2 = "FRM-PROCESSED-OTHER";

        /*
         * Expected Results - att1 is processed individually and it goes to two places: FakeAnalyzePlace and
         * FakeVerifyPlace
         *
         * filename: testing_file-att-1
         * creationTimestamp: Fri Nov 09 18:49:27 GMT 2018
         * currentForms: [FRM-PROCESSED-alternative, FRM-PROCESSED]
         * filetype: TST
         * transform history (3) :
         *   *.FAKE_ANALYZE.ANALYZE.http://localhost:8543/VisitedHereFirstPlace.localhost:8543
         *   FRM-PROCESSED-alternative.FAKE_ANALYZE.ANALYZE.http://localhost:8543/FakeAnalyzePlace$5050
         *   FRM-PROCESSED-alternative.FAKE_VERIFY.VERIFY.http://localhost:8543/FakeVerifyPlace$5050
         */
        IBaseDataObject att1 = generateIbdo(1, form1);
        ma.clear();
        ma.addPayload(att1);
        ma.agentControl(analyze);
        assertTrue(att1.transformHistory().contains(FAKE_ANALYZE_HISTORY));
        assertTrue(att1.transformHistory().contains(FAKE_VERIFY_HISTORY));

        /*
         * Expected Results - att2 is processed individually and it goes to three places: FakeAnalyzePlace,
         * FakeSkippedPlace, and FakeVerifyPlace
         *
         * filename: testing_file-att-2
         * creationTimestamp: Fri Nov 09 18:49:28 GMT 2018
         * currentForms: [FRM-PROCESSED-OTHER, FRM-PROCESSED-alternative]
         * filetype: TST
         * transform history (4) :
         *   *.FAKE_ANALYZE.ANALYZE.http://localhost:8543/VisitedHereFirstPlace.localhost:8543
         *   FRM-PROCESSED-alternative.FAKE_ANALYZE.ANALYZE.http://localhost:8543/FakeAnalyzePlace$5050
         *   FRM-PROCESSED-OTHER.FAKE_SKIPPED.ANALYZE.http://localhost:8543/FakeSkippedPlace$6050
         *   FRM-PROCESSED-OTHER.FAKE_VERIFY.VERIFY.http://localhost:8543/FakeVerifyPlace$5050
         */
        IBaseDataObject att2 = generateIbdo(2, form2);
        ma.clear();
        ma.addPayload(att2);
        ma.agentControl(analyze);
        assertTrue(att2.transformHistory().contains(FAKE_ANALYZE_HISTORY));
        assertTrue(att2.transformHistory().contains(FAKE_SKIPPED_HISTORY));
        assertTrue(att2.transformHistory().contains(FAKE_VERIFY_OTHER_HISTORY));

        /*
         * Unexpected Results - att1 and att2 are processed together. Att1 goes to the expected two places:
         * FakeAnalyzePlace, and FakeVerifyPlace. Att2 is expected to go to three places: FakeAnalyzePlace,
         * FakeSkippedPlace, and FakeVerifyPlace but FakeSkippedPlace is never called
         *
         * Explanation: The bug is in HDMobileAgent on line 244, the comment reads
         *   // Add any other payload that has the same current form
         *   // and last place visited as this one while we are here
         *   for (final IBaseDataObject slug : this.payloadList) { ...
         *
         * Att1 and Att2 have the same current form, FRM-PROCESSED-alternative, so they get grouped together. There are
         * no analyze places left for FRM-PROCESSED-alternative, so they go to the Verify stage - FakeVerifyPlace.
         * Att2 has another form, FRM-PROCESSED-OTHER, that is never processed.
         *
         * filename: testing_file-att-1
         * creationTimestamp: Fri Nov 09 18:49:28 GMT 2018
         * currentForms: [FRM-PROCESSED-alternative, FRM-PROCESSED]
         * filetype: TST
         * transform history (3) :
         *   *.FAKE_ANALYZE.ANALYZE.http://localhost:8543/VisitedHereFirstPlace.localhost:8543
         *   FRM-PROCESSED-alternative.FAKE_ANALYZE.ANALYZE.http://localhost:8543/FakeAnalyzePlace$5050
         *   FRM-PROCESSED-alternative.FAKE_VERIFY.VERIFY.http://localhost:8543/FakeVerifyPlace$5050
         *
         * filename: testing_file-att-2
         * creationTimestamp: Fri Nov 09 18:49:28 GMT 2018
         * currentForms: [FRM-PROCESSED-alternative, FRM-PROCESSED-OTHER]
         * filetype: TST
         * transform history (3) :
         *   *.FAKE_ANALYZE.ANALYZE.http://localhost:8543/VisitedHereFirstPlace.localhost:8543
         *   FRM-PROCESSED-alternative.FAKE_ANALYZE.ANALYZE.http://localhost:8543/FakeAnalyzePlace$5050
         *   FRM-PROCESSED-alternative.FAKE_VERIFY.VERIFY.http://localhost:8543/FakeVerifyPlace$5050
         */
        att1 = generateIbdo(1, form1);
        att2 = generateIbdo(2, form2);
        ma.clear();
        ma.addPayload(att1);
        ma.addPayload(att2);
        ma.agentControl(analyze);
        assertTrue(att1.transformHistory().contains(FAKE_ANALYZE_HISTORY));
        assertTrue(att1.transformHistory().contains(FAKE_VERIFY_HISTORY));
        assertTrue(att2.transformHistory().contains(FAKE_ANALYZE_HISTORY));
        // wrong
        assertTrue(att2.transformHistory().contains(FAKE_VERIFY_HISTORY));
        // these should be part of the history, but are not
        assertFalse(att2.transformHistory().contains(FAKE_SKIPPED_HISTORY));
        assertFalse(att2.transformHistory().contains(FAKE_VERIFY_OTHER_HISTORY));
    }
    //@formatter:on

    /**
     * *.FAKE_ANALYZE.ANALYZE.http://localhost:8543/FakeAnalyzePlace$5050
     */
    static final class FakeAnalyzePlace extends ServiceProviderPlace {

        @SuppressWarnings("unused")
        public FakeAnalyzePlace(String configFile, String theDir, String thePlaceLocation) throws IOException {}

        @Override
        public List<IBaseDataObject> processHeavyDuty(IBaseDataObject payloadListArg) {
            return Collections.emptyList();
        }
    }

    /**
     * FRM-PROCESSED-OTHER.FAKE_SKIPPED.ANALYZE.http://localhost:8543/FakeSkippedPlace$6050
     */
    static final class FakeSkippedPlace extends ServiceProviderPlace {

        @SuppressWarnings("unused")
        public FakeSkippedPlace(String configFile, String theDir, String thePlaceLocation) throws IOException {}

        @Override
        public List<IBaseDataObject> processHeavyDuty(IBaseDataObject payloadListArg) {
            return Collections.emptyList();
        }
    }

    /**
     * *.FAKE_VERIFY.VERIFY.http://localhost:8543/FakeVerifyPlace$5050
     */
    static final class FakeVerifyPlace extends ServiceProviderPlace {

        @SuppressWarnings("unused")
        public FakeVerifyPlace(String configFile, String theDir, String thePlaceLocation) throws IOException {}

        @Override
        public List<IBaseDataObject> processHeavyDuty(IBaseDataObject payloadListArg) {
            return Collections.emptyList();
        }
    }
}
