package emissary.output.roller.journal;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pool implementation that utilizes a Journal to durably track state out written data. The implementation will create
 * up to the maximum configured output channels. Channels are lazily initialized to minimize resource utilization. The
 * Journal is only updated when files are created and committed.
 */
public class JournaledChannelPool implements AutoCloseable {
    public static final String EXTENSION = ".bgpart";
    private static final Logger LOG = LoggerFactory.getLogger(JournaledChannelPool.class);
    public static final int DEFAULT_MAX = 10;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition freeCondition = this.lock.newCondition();
    final int max;
    final Path directory;
    final String key;
    private final Queue<JournaledChannel> free = new LinkedList<>();
    private int created;
    private JournaledChannel[] allchannels;

    public JournaledChannelPool(final Path directory, final String key, final int max) throws IOException {
        this.max = max;
        this.directory = directory;
        this.key = key;
        this.allchannels = new JournaledChannel[max];
    }

    int getFreeSize() {
        return this.free.size();
    }

    int getCreatedCount() {
        return this.created;
    }

    /**
     * Supplied key to identify the pool.
     * 
     * @return key
     */
    public String getKey() {
        return this.key;
    }

    /**
     * Returns an available output from the pool. This method will block if there are no free objects available and the max
     * number of outputs has been created.
     * 
     * @return an available KeyedOutput from the pool
     * @throws IOException If there is some I/O problem.
     * @throws InterruptedException If interrupted.
     */
    public KeyedOutput getFree() throws InterruptedException, IOException {
        this.lock.lock();
        JournaledChannel jc = null;
        try {
            checkClosed();
            jc = findFree();
            jc.setPosition();
            return new KeyedOutput(this, jc);
        } catch (Throwable t) {
            if (jc != null) {
                LOG.debug("Throwable occurred while obtaining channel. Returning to the pool. {}", jc.path, t);
                free(jc);
            }
            throw t;
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * Flushes underlying channel and writes journal entry, updating current position.
     * 
     * @param jc the JournaledChannel to flush
     */
    void free(final JournaledChannel jc) {
        if (jc == null) {
            throw new IllegalArgumentException("Cannot return a null JournaledChannel.");
        }
        this.lock.lock();
        try {
            if (this.free.contains(jc) || !this.free.offer(jc)) {
                LOG.warn("Could not return the channel to the pool {}", this.key);
            }
            // signal everyone since close and find may be waiting
            this.freeCondition.signalAll();
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * Closes the underlying pool. This method will block if any resources have not been returned.
     * 
     * @throws InterruptedException If interrupted.
     * @throws IOException If there is some I/O problem.
     */
    @Override
    public void close() throws InterruptedException, IOException {
        this.lock.lock();
        try {
            while (this.free.size() < this.created) {
                LOG.debug("Waiting for leased {} objects.", this.created - this.free.size());
                this.freeCondition.await();
            }
            for (final JournaledChannel fc : this.free) {
                this.allchannels[fc.index].close();
            }
            this.allchannels = null;
        } finally {
            this.lock.unlock();
        }
    }

    private void checkClosed() throws ClosedChannelException {
        if (this.allchannels == null) {
            throw new ClosedChannelException();
        }
    }

    private JournaledChannel findFree() throws InterruptedException, IOException {
        // if nothing is available and we can create additional channels, do it
        // clould get closed when we await
        while (this.free.isEmpty()) {
            if (this.created < this.max) {
                createChannel();
            } else {
                this.freeCondition.await();
                checkClosed();
            }
        }
        return this.free.poll();
    }

    private void createChannel() throws IOException {
        final Path p = Paths.get(this.directory.toString(), this.key + "_" + UUID.randomUUID().toString() + EXTENSION);
        final JournaledChannel ko = new JournaledChannel(p, this.key, this.created);
        this.allchannels[this.created++] = ko;
        this.free.add(ko);
    }

    Path getDirectory() {
        return this.directory;
    }
}
