package emissary.util.roll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import emissary.test.core.junit5.UnitTest;
import emissary.util.io.FileNameGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests Rollable Output Stream.
 */
class RollableFileOutputStreamTest extends UnitTest implements FileNameGenerator {
    private static final String data = "some junk bytes";
    private static final int INT = 1;
    Path tmpDir;
    String currentFile;
    String lastFile;

    @BeforeEach
    public void setUp(@TempDir final Path tmpDir) throws Exception {
        super.setUp();
        this.tmpDir = tmpDir;
    }

    /**
     * Test handling of orphaned files
     */
    @Test
    void testHandleOrphanedFiles() throws Exception {
        // setup
        String file1name = this.nextFileName();
        String file2name = this.nextFileName();
        Path file1 = Paths.get(tmpDir.toString(), "." + file1name);
        Files.createFile(file1);
        Files.write(file1, "Foo".getBytes());
        Path file2 = Paths.get(tmpDir.toString(), "." + file2name);
        Files.createFile(file2);
        // test
        try (RollableFileOutputStream instance = new RollableFileOutputStream(this, tmpDir.toFile())) {
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
    void testRoll() throws Exception {

        try (RollableFileOutputStream instance = new RollableFileOutputStream(this, tmpDir.toFile())) {
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
    void testClose() throws Exception {
        RollableFileOutputStream instance = new RollableFileOutputStream(this, tmpDir.toFile());
        instance.close();
        assertTrue(instance.fileOutputStream == null && instance.currentFile == null);
    }

    /**
     * Test of write method, of class RollableFileOutputStream.
     */
    @Test
    void testWrite_int() throws Exception {
        try (RollableFileOutputStream instance = new RollableFileOutputStream(this, tmpDir.toFile())) {
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
    void testWriteBytes() throws Exception {
        try (RollableFileOutputStream instance = new RollableFileOutputStream(this, tmpDir.toFile())) {
            instance.write(data.getBytes(), 0, data.length());
        }
        Path f = Paths.get(tmpDir.toString(), currentFile);
        try (InputStream is = Files.newInputStream(f)) {
            int i = 0;
            for (; i < data.length(); i++) {
                is.read();
            }
            assertEquals(-1, is.read());
        }
    }

    /**
     * Test of write method, of class RollableFileOutputStream.
     */
    @Test
    void testWriteZeroBytes() throws Exception {
        try (RollableFileOutputStream instance = new RollableFileOutputStream(this, tmpDir.toFile())) {
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
