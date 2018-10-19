package emissary.output.roller.journal;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lightweight wrapper holding reference to a pooled object and the pool, which returns the channel to the pool on
 * close.
 */
public class KeyedOutput extends OutputStream implements SeekableByteChannel {

    static final Logger LOG = LoggerFactory.getLogger(KeyedOutput.class);
    JournaledChannelPool pool;
    JournaledChannel jc;

    KeyedOutput(final JournaledChannelPool pool, final JournaledChannel jc) {
        this.pool = pool;
        this.jc = jc;
    }

    public Path getFinalDestination() {
        return this.pool.getDirectory().resolve(this.pool.getKey());
    }

    @Override
    public void write(final int b) throws IOException {
        this.jc.write(b);
    }

    @Override
    public void write(final byte[] bs, final int off, final int len) throws IOException {
        this.jc.write(bs, off, len);
    }

    @Override
    public int write(final ByteBuffer src) throws IOException {
        return this.jc.write(src);
    }

    @Override
    public long position() throws IOException {
        return this.jc.position();
    }

    @Override
    public long size() throws IOException {
        return this.jc.size();
    }

    @Override
    public boolean isOpen() {
        return (this.jc != null) && this.jc.isOpen();
    }

    /**
     * Commits writes to underlying storage. This method should only be called after a successful write.
     * 
     * @throws IOException If there is some I/O problem.
     */
    public final void commit() throws IOException {
        this.jc.commit();
    }

    /**
     * Closes this Channel/Output Stream by releasing resources to underlying this.pool.
     * 
     * @throws IOException If there is some I/O problem.
     */
    @Override
    public void close() throws IOException {
        if (this.pool == null) {
            return;
        }
        LOG.debug("Returning object to pool {}", this.jc.path);
        this.pool.free(this.jc);
        this.pool = null;
        this.jc = null;
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
