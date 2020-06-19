package emissary.output.roller;

import static emissary.output.roller.journal.Journal.EXT;
import static emissary.output.roller.journal.JournaledChannelPool.EXTENSION;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.isReadable;
import static java.nio.file.Files.isWritable;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

import emissary.output.roller.journal.Journal;
import emissary.output.roller.journal.JournalEntry;
import emissary.output.roller.journal.JournalReader;
import emissary.output.roller.journal.JournaledChannelPool;
import emissary.output.roller.journal.KeyedOutput;
import emissary.util.io.FileNameGenerator;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Rollable implemenation that uses a journal to record offsets of completed writes to a pool of outputs. The
 * Journal serves as a write ahead log and records positions of all open file handles until rolled.
 * <p>
 * During a roll, all Journals are identified and their outputs are combined into a destination filename denoted by the
 * FileNameGenerator.
 *
 */
public class JournaledCoalescer implements IJournaler, ICoalescer {

    private static final Logger LOG = LoggerFactory.getLogger(JournaledCoalescer.class);
    /**
     * Locks for protecting writes to underlying stream
     */
    private final ReentrantLock lock = new ReentrantLock();
    /**
     * Flag to let callers know if this class is currently rolling *
     */
    private volatile boolean rolling;
    /**
     * The name generator to use when creating new output files *
     */
    private FileNameGenerator fileNameGenerator;
    /**
     * The path to read input and write rolled output *
     */
    protected Path outputPath;
    /**
     * The current pool of output channels we're writing to *
     */
    private JournaledChannelPool journaledPool;
    /**
     * Max number of pooled outputs to create *
     */
    private final int poolsize;
    /**
     * File extension used while coalescing part files
     */
    static final String ROLLING_EXT = ".rolling";
    /**
     * File extension used while cleanup of part files after coalescing has completed
     */
    static final String ROLLED_EXT = ".rolled";
    /**
     * File extension used while cleanup of part files after coalescing has completed
     */
    static final String ERROR_EXT = ".error";
    /**
     * Part/journal file matcher
     */
    private static final String PART_GLOB = "*{" + EXTENSION + "," + EXT + "}";

    /**
     * @see JournaledCoalescer#JournaledCoalescer(java.nio.file.Path, FileNameGenerator, int)
     * @param outPath The Path to use for reading input and writing combined output
     * @param fileNameGenerator The FileNameGenerator to use for unique destination file names
     * @throws IOException If there is some I/O problem.
     */
    public JournaledCoalescer(final Path outPath, final FileNameGenerator fileNameGenerator) throws IOException, InterruptedException {
        this(outPath, fileNameGenerator, JournaledChannelPool.DEFAULT_MAX);
    }

    /**
     * The Rollable with take all files in a Path and combine them into a single destination file on each roll.
     *
     * @param outPath The Path to use for reading input and writing combined output
     * @param fileNameGenerator The FileNameGenerator to use for unique destination file names
     * @param poolsize The max number of outputs for the pool.
     */
    public JournaledCoalescer(final Path outPath, final FileNameGenerator fileNameGenerator, int poolsize) throws IOException, InterruptedException {
        this.outputPath = outPath.toAbsolutePath();
        this.fileNameGenerator = fileNameGenerator;
        this.poolsize = poolsize;
        validateOutputPath();
        cleanupOrphanedRolledFiles();
        initializeNextPool();
    }

    /**
     * Validate the Path we are using for combining files
     */
    private void validateOutputPath() throws IOException {
        if (!exists(this.outputPath)) {
            throw new FileNotFoundException("The output file path does not exist: " + this.outputPath + ".");
        } else if (!isDirectory(this.outputPath)) {
            throw new IllegalArgumentException("The output file path is not a directory: " + this.outputPath + ".");
        } else if (!(isReadable(this.outputPath) && isWritable(this.outputPath))) {
            throw new IllegalAccessError("The output path is not readable and writable: " + this.outputPath + ".");
        }
    }

