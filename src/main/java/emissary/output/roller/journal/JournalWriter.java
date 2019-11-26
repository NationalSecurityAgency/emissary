package emissary.output.roller.journal;

import static emissary.output.roller.journal.Journal.SEP;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.locks.ReentrantLock;

/**
 * BG Write ahead log to track progress, often files, that have been successfully flushed to disk. The file itself
 * contains metadata at the start, followed by a collection of log entries of fixed length, currently 1024. Leading
 * metadata is in the format:
 *
 * <code>
 * [Journal Magic][Journal Version][Journal key.size()][Journal key.getBytes()][Journal Sequence Number][null byte][1-n Journal Entries]
 * </code>
 *
 * Record format:
 *
 * <code>
 * [Journal sequence number][null byte][Entry value.size()][null byte][Entry val.getBytes()][null byte][position][null padded to fixed len]
 * </code>
 */
public class JournalWriter implements Closeable {

    private final ReentrantLock lock = new ReentrantLock();
    private ByteBuffer b = ByteBuffer.allocateDirect(Journal.ENTRY_LENGTH);
    // full path to journal file
    final Path journalPath;
    // current sequence value
    private long sequence;
    byte version;
    String key;
    // persisted journal
    FileChannel journal;
    JournalEntry prev;

    public JournalWriter(final Path dir, final String key) throws IOException {
        this(dir, key, key);
    }

    public JournalWriter(final Path dir, final String journalFileName, final String key) throws IOException {
        this.journalPath = dir.resolve(journalFileName + Journal.EXT);
        this.key = key;
        checkJournal();
    }

    private void checkJournal() throws IOException {
        if (Files.exists(journalPath) && Files.size(journalPath) > 0L) {
            throw new IllegalStateException("Journals Are Immutable");
        } else {
            Files.deleteIfExists(journalPath);
        }
    }

    /**
     * 
     * @return position difference between last entry and current
     */
    public long write(JournalEntry e) throws IOException {
        lock.lock();
        try {
            if (journal == null) {
                writeHeader();
            }
            b.clear();
            b.putLong(++sequence);
            b.put(SEP);
            e.serialize(b);
            // fixed record length format so zero out everything
            nullpad();
            write();
            return prev == null ? e.offset : e.offset - prev.offset;
        } finally {
            prev = e;
            lock.unlock();
        }
    }

    private void writeHeader() throws IOException {
        this.journal = FileChannel.open(journalPath, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        b.clear();
        b.put(Journal.MAGIC);
        b.put(Journal.CURRENT_VERSION);
        byte[] keyBytes = key.getBytes();
        b.putInt(keyBytes.length);
        for (int i = 0; i < keyBytes.length; i++) {
            if (b.remaining() == 0) {
                write();
            }
            b.put(keyBytes[i]);
        }
        if (b.remaining() < Journal.NINE) {
            write();
        }
        sequence = System.currentTimeMillis();
        b.putLong(sequence);
        b.put(SEP);
        write();
    }

    private void write() throws IOException {
        b.flip();
        journal.write(b);
        b.clear();
    }

    // fill buffer with zeros from current position to limit
    private void nullpad() {
        while (b.hasRemaining()) {
            b.put(SEP);
        }
    }

    /**
     * Closes underlying journal channel.
     */
    @Override
    public void close() throws IOException {
        lock.lock();
        try {
            if (journal != null) {
                journal.close();
            }
            journal = null;
            b = null;
        } finally {
            lock.unlock();
        }
    }
}
