package emissary.output.roller.journal;

import static org.junit.Assert.assertTrue;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;

import emissary.test.core.UnitTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class JournaledChannelPoolTest extends UnitTest {

    private Path directory;
    private String key;
    private JournaledChannelPool instance;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        this.directory = Files.createTempDirectory("JournaledChannelPoolTest");
        this.key = UUID.randomUUID().toString();
        this.instance = new JournaledChannelPool(this.directory, this.key, 3);
    }

    @After
    @Override
    public void tearDown() throws Exception {
        this.instance.close();
        super.tearDown();
    }

    /**
     * Test of freeChannel method, of class JournaledChannelPool.
     */
    @Test
    public void testGetAndFree() throws Exception {
        final SeekableByteChannel out = this.instance.getFree();
        final int created = this.instance.getCreatedCount();
        assertTrue("Should have recorded creation of at least 1 object", created > 0);
        final int free = this.instance.getFreeSize();
        // out is a wrapper, closing it will delegate to freeChannel
        out.close();
        assertTrue("Free should be one more than " + free, (free + 1) == this.instance.getFreeSize());
    }

    /**
     * Test of getJournalEntries method, of class JournaledChannelPool.
     */
    @Test
    public void testGetJournalEntries() throws Exception {
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
        assertTrue("Expected 4 Journal Entries " + result.size(), result.size() == 4);
        int jrnltot = 0;
        for (final JournalEntry e : result) {
            jrnltot += (int) e.getOffset();
        }
        final int totlen = onetext.length() + twotext.length();
        assertTrue("Total length should be " + totlen, totlen == jrnltot);
    }

    private void writeText(final KeyedOutput ko, final String text) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(Channels.newWriter(ko, "UTF-8"))) {
            bw.write(text);
            bw.flush();
            ko.commit();
        }
    }
}
