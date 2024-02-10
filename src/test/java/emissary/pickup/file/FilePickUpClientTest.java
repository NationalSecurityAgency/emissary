package emissary.pickup.file;

import emissary.admin.PlaceStarter;
import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.pickup.WorkBundle;
import emissary.test.core.junit5.UnitTest;
import emissary.util.Hexl;
import emissary.util.TimeUtil;
import emissary.util.io.ResourceReader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.Map;
import javax.annotation.Nullable;

import static emissary.core.constants.Parameters.INPUT_FILENAME;
import static emissary.core.constants.Parameters.ORIGINAL_FILENAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilePickUpClientTest extends UnitTest {
    private static final String CLIENT_KEY = "http://localhost:8005/FilePickUpClient";
    private static final String DIRECTORY_KEY = "DIR.DIRECTORY.STUDY.http://localhost:8005/DirectoryPlace";
    @Nullable
    private IBaseDataObject payload = null;
    @Nullable
    private MyFilePickUpClient client = null;
    @Nullable
    private WorkBundle bundle = null;

    @BeforeEach
    public void testSetup() {
        payload = DataObjectFactory.getInstance("This is a test".getBytes(), "/eat/prefix/testPath", "UNKNOWN");
        InputStream clientConfig = new ResourceReader().getConfigDataAsStream(this);
        client =
                (MyFilePickUpClient) PlaceStarter.createPlace(CLIENT_KEY, clientConfig, MyFilePickUpClient.class.getName(), DIRECTORY_KEY);
        bundle = new WorkBundle("/output/root", "/eat/prefix");
        client.setCurrentBundle(bundle);
    }

    @AfterEach
    public void testTearDown() {
        if (client != null) {
            client.shutDown();
            client = null;
        }
        bundle = null;
        payload = null;

    }

    @Test
    void testDataObjectCreatedCallbackWithMissingPath() {
        client.dataObjectCreated(payload, new File("/eat/prefix/foo/bar"));
        assertEquals("bar", payload.getStringParameter(INPUT_FILENAME), "Input filename must be recorded");
        assertEquals("/foo", payload.getStringParameter("TARGETBIN"), "TargetBin must be recorded without prefix");
        assertEquals(bundle.getPriority(), payload.getPriority(), "Priority must be transferred from bundle to payload");
    }

    @Test
    void testDataObjectCreatedCallbackWithSimpleCaseId() {
        bundle.setCaseId("PETERPAN");
        client.dataObjectCreated(payload, new File("/eat/prefix/foo/bar"));
        assertEquals(bundle.getCaseId(), payload.getStringParameter("DATABASE_CASE_ID"), "Simple case id must be transferred from bundle to payload");
    }

    @Test
    void testDataObjectCreatedCallbackWithComplexCaseId() {
        String originalShortName = payload.shortName();
        bundle.setCaseId("PROJECT:PETERPAN");
        client.dataObjectCreated(payload, new File("/eat/prefix/foo/bar"));
        assertEquals("PETERPAN", payload.getStringParameter("PROJECT"), "Complex case id must be transferred from bundle to payload");
        assertEquals(originalShortName, payload.shortName(), "Complex case id must not mess with payload filename when not simple");
    }

    @Test
    void testDataObjectCreatedCallbackWithComplexCaseIdInSimpleMode() {
        bundle.setCaseId("PROJECT:PETERPAN");
        payload.putParameter("SIMPLE_MODE", "true");
        client.dataObjectCreated(payload, new File("/eat/prefix/foo/bar"));
        assertEquals("PETERPAN", payload.getStringParameter("PROJECT"), "Complex case id must be transferred from bundle to payload");
        assertTrue(payload.shortName().startsWith("PETERPAN-"),
                "Complex case in simple mode must add fn hash to payload not " + payload.shortName());
        assertEquals("/foo/bar", payload.getStringParameter(ORIGINAL_FILENAME), "Original-Filename should be set in simple mode");
    }

    @Test
    void testNullReturnedFromFixCaseIdHookUsesTimeForCase() {
        bundle.setCaseId("PETERPAN");
        client.nullifyCaseIdInHook = true;
        client.dataObjectCreated(payload, new File("/eat/prefix/foo/bar"));
        assertEquals(TimeUtil.getCurrentDateOrdinal(),
                payload.getStringParameter("DATABASE_CASE_ID"),
                "Current oridina date must be used when hook nullifies simple case name");
    }

    @Test
    void testCreateFileName() throws Exception {
        // Perform the default filename creation strategy
        String filePath = "/foo/bar";
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.reset();
        byte[] resultHash = digest.digest(filePath.getBytes());
        String resultString = Hexl.toUnformattedHexString(resultHash);

        // Compare with the output of the createFilename() method
        assertEquals("PETERPAN-" + resultString, client.createFilename(filePath, "PETERPAN"), "File names do not match");

        // Compare with what was set in the payload for simple mode
        bundle.setCaseId("PROJECT:PETERPAN");
        payload.putParameter("SIMPLE_MODE", "true");
        client.dataObjectCreated(payload, new File(filePath));
        assertEquals("PETERPAN-" + resultString, payload.getFilename(), "Payload filename is not set to correct value");
    }

    public static class MyFilePickUpClient extends FilePickUpClient {

        public boolean nullifyCaseIdInHook = false;

        public MyFilePickUpClient(InputStream configInfo, String dir, String placeLoc) throws IOException {
            super(configInfo, dir, placeLoc);
        }

        @Override
        public void dataObjectCreated(IBaseDataObject d, File f) {
            super.dataObjectCreated(d, f);
        }

        public void setCurrentBundle(WorkBundle wb) {
            currentBundle = wb;
        }

        public WorkBundle getCurrentBundle() {
            return currentBundle;
        }

        @Override
        @Nullable
        protected String caseIdHook(String initialCaseId, String sessionname, String fileName, Map<String, Collection<Object>> metadata) {
            if (nullifyCaseIdInHook)
                return null;
            else
                return super.caseIdHook(initialCaseId, sessionname, fileName, metadata);
        }

        @Override
        protected String createFilename(String filePath, String prefix) {
            return super.createFilename(filePath, prefix);
        }
    }
}
