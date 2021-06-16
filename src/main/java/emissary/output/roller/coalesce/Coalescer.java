package emissary.output.roller.coalesce;

import static emissary.output.roller.journal.Journal.ERROR_EXT;
import static emissary.output.roller.journal.Journal.EXT;
import static emissary.output.roller.journal.JournalReader.getJournalPaths;
import static emissary.output.roller.journal.JournaledChannelPool.EXTENSION;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Collection;

import emissary.output.roller.ICoalescer;
import emissary.output.roller.journal.Journal;
import emissary.output.roller.journal.JournalEntry;
import emissary.output.roller.journal.JournalReader;
import emissary.util.io.LocalOutputUtil;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads journal files that have recorded offsets of completed writes to a set of part files. The Journal serves as a
 * write ahead log and records positions of all open file handles until rolled.
 * <p>
 * All Journals are identified and their outputs are combined into a destination filename denoted by the Journal key.
 *
 */
public class Coalescer implements ICoalescer {

    private static final Logger LOG = LoggerFactory.getLogger(Coalescer.class);

    public static final boolean DEFAULT_SETUP_OUTPUT_PATH = false;

    /**
     * The path to read input and write rolled output *
     */
    protected Path outputPath;
    /**
     * File extension used while coalescing part files
     */
    public static final String ROLLING_EXT = ".bgrolling";
    /**
     * File extension used while cleanup of part files after coalescing has completed
     */
    public static final String ROLLED_EXT = ".bgrolled";
    /**
     * Part/journal file matcher
     */
    public static final String PART_GLOB = "*{" + EXTENSION + "," + EXT + "}";
    /**
     * Roll file matcher
     */
    public static final String ROLL_GLOB = "*{" + ROLLING_EXT + "," + ROLLED_EXT + "}";

    /**
     * Take all files in a Path and combine them into a single destination file.
     *
     * @param outPath The Path to use for reading input and writing combined output
     * @throws IOException If there is some I/O problem.
     */
    public Coalescer(final Path outPath) throws IOException {
        this(outPath, DEFAULT_SETUP_OUTPUT_PATH);
    }

    /**
     * Take all files in a Path and combine them into a single destination file.
     *
     * @param outPath The Path to use for reading input and writing combined output
     * @param setupOutputPath Create the output path if it does not exist
     * @throws IOException If there is some I/O problem.
     */
    public Coalescer(final Path outPath, boolean setupOutputPath) throws IOException {
        this.outputPath = outPath.toAbsolutePath();
        setup(setupOutputPath);
    }

    /**
     * Setup the path matcher
     */
    protected void setup(boolean setupOutputPath) throws IOException {
        if (setupOutputPath) {
            setupOutputPath();
        }
        validateOutputPath();
        cleanupOrphanedRolledFiles();
    }

    /**
     * Setup the Path we are using for combining files
     */
    protected void setupOutputPath() throws IOException {
        LocalOutputUtil.setup(this.outputPath);
    }