    /**
     * Sometimes the rolled files can hang around after a crash. If there is a rolled file, that means all of the files
     * coalesced successfully but cleanup failed. If there are not any files with the same name, just rename the rolled
     * file. Otherwise, the rolled file will get cleaned up with the normal process.
     */
    private void cleanupOrphanedRolledFiles() throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(outputPath, "*" + ROLLED_EXT)) {
            for (Path entry : stream) {
                String finalOutputFilename = FilenameUtils.getBaseName(entry.toString());
                if (isOrphanedFile(finalOutputFilename)) {
                    finalize(entry, outputPath.resolve(finalOutputFilename));
                }
            }
        } catch (IOException e) {
            LOG.error("There was an error trying to cleanup rolled files {}", outputPath, e);
        }
    }

    /**
     * Sets the current file name to be used when creating files and rolling output. Closes current pool, retrieves a list
     * of Journal files that need to be rolled, and instantiates the next pool.
     * <p>
     * Called by the roll method, synchronized for consistency.
     */
    private Collection<Path> initializeNextPool() throws IOException, InterruptedException {
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
            Collection<Path> paths = initializeNextPool();
            coalesce(paths);
        } catch (IOException ex) {
            LOG.error("Error occurred during roll.", ex);
        } catch (InterruptedException ex) {
            LOG.warn("Roll interrupted during execution. Should continue on next roll.", ex);
        } finally {
            this.rolling = false;
        }
    }

    @Override
    public void coalesce() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void coalesce(Collection<Path> journalPaths) throws IOException {
        if (journalPaths.isEmpty()) {
            // nothing to do...
            return;
        }
        HashMap<String, Collection<Journal>> outputMap = new HashMap<>();
        journalPaths.forEach(path -> loadJournal(path, outputMap));
        outputMap.forEach(this::coalesceFiles);
    }

    private void loadJournal(Path path, HashMap<String, Collection<Journal>> outputMap) {
        try (JournalReader jr = new JournalReader(path)) {
            Journal j = jr.getJournal();
            outputMap.computeIfAbsent(j.getKey(), k -> new ArrayList<>()).add(j);
        } catch (IOException ex) {
            LOG.error("Unable to load Journal {}, renaming to {}{}", path.toString(), path.toString(), ERROR_EXT, ex);
            renameToError(path);
        }
    }

    /**
     * Combines all files in the path that are ready to be rolled into their output file.
     *
     * @param key The path to use for reading and writing files
     * @param journals The journal files currently needed to roll
     */
    protected void coalesceFiles(String key, Collection<Journal> journals) {
        try {
            // Create the path to the final outputFile
            Path finalOutputPath = this.outputPath.resolve(key);
            Path rolledOutputPath = this.outputPath.resolve(key + ROLLED_EXT);

            // Check to see if we already rolled files successfully and crashed on deletion
            if (Files.exists(rolledOutputPath)) {
                LOG.warn("Full output file already found {}. Deleting old part files.", rolledOutputPath);
                finalize(journals, rolledOutputPath, finalOutputPath);
                return;
            }

            // Create the path to the working outputFile
            Path workingOutputPath = this.outputPath.resolve(key + ROLLING_EXT);

            // Create the working file output stream, truncating a bad file from a crashed run, if it exists
            try (FileChannel workingOutputChannel = FileChannel.open(workingOutputPath, CREATE, TRUNCATE_EXISTING, WRITE)) {
                // Combine the files into the rolledOutputFile and delete them
                for (Journal j : journals) {
                    combineFiles(j, workingOutputChannel);
                }
                // Flush and close output stream
                workingOutputChannel.force(true);
            }

            Files.move(workingOutputPath, rolledOutputPath);
            LOG.info("Successfully coalesced {} files into: {}. Size: {}", journals.size(), rolledOutputPath, Files.size(rolledOutputPath));

            finalize(journals, rolledOutputPath, finalOutputPath);
        } catch (IOException ex) {
            LOG.error("IOException while processing journals for {}", key, ex);
        }
    }

    /**
     * Copies all bytes from all paths that match to an output stream.
     *
     * @param journal The journal to combine in the output stream
     * @param rolledOutput The OutputStream object to use
     */
    protected void combineFiles(Journal journal, SeekableByteChannel rolledOutput) throws IOException {
        long startPos = rolledOutput.position();
        JournalEntry last = journal.getLastEntry();
        if (last == null) {
            LOG.debug("Empty Journal encountered. {}", journal);
            return;
        }
        long offset = last.getOffset();
        Path p = Paths.get(last.getVal());
        LOG.debug("Reading from path {}", p);
        try (FileChannel part = FileChannel.open(p, READ)) {
            long partSize = Files.size(p);
            if (partSize < last.getOffset()) {
                JournalEntry lastGood = journal.getLastValidEntry(partSize);
                offset = lastGood.getOffset();
                LOG.warn("The bgpart file, {}, likely lost data due to a crash. Part size: {}, Expected {}, Actual: {}", last.getVal(), partSize,
                        last.getOffset(), offset);
            }
            long xfer;
            // for loop due to contract of channel.transferTo()
            for (long count = offset; count > 0L;) {
                xfer = part.transferTo(part.position(), count, rolledOutput);
                part.position(part.position() + xfer);
                count -= xfer;
                if (part.position() == partSize && count > 0L) {
                    throw new IOException("Premature EOF. Expected " + offset + ", but only transferred " + partSize);
                }
            }
            LOG.debug("Successfully appended {} bytes from {} to output file.", offset, p);
        } catch (IOException ex) {
            LOG.error("Exception attempting to transfer {} bytes from {} to output", offset, p.toString(), ex);
            renameToError(p);
            renameToError(journal.getJournalPath());
            rolledOutput.truncate(startPos);
            rolledOutput.position(startPos);
        }
    }

    @Override
    public boolean isRolling() {
        return this.rolling;
    }

    protected void finalize(Collection<Journal> journals, Path rolledOutputPath, Path finalOutputPath) throws IOException {
        cleanupFiles(journals);
        finalize(rolledOutputPath, finalOutputPath);
    }

    protected void finalize(Path rolledOutputPath, Path finalOutputPath) throws IOException {
        if (!Files.exists(rolledOutputPath)) {
            return;
        }

        if (Files.size(rolledOutputPath) > 0) {
            Files.move(rolledOutputPath, finalOutputPath);
            LOG.info("Cleaned part files and moved rolled file to {}", finalOutputPath);
        } else {
            // delete the rolled file if it is empty
            Files.delete(rolledOutputPath);
        }
    }

    protected void renameToError(Path path) {
        try {
            Path errorPath = Paths.get(path.toString() + ERROR_EXT);
            Files.move(path, errorPath);
        } catch (IOException ex) {
            LOG.warn("Unable to rename file {}.", path.toString(), ex);
        }
    }

    /**
     * Test to see if a rolled file is orphaned
     *
     * @param startsWith the name of the rolled file
     * @return true if no part/journal files exist, false otherwise
     * @throws IOException if there is an issue
     */
    private boolean isOrphanedFile(String startsWith) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(outputPath, startsWith + PART_GLOB)) {
            return !stream.iterator().hasNext();
        }
    }

    protected void cleanupFiles(Collection<Journal> journals) throws IOException {
        for (Journal journal : journals) {
            Path jpath = journal.getJournalPath();
            deleteParts(journal.getEntries());
            Files.deleteIfExists(jpath);
        }
    }

    private static void deleteParts(Collection<JournalEntry> entries) throws IOException {
        for (JournalEntry entry : entries) {
            Path p = Paths.get(entry.getVal());
            Files.deleteIfExists(p);
        }
    }

}
