package emissary.output.roller.coalesce;

import static emissary.output.roller.coalesce.MaxAttemptCoalescer.ATTEMPT_EXT;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.collection.IsIterableContainingInRelativeOrder.containsInRelativeOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;

import emissary.output.io.SimpleFileNameGenerator;
import emissary.output.roller.journal.Journal;
import emissary.output.roller.journal.JournalReader;
import emissary.output.roller.journal.JournaledChannelPool;
import emissary.output.roller.journal.Journaler;
import emissary.output.roller.journal.KeyedOutput;
import emissary.test.core.UnitTest;
import emissary.util.io.UnitTestFileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MaxAttemptCoalescerTest extends UnitTest {

    public static final Logger logger = LoggerFactory.getLogger(MaxAttemptCoalescerTest.class);

    Coalescer coalescer;
    Journaler journaler;

    Path targetPath;

    Path temp1;
    final String temp1Filename = "output1.out";
    final List<String> temp1FileLines = Arrays.asList("Line1", "Line2");

    Path temp2;
    final String temp2Filename = "output2.out";
    final List<String> temp2FileLines = Arrays.asList("Line3", "Line4");

    @Before
    @Override
    public void setUp() throws Exception {
        targetPath = Files.createTempDirectory("temp-files");
        journaler = new Journaler(targetPath, new SimpleFileNameGenerator());
        coalescer = getCoalescer();

        logger.info("Setup coalescer {}", coalescer);

        // setup temp files
        temp1 = Files.createTempFile(targetPath, temp1Filename, "");
        Files.write(temp1, temp1FileLines, Charset.defaultCharset(), StandardOpenOption.WRITE);

        temp2 = Files.createTempFile(targetPath, temp2Filename, "");
        Files.write(temp2, temp2FileLines, Charset.defaultCharset(), StandardOpenOption.WRITE);
    }

    protected Coalescer getCoalescer() throws IOException {
        return new MaxAttemptCoalescer(targetPath);
    }

    @After
    public void cleanUp() throws IOException {
        UnitTestFileUtils.cleanupDirectoryRecursively(targetPath);
    }

    @Test
    public void testMaxAttemptCoalesce() throws Exception {
        journal();
        roll();
        coalescer.coalesce(JournalReader.getJournalPaths(targetPath));
        coalesce();
        assertSuccessOutput();
    }

    @Test
    public void testCoalesceCannotReadAttemptFile1() throws Exception {
        journal();
        roll();
        writeAttemptFile("-1 ");
        coalesce();
        assertSuccessOutput();
    }

    @Test
    public void testCoalesceCannotReadAttemptFile2() throws Exception {
        journal();
        roll();
        writeAttemptFile("junk");
        coalesce();
        assertSuccessOutput();
    }

    @Test
    public void testCoalesceTooManyAttempts() throws Exception {
        journal();
        roll();
        Path attemptFile = writeAttemptFile("3");
        coalesce();

        // verify
        Path destination = targetPath.resolve(temp1Filename);
        assertFalse(Files.exists(destination));
        assertFalse(Files.exists(attemptFile));
        testFileCount(4, "/*" + Journal.ERROR_EXT);
        testFileCount(0, "/*" + JournaledChannelPool.EXTENSION);
        testFileCount(0, "/*" + Journal.EXT);
        testFileCount(0, "/*" + Coalescer.ROLL_GLOB);
    }

    protected void journal() throws IOException, InterruptedException {
        // setup
        try (JournaledChannelPool pool = new JournaledChannelPool(targetPath, temp1Filename, 2);
                KeyedOutput one = pool.getFree();
                KeyedOutput two = pool.getFree()) {
            Files.copy(temp1, one);
            Files.copy(temp2, two);
            one.commit();
            two.commit();
        }
    }

    protected void roll() {
        journaler.roll();
    }

    protected void coalesce() throws IOException {
        coalescer.coalesce(JournalReader.getJournalPaths(targetPath));
    }

    protected Path writeAttemptFile(String content) throws IOException {
        Path attemptFile = Paths.get(targetPath.toString(), temp1Filename + ATTEMPT_EXT);
        Files.write(attemptFile, content.getBytes(StandardCharsets.UTF_8));
        return attemptFile;
    }

    protected void assertSuccessOutput() throws IOException {
        // verify
        Path destination = targetPath.resolve(temp1Filename);
        assertTrue(Files.exists(destination));
        List<String> fileResults = Files.readAllLines(destination, Charset.defaultCharset());
        assertThat("Expected 4 lines in " + destination, fileResults.size(), equalTo(4));
        assertThat(fileResults, containsInRelativeOrder(temp1FileLines.get(0), temp1FileLines.get(1)));
        assertThat(fileResults, containsInRelativeOrder(temp2FileLines.get(0), temp2FileLines.get(1)));
    }

    protected void testFileCount(int expected, String glob) throws IOException {
        testFileCount(expected, targetPath, glob);
    }

    protected void testFileCount(int expected, Path path, String glob) throws IOException {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + path + glob);
        assertEquals(expected, Files.list(path).filter(matcher::matches).count());
    }

}