    /**
     * Validate the Path we are using for combining files
     */
    protected void validateOutputPath() throws IOException {
        LocalOutputUtil.validate(this.outputPath);
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
        JournalReader.getJournalsGroupedByKey(journalPaths).forEach(this::coalesceFiles);
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
            Path finalOutputPath = getFinalOutputPath(key, journals);
            Path rolledOutputPath = getRolledOutputPath(key, journals);

            LOG.trace("Attempting to roll files for key {}", key);

            // Check to see if we already rolled files successfully and crashed on deletion
            if (Files.exists(rolledOutputPath)) {
                LOG.info("Full output file already found {}. Deleting old part files.", rolledOutputPath);
                finalize(journals, rolledOutputPath, finalOutputPath);
                return;
            }

            preRollHook(key, journals);

            LOG.info("Coalescing files with key {}", key);

            // Create the path to the working outputFile
            Path workingOutputPath = getWorkingOutputPath(key, journals);

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
            LOG.info("Successfully coalesced {} files into: {}. Size: {} bytes", journals.size(), rolledOutputPath, Files.size(rolledOutputPath));

            finalize(journals, rolledOutputPath, finalOutputPath);
        } catch (IOException ex) {
            LOG.error("IOException while processing journals for {}", key, ex);
        }
    }

    /**
     * Get the path to the rolling file
     *
     * @param key the key for the output file
     * @param journals the collection of journals
     * @return the location of the rolling file
     */
    protected Path getWorkingOutputPath(String key, Collection<Journal> journals) {
        return resolveOutputPath(journals, key + ROLLING_EXT);
    }

    /**
     * Get the path to the rolled file
     *
     * @param key the key for the output file
     * @param journals the collection of journals
     * @return the location of the rolled file
     */
    protected Path getRolledOutputPath(String key, Collection<Journal> journals) {
        return resolveOutputPath(journals, key + ROLLED_EXT);
    }

    /**
     * Get the path to the final output file
     *
     * @param key the key for the output file
     * @param journals the collection of journals
     * @return the location of the final output file
     */
    protected Path getFinalOutputPath(String key, Collection<Journal> journals) {
        return resolveOutputPath(journals, key);
    }

    /**
     * Get the output path for a given file
     *
     * @param journals the collection of journals
     * @param filename the file name to resolve
     * @return the path to the file
     */
    protected Path resolveOutputPath(Collection<Journal> journals, String filename) {
        return this.outputPath.resolve(filename);
    }

    /**
     * Method called prior to rolling output part files
     *
     * @param key The path to use for reading and writing files
     * @param journals The journal files currently needed to roll
     */
    protected void preRollHook(String key, Collection<Journal> journals) throws IOException {
        // nothing to do
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

    protected void cleanupFiles(Collection<Journal> journals) throws IOException {
        for (Journal journal : journals) {
            Path jpath = journal.getJournalPath();
            deleteParts(journal.getEntries());
            Files.deleteIfExists(jpath);
        }
    }

    protected static void deleteParts(Collection<JournalEntry> entries) throws IOException {
        for (JournalEntry entry : entries) {
            Path p = Paths.get(entry.getVal());
            Files.deleteIfExists(p);
        }
    }

    /**
     * Add an error extension to a file
     * 
     * @param path to add an error ext
     */
    public static void renameToError(Path path) {
        try {
            Path errorPath = Paths.get(path.toString() + ERROR_EXT);
            Files.move(path, errorPath);
        } catch (IOException ex) {
            LOG.warn("Unable to rename file {}.", path.toString(), ex);
        }
    }

    /**
     * Rename journals and entries to failed
     * 
     * @param journals the journals to mark as error
     */
    protected static void renameJournalAndPartsToError(Collection<Journal> journals) {
        journals.forEach(Coalescer::renameJournalAndPartsToError);
    }

    /**
     * Rename a journal and entries to failed
     * 
     * @param journal the journal to mark as error
     */
    protected static void renameJournalAndPartsToError(Journal journal) {
        if (Files.exists(journal.getJournalPath())) {
            LOG.trace("Renaming journal to error {}", journal.getJournalPath());
            journal.getEntries().stream().distinct().forEach(journalEntry -> renameToError(Paths.get(journalEntry.getVal())));
            renameToError(journal.getJournalPath());
        }
    }

    /**
     * Cleanup any rolling/rolled files
     * 
     * @param dir the directory to search
     * @param key the key for the files
     * @throws IOException if there is an error removing the files
     */
    protected static void removeRollFiles(Path dir, String key) throws IOException {
        try (DirectoryStream<Path> paths = Files.newDirectoryStream(dir, key + ROLL_GLOB)) {
            for (Path path : paths) {
                LOG.trace("Deleting roll file {}", path);
                Files.deleteIfExists(path);
            }
        }
    }

    /**
     * Sometimes the rolled files can hang around after a crash. If there is a rolled file, that means all of the files
     * coalesced successfully but cleanup failed. If there are not any files with the same name, just rename the rolled
     * file. Otherwise, the rolled file will get cleaned up with the normal process.
     */
    protected void cleanupOrphanedRolledFiles() {
        cleanupOrphanedRolledFiles(1, outputPath + "/*" + ROLLED_EXT);
    }

    /**
     * Sometimes the rolled files can hang around after a crash. If there is a rolled file, that means all of the files
     * coalesced successfully but cleanup failed. If there are not any files with the same name, just rename the rolled
     * file. Otherwise, the rolled file will get cleaned up with the normal process.
     *
     * @param depth number of level to search
     * @param glob pattern to match
     */
    protected void cleanupOrphanedRolledFiles(final int depth, final String glob) {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
        try {
            for (Path entry : getJournalPaths(outputPath, depth, matcher)) {
                String finalOutputFilename = FilenameUtils.getBaseName(entry.toString());
                Path parent = entry.getParent();
                if (isOrphanedFile(parent, finalOutputFilename)) {
                    finalize(entry, parent.resolve(finalOutputFilename));
                }
            }
        } catch (IOException e) {
            LOG.error("There was an error trying to cleanup rolled files in {}{}", outputPath, glob, e);
        }
    }

    /**
     * Test to see if a rolled file is orphaned
     *
     * @param startsWith the name of the rolled file
     * @return true if no part/journal files exist, false otherwise
     * @throws IOException if there is an issue
     */
    protected boolean isOrphanedFile(Path parent, String startsWith) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(parent, startsWith + PART_GLOB)) {
            return !stream.iterator().hasNext();
        }
    }

    @Override
    public String toString() {
        return getClass().getName() + "{" +
                "outputPath=" + outputPath +
                '}';
    }
}
