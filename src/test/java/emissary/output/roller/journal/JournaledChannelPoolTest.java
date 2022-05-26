package emissary.output.roller.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;

import emissary.test.core.UnitTest;
import emissary.util.io.UnitTestFileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JournaledChannelPoolTest extends UnitTest {

    private Path directory;
    private String key;
    private JournaledChannelPool instance;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        super.setUp();
        this.directory = Files.createTempDirectory("JournaledChannelPoolTest");
        this.key = UUID.randomUUID().toString();
        this.instance = new JournaledChannelPool(this.directory, this.key, 3);
    }

    @AfterEach
    @Override
    public void tearDown() throws Exception {
        this.instance.close();
        UnitTestFileUtils.cleanupDirectoryRecursively(this.directory);
        super.tearDown();
    }

    /**
     * Test of freeChannel method, of class JournaledChannelPool.
     */
    @Test
    void testGetAndFree() throws Exception {
        final SeekableByteChannel out = this.instance.getFree();
        final int created = this.instance.getCreatedCount();
        assertTrue(created > 0, "Should have recorded creation of at least 1 object");
        final int free = this.instance.getFreeSize();
        // out is a wrapper, closing it will delegate to freeChannel
        out.close();
        assertEquals((free + 1), this.instance.getFreeSize(), "Free should be one more than " + free);
    }

    /**
     * Test of getJournalEntries method, of class JournaledChannelPool.
     */
    @Test
    void testGetJournalEntries() throws Exception {
        final String onetext = "one line of text";
        final String twotext = "two lines of text\nthe second line";
        try (KeyedOutput k1 = this.instance.getFree(); KeyedOutput k2 = this.instance.getFree()) {
            writeText(k1, onetext);
            writeText(k2, twotext);
        }
        final ArrayList<JournalEntry> result = new ArrayList<>();
        for (final Path journalPath : JournalReader.getJournalPaths(this.directory)) {
            try (final JournalReader jr = new JournalReader(journalPath)) {
                Journal j = jr.getJournal();
                result.addAll(j.getEntries());
            }
        }
        assertEquals(4, result.size(), "Expected 4 Journal Entries " + result.size());
        int jrnltot = 0;
        for (final JournalEntry e : result) {
            jrnltot += (int) e.getOffset();
        }
        final int totlen = onetext.length() + twotext.length();
        assertEquals(totlen, jrnltot, "Total length should be " + totlen);
    }

    private void writeText(final KeyedOutput ko, final String text) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(Channels.newWriter(ko, "UTF-8"))) {
            bw.write(text);
            bw.flush();
            ko.commit();
        }
    }
}
