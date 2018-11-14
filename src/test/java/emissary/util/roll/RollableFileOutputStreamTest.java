package emissary.util.roll;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.UUID;

import emissary.test.core.UnitTest;
import emissary.util.io.FileNameGenerator;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests Rollable Output Stream.
 */
public class RollableFileOutputStreamTest extends UnitTest implements FileNameGenerator {
    private static final String data = "some junk bytes";
    private static final int INT = 1;
    File tmpDir;
    String currentFile;
    String lastFile;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        tmpDir = Files.createTempDirectory("emissary_rfost_test").toFile();
        tmpDir.deleteOnExit();
    }

    /**
     * Test handling of orphaned files
     */
    @Test
    public void testHandleOrphanedFiles() throws Exception {
        // setup
        String file1name = this.nextFileName();
        String file2name = this.nextFileName();
        File file1 = new File(tmpDir, "." + file1name);
        file1.createNewFile();
        FileWriter fw = new FileWriter(file1);
        fw.write("Foo");
        fw.flush();
        fw.close();
        File file2 = new File(tmpDir, "." + file2name);
        file2.createNewFile();
        // test
        try (RollableFileOutputStream instance = new RollableFileOutputStream(this, tmpDir)) {
        }

        // verify
        assertThat(file1.exists(), equalTo(false));
        assertThat(new File(tmpDir, file1name).exists(), equalTo(true));
        // make sure 0 byte file was deleted
        assertThat(file2.exists(), equalTo(false));
        assertThat(new File(tmpDir, file2name).exists(), equalTo(false));
    }

    /**
     * Test of roll method, of class RollableFileOutputStream.
     */
    @Test
    public void testRoll() throws Exception {

        try (RollableFileOutputStream instance = new RollableFileOutputStream(this, tmpDir)) {
            instance.write(data.getBytes());
            assertTrue(data.length() == instance.bytesWritten);
            instance.roll();
            assertTrue(new File(tmpDir, lastFile).exists());
            assertTrue(new File(tmpDir, "." + currentFile).exists());
            assertTrue(0L == instance.bytesWritten);
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
        File f = new File(tmpDir, currentFile);
        try (FileInputStream is = new FileInputStream(f)) {
            assertTrue(is.read() == INT);
            assertTrue(is.read() == -1);
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
        File f = new File(tmpDir, currentFile);
        try (FileInputStream is = new FileInputStream(f)) {
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
        File f = new File(tmpDir, currentFile);
        assertTrue(!f.exists());
    }

    @Override
    public String nextFileName() {
        lastFile = currentFile;
        currentFile = UUID.randomUUID().toString();
        return currentFile;
    }

}
