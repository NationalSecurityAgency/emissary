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

    private Path targetKRYOPath;
    private Path tempKRYO1;
    private final String KRYO1_NAME = "kryo1";
    private Path tempKRYO2;
    private final String KRYO2_NAME = "kryo2";
    private final List<String> KRYO1_LINES = Arrays.asList("Line1", "Line2");
    private final List<String> KRYO2_LINES = Arrays.asList("Line3", "Line4");

    @Before
    @Override
    public void setUp() throws Exception {
        fileNameGenerator = new SimpleFileNameGenerator();
        targetKRYOPath = Files.createTempDirectory("temp-files");
        journaledCoalescer = new JournaledCoalescer(targetKRYOPath, fileNameGenerator);

        // setup temp files
        tempKRYO1 = Files.createTempFile(targetKRYOPath, KRYO1_NAME, "");
        Files.write(tempKRYO1, KRYO1_LINES, Charset.defaultCharset(), StandardOpenOption.WRITE);

        tempKRYO2 = Files.createTempFile(targetKRYOPath, KRYO2_NAME, "");
        Files.write(tempKRYO2, KRYO2_LINES, Charset.defaultCharset(), StandardOpenOption.WRITE);
    }

    @After
    public void cleanUp() throws IOException {
        journaledCoalescer.close();
        UnitTestFileUtils.cleanupDirectoryRecursively(targetKRYOPath);
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
        try (JournaledChannelPool pool = new JournaledChannelPool(targetKRYOPath, KRYO1_NAME, 2);
                KeyedOutput one = pool.getFree();
                KeyedOutput two = pool.getFree()) {
            Files.copy(tempKRYO1, one);
            Files.copy(tempKRYO2, two);
            one.commit();
            two.commit();
        }

        // test
        journaledCoalescer.roll();

        // verify
        Path kryo1destination = targetKRYOPath.resolve(KRYO1_NAME);
        assertThat(Files.exists(kryo1destination), equalTo(true));
        List<String> fileResults = Files.readAllLines(kryo1destination, Charset.defaultCharset());
        assertThat("Expected 4 lines in " + kryo1destination, fileResults.size(), equalTo(4));
        assertThat(fileResults, containsInRelativeOrder(KRYO1_LINES.get(0), KRYO1_LINES.get(1)));
        assertThat(fileResults, containsInRelativeOrder(KRYO2_LINES.get(0), KRYO2_LINES.get(1)));
    }

    @Test
    public void testAddFilesBeforeRoll() throws Exception {
        // test
        Path kryo1destination = null;
        Path kryo2destination = null;
        try (KeyedOutput one = journaledCoalescer.getOutput(); KeyedOutput two = journaledCoalescer.getOutput()) {
            Files.copy(tempKRYO1, one);
            kryo1destination = one.getFinalDestination();
            Files.copy(tempKRYO2, two);
            kryo2destination = two.getFinalDestination();
            one.commit();
            two.commit();
        }
        // verify
        // no roll happened
        assertThat(kryo1destination, notNullValue());
        assertThat(kryo1destination, equalTo(kryo2destination));
        assertThat(Files.exists(kryo1destination), equalTo(false));

        // verify
        journaledCoalescer.roll();
        assertThat(Files.exists(kryo1destination), equalTo(true));
        long totalSize = Files.size(tempKRYO1) + Files.size(tempKRYO2);
        assertThat(Files.size(kryo1destination), equalTo(totalSize));

    }

    @Test
    public void testAddFilesWithRoll() throws Exception {
        // setup
        String expectedPrefix1 = null;

        // test
        try (KeyedOutput one = journaledCoalescer.getOutput()) {
            Files.copy(tempKRYO1, one);
            expectedPrefix1 = one.getFinalDestination().getFileName().toString();
            one.commit();
        }

        journaledCoalescer.roll();

        String expectedPrefix2 = null;

        try (KeyedOutput one = journaledCoalescer.getOutput()) {
            Files.copy(tempKRYO2, one);
            expectedPrefix2 = one.getFinalDestination().getFileName().toString();
            one.commit();
        }
        // verify
        Path kryo1Destination = targetKRYOPath.resolve(expectedPrefix1);
        assertThat("Expected target to exist " + kryo1Destination.toString(), Files.exists(kryo1Destination), equalTo(true));
        assertThat(kryo1Destination.getFileName().toString(), not(equalTo(expectedPrefix2)));
        assertThat(kryo1Destination.getParent(), equalTo(targetKRYOPath));
        assertThat(kryo1Destination.getFileName().toString(), equalTo(expectedPrefix1));
        assertThat(Files.readAllLines(kryo1Destination, Charset.defaultCharset()), equalTo(KRYO1_LINES));

        assertThat(expectedPrefix1, not(equalTo(expectedPrefix2)));
    }

    /**
     * This test case tries to simulate a crash during the roll up. There would be a '.rolling' file present from the last
     * run, which should be deleted and normal operations carried out from there.
     */
    @SuppressWarnings("resource")
    @Test
    public void testCrashWhileRolling() throws Exception {
        // setup
        Path finalKryoOutput = null;
        try (KeyedOutput one = journaledCoalescer.getOutput()) {
            Files.copy(tempKRYO1, one);
            finalKryoOutput = one.getFinalDestination();
            one.commit();
        }

        Path oldRolling = Paths.get(targetKRYOPath.toString(), finalKryoOutput.getFileName().toString() + ROLLING_EXT);
        Files.createFile(oldRolling);

        // test
        new JournaledCoalescer(targetKRYOPath, fileNameGenerator).roll();

        // verify
        assertThat(Files.exists(oldRolling), equalTo(false));
        assertThat(Files.exists(finalKryoOutput), equalTo(true));
        assertThat(Files.readAllLines(finalKryoOutput, Charset.defaultCharset()), equalTo(KRYO1_LINES));
    }

    /**
     * Test to see the rolled and part files are cleaned up without rolling again.
     */
    @Test
    public void testCrashAfterRolled() throws Exception {
        try (JournaledChannelPool pool = new JournaledChannelPool(targetKRYOPath, KRYO1_NAME, 2);
                KeyedOutput one = pool.getFree();
                KeyedOutput two = pool.getFree()) {
            Files.copy(tempKRYO1, one);
            Files.copy(tempKRYO2, two);
            one.commit();
            two.commit();
        }

        // create the rolled file
        Path oldRolled = Files.createFile(targetKRYOPath.resolve(KRYO1_NAME + JournaledCoalescer.ROLLED_EXT));
        Files.write(oldRolled, KRYO1_LINES, Charset.defaultCharset(), StandardOpenOption.WRITE);

        JournaledCoalescer jrnl = new JournaledCoalescer(targetKRYOPath, fileNameGenerator);

        assertThat(Files.exists(oldRolled), equalTo(true));
        assertThat(Files.exists(targetKRYOPath.resolve(KRYO1_NAME)), equalTo(false));

        jrnl.roll();

        assertThat(Files.exists(oldRolled), equalTo(false));
        assertThat(Files.exists(targetKRYOPath.resolve(KRYO1_NAME)), equalTo(true));
    }

    /**
     * Test to make sure an orphaned rolled file is cleaned up. This can happen during a crash when the part/journal files
     * are deleted, but the rolled file is not renamed.
     */
    @Test
    public void testCrashAfterRolledNoPartFiles() throws Exception {
        // create the rolled file without any part/journal files
        Path oldRolled = Files.createFile(targetKRYOPath.resolve(KRYO1_NAME + JournaledCoalescer.ROLLED_EXT));
        Files.write(oldRolled, KRYO1_LINES, Charset.defaultCharset(), StandardOpenOption.WRITE);

        new JournaledCoalescer(targetKRYOPath, fileNameGenerator);

        // verify orphaned rolled file is cleaned up
        assertThat(Files.exists(oldRolled), equalTo(false));
        assertThat(Files.exists(targetKRYOPath.resolve(KRYO1_NAME)), equalTo(true));
    }

    @Test
    public void testCrashAfterRolledEmpty() throws Exception {
        try (JournaledChannelPool pool = new JournaledChannelPool(targetKRYOPath, KRYO1_NAME, 2);
                KeyedOutput one = pool.getFree();
                KeyedOutput two = pool.getFree()) {
            one.commit();
            two.commit();
        }

        Path oldRolled = Files.createFile(targetKRYOPath.resolve(KRYO1_NAME + JournaledCoalescer.ROLLED_EXT));

        JournaledCoalescer jrnl = new JournaledCoalescer(targetKRYOPath, fileNameGenerator);

        assertThat(Files.exists(oldRolled), equalTo(true));
        assertThat(Files.exists(targetKRYOPath.resolve(KRYO1_NAME)), equalTo(false));

        jrnl.roll();

        assertThat(Files.exists(oldRolled), equalTo(false));
        assertThat(Files.exists(targetKRYOPath.resolve(KRYO1_NAME)), equalTo(false));
    }

    @Test
    public void testCrashAfterRolledEmptyNoPartFiles() throws Exception {
        // create the rolled file without any part/journal files
        Path oldRolled = Files.createFile(targetKRYOPath.resolve(KRYO1_NAME + JournaledCoalescer.ROLLED_EXT));

        new JournaledCoalescer(targetKRYOPath, fileNameGenerator);

        // verify orphaned rolled file is cleaned up
        assertThat(Files.exists(oldRolled), equalTo(false));
        assertThat(Files.exists(targetKRYOPath.resolve(KRYO1_NAME)), equalTo(false));
    }

    @Test
    public void testRollEmptyFiles() throws Exception {
        // setup
        Files.write(tempKRYO1, new byte[] {}, StandardOpenOption.TRUNCATE_EXISTING);
        Path finalKryoOutput = null;
        try (KeyedOutput os = journaledCoalescer.getOutput()) {
            // make sure we create an empty file
            finalKryoOutput = os.getFinalDestination();
        }
        // test
        journaledCoalescer.roll();

        // verify
        assertThat(Files.exists(finalKryoOutput), equalTo(false));
    }

    @Test
    public void testRollBadFiles() throws Exception {
        // setup
        Path target = Files.createTempFile(targetKRYOPath, "badfile", "");
        String key = target.toString();
        try (JournalWriter jw = new JournalWriter(targetKRYOPath, key);
                SeekableByteChannel c = Files.newByteChannel(target, StandardOpenOption.WRITE)) {
            ArrayList<String> strings = new ArrayList<>(KRYO1_LINES);
            strings.addAll(KRYO2_LINES);
            for (String line : strings) {
                c.write(ByteBuffer.wrap(line.getBytes()));
                jw.write(new JournalEntry(key, c.position()));
            }
            // phony write
            jw.write(new JournalEntry(key, c.position() + 1000));

        }
        long partSize = Files.size(target);
        // open journal
        JournalReader jr = new JournalReader(Paths.get(key + Journal.EXT));
        Journal j = jr.getJournal();
        Path rolled = Files.createTempFile(targetKRYOPath, "rolled_badfile", "");
        try (SeekableByteChannel sbc = Files.newByteChannel(rolled, StandardOpenOption.WRITE)) {
            journaledCoalescer.combineFiles(j, sbc);
        }

        long rolledSize = Files.size(rolled);

        // verify
        assertThat(rolledSize, equalTo(partSize));
        assertThat(j.getLastEntry().getOffset(), Matchers.greaterThan(partSize));
    }

}
