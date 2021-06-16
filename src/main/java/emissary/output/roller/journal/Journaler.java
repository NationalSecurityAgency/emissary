package emissary.output.roller.journal;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.locks.ReentrantLock;

import emissary.output.roller.IJournaler;
import emissary.util.io.FileNameGenerator;
import emissary.util.io.LocalOutputUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Rollable implementation that uses a journal to record offsets of completed writes to a pool of outputs. The
 * Journal serves as a write ahead log and records positions of all open file handles until rolled.
 *
 */
public class Journaler implements IJournaler {

    private static final Logger LOG = LoggerFactory.getLogger(Journaler.class);
    /**
     * Locks for protecting writes to underlying stream
     */
    protected final ReentrantLock lock = new ReentrantLock();
    /**
     * Flag to let callers know if this class is currently rolling *
     */
    protected volatile boolean rolling;
    /**
     * The name generator to use when creating new output files *
     */
    protected FileNameGenerator fileNameGenerator;
    /**
     * The path to read input and write rolled output *
     */
    protected Path outputPath;
    /**
     * The current pool of output channels we're writing to *
     */
    protected JournaledChannelPool journaledPool;
    /**
     * Max number of pooled outputs to create *
     */
    protected final int poolsize;

    /**
     * @see Journaler#Journaler(Path, FileNameGenerator, int)
     * @param outPath The Path to use for reading input and writing combined output
     * @param fileNameGenerator The FileNameGenerator to use for unique destination file names
     * @throws IOException If there is some I/O problem.
     */
    public Journaler(final Path outPath, final FileNameGenerator fileNameGenerator) throws IOException, InterruptedException {
        this(outPath, fileNameGenerator, JournaledChannelPool.DEFAULT_MAX);
    }

    /**
     * The Rollable with take all files in a Path and combine them into a single destination file on each roll.
     *
     * @param outPath The Path to use for reading input and writing combined output
     * @param fileNameGenerator The FileNameGenerator to use for unique destination file names
     * @param poolsize The max number of outputs for the pool.
     */
    public Journaler(final Path outPath, final FileNameGenerator fileNameGenerator, int poolsize) throws IOException, InterruptedException {
        this.outputPath = outPath.toAbsolutePath();
        this.fileNameGenerator = fileNameGenerator;
        this.poolsize = poolsize;
        validateOutputPath();
        initializeNextPool();
    }

    /**
     * Validate the Path we are using for combining files
     */
    protected void validateOutputPath() throws IOException {
        LocalOutputUtil.validate(this.outputPath);
    }

    /**
     * Sets the current file name to be used when creating files and rolling output. Closes current pool, retrieves a list
     * of Journal files that need to be rolled, and instantiates the next pool.
     * <p>
     * Called by the roll method, synchronized for consistency.
     */
    protected Collection<Path> initializeNextPool() throws IOException, InterruptedException {
        lock.lock();
        try {
            if (journaledPool != null) {
                this.journaledPool.close();
            }
            Collection<Path> journals = JournalReader.getJournalPaths(outputPath);
            this.journaledPool = new JournaledChannelPool(outputPath, this.fileNameGenerator.nextFileName(), poolsize);
            LOG.debug("Generated new Journal file name: {}", this.journaledPool);
            return journals;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns and KeyedOutput object containing the final output file and can be written to as either an OutputStream or a
     * SeekableByteChannel. This method will block if objects from the pool have been exhausted.
     *
     * @return a KeyedOutput
     */
    public final KeyedOutput getOutput() throws IOException {
        lock.lock();
        try {
            // Return the final, full path
            return journaledPool.getFree();
        } catch (InterruptedException ex) {
            // should not happen in our implementation
            throw new IOException("Interrupted trying to obtain KeyedOutput", ex);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() throws IOException {
        roll();
    }

    @Override
    public void roll() {
        try {
            this.rolling = true;
            preInitializeNextPool();
            Collection<Path> paths = initializeNextPool();
            postInitializeNextPool(paths);
        } catch (IOException ex) {
            LOG.error("Error occurred during roll.", ex);
        } catch (InterruptedException ex) {
            LOG.warn("Roll interrupted during execution. Should continue on next roll.", ex);
        } finally {
            this.rolling = false;
        }
    }

    protected void preInitializeNextPool() throws IOException {}

    protected void postInitializeNextPool(Collection<Path> paths) throws IOException {}

    @Override
    public boolean isRolling() {
        return this.rolling;
    }

}
