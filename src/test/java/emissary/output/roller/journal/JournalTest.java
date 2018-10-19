package emissary.output.roller.journal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.UUID;

import emissary.test.core.UnitTest;
import emissary.util.io.UnitTestFileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class JournalTest extends UnitTest {
    private Path tmpDir;


    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        this.tmpDir = Files.createTempDirectory("JournalTest");
    }

    @After
    @Override
    public void tearDown() throws Exception {
        UnitTestFileUtils.cleanupDirectoryRecursively(this.tmpDir);
        super.tearDown();
    }

    /**
     * Test of write method, of class Journal.
     */
    @Test
    public void testWrite() throws Exception {
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
    public void testLoadEntries() throws Exception {
        final String uuid = writeRandomJournal();
        JournalReader.getJournalPaths(tmpDir);
        long oneOffset = 0;
        long twoOffset = 0;
        try (JournalReader instance = new JournalReader(this.tmpDir.resolve(uuid + Journal.EXT))) {
            Journal j = instance.getJournal();
            final Collection<JournalEntry> entries = j.getEntries();
            assertTrue("Expected 4 entries but was " + entries.size(), entries.size() == 4);
            for (final JournalEntry entry : entries) {
                if ((uuid + "-1").equals(entry.getVal())) {
                    oneOffset = entry.getOffset();
                } else {
                    twoOffset = entry.getOffset();
                }
            }
        }
        assertTrue("Entry expecting  500", oneOffset == 500);
        assertTrue("Entry expecting 2000 ", twoOffset == 2000);
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
    public void testEmptyJournal() {
        Journal j = new Journal(tmpDir);
        assertEquals("Journal Entry should be null", j.getLastEntry(), null);
    }
}
