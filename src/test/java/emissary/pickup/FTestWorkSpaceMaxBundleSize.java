package emissary.pickup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import emissary.command.FeedCommand;
import emissary.core.Namespace;
import emissary.directory.EmissaryNode;
import emissary.directory.IDirectoryPlace;
import emissary.test.core.FunctionalTest;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FTestWorkSpaceMaxBundleSize extends FunctionalTest {
    private MyWorkSpace space = null;
    private IDirectoryPlace peer = null;

    // Workspace input and output directories
    private File inarea1;
    private File inareadir1;
    private File outarea1;
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
        inarea1 = new File(TMPDIR + "/test/space/in");
        inarea1.mkdirs();

        outarea1 = new File(TMPDIR + "/test/space/out");
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
        File testfile3 = File.createTempFile("temp3", ".dat", inarea1);
        workingFiles.add(testfile3);
        workingFilePaths.add(testfile3.getName());
        testfile3.deleteOnExit();
        FileOutputStream os = new FileOutputStream(testfile);
        os.write("This is a test".getBytes());
        os.close();
        os = new FileOutputStream(testfile2);
        os.write("This is an even bigger test file!".getBytes());
        os.close();
        os = new FileOutputStream(testfile3);
        os.write("This is a 3rd test.".getBytes());
        os.close();

        // start jetty and directory services
        // TODO These FTestWorkSpace* tests will compile now but need to be totally reworked due to the way we
        // start/stop emissary
        startJetty(8005);
        // Start a second client to keep things happy
        peer = startDirectory(9005);
        peer.heartbeatRemoteDirectory(directory.getKey());
        directory.heartbeatRemoteDirectory(peer.getKey());

        System.setProperty(EmissaryNode.NODE_PORT_PROPERTY, "" + 8005);
        logger.debug("WorkSpace test setup completed");
    }

    @Override
    @After
    public void tearDown() throws Exception {
        if (space != null) {
            space.stop();
            space = null;
        }

        demolishServer();

        // Clean up directories
        FileUtils.deleteDirectory(inarea1.getParentFile());
        FileUtils.deleteDirectory(outarea1);

        super.tearDown();
    }

    private void createWorkspace(String namespace, int maxCount, long maxBytes) throws Exception {
        // Create and configure a WorkSpace
        space =
                new MyWorkSpace(FeedCommand.parse(FeedCommand.class, new String[] {"-nsname", namespace, "-c", TMPDIR, "-i",
                        TMPDIR + "/test/space/in:10"}));
        space.setEatPrefix(TMPDIR + "/test/space/in");
        space.setOutputRoot(TMPDIR + "/test/space/out");
        space.setCaseId("space1case");
        space.setLoop(false);
        space.setPauseTime(10);// millis
        space.setRetryStrategy(true);
        space.setDirectoryProcessing(false);
        space.setFPM(maxCount);
        space.setBPM(maxBytes);

        assertTrue("WorkSpace should exist in namespace", Namespace.exists("http://localhost:8005/" + namespace));

        assertEquals("No files proessed", 0, space.getFilesProcessed());
        assertEquals("No bytes proessed", 0, space.getBytesProcessed());
        assertEquals("No bundles proessed", 0, space.getBundlesProcessed());
        assertEquals("Outbound queue count in " + space.getKey(), 0, space.getOutboundQueueSize());

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
    public void testMaxFiles() throws Exception {
        createWorkspace("testMaxFiles", 2, -1);
        detachWorkspace("testMaxFiles");

        pause(500);

        assertEquals("files processed on " + space.getKey(), 3, space.getFilesProcessed());
        assertEquals("bytes processed on " + space.getKey(), 66, space.getBytesProcessed());
        assertEquals("bundles processed on " + space.getKey(), 2, space.getBundlesProcessed());
        assertEquals("Outbound queue count in " + space.getKey(), 2, space.getOutboundQueueSize());
    }

    @Test
    public void testMaxBytes() throws Exception {
        createWorkspace("testMaxBytes", -1, 10);
        detachWorkspace("testMaxBytes");

        pause(500);

        assertEquals("files processed on " + space.getKey(), 3, space.getFilesProcessed());
        assertEquals("bytes processed on " + space.getKey(), 66, space.getBytesProcessed());
        assertEquals("bundles processed on " + space.getKey(), 3, space.getBundlesProcessed());
        assertEquals("Outbound queue count in " + space.getKey(), 3, space.getOutboundQueueSize());
    }

    @Test
    public void testMaxBoth() throws Exception {
        createWorkspace("testMaxBoth", 3, 20);
        detachWorkspace("testMaxBoth");

        pause(1000);

        assertEquals("files processed on " + space.getKey(), 3, space.getFilesProcessed());
        assertEquals("bytes processed on " + space.getKey(), 66, space.getBytesProcessed());
        assertEquals("bundles processed on " + space.getKey(), 2, space.getBundlesProcessed());
        assertEquals("Outbound queue count in " + space.getKey(), 2, space.getOutboundQueueSize());
    }


    @SuppressWarnings("unused")
    // test class
    private static final class MyWorkSpace extends WorkSpace {
        public MyWorkSpace() throws Exception {
            super();
        }

        public MyWorkSpace(FeedCommand command) throws Exception {
            super(command);
        }

        public void setFPM(int value) {
            this.FILES_PER_MESSAGE = value;
        }

        public void setBPM(long value) {
            this.MAX_BUNDLE_SIZE = value;
        }

        public int getFPM() {
            return this.FILES_PER_MESSAGE;
        }

        public long getBPM() {
            return this.MAX_BUNDLE_SIZE;
        }
    }
}
