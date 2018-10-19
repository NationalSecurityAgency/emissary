package emissary.pickup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import emissary.command.FeedCommand;
import emissary.core.Namespace;
import emissary.directory.EmissaryNode;
import emissary.directory.IDirectoryPlace;
import emissary.pickup.file.FilePickUpClient;
import emissary.test.core.FunctionalTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FTestMultipleWorkSpaces extends FunctionalTest {
    private FilePickUpClient place = null;
    private WorkSpace space1 = null;
    private WorkSpace space2 = null;
    private IDirectoryPlace peer2 = null;

    // Workspace input and output directories
    private File inarea1;
    private File inareadir1;
    private File outarea1;

    private File inarea2;
    private File inareadir2;
    private File outarea2;

    private File holdarea;

    private List<File> workingFiles = new ArrayList<File>();
    private List<String> workingFilePaths = new ArrayList<String>();

    @Override
    @Before
    public void setUp() throws Exception {

        // set config to java.io.tmpdir, this config package
        setConfig(System.getProperty("java.io.tmpdir", "."), true);

        logger.debug("Starting WorkSpace tests");

        // Set up a directory struction with two files to be processed
        // rom each workspace
        inarea1 = new File(TMPDIR + "/multipicktest/space1/in");
        inarea1.mkdirs();

        outarea1 = new File(TMPDIR + "/multipicktest/space1/out");
        outarea1.mkdirs();

        holdarea = new File(TMPDIR, "/data/HoldData");
        holdarea.mkdirs();

        File testfile = File.createTempFile("temp1", ".dat", inarea1);
        workingFiles.add(testfile);
        workingFilePaths.add(testfile.getName());
        testfile.deleteOnExit();

        inareadir1 = new File(inarea1, "subdir1");
        inareadir1.mkdirs();
        inareadir1.deleteOnExit();
        File testfile2 = File.createTempFile("temp2", ".dat", inareadir1);
        workingFiles.add(testfile2);
        workingFilePaths.add("subdir1/" + testfile2.getName());
        testfile2.deleteOnExit();
        FileOutputStream os = new FileOutputStream(testfile);
        os.write("This is a test".getBytes());
        os.close();
        os = new FileOutputStream(testfile2);
        os.write("This is a test".getBytes());
        os.close();

        inarea2 = new File(TMPDIR + "/multipicktest/space2/in");
        inarea2.mkdirs();

        outarea2 = new File(TMPDIR + "/multipicktest/space2/out");
        outarea2.mkdirs();

        File testfile3 = File.createTempFile("temp3", ".dat", inarea2);
        workingFiles.add(testfile3);
        workingFilePaths.add(testfile3.getName());
        testfile3.deleteOnExit();

        inareadir2 = new File(inarea2, "subdir2");
        inareadir2.mkdirs();
        inareadir2.deleteOnExit();
        File testfile4 = File.createTempFile("temp2", ".dat", inareadir2);
        workingFiles.add(testfile4);
        workingFilePaths.add("subdir2/" + testfile4.getName());
        testfile4.deleteOnExit();
        os = new FileOutputStream(testfile3);
        os.write("This is a test".getBytes());
        os.close();
        os = new FileOutputStream(testfile4);
        os.write("This is a test".getBytes());
        os.close();


        // start jetty and directory services
        // TODO These FTestWorkSpace* tests will compile now but need to be totally reworked due to the way we
        // start/stop emissary
        startJetty(8005);
        // Start a second client to keep things happy
        peer2 = startDirectory(9005);
        peer2.heartbeatRemoteDirectory(directory.getKey());
        directory.heartbeatRemoteDirectory(peer2.getKey());

        // Start a FilePickUpClient
        place = (FilePickUpClient) addPlace("http://localhost:8005/FilePickUpClient", FilePickUpClient.class.getName());

        System.setProperty(EmissaryNode.NODE_PORT_PROPERTY, "" + 8005);

        // Create and configure a WorkSpace
        space1 =
                new WorkSpace(FeedCommand.parse(FeedCommand.class, new String[] {"-ns", "WorkSpace1", "-c", TMPDIR, "-i",
                        TMPDIR + "/multipicktest/space1/in:10"}));
        space1.setEatPrefix(TMPDIR + "/multipicktest/space1/in");
        space1.setOutputRoot(TMPDIR + "/multipicktest/space1/out");
        space1.setCaseId("space1case");
        space1.setLoop(false);
        space1.setPauseTime(10);// millis
        space1.setRetryStrategy(true);
        space1.setDirectoryProcessing(false);

        // Create and configure a second WorkSpace
        space2 =
                new WorkSpace(FeedCommand.parse(FeedCommand.class, new String[] {"-ns", "WorkSpace2", "-c", TMPDIR, "-i",
                        TMPDIR + "/multipicktest/space2/in:10"}));
        space2.setEatPrefix(TMPDIR + "/multipicktest/space2/in");
        space2.setOutputRoot(TMPDIR + "/multipicktest/space2/out");
        space2.setCaseId("space2case");
        space2.setLoop(false);
        space2.setPauseTime(10);// millis
        space2.setRetryStrategy(true);
        space2.setDirectoryProcessing(false);

        logger.debug("WorkSpace test setup completed");
    }

    @Override
    @After
    public void tearDown() throws Exception {

        logger.debug("Starting tearDown phase");

        if (space1 != null) {
            logger.debug("Space1 stats >> " + space1.getStatsMessage());
            space1.stop();
            space1 = null;
        }

        if (space2 != null) {
            logger.debug("Space2 stats >> " + space2.getStatsMessage());
            space2.stop();
            space2 = null;
        }

        if (place != null) {
            place.shutDown();
            place = null;
        }

        if (peer2 != null) {
            peer2.shutDown();
            peer2 = null;
        }

        demolishServer();

        // Clean up directories
        inareadir1.delete();
        inarea1.delete();
        outarea1.delete();
        inarea1.getParentFile().delete();
        inareadir2.delete();
        inarea2.delete();
        outarea2.delete();
        inarea2.getParentFile().delete();

        super.tearDown();
    }

    @Test
    public void testAll() {

        assertTrue("First WorkSpace should exist in namespace", Namespace.exists("http://localhost:8005/WorkSpace1"));

        assertTrue("Second WorkSpace should exist in namespace", Namespace.exists("http://localhost:8005/WorkSpace2"));

        pause(100);

        int byteSize = 0;
        int fileCount = 0;
        int bundleCount = 0;
        int clientCount = 1;
        int expectedOutbound = 0;
        int expectedPending = 0;
        int expectedRetries = 0;

        checkFileCounts(space1, fileCount, byteSize, bundleCount, clientCount, expectedOutbound, expectedPending, expectedRetries);
        checkFileCounts(space2, fileCount, byteSize, bundleCount, clientCount, expectedOutbound, expectedPending, expectedRetries);

        Thread tspacethr1 = new Thread(space1, "WorkSpace1Test");
        tspacethr1.setDaemon(true);
        tspacethr1.start();
        Thread.yield();
        Thread tspacethr2 = new Thread(space2, "WorkSpace2Test");
        tspacethr2.setDaemon(true);
        tspacethr2.start();
        Thread.yield();

        logger.debug("WorkSpaces are both started!");

        pause(10000);

        // Only count half of the stuff
        byteSize = "This is a test".length() * 2;
        bundleCount++;
        fileCount += 2;

        checkFileCounts(space1, fileCount, byteSize, bundleCount, clientCount, expectedOutbound, expectedPending, expectedRetries);
        checkFileCounts(space2, fileCount, byteSize, bundleCount, clientCount, expectedOutbound, expectedPending, expectedRetries);

        pause(500);
        checkFileLocations();
        logger.debug("MultipleWorkSpace all tests completed!");
    }

    private void checkFileLocations() {
        // Detailed debugging help on the structure of what is left in the file system
        if (logger.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder();
            new emissary.util.shell.Executrix().execute(new String[] {"find", TMPDIR + "/multipicktest", TMPDIR + "data", "-print"}, sb);
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
            File fp = new File(place.getInProcessArea() + "/" + fn);
            File fd1 = new File(space1.getOutputRoot() + "/" + fn);
            File fd2 = new File(space2.getOutputRoot() + "/" + fn);
            assertFalse("File[" + counter + "] should not exist in in-process area any more - " + fn + " - " + fp.getPath(), fp.exists());
            assertTrue(
                    "File[" + counter + "] should exist in one of the two output root areas - " + fn + " - " + fd1.getPath() + ", " + fd2.getPath(),
                    fd1.exists() || fd2.exists());
            counter++;
        }
    }

    private void checkFileCounts(WorkSpace space, int files, int bytes, int bundles, int places, int outbound, int pending, int retried) {
        assertEquals("files processed on " + space.getKey(), files, space.getFilesProcessed());
        assertEquals("bytes processed on " + space.getKey(), bytes, space.getBytesProcessed());
        assertEquals("bundles processed on " + space.getKey(), bundles, space.getBundlesProcessed());
        assertEquals("pickup place count in " + space.getKey(), places, space.getPickUpPlaceCount());
        assertEquals("Outbound queue count in " + space.getKey(), outbound, space.getOutboundQueueSize());
        assertEquals("Pending queue count in " + space.getKey(), pending, space.getPendingQueueSize());
        assertEquals("Retried bundle count in " + space.getKey(), retried, space.getRetriedCount());
    }
}
