package emissary.pickup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import emissary.core.Namespace;
import emissary.directory.EmissaryNode;
import emissary.directory.IDirectoryPlace;
import emissary.test.core.FunctionalTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FTestFailedPickupClient extends FunctionalTest {
    private BreakableFilePickUpClient goodplace = null;
    private BreakableFilePickUpClient badplace = null;
    private MyWorkSpace space = null;
    private IDirectoryPlace peer2 = null;

    // Workspace input and output directories
    private File inarea;
    private File inareadir;
    private File holdarea;
    private File outarea;

    private List<File> workingFiles = new ArrayList<File>();
    private List<String> workingFilePaths = new ArrayList<String>();

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

        holdarea = new File(TMPDIR, "/data/HoldData");
        holdarea.mkdirs();

        File testfile = File.createTempFile("temp1", ".dat", inarea);
        workingFiles.add(testfile);
        workingFilePaths.add(testfile.getName());
        testfile.deleteOnExit();

        inareadir = new File(inarea, "subdir");
        inareadir.mkdirs();
        inareadir.deleteOnExit();
        File testfile2 = File.createTempFile("temp2", ".dat", inareadir);
        workingFiles.add(testfile2);
        workingFilePaths.add("subdir/" + testfile2.getName());
        testfile2.deleteOnExit();
        FileOutputStream os = new FileOutputStream(testfile);
        os.write("This is a test".getBytes());
        os.close();
        os = new FileOutputStream(testfile2);
        os.write("This is a test".getBytes());
        os.close();

        // start jetty and a workspace
        startJetty(8005);
        // Start a second client
        peer2 = startDirectory(9005);
        peer2.heartbeatRemoteDirectory(directory.getKey());
        directory.heartbeatRemoteDirectory(peer2.getKey());

        // Start a FilePickUpClient
        logger.debug("STARTING BROKEN DURING RECEIVE CLIENT");
        badplace = (BreakableFilePickUpClient) addPlace("http://localhost:8005/FilePickUpClient", BreakableFilePickUpClient.class.getName());
        badplace.setBrokenDuringReceive(true); // crash when getting a bundle

        // Create and configure a WorkSpace
        System.setProperty(EmissaryNode.NODE_PORT_PROPERTY, "" + 8005);
        space = new MyWorkSpace();
        space.setEatPrefix(TMPDIR + "/filepicktest/in");
        space.setOutputRoot(TMPDIR + "/filepicktest/out");
        space.setCaseId("filetestcase");
        space.setLoop(false);
        space.setPauseTime(10);// millis
        space.setRetryStrategy(true);
        space.addDirectory(new PriorityDirectory(inarea.getPath(), Priority.DEFAULT));
        space.setDirectoryProcessing(false);

        logger.debug("WorkSpace test setup completed");
    }

    @Override
    @After
    public void tearDown() throws Exception {

        logger.debug("Starting tearDown phase");

        if (space != null) {
            logger.debug("Space stats >> " + space.getStatsMessage());
            space.stop();
            space = null;
        }

        if (goodplace != null) {
            goodplace.shutDown();
            goodplace = null;
        }

        if (badplace != null) {
            // should already be shutdown
            badplace = null;
        }

        if (peer2 != null) {
            peer2.shutDown();
            peer2 = null;
        }

        demolishServer();

        // Clean up directories
        inareadir.delete();
        inarea.delete();
        outarea.delete();
        inarea.getParentFile().delete();

        super.tearDown();
    }

    @Test
    public void testAll() {

        assertTrue("WorkSpace should exist in namespace as " + space.getKey(), Namespace.exists("http://localhost:8005/WorkSpace"));

        pause(100);

        int byteSize = 0;
        int fileCount = 0;
        int bundleCount = 0;
        int clientCount = 1;
        int expectedOutbound = 0;
        int expectedPending = 0;
        int expectedRetries = 0;

        checkFileCounts(space, fileCount, byteSize, bundleCount, clientCount, expectedOutbound, expectedPending, expectedRetries);

        Thread tspacethr = new Thread(space, "WorkSpaceTestFPC");
        tspacethr.setDaemon(true);
        tspacethr.start();

        logger.debug("WorkSpace is started!");

        pause(1500);

        for (File f : workingFiles) {
            byteSize += f.length();
        }
        bundleCount++;
        fileCount += 2;

        // BadPlace should have crashed and been
        // deregistered when it received a bundle
        expectedOutbound++;
        clientCount--;

        // Account for retries
        expectedRetries++;

        checkFileCounts(space, fileCount, byteSize, bundleCount, clientCount, expectedOutbound, expectedPending, expectedRetries);
        pause(1000);

        // Add another broken FilePickUpCLient, but one that receives
        // ok and fails to process
        logger.debug("STARTING BROKEN DURING PROCESSING CLIENT");
        badplace =
                (BreakableFilePickUpClient) addPlace("http://localhost:9005/FilePickUpClient", BreakableFilePickUpClient.class.getName(),
                        peer2.getKey());
        badplace.setBrokenDuringProcessing(true); // crash after receiving a bundle
        space.addPickUp(badplace.getKey());
        clientCount++;

        pause(1000); // it dies
        space.removePickUp(badplace.getKey());
        expectedRetries++;
        clientCount--;
        checkFileCounts(space, fileCount, byteSize, bundleCount, clientCount, expectedOutbound, expectedPending, expectedRetries);

        pause(1000);


        // Start a GOOD FilePickUpClient
        logger.debug("STARTING GOOD CLIENT");
        goodplace =
                (BreakableFilePickUpClient) addPlace("http://localhost:9005/FilePickUpClient", BreakableFilePickUpClient.class.getName(),
                        peer2.getKey());
        space.addPickUp(goodplace.getKey());

        clientCount++;
        expectedOutbound--;
        pause(1000);

        checkFileCounts(space, fileCount, byteSize, bundleCount, clientCount, expectedOutbound, expectedPending, expectedRetries);

        pause(500);
        checkFileLocations();
        logger.debug("Failed Space Place all tests completed!");
    }

    private void checkFileLocations() {
        // Detailed debugging help on the structure of what is left in the file system
        if (logger.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder();
            new emissary.util.shell.Executrix().execute(new String[] {"find", TMPDIR + "/filepicktest", TMPDIR + "/data", "-print"}, sb);
            logger.debug("Files:\n" + sb);
        }

        // Assert things about where the files are located
        int counter = 0;
        for (File f : workingFiles) {
            assertFalse("File[" + counter + "] should not exist in input area any more - " + f, f.exists());
            counter++;
        }

        counter = 0;
        for (String fn : workingFilePaths) {
            File fp = new File(goodplace.getInProcessArea() + "/" + fn);
            File fd = new File(space.getOutputRoot() + "/" + fn);
            assertFalse("File[" + counter + "] should not exist in in-process area any more - " + fn + " - " + fp.getPath(), fp.exists());
            assertTrue("File[" + counter + "] should exist in the output area - " + fn + " - " + fd.getPath(), fd.exists());
            counter++;
        }
    }

    private void checkFileCounts(WorkSpace space, int files, int bytes, int bundles, int places, int outbound, int pending, int retried) {
        assertEquals("files processed", files, space.getFilesProcessed());
        assertEquals("bytes processed", bytes, space.getBytesProcessed());
        assertEquals("bundles processed", bundles, space.getBundlesProcessed());
        assertEquals("pickup place count", places, space.getPickUpPlaceCount());
        assertEquals("Outbound queue count", outbound, space.getOutboundQueueSize());
        assertEquals("Pending queue count", pending, space.getPendingQueueSize());
        assertEquals("Retried bundle count", retried, space.getRetriedCount());
    }

    private static final class MyWorkSpace extends WorkSpace {
        public MyWorkSpace() throws Exception {}

        @Override
        public void addPickUp(String key) {
            super.addPickUp(key);
        }

        @Override
        public void removePickUp(String key) {
            super.removePickUp(key);
        }
    }
}
