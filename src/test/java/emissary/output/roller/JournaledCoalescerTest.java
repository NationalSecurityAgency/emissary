package emissary.output.roller;

import static emissary.output.roller.JournaledCoalescer.ROLLING_EXT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import emissary.output.io.SimpleFileNameGenerator;
import emissary.output.roller.journal.Journal;
import emissary.output.roller.journal.JournalEntry;
import emissary.output.roller.journal.JournalReader;
import emissary.output.roller.journal.JournalWriter;
import emissary.output.roller.journal.JournaledChannelPool;
import emissary.output.roller.journal.KeyedOutput;
import emissary.test.core.junit5.UnitTest;
import emissary.util.io.FileNameGenerator;
import emissary.util.io.UnitTestFileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JournaledCoalescerTest extends UnitTest {

    private FileNameGenerator fileNameGenerator;
    private JournaledCoalescer journaledCoalescer;

    private Path targetBUDPath;
    private Path tempBUD1;
    private final String BUD1_NAME = "bud1";
    private Path tempBUD2;
    private final String BUD2_NAME = "bud2";
    private final List<String> BUD1_LINES = Arrays.asList("Line1", "Line2");
    private final List<String> BUD2_LINES = Arrays.asList("Line3", "Line4");

    @BeforeEach
    public void setUp(@TempDir final Path tempFilesPath) throws Exception {
        fileNameGenerator = new SimpleFileNameGenerator();
        targetBUDPath = tempFilesPath;
        journaledCoalescer = new JournaledCoalescer(targetBUDPath, fileNameGenerator);

        // setup temp files
        tempBUD1 = Files.createTempFile(targetBUDPath, BUD1_NAME, "");
        Files.write(tempBUD1, BUD1_LINES, Charset.defaultCharset(), StandardOpenOption.WRITE);

        tempBUD2 = Files.createTempFile(targetBUDPath, BUD2_NAME, "");
        Files.write(tempBUD2, BUD2_LINES, Charset.defaultCharset(), StandardOpenOption.WRITE);
    }

    @AfterEach
    public void cleanUp() throws IOException {
        journaledCoalescer.close();
        UnitTestFileUtils.cleanupDirectoryRecursively(targetBUDPath);
    }

    @SuppressWarnings("resource")
    @Test
    void testNonExistentDirectory() {
        // test
        assertThrows(FileNotFoundException.class, () -> new JournaledCoalescer(Paths.get("/asdasd/asdasd/asdasd"), fileNameGenerator));
    }

    @SuppressWarnings("resource")
    @Test
    void testNonDirectoryArgument() throws Exception {
        // test
        Path tmpFile = Files.createTempFile(temporaryDirectory.toPath(), ".", "temp-name");
        assertThrows(IllegalArgumentException.class, () -> new JournaledCoalescer(tmpFile, fileNameGenerator));
        Files.deleteIfExists(tmpFile);
    }

    @SuppressWarnings("resource")
    @Test
    void testNonReadable() throws Exception {
        // setup
        Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_WRITE);
        // test
        Path tmpdir = Files.createTempDirectory(temporaryDirectory.toPath(), "tmpdir", PosixFilePermissions.asFileAttribute(perms));
        assertThrows(IllegalAccessError.class, () -> new JournaledCoalescer(tmpdir, fileNameGenerator));
        perms.add(PosixFilePermission.OWNER_READ);
        Files.setPosixFilePermissions(tmpdir, perms);
        UnitTestFileUtils.cleanupDirectoryRecursively(tmpdir);
    }

    @SuppressWarnings("resource")
    @Test
    void testNonWritable() throws Exception {
        // setup
        Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_READ);

        // test
        Path tmpdir = Files.createTempDirectory(temporaryDirectory.toPath(), "tmpdir", PosixFilePermissions.asFileAttribute(perms));
        assertThrows(IllegalAccessError.class, () -> new JournaledCoalescer(tmpdir, fileNameGenerator));
        UnitTestFileUtils.cleanupDirectoryRecursively(tmpdir);
    }

    @Test
    void testRollOrphanedFiles() throws Exception {
        // setup
        try (JournaledChannelPool pool = new JournaledChannelPool(targetBUDPath, BUD1_NAME, 2);
                KeyedOutput one = pool.getFree();
                KeyedOutput two = pool.getFree()) {
            Files.copy(tempBUD1, one);
            Files.copy(tempBUD2, two);
            one.commit();
            two.commit();
        }

        // test
        journaledCoalescer.roll();

        // verify
        Path bud1destination = targetBUDPath.resolve(BUD1_NAME);
        assertTrue(Files.exists(bud1destination));
        List<String> fileResults = Files.readAllLines(bud1destination, Charset.defaultCharset());
        assertEquals(4, fileResults.size());
        assertTrue(fileResults.containsAll(Arrays.asList(BUD1_LINES.get(0), BUD1_LINES.get(1), BUD2_LINES.get(0), BUD2_LINES.get(1))));
    }

    @Test
    void testAddFilesBeforeRoll() throws Exception {
        // test
        Path bud1destination;
        Path bud2destination;
        try (KeyedOutput one = journaledCoalescer.getOutput(); KeyedOutput two = journaledCoalescer.getOutput()) {
            Files.copy(tempBUD1, one);
            bud1destination = one.getFinalDestination();
            Files.copy(tempBUD2, two);
            bud2destination = two.getFinalDestination();
            one.commit();
            two.commit();
        }
        // verify
        // no roll happened
        assertNotNull(bud1destination);
        assertEquals(bud1destination, bud2destination);
        assertFalse(Files.exists(bud1destination));

        // verify
        journaledCoalescer.roll();
        assertTrue(Files.exists(bud1destination));
        long totalSize = Files.size(tempBUD1) + Files.size(tempBUD2);
        assertEquals(totalSize, Files.size(bud1destination));

    }

    @Test
    void testAddFilesWithRoll() throws Exception {
        // setup
        String expectedPrefix1;

        // test
        try (KeyedOutput one = journaledCoalescer.getOutput()) {
            Files.copy(tempBUD1, one);
            expectedPrefix1 = one.getFinalDestination().getFileName().toString();
            one.commit();
        }

        journaledCoalescer.roll();

        String expectedPrefix2;

        try (KeyedOutput one = journaledCoalescer.getOutput()) {
            Files.copy(tempBUD2, one);
            expectedPrefix2 = one.getFinalDestination().getFileName().toString();
            one.commit();
        }
        // verify
        Path bud1Destination = targetBUDPath.resolve(expectedPrefix1);
        assertTrue(Files.exists(bud1Destination), "Expected target to exist " + bud1Destination);
        assertNotEquals(expectedPrefix2, bud1Destination.getFileName().toString());
        assertEquals(targetBUDPath, bud1Destination.getParent());
        assertEquals(expectedPrefix1, bud1Destination.getFileName().toString());
        assertEquals(BUD1_LINES, Files.readAllLines(bud1Destination, Charset.defaultCharset()));

        assertNotEquals(expectedPrefix1, expectedPrefix2);
    }

    /**
     * This test case tries to simulate a crash during the roll up. There would be a '.rolling' file present from the last
     * run, which should be deleted and normal operations carried out from there.
     */
    @Test
    void testCrashWhileRolling() throws Exception {
        // setup
        Path finalBudOutput;
        try (KeyedOutput one = journaledCoalescer.getOutput()) {
            Files.copy(tempBUD1, one);
            finalBudOutput = one.getFinalDestination();
            one.commit();
        }

        Path oldRolling = Paths.get(targetBUDPath.toString(), finalBudOutput.getFileName().toString() + ROLLING_EXT);
        Files.createFile(oldRolling);

        // test
        try (JournaledCoalescer jc = new JournaledCoalescer(targetBUDPath, fileNameGenerator)) {
            jc.roll();
        }

        // verify
        assertFalse(Files.exists(oldRolling));
        assertTrue(Files.exists(finalBudOutput));
        assertEquals(BUD1_LINES, Files.readAllLines(finalBudOutput, Charset.defaultCharset()));
    }

    /**
     * Test to see the rolled and part files are cleaned up without rolling again.
     */
    @Test
    void testCrashAfterRolled() throws Exception {
        try (JournaledChannelPool pool = new JournaledChannelPool(targetBUDPath, BUD1_NAME, 2);
                KeyedOutput one = pool.getFree();
                KeyedOutput two = pool.getFree()) {
            Files.copy(tempBUD1, one);
            Files.copy(tempBUD2, two);
            one.commit();
            two.commit();
        }

        // create the rolled file
        Path oldRolled = Files.createFile(targetBUDPath.resolve(BUD1_NAME + JournaledCoalescer.ROLLED_EXT));
        Files.write(oldRolled, BUD1_LINES, Charset.defaultCharset(), StandardOpenOption.WRITE);

        try (JournaledCoalescer jrnl = new JournaledCoalescer(targetBUDPath, fileNameGenerator)) {
            assertTrue(Files.exists(oldRolled));
            assertFalse(Files.exists(targetBUDPath.resolve(BUD1_NAME)));
            jrnl.roll();
        }

        assertFalse(Files.exists(oldRolled));
        assertTrue(Files.exists(targetBUDPath.resolve(BUD1_NAME)));
    }

    /**
     * Test to make sure an orphaned rolled file is cleaned up. This can happen during a crash when the part/journal files
     * are deleted, but the rolled file is not renamed.
     */
    @Test
    void testCrashAfterRolledNoPartFiles() throws Exception {
        // create the rolled file without any part/journal files
        Path oldRolled = Files.createFile(targetBUDPath.resolve(BUD1_NAME + JournaledCoalescer.ROLLED_EXT));
        Files.write(oldRolled, BUD1_LINES, Charset.defaultCharset(), StandardOpenOption.WRITE);

        new JournaledCoalescer(targetBUDPath, fileNameGenerator).close();

        // verify orphaned rolled file is cleaned up
        assertFalse(Files.exists(oldRolled));
        assertTrue(Files.exists(targetBUDPath.resolve(BUD1_NAME)));
    }

    @Test
    void testCrashAfterRolledEmpty() throws Exception {
        try (JournaledChannelPool pool = new JournaledChannelPool(targetBUDPath, BUD1_NAME, 2);
                KeyedOutput one = pool.getFree();
                KeyedOutput two = pool.getFree()) {
            one.commit();
            two.commit();
        }

        Path oldRolled = Files.createFile(targetBUDPath.resolve(BUD1_NAME + JournaledCoalescer.ROLLED_EXT));

        try (JournaledCoalescer jrnl = new JournaledCoalescer(targetBUDPath, fileNameGenerator)) {
            assertTrue(Files.exists(oldRolled));
            assertFalse(Files.exists(targetBUDPath.resolve(BUD1_NAME)));
            jrnl.roll();
        }

        assertFalse(Files.exists(oldRolled));
        assertFalse(Files.exists(targetBUDPath.resolve(BUD1_NAME)));
    }

    @Test
    void testCrashAfterRolledEmptyNoPartFiles() throws Exception {
        // create the rolled file without any part/journal files
        Path oldRolled = Files.createFile(targetBUDPath.resolve(BUD1_NAME + JournaledCoalescer.ROLLED_EXT));

        new JournaledCoalescer(targetBUDPath, fileNameGenerator).close();

        // verify orphaned rolled file is cleaned up
        assertFalse(Files.exists(oldRolled));
        assertFalse(Files.exists(targetBUDPath.resolve(BUD1_NAME)));
    }

    @Test
    void testRollEmptyFiles() throws Exception {
        // setup
        Files.write(tempBUD1, new byte[] {}, StandardOpenOption.TRUNCATE_EXISTING);
        Path finalBudOutput;
        try (KeyedOutput os = journaledCoalescer.getOutput()) {
            // make sure we create an empty file
            finalBudOutput = os.getFinalDestination();
        }
        // test
        journaledCoalescer.roll();

        // verify
        assertFalse(Files.exists(finalBudOutput));
    }

    @Test
    void testRollBadFiles() throws Exception {
        // setup
        Path target = Files.createTempFile(targetBUDPath, "badfile", "");
        String key = target.toString();
        try (JournalWriter jw = new JournalWriter(targetBUDPath, key);
                SeekableByteChannel c = Files.newByteChannel(target, StandardOpenOption.WRITE)) {
            ArrayList<String> strings = new ArrayList<>(BUD1_LINES);
            strings.addAll(BUD2_LINES);
            for (String line : strings) {
                c.write(ByteBuffer.wrap(line.getBytes(UTF_8)));
                jw.write(new JournalEntry(key, c.position()));
            }
            // phony write
            jw.write(new JournalEntry(key, c.position() + 1000));

        }
        long partSize = Files.size(target);
        // open journal
        Journal j;
        try (JournalReader jr = new JournalReader(Paths.get(key + Journal.EXT))) {
            j = jr.getJournal();
        }
        Path rolled = Files.createTempFile(targetBUDPath, "rolled_badfile", "");
        try (SeekableByteChannel sbc = Files.newByteChannel(rolled, StandardOpenOption.WRITE)) {
            journaledCoalescer.combineFiles(j, sbc);
        }

        long rolledSize = Files.size(rolled);

        // verify
        assertEquals(partSize, rolledSize);
        assertTrue(Objects.requireNonNull(j.getLastEntry()).getOffset() > partSize);
    }

}
