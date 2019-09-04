package emissary.output.roller;

import static emissary.output.roller.JournaledCoalescer.ROLLING_EXT;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.collection.IsIterableContainingInRelativeOrder.containsInRelativeOrder;
import static org.junit.Assert.assertThat;

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
import java.util.Set;

import emissary.output.io.SimpleFileNameGenerator;
import emissary.output.roller.journal.Journal;
import emissary.output.roller.journal.JournalEntry;
import emissary.output.roller.journal.JournalReader;
import emissary.output.roller.journal.JournalWriter;
import emissary.output.roller.journal.JournaledChannelPool;
import emissary.output.roller.journal.KeyedOutput;
import emissary.test.core.UnitTest;
import emissary.util.io.FileNameGenerator;
import emissary.util.io.UnitTestFileUtils;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class JournaledCoalescerTest extends UnitTest {
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private FileNameGenerator fileNameGenerator;
    private JournaledCoalescer journaledCoalescer;

    private Path targetBUDPath;
    private Path tempBUD1;
    private final String BUD1_NAME = "bud1";
    private Path tempBUD2;
    private final String BUD2_NAME = "bud2";
    private final List<String> BUD1_LINES = Arrays.asList("Line1", "Line2");
    private final List<String> BUD2_LINES = Arrays.asList("Line3", "Line4");

    @Before
    @Override
    public void setUp() throws Exception {
        fileNameGenerator = new SimpleFileNameGenerator();
        targetBUDPath = Files.createTempDirectory("temp-files");
        journaledCoalescer = new JournaledCoalescer(targetBUDPath, fileNameGenerator);

        // setup temp files
        tempBUD1 = Files.createTempFile(targetBUDPath, BUD1_NAME, "");
        Files.write(tempBUD1, BUD1_LINES, Charset.defaultCharset(), StandardOpenOption.WRITE);

        tempBUD2 = Files.createTempFile(targetBUDPath, BUD2_NAME, "");
        Files.write(tempBUD2, BUD2_LINES, Charset.defaultCharset(), StandardOpenOption.WRITE);
    }

    @After
    public void cleanUp() throws IOException {
        journaledCoalescer.close();
        UnitTestFileUtils.cleanupDirectoryRecursively(targetBUDPath);
    }

    @SuppressWarnings("resource")
    @Test(expected = FileNotFoundException.class)
    public void testNonExistentDirectory() throws Exception {
        // test
        new JournaledCoalescer(Paths.get("/asdasd/asdasd/asdasd"), fileNameGenerator);
    }

    @SuppressWarnings("resource")
    @Test(expected = IllegalArgumentException.class)
    public void testNonDirectoryArgument() throws Exception {
        // test
        new JournaledCoalescer(Files.createTempFile(".", "temp-name"), fileNameGenerator);
    }

    @SuppressWarnings("resource")
    @Test(expected = IllegalAccessError.class)
    public void testNonReadible() throws Exception {
        // setup
        Set<PosixFilePermission> perms = new HashSet<PosixFilePermission>();
        perms.add(PosixFilePermission.OWNER_WRITE);

        // test
        new JournaledCoalescer(Files.createTempDirectory("tmpdir", PosixFilePermissions.asFileAttribute(perms)), fileNameGenerator);
    }

    @SuppressWarnings("resource")
    @Test(expected = IllegalAccessError.class)
    public void testNonWritable() throws Exception {
        // setup
        Set<PosixFilePermission> perms = new HashSet<PosixFilePermission>();
        perms.add(PosixFilePermission.OWNER_READ);

        // test
        new JournaledCoalescer(Files.createTempDirectory("tmpdir", PosixFilePermissions.asFileAttribute(perms)), fileNameGenerator);
    }

    @Test
    public void testRollOrphanedFiles() throws Exception {
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
        assertThat(Files.exists(bud1destination), equalTo(true));
        List<String> fileResults = Files.readAllLines(bud1destination, Charset.defaultCharset());
        assertThat("Expected 4 lines in " + bud1destination, fileResults.size(), equalTo(4));
        assertThat(fileResults, containsInRelativeOrder(BUD1_LINES.get(0), BUD1_LINES.get(1)));
        assertThat(fileResults, containsInRelativeOrder(BUD2_LINES.get(0), BUD2_LINES.get(1)));
    }

    @Test
    public void testAddFilesBeforeRoll() throws Exception {
        // test
        Path bud1destination = null;
        Path bud2destination = null;
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
        assertThat(bud1destination, notNullValue());
        assertThat(bud1destination, equalTo(bud2destination));
        assertThat(Files.exists(bud1destination), equalTo(false));

        // verify
        journaledCoalescer.roll();
        assertThat(Files.exists(bud1destination), equalTo(true));
        long totalSize = Files.size(tempBUD1) + Files.size(tempBUD2);
        assertThat(Files.size(bud1destination), equalTo(totalSize));

    }

    @Test
    public void testAddFilesWithRoll() throws Exception {
        // setup
        String expectedPrefix1 = null;

        // test
        try (KeyedOutput one = journaledCoalescer.getOutput()) {
            Files.copy(tempBUD1, one);
            expectedPrefix1 = one.getFinalDestination().getFileName().toString();
            one.commit();
        }

        journaledCoalescer.roll();

        String expectedPrefix2 = null;

        try (KeyedOutput one = journaledCoalescer.getOutput()) {
            Files.copy(tempBUD2, one);
            expectedPrefix2 = one.getFinalDestination().getFileName().toString();
            one.commit();
        }
        // verify
        Path bud1Destination = targetBUDPath.resolve(expectedPrefix1);
        assertThat("Expected target to exist " + bud1Destination.toString(), Files.exists(bud1Destination), equalTo(true));
        assertThat(bud1Destination.getFileName().toString(), not(equalTo(expectedPrefix2)));
        assertThat(bud1Destination.getParent(), equalTo(targetBUDPath));
        assertThat(bud1Destination.getFileName().toString(), equalTo(expectedPrefix1));
        assertThat(Files.readAllLines(bud1Destination, Charset.defaultCharset()), equalTo(BUD1_LINES));

        assertThat(expectedPrefix1, not(equalTo(expectedPrefix2)));
    }

    /**
     * This test case tries to simulate a crash during the roll up. There would be a '.rolling' file present from the last
     * run, which should be deleted and normal operations carried out from there.
     */
    @Test
    public void testCrashWhileRolling() throws Exception {
        // setup
        Path finalBudOutput = null;
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
        assertThat(Files.exists(oldRolling), equalTo(false));
        assertThat(Files.exists(finalBudOutput), equalTo(true));
        assertThat(Files.readAllLines(finalBudOutput, Charset.defaultCharset()), equalTo(BUD1_LINES));
    }

    /**
     * Test to see the rolled and part files are cleaned up without rolling again.
     */
    @Test
    public void testCrashAfterRolled() throws Exception {
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
            assertThat(Files.exists(oldRolled), equalTo(true));
            assertThat(Files.exists(targetBUDPath.resolve(BUD1_NAME)), equalTo(false));
            jrnl.roll();
        }

        assertThat(Files.exists(oldRolled), equalTo(false));
        assertThat(Files.exists(targetBUDPath.resolve(BUD1_NAME)), equalTo(true));
    }

    /**
     * Test to make sure an orphaned rolled file is cleaned up. This can happen during a crash when the part/journal files
     * are deleted, but the rolled file is not renamed.
     */
    @Test
    public void testCrashAfterRolledNoPartFiles() throws Exception {
        // create the rolled file without any part/journal files
        Path oldRolled = Files.createFile(targetBUDPath.resolve(BUD1_NAME + JournaledCoalescer.ROLLED_EXT));
        Files.write(oldRolled, BUD1_LINES, Charset.defaultCharset(), StandardOpenOption.WRITE);

        new JournaledCoalescer(targetBUDPath, fileNameGenerator).close();

        // verify orphaned rolled file is cleaned up
        assertThat(Files.exists(oldRolled), equalTo(false));
        assertThat(Files.exists(targetBUDPath.resolve(BUD1_NAME)), equalTo(true));
    }

    @Test
    public void testCrashAfterRolledEmpty() throws Exception {
        try (JournaledChannelPool pool = new JournaledChannelPool(targetBUDPath, BUD1_NAME, 2);
                KeyedOutput one = pool.getFree();
                KeyedOutput two = pool.getFree()) {
            one.commit();
            two.commit();
        }

        Path oldRolled = Files.createFile(targetBUDPath.resolve(BUD1_NAME + JournaledCoalescer.ROLLED_EXT));

        try (JournaledCoalescer jrnl = new JournaledCoalescer(targetBUDPath, fileNameGenerator)) {
            assertThat(Files.exists(oldRolled), equalTo(true));
            assertThat(Files.exists(targetBUDPath.resolve(BUD1_NAME)), equalTo(false));
            jrnl.roll();
        }

        assertThat(Files.exists(oldRolled), equalTo(false));
        assertThat(Files.exists(targetBUDPath.resolve(BUD1_NAME)), equalTo(false));
    }

    @Test
    public void testCrashAfterRolledEmptyNoPartFiles() throws Exception {
        // create the rolled file without any part/journal files
        Path oldRolled = Files.createFile(targetBUDPath.resolve(BUD1_NAME + JournaledCoalescer.ROLLED_EXT));

        new JournaledCoalescer(targetBUDPath, fileNameGenerator).close();

        // verify orphaned rolled file is cleaned up
        assertThat(Files.exists(oldRolled), equalTo(false));
        assertThat(Files.exists(targetBUDPath.resolve(BUD1_NAME)), equalTo(false));
    }

    @Test
    public void testRollEmptyFiles() throws Exception {
        // setup
        Files.write(tempBUD1, new byte[] {}, StandardOpenOption.TRUNCATE_EXISTING);
        Path finalBudOutput = null;
        try (KeyedOutput os = journaledCoalescer.getOutput()) {
            // make sure we create an empty file
            finalBudOutput = os.getFinalDestination();
        }
        // test
        journaledCoalescer.roll();

        // verify
        assertThat(Files.exists(finalBudOutput), equalTo(false));
    }

    @Test
    public void testRollBadFiles() throws Exception {
        // setup
        Path target = Files.createTempFile(targetBUDPath, "badfile", "");
        String key = target.toString();
        try (JournalWriter jw = new JournalWriter(targetBUDPath, key);
                SeekableByteChannel c = Files.newByteChannel(target, StandardOpenOption.WRITE)) {
            ArrayList<String> strings = new ArrayList<>(BUD1_LINES);
            strings.addAll(BUD2_LINES);
            for (String line : strings) {
                c.write(ByteBuffer.wrap(line.getBytes()));
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
        assertThat(rolledSize, equalTo(partSize));
        assertThat(j.getLastEntry().getOffset(), Matchers.greaterThan(partSize));
    }

}
