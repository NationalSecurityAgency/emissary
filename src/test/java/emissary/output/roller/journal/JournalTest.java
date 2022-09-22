package emissary.output.roller.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.util.Collection;
import java.util.UUID;

import emissary.test.core.junit5.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JournalTest extends UnitTest {
    private Path tmpDir;

    @BeforeEach
    public void setUp(@TempDir final Path tmpDir) throws Exception {
        super.setUp();
        this.tmpDir = tmpDir;
    }

    /**
     * Test of write method, of class Journal.
     */
    @Test
    void testWrite() throws Exception {
        final String uuid = UUID.randomUUID().toString();
        final JournalEntry e = new JournalEntry(uuid, 100L);
        try (JournalWriter instance = new JournalWriter(this.tmpDir, uuid)) {
            final long expResult = 100L;
            final long result = instance.write(e);
            assertEquals(expResult, result);
        }
    }

    /**
     * Test of loadEntries method, of class Journal.
     */
    @Test
    void testLoadEntries() throws Exception {
        final String uuid = writeRandomJournal();
        JournalReader.getJournalPaths(tmpDir);
        long oneOffset = 0;
        long twoOffset = 0;
        try (JournalReader instance = new JournalReader(this.tmpDir.resolve(uuid + Journal.EXT))) {
            Journal j = instance.getJournal();
            final Collection<JournalEntry> entries = j.getEntries();
            assertEquals(4, entries.size(), "Expected 4 entries but was " + entries.size());
            for (final JournalEntry entry : entries) {
                if ((uuid + "-1").equals(entry.getVal())) {
                    oneOffset = entry.getOffset();
                } else {
                    twoOffset = entry.getOffset();
                }
            }
        }
        assertEquals(500, oneOffset, "Entry expecting  500");
        assertEquals(2000, twoOffset, "Entry expecting 2000 ");
    }

    private String writeRandomJournal() throws Exception {
        final String uuid = UUID.randomUUID().toString();
        final String one = uuid + "-1";
        final String two = uuid + "-2";
        try (JournalWriter instance = new JournalWriter(this.tmpDir, uuid)) {
            JournalEntry e = new JournalEntry(one, 0);
            instance.write(e);
            e = new JournalEntry(two, 0);
            instance.write(e);
            e = new JournalEntry(one, 500);
            instance.write(e);
            e = new JournalEntry(two, 2000);
            instance.write(e);
        }
        return uuid;
    }

    @Test
    void testEmptyJournal() {
        Journal j = new Journal(tmpDir);
        assertNull(j.getLastEntry(), "Journal Entry should be null");
    }
}
