package emissary.output.roller.journal;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tracks written progress of a corresponding file. Some formats within the framework don't lend themselves to knowing
 * length of output prior to writing as in many journaled formats. Our model allows us to track successful or complete
 * writes to an output file using JournalEntry objects and is simplified further by using a JournaledChannel.
 * 
 * @see JournalReader
 * @see JournalWriter
 */
public final class Journal {

    static final byte SEP = 0x00;
    // 6 byte magic
    static final byte[] MAGIC = "BGJRNL".getBytes();
    static final byte CURRENT_VERSION = 1;
    static final int ENTRY_LENGTH = 1024;
    // eight bytes and a null separator
    static final int NINE = 9;
    public static final String EXT = ".bgjournal";
    static final String DELEXT = ".deletemarker";
    // Fields package protected for testing
    // not final to release on close
    byte version;
    String key;
    final Path journalPath;

    ArrayList<JournalEntry> entries = new ArrayList<>();

    public Journal(Path journalPath) {
        this.journalPath = journalPath;
    }

    public List<JournalEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    void setVersion(byte b) {
        this.version = b;
    }

    public byte getVersion() {
        return version;
    }

    void setKey(String key) {
        this.key = key;
    }

    /**
     * Unique identifier for this Journal, typically used to identify a target, finalized file name.
     * 
     * @return The key, generally a target file, for this Journal
     */
    public String getKey() {
        return this.key;
    }

    public Path getJournalPath() {
        return this.journalPath;
    }

    /**
     * Last JournalEntry in the file, which generally represents that last successful written offset in the associated
     * content file.
     * 
     * @return Last entry that should correlate to the last good position in a file or null if no entries are present.
     */
    public JournalEntry getLastEntry() {
        return entries.isEmpty() ? null : entries.get(entries.size() - 1);
    }

    /**
     * This method retrieves the last valid JournalEntry based on the length or position within the content file. It is
     * possible, on some architectures, that upon a crash data may not have flushed to disk. In this case, we need to query
     * the Journal to find the last good position based on the length of the file.
     * 
     * @param channelSize The maximum position, generally the file size, to search for
     * @return JournalEntry containing the last good offset less than or equal to channelSize
     */
    public JournalEntry getLastValidEntry(long channelSize) {
        if (channelSize < 0) {
            throw new IllegalArgumentException("Channel Size must be 0 or larger");
        }
        for (int i = entries.size() - 1; i > 0; i--) {
            JournalEntry je = entries.get(i);
            if (channelSize >= je.getOffset()) {
                return je;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "Journal{" + "version=" + version + ", key=" + key + ", journalPath=" + journalPath + ", entries=" + entries.size() + '}';
    }
}
