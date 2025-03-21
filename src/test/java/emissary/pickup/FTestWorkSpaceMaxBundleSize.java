package emissary.pickup;

import emissary.command.FeedCommand;
import emissary.core.Namespace;
import emissary.directory.EmissaryNode;
import emissary.directory.IDirectoryPlace;
import emissary.test.core.junit5.FunctionalTest;

import jakarta.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FTestWorkSpaceMaxBundleSize extends FunctionalTest {
    @Nullable
    private MyWorkSpace space = null;
    @Nullable
    private IDirectoryPlace peer = null;

    // Workspace input and output directories
    private File inarea1;
    private File inareadir1;
    private File outarea1;
    private File holdarea;

    private final List<File> workingFiles = new ArrayList<>();
    private final List<String> workingFilePaths = new ArrayList<>();

    @Override
    @BeforeEach
    public void setUp() throws Exception {
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
    @AfterEach
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
        space.setFpm(maxCount);
        space.setBpm(maxBytes);

        assertTrue(Namespace.exists("http://localhost:8005/" + namespace), "WorkSpace should exist in namespace");

        assertEquals(0, space.getFilesProcessed(), "No files proessed");
        assertEquals(0, space.getBytesProcessed(), "No bytes proessed");
        assertEquals(0, space.getBundlesProcessed(), "No bundles proessed");
        assertEquals(0, space.getOutboundQueueSize(), "Outbound queue count in " + space.getKey());

    }

    @SuppressWarnings("ThreadPriorityCheck")
    private void detachWorkspace(String threadName) {
        // Create a thread and run the workspace detached
        Thread tspacethr = new Thread(space, threadName);
        tspacethr.setDaemon(false);
        tspacethr.start();
        logger.debug("WorkSpace is started and detached on thread " + threadName);
        Thread.yield();
    }


    @Test
    void testMaxFiles() throws Exception {
        createWorkspace("testMaxFiles", 2, -1);
        detachWorkspace("testMaxFiles");

        pause(500);

        assertEquals(3, space.getFilesProcessed(), "files processed on " + space.getKey());
        assertEquals(66, space.getBytesProcessed(), "bytes processed on " + space.getKey());
        assertEquals(2, space.getBundlesProcessed(), "bundles processed on " + space.getKey());
        assertEquals(2, space.getOutboundQueueSize(), "Outbound queue count in " + space.getKey());
    }

    @Test
    void testMaxBytes() throws Exception {
        createWorkspace("testMaxBytes", -1, 10);
        detachWorkspace("testMaxBytes");

        pause(500);

        assertEquals(3, space.getFilesProcessed(), "files processed on " + space.getKey());
        assertEquals(66, space.getBytesProcessed(), "bytes processed on " + space.getKey());
        assertEquals(3, space.getBundlesProcessed(), "bundles processed on " + space.getKey());
        assertEquals(3, space.getOutboundQueueSize(), "Outbound queue count in " + space.getKey());
    }

    @Test
    void testMaxBoth() throws Exception {
        createWorkspace("testMaxBoth", 3, 20);
        detachWorkspace("testMaxBoth");

        pause(1000);

        assertEquals(3, space.getFilesProcessed(), "files processed on " + space.getKey());
        assertEquals(66, space.getBytesProcessed(), "bytes processed on " + space.getKey());
        assertEquals(2, space.getBundlesProcessed(), "bundles processed on " + space.getKey());
        assertEquals(2, space.getOutboundQueueSize(), "Outbound queue count in " + space.getKey());
    }


    @SuppressWarnings("unused")
    // test class
    private static final class MyWorkSpace extends WorkSpace {
        public MyWorkSpace() throws Exception {
            super();
        }

        public MyWorkSpace(FeedCommand command) {
            super(command);
        }

        public void setFpm(int value) {
            this.filesPerMessage = value;
        }

        public void setBpm(long value) {
            this.maxBundleSize = value;
        }

        public int getFpm() {
            return this.filesPerMessage;
        }

        public long getBpm() {
            return this.maxBundleSize;
        }
    }
}
