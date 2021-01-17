package emissary.util.roll;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import emissary.test.core.UnitTest;
import emissary.util.io.FileNameGenerator;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests Rollable Output Stream.
 */
public class RollableFileOutputStreamTest extends UnitTest implements FileNameGenerator {
    private static final String data = "some junk bytes";
    private static final int INT = 1;
    Path tmpDir;
    String currentFile;
    String lastFile;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        tmpDir = Files.createTempDirectory("emissary_rfost_test");
        tmpDir.toFile().deleteOnExit();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        FileUtils.deleteDirectory(tmpDir.toFile());
    }

    /**
     * Test handling of orphaned files
     */
    @Test
    public void testHandleOrphanedFiles() throws Exception {
        // setup
        String file1name = this.nextFileName();
        String file2name = this.nextFileName();
        Path file1 = Paths.get(tmpDir.toString(), "." + file1name);
        Files.createFile(file1);
        Files.write(file1, "Foo".getBytes());
        Path file2 = Paths.get(tmpDir.toString(), "." + file2name);
        Files.createFile(file2);
        // test
        try (RollableFileOutputStream instance = new RollableFileOutputStream(this, tmpDir)) {
        }

        // verify
        assertFalse(Files.exists(file1));
        assertTrue(Files.exists(Paths.get(tmpDir.toString(), file1name)));
        // make sure 0 byte file was deleted
        assertFalse(Files.exists(file2));
        assertFalse(Files.exists(Paths.get(tmpDir.toString(), file2name)));
    }

    /**
     * Test of roll method, of class RollableFileOutputStream.
     */
    @Test
    public void testRoll() throws Exception {

        try (RollableFileOutputStream instance = new RollableFileOutputStream(this, tmpDir)) {
            instance.write(data.getBytes());
            assertEquals(data.length(), instance.bytesWritten);
            instance.roll();
            assertTrue(Files.exists(Paths.get(tmpDir.toString(), lastFile)));
            assertTrue(Files.exists(Paths.get(tmpDir.toString(), "." + currentFile)));
            assertEquals(0L, instance.bytesWritten);
        }
    }

    /**
     * Test of close method, of class RollableFileOutputStream.
     */
    @Test
    public void testClose() throws Exception {
        RollableFileOutputStream instance = new RollableFileOutputStream(this, tmpDir);
        instance.close();
        assertTrue(instance.fileOutputStream == null && instance.currentFile == null);
    }

    /**
     * Test of write method, of class RollableFileOutputStream.
     */
    @Test
    public void testWrite_int() throws Exception {
        try (RollableFileOutputStream instance = new RollableFileOutputStream(this, tmpDir)) {
            instance.write(INT);
        }
        Path f = Paths.get(tmpDir.toString(), currentFile);
        try (InputStream is = Files.newInputStream(f)) {
            assertEquals(INT, is.read());
            assertEquals(-1, is.read());
        }
    }

    /**
     * Test of write method, of class RollableFileOutputStream.
     */
    @Test
    public void testWriteBytes() throws Exception {
        try (RollableFileOutputStream instance = new RollableFileOutputStream(this, tmpDir)) {
            instance.write(data.getBytes(), 0, data.length());
        }
        Path f = Paths.get(tmpDir.toString(), currentFile);
        try (InputStream is = Files.newInputStream(f)) {
            int i = 0;
            for (; i < data.length(); i++) {
                is.read();
            }
            assertTrue(is.read() == -1);
        }
    }

    /**
     * Test of write method, of class RollableFileOutputStream.
     */
    @Test
    public void testWriteZeroBytes() throws Exception {
        try (RollableFileOutputStream instance = new RollableFileOutputStream(this, tmpDir)) {
            // set to true by default...
            instance.setDeleteZeroByteFiles(true);
            instance.roll();
        }
        Path f = Paths.get(tmpDir.toString(), currentFile);
        assertFalse(Files.exists(f));
    }

    @Override
    public String nextFileName() {
        lastFile = currentFile;
        currentFile = UUID.randomUUID().toString();
        return currentFile;
    }

}
