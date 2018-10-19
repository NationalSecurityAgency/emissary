package emissary.pickup.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import emissary.core.IBaseDataObject;
import emissary.core.Namespace;
import emissary.pickup.Priority;
import emissary.pickup.PriorityDirectory;
import emissary.pickup.WorkBundle;
import emissary.pickup.WorkSpace;
import emissary.test.core.FunctionalTest;
import emissary.util.io.ResourceReader;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FTestSpacePlaceInteraction extends FunctionalTest {
    private FilePickUpClient place = null;
    private WorkSpace space = null;
    private InputStream configStream = null;

    // Workspace input and output directories
    private File inarea;
    private File outarea;

    @Override
    @Before
    public void setUp() throws Exception {

        // set config to java.io.tmpdir, this config package
        setConfig(System.getProperty("java.io.tmpdir", "."), true);

        logger.debug("Starting WorkSpace tests");

        // Set up a directory struction with two files to be processed
        inarea = new File(TMPDIR + "/filepicktest/in");
        inarea.mkdirs();

        outarea = new File(TMPDIR + "/filepicktest/out");
        outarea.mkdirs();

        File testfile = File.createTempFile("temp", ".dat", inarea);
        testfile.deleteOnExit();

        File inareadir = new File(inarea, "subdir");
        inareadir.mkdirs();
        inareadir.deleteOnExit();
        File testfile2 = File.createTempFile("temp", ".dat", inareadir);
        testfile2.deleteOnExit();
        FileOutputStream os = new FileOutputStream(testfile);
        os.write("This is a test".getBytes());
        os.close();
        os = new FileOutputStream(testfile2);
        os.write("This is a test".getBytes());
        os.close();

        // start jetty
        startJetty(8005);
        logger.debug("WorkSpace test setup completed");
    }

    @Override
    @After
    public void tearDown() throws Exception {

        logger.debug("Starting tearDown phase");

        // if (!allTestsCompleted && logger.isDebugEnabled())
        // {
        // logger.debug("Entering inspection sleep due to test failure. Turn off debug to avoid delay");
        // pause(300000);
        // }

        if (space != null) {
            space.stop();
            space = null;
        }

        if (place != null) {
            place.shutDown();
            place = null;
        }

        demolishServer();
        restoreConfig();

        // Clean up directories
        FileUtils.deleteDirectory(inarea.getParentFile());
        FileUtils.deleteDirectory(outarea);
        FileUtils.deleteDirectory(new File(TMPDIR + "/data"));
    }

    private void createWorkspace(boolean useFileTimestamps, boolean useSimple) throws Exception {
        // Create and bind a WorkSpace
        space = new WorkSpace();
        space.setEatPrefix(TMPDIR + "/filepicktest/in");
        space.setOutputRoot(TMPDIR + "/filepicktest/out");
        space.setCaseId("testcase");
        space.setPattern("*.FILE_PICK_UP.INPUT.*");
        space.setLoop(true);
        space.setPauseTime(10);// millis

        space.setRetryStrategy(true);
        space.addDirectory(new PriorityDirectory(inarea.getPath(), Priority.DEFAULT));
        space.setDirectoryProcessing(false);
        space.setFileTimestampUsage(useFileTimestamps);
        space.setSimpleMode(useSimple);

        assertTrue("WorkSpace should exist in namespace", Namespace.exists("http://localhost:8005/WorkSpace"));

        assertEquals("No files proessed", 0, space.getFilesProcessed());
        assertEquals("No bytes proessed", 0, space.getBytesProcessed());
        assertEquals("No bundles proessed", 0, space.getBundlesProcessed());
        assertEquals("Found pickup place", 1, space.getPickUpPlaceCount());

    }

    private void detachWorkspace(String threadName) {
        // Create a thread and run the workspace detached
        Thread tspacethr = new Thread(space, threadName);
        tspacethr.setDaemon(false);
        tspacethr.start();
        logger.debug("WorkSpace is started and detached on thread " + threadName);
        Thread.yield();
    }

    @Test
    public void testUsingFileSeenList() throws Exception {
        // Start a client
        configStream = new ResourceReader().getConfigDataAsStream(this);
        place = (FilePickUpClient) addPlace("http://localhost:8005/FilePickUpClient", "emissary.pickup.file.FilePickUpClient", configStream);
        createWorkspace(false, false);
        detachWorkspace("WorkSpaceTestSPI-testUsingFileSeenList");
        runTest(800L, 1000L, 200L, 100L, false);
    }

    @Test
    public void testUsingFileTimeStamps() throws Exception {
        // Start a client
        configStream = new ResourceReader().getConfigDataAsStream(this);
        place = (FilePickUpClient) addPlace("http://localhost:8005/FilePickUpClient", "emissary.pickup.file.FilePickUpClient", configStream);
        createWorkspace(true, false);
        detachWorkspace("WorkSpaceTestSPI-testUsingFileTimeStamps");
        runTest(800L, 1000L, 200L, 100L, false);
    }

    @Test
    public void testUsingSimpleMode() throws Exception {
        // Start a client
        configStream = new ResourceReader().getConfigDataAsStream(this);
        MyFilePickUpClient myplace =
                (MyFilePickUpClient) addPlace("http://localhost:8005/MyFilePickUpClient",
                        "emissary.pickup.file.FTestSpacePlaceInteraction$MyFilePickUpClient", configStream);
        place = myplace;
        createWorkspace(true, true);
        detachWorkspace("WorkSpaceTestSPI-testUsingSimpleMode");
        runTest(800L, 1500L, 200L, 100L, true);
        assertEquals("PickupPlace should have created payload objects that mirror WorkSpace simple setting with no mismatches", 0,
                myplace.expectationsNotMetCount);
    }

    private void runTest(long t1, long t2, long t3, long t4, boolean simple) {
        pause(t1);

        assertEquals("File has been processed", 2, space.getFilesProcessed());
        assertEquals("Bundle was created", 1, space.getBundlesProcessed());
        assertEquals("Byte count matches file", "This is a test".length() * 2, space.getBytesProcessed());

        pause(t2);

        assertEquals("Outbound queue should empty", 0, space.getOutboundQueueSize());
        assertEquals("Pending queue should empty", 0, space.getPendingQueueSize());
        logger.debug("Shutting down file pick up client");
        place.shutDown();
        place = null;
        assertTrue("WorkSpace should exist in namespace", Namespace.exists("http://localhost:8005/WorkSpace"));

        pause(t3);
        assertEquals("PickupPlace count adjusted", 0, space.getPickUpPlaceCount());

        // Stuff another file into the workspace input area
        // while there are no active places to work on it
        String fakeKey = "THIS.IS.MY_KEY.http://localhost:8005/TestRunner";
        try {
            File extrafile = File.createTempFile("temp", ".dat", inarea);
            extrafile.deleteOnExit();
            logger.debug("Created new input file at " + extrafile.getPath());
            FileOutputStream os = new FileOutputStream(extrafile);
            String extramsg = "This is an extra test file";
            os.write(extramsg.getBytes());
            os.close();
            pause(t4);

            // The extra file should be noticed by the collector
            assertEquals("Files proessed notices new file", 3, space.getFilesProcessed());

            // Clean it all up by requesting the bundle containing this
            // file and marking it completed.
            WorkBundle bundle = space.take(fakeKey);
            pause(t4);

            assertNotNull("Bundle should be retreived with direct call", bundle);
            assertEquals("File marked pending", 1, space.getPendingQueueSize());

            assertEquals("Bundle should contain the one extra file", 1, bundle.size());
            assertEquals("Bundle should match extra file name", extrafile.getPath(), bundle.getFileNameList().get(0));
            assertEquals("Bundle should mirror work space simple mode", simple, bundle.getSimpleMode());
            space.workCompleted(fakeKey, bundle.getBundleId(), true);
            pause(t4);

            assertEquals("File no lnger marked pending", 0, space.getPendingQueueSize());
        } catch (IOException ex) {
            fail("Cannot create extra test file " + ex.getMessage());
        }

        // restart the client with the space already online
        configStream = new ResourceReader().getConfigDataAsStream(this);
        place = (FilePickUpClient) addPlace("http://localhost:8005/FilePickUpClient", "emissary.pickup.file.FilePickUpClient", configStream);

        pause(t4);
        assertEquals("Refound pickup place", 1, space.getPickUpPlaceCount());

        // Clean up any mess
        WorkBundle b = null;
        do {
            b = space.take(fakeKey);
            if (b == null || b.size() == 0) {
                break;
            }
        } while (b != null);

        logger.debug("SpacePlace all tests completed!");

    }

    public static class MyFilePickUpClient extends FilePickUpClient {

        boolean expectSimple = true;
        public int expectationsMetCount = 0;
        public int expectationsNotMetCount = 0;

        public MyFilePickUpClient(InputStream configInfo, String dir, String placeLoc) throws IOException {
            super(configInfo, dir, placeLoc);
        }

        public void setSimpleExpected(boolean value) {
            expectSimple = value;
        }

        @Override
        protected void dataObjectCreated(IBaseDataObject d, File f) {
            super.dataObjectCreated(d, f);
            boolean foundSimple = Boolean.parseBoolean(d.getStringParameter("SIMPLE_MODE"));
            if (expectSimple == foundSimple) {
                expectationsMetCount++;
            } else {
                expectationsNotMetCount++;
            }
            assertEquals("Payloads should mirror forensic expectations", expectSimple, foundSimple);
        }
    }
}
