package emissary.pickup.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.Map;

import emissary.admin.PlaceStarter;
import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.pickup.WorkBundle;
import emissary.test.core.UnitTest;
import emissary.util.io.ResourceReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FilePickUpClientTest extends UnitTest {
    private static String CLIENT_KEY = "http://localhost:8005/FilePickUpClient";
    private static String DIRECTORY_KEY = "DIR.DIRECTORY.STUDY.http://localhost:8005/DirectoryPlace";
    private IBaseDataObject payload = null;
    private MyFilePickUpClient client = null;
    private WorkBundle bundle = null;

    @Before
    public void testSetup() throws Exception {
        payload = DataObjectFactory.getInstance("This is a test".getBytes(), "/eat/prefix/testPath", "UNKNOWN");
        InputStream clientConfig = new ResourceReader().getConfigDataAsStream(this);
        client =
                (MyFilePickUpClient) PlaceStarter.createPlace(CLIENT_KEY, clientConfig, MyFilePickUpClient.class.getName().toString(), DIRECTORY_KEY);
        bundle = new WorkBundle("/output/root", "/eat/prefix");
        client.setCurrentBundle(bundle);
    }

    @After
    public void testTearDown() {
        if (client != null) {
            client.shutDown();
            client = null;
        }
        bundle = null;
        payload = null;

    }

    @Test
    public void testDataObjectCreatedCallbackWithMissingPath() {
        client.dataObjectCreated(payload, new File("/eat/prefix/foo/bar"));
        assertEquals("Input filename must be recorded", "bar", payload.getStringParameter("INPUT_FILENAME"));
        assertEquals("TargetBin must be recorded without prefix", "/foo", payload.getStringParameter("TARGETBIN"));
        assertEquals("Priority must be transferred from bundle to payload", bundle.getPriority(), payload.getPriority());
    }

    @Test
    public void testDataObjectCreatedCallbackWithSimpleCaseId() {
        bundle.setCaseId("PETERPAN");
        client.dataObjectCreated(payload, new File("/eat/prefix/foo/bar"));
        assertEquals("Simple case id must be transferred from bundle to payload", bundle.getCaseId(), payload.getStringParameter("DATABASE_CASE_ID"));
    }

    @Test
    public void testDataObjectCreatedCallbackWithComplexCaseId() {
        String originalShortName = payload.shortName();
        bundle.setCaseId("PROJECT:PETERPAN");
        client.dataObjectCreated(payload, new File("/eat/prefix/foo/bar"));
        assertEquals("Complex case id must be transferred from bundle to payload", "PETERPAN", payload.getStringParameter("PROJECT"));
        assertEquals("Complex case id must not mess with payload filename when not simple", originalShortName, payload.shortName());
    }

    @Test
    public void testDataObjectCreatedCallbackWithComplexCaseIdInSimpleMode() {
        bundle.setCaseId("PROJECT:PETERPAN");
        payload.putParameter("SIMPLE_MODE", "true");
        client.dataObjectCreated(payload, new File("/eat/prefix/foo/bar"));
        assertEquals("Complex case id must be transferred from bundle to payload", "PETERPAN", payload.getStringParameter("PROJECT"));
        assertTrue("Complex case in simple mode must add fn hash to payload not " + payload.shortName(),
                payload.shortName().startsWith("PETERPAN-"));
        assertEquals("Original-Filename should be set in simple mode", "/foo/bar", payload.getStringParameter("Original-Filename"));
    }

    @Test
    public void testNullReturnedFromFixCaseIdHookUsesTimeForCase() {
        bundle.setCaseId("PETERPAN");
        client.nullifyCaseIdInHook = true;
        client.dataObjectCreated(payload, new File("/eat/prefix/foo/bar"));
        assertEquals("Current oridina date must be used when hook nullifies simple case name", emissary.util.TimeUtil.getCurrentDateOrdinal(),
                payload.getStringParameter("DATABASE_CASE_ID"));
    }

    @Test
    public void testCreateFileName() throws Exception {
        // Perform the default filename creation strategy
        String filePath = "/foo/bar";
        MessageDigest digest = MessageDigest.getInstance("MD5");
        digest.reset();
        byte[] resultHash = digest.digest(filePath.getBytes());
        String resultString = emissary.util.Hexl.toUnformattedHexString(resultHash);

        // Compare with the output of the createFilename() method
        assertEquals("File names do not match", "PETERPAN-" + resultString, client.createFilename(filePath, "PETERPAN"));

        // Compare with what was set in the payload for simple mode
        bundle.setCaseId("PROJECT:PETERPAN");
        payload.putParameter("SIMPLE_MODE", "true");
        client.dataObjectCreated(payload, new File(filePath));
        assertEquals("Payload filename is not set to correct value", "PETERPAN-" + resultString, payload.getFilename());
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
