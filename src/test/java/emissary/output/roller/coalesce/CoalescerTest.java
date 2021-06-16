package emissary.output.roller.coalesce;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import emissary.output.roller.journal.Journal;
import emissary.output.roller.journal.JournalEntry;
import emissary.output.roller.journal.JournalReader;
import emissary.output.roller.journal.JournalWriter;
import emissary.test.core.UnitTest;
import emissary.util.io.UnitTestFileUtils;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class CoalescerTest extends UnitTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private Coalescer coalescer;

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
        targetBUDPath = Files.createTempDirectory("temp-files");
        coalescer = new Coalescer(targetBUDPath);

        // setup temp files
        tempBUD1 = Files.createTempFile(targetBUDPath, BUD1_NAME, "");
        Files.write(tempBUD1, BUD1_LINES, Charset.defaultCharset(), StandardOpenOption.WRITE);

        tempBUD2 = Files.createTempFile(targetBUDPath, BUD2_NAME, "");
        Files.write(tempBUD2, BUD2_LINES, Charset.defaultCharset(), StandardOpenOption.WRITE);
    }

    @After
    public void cleanUp() throws IOException {
        UnitTestFileUtils.cleanupDirectoryRecursively(targetBUDPath);
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
            coalescer.combineFiles(j, sbc);
        }

        long rolledSize = Files.size(rolled);

        // verify
        assertThat(rolledSize, equalTo(partSize));
        assertThat(j.getLastEntry() == null ? 0 : j.getLastEntry().getOffset(), Matchers.greaterThan(partSize));
    }


}
