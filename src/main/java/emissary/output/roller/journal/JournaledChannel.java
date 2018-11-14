package emissary.output.roller.journal;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wrapper class to allow for use of underlying channel in either OutputStream code or WritableChannel.
 */
public class JournaledChannel extends OutputStream implements SeekableByteChannel {

    static final Logger LOG = LoggerFactory.getLogger(JournaledChannel.class);
    // 128k
    static final int BUFF_SIZE = 128 * 1024;
    FileChannel fc;
    Path path;
    JournalEntry e;
    final int index;
    final JournalWriter journal;
    ByteBuffer directBuff;

    JournaledChannel(final Path path, final String key, final int index) throws IOException {
        this.fc = FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.READ, StandardOpenOption.WRITE);
        this.path = path;
        this.index = index;
        this.journal = new JournalWriter(path.getParent(), path.getFileName().toString(), key);
        this.directBuff = ByteBuffer.allocateDirect(BUFF_SIZE);
        writeEntry();
    }

    private byte[] b1 = null;

    @Override
    public void write(final int b) throws IOException {
        if (this.b1 == null) {
            this.b1 = new byte[1];
        }
        this.b1[0] = (byte) b;
        this.write(this.b1);
    }

    @Override
    public void write(final byte[] bs, final int off, final int len) throws IOException {
        if ((off < 0) || (off > bs.length) || (len < 0) || ((off + len) > bs.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }

        int remaining = len;
        int offset = off;
        while (remaining > 0) {
            final int limit = Math.min(remaining, this.directBuff.capacity());
            this.directBuff.clear();
            this.directBuff.put(bs, offset, limit);
            this.directBuff.flip();
            while (this.directBuff.hasRemaining()) {
                if (this.fc.write(this.directBuff) <= 0) {
                    throw new RuntimeException("no bytes written");
                }
            }
            offset += limit;
            remaining -= limit;
        }
    }

    @Override
    public int write(final ByteBuffer src) throws IOException {
        // doing this to avoid java bug that caused direct memory leaks.
        // could be removed in java 9 when they provide a JVM argument to
        // limit caching
        if (!(src.isDirect())) {
            int written = 0;
            while (src.hasRemaining()) {
                this.directBuff.clear();
                while (this.directBuff.hasRemaining() && src.hasRemaining()) {
                    this.directBuff.put(src.get());
                }
                this.directBuff.flip();
                written += this.fc.write(this.directBuff);
            }
            return written;
        } else {
            return this.fc.write(src);
        }
    }

    @Override
    public long position() throws IOException {
        return this.fc.position();
    }

    @Override
    public long size() throws IOException {
        return this.fc.size();
    }

    @Override
    public boolean isOpen() {
        return (this.fc != null) && this.fc.isOpen();
    }

    /**
     * Sets the position of the channel according to the current entry. Should only be called by the pool.
     * 
     * @throws IOException If there is some I/O problem.
     */
    void setPosition() throws IOException {
        if (this.e.getOffset() != this.fc.position()) {
            this.fc.position(this.e.getOffset());
        }
    }

    /**
     * Commits writes to underlying storage. This method should only be called after a successful write.
     * 
     * @throws IOException If there is some I/O problem.
     */
    public final void commit() throws IOException {
        writeEntry();
    }

    private void writeEntry() throws IOException {
        final JournalEntry entry = new JournalEntry(this.path.toString(), this.fc.position());
        this.journal.write(entry);
        this.e = entry;
    }

    /**
     * Closes this Channel/Output Stream by releasing resources to underlying pool. Further calls result in unspecified
     * behavior
     * 
     * @throws IOException If there is some I/O problem.
     */
    @Override
    public void close() throws IOException {
        if (this.fc != null) {
            this.fc.close();
            this.fc = null;
        }
        this.journal.close();
        this.e = null;
        this.directBuff = null;
    }

    /**
     * Opens this object for writing and sets position according to current Journal Entry.
     * 
     * @throws IOException If there is some I/O problem.
     */
    void open() throws IOException {
        LOG.debug("Opening channel for writing {}", this.path);
        if (this.fc.position() != this.e.getOffset()) {
            this.fc.position(this.e.getOffset());
        }
    }

    /* Unsupported operations */
    @Override
    public SeekableByteChannel position(final long newPosition) throws IOException {
        throw new UnsupportedOperationException("This operation is not permitted");
    }

    @Override
    public int read(final ByteBuffer dst) throws IOException {
        throw new UnsupportedOperationException("This operation is not permitted");
    }

    @Override
    public SeekableByteChannel truncate(final long size) throws IOException {
        throw new UnsupportedOperationException("This operation is not permitted");
    }
}
