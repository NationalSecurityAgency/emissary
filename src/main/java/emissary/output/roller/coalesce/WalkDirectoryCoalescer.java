package emissary.output.roller.coalesce;

import static emissary.output.roller.journal.Journal.EXT;
import static emissary.output.roller.journal.JournalReader.getJournalPaths;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import emissary.output.roller.journal.Journal;
import emissary.output.roller.journal.JournalReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovers journal files on the file system for a given output and optional sub-directory path. Reads journal files
 * that have recorded offsets of completed writes to a set of part files. The Journal serves as a write ahead log and
 * records positions of all open file handles until rolled.
 * <p>
 * All Journals are identified and their outputs are combined into a destination filename denoted by the Journal key.
 */
public abstract class WalkDirectoryCoalescer extends MaxAttemptCoalescer {

    private static final Logger logger = LoggerFactory.getLogger(WalkDirectoryCoalescer.class);

    public static final String DEFAULT_SUB_DIR_MATCHER = "";
    /**
     * Perform match operations on journal files
     */
    protected PathMatcher journalMatcher;
    /**
     * Process sub-directories
     */
    protected String subDirMatcher;
    /**
     * Glob for sub-directories
     */
    protected String subDirGlob;

    /**
     * Take all files that do not have an open file handle and combine them into a single destination file.
     *
     * @param outPath The Path to use for reading input and writing combined output
     * @throws IOException If there is some I/O problem.
     */
    public WalkDirectoryCoalescer(final Path outPath) throws IOException {
        this(outPath, DEFAULT_SETUP_OUTPUT_PATH);
    }

    /**
     * Take all files that do not have an open file handle and combine them into a single destination file.
     *
     * @param outPath The Path to use for reading input and writing combined output
     * @param setupOutputPath Create the output path if it does not exist
     * @throws IOException If there is some I/O problem.
     */
    public WalkDirectoryCoalescer(final Path outPath, boolean setupOutputPath) throws IOException {
        this(outPath, DEFAULT_MAX_ATTEMPTS, setupOutputPath);
    }

    /**
     * Take all files that do not have an open file handle and combine them into a single destination file.
     *
     * @param outPath The Path to use for reading input and writing combined output
     * @param maxAttempts The maximum number of attempts to coalesce part files
     * @throws IOException If there is some I/O problem.
     */
    public WalkDirectoryCoalescer(final Path outPath, int maxAttempts) throws IOException {
        this(outPath, maxAttempts, DEFAULT_SETUP_OUTPUT_PATH);
    }

    /**
     * Take all files that do not have an open file handle and combine them into a single destination file.
     *
     * @param outPath The Path to use for reading input and writing combined output
     * @param maxAttempts The maximum number of attempts to coalesce part files
     * @param setupOutputPath Create the output path if it does not exist
     * @throws IOException If there is some I/O problem.
     */
    public WalkDirectoryCoalescer(final Path outPath, int maxAttempts, boolean setupOutputPath) throws IOException {
        this(outPath, DEFAULT_SUB_DIR_MATCHER, maxAttempts, setupOutputPath);
    }

    /**
     * Take all files that do not have an open file handle and combine them into a single destination file.
     *
     * @param outPath The Path to use for reading input and writing combined output
     * @param subDirMatcher walk sub directories looking for keys to coalesce
     * @throws IOException If there is some I/O problem.
     */
    public WalkDirectoryCoalescer(final Path outPath, final String subDirMatcher) throws IOException {
        this(outPath, subDirMatcher, DEFAULT_MAX_ATTEMPTS);
    }

    /**
     * Take all files that do not have an open file handle and combine them into a single destination file.
     *
     * @param outPath The Path to use for reading input and writing combined output
     * @param subDirMatcher walk sub directories looking for keys to coalesce
     * @param maxAttempts The maximum number of attempts to coalesce part files
     * @throws IOException If there is some I/O problem.
     */
    public WalkDirectoryCoalescer(final Path outPath, final String subDirMatcher, int maxAttempts) throws IOException {
        this(outPath, subDirMatcher, maxAttempts, DEFAULT_SETUP_OUTPUT_PATH);
    }

    /**
     * Take all files that do not have an open file handle and combine them into a single destination file.
     *
     * @param outPath The Path to use for reading input and writing combined output
     * @param subDirMatcher walk sub directories looking for keys to coalesce
     * @param setupOutputPath Create the output path if it does not exist
     * @throws IOException If there is some I/O problem.
     */
    public WalkDirectoryCoalescer(final Path outPath, final String subDirMatcher, boolean setupOutputPath) throws IOException {
        this(outPath, subDirMatcher, DEFAULT_MAX_ATTEMPTS, setupOutputPath);
    }

    /**
     * Take all files that do not have an open file handle and combine them into a single destination file.
     *
     * @param outPath The Path to use for reading input and writing combined output
     * @param subDirMatcher walk sub directories looking for keys to coalesce
     * @param maxAttempts The maximum number of attempts to coalesce part files
     * @param setupOutputPath Create the output path if it does not exist
     * @throws IOException If there is some I/O problem.
     */
    public WalkDirectoryCoalescer(final Path outPath, final String subDirMatcher, int maxAttempts, boolean setupOutputPath) throws IOException {
        super(outPath, maxAttempts, setupOutputPath);
        this.subDirMatcher = subDirMatcher;
        setup();
    }

    /**
     * Setup the path matcher
     */
    protected void setup() throws IOException {
        subDirGlob = outputPath + subDirMatcher + "/*";
        journalMatcher = FileSystems.getDefault().getPathMatcher("glob:" + subDirGlob + EXT);
        cleanupOrphanedRolledFilesSubDirs();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void coalesce() throws IOException {
        findClosedJournals().forEach(this::coalesceFiles);
    }

    /**
     * Find journals that do not have any open file handles
     *
     * @return journals grouped by key
     * @throws IOException if an error occurs
     */
    protected Map<String, List<Journal>> findClosedJournals() throws IOException {
        return filterJournals(findAllJournals());
    }

    /**
     * Find all journal files that match the {@link PathMatcher}
     *
     * @return journals matching a certain pattern
     * @throws IOException if an error occurs
     */
    protected Collection<Path> findAllJournals() throws IOException {
        logger.trace("Looking for journal files in output directory {}{}", subDirGlob, EXT);
        return getJournalPaths(outputPath, journalMatcher);
    }

    /**
     * Given a set of journals, remove any groups that still may be writing data
     *
     * @param journals the journals to check
     * @return journals grouped by key
     * @throws IOException if an error occurs
     */
    protected Map<String, List<Journal>> filterJournals(Collection<Path> journals) throws IOException {
        logger.trace("Filtering out open journal files, {}", journals);
        return JournalReader.getJournalsGroupedByKey(journals)
                .entrySet().stream()
                .filter(this::isJournalGroupReady)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private boolean isJournalGroupReady(Map.Entry<String, List<Journal>> entry) {
        return isJournalGroupReady(entry.getKey(), entry.getValue());
    }

    /**
     * Test to see if a journal grouping is still in use
     *
     * @param key the journal grouping key
     * @param journals the journals to check
     * @return true if the journals are ready to be coalesced, false otherwise
     */
    protected boolean isJournalGroupReady(String key, Collection<Journal> journals) {
        return true;
    }

    @Override
    protected Path getWorkingOutputPath(String key, Collection<Journal> journals) {
        return resolveOutputPath(journals, key + ROLLING_EXT);
    }

    @Override
    protected Path getRolledOutputPath(String key, Collection<Journal> journals) {
        return resolveOutputPath(journals, key + ROLLED_EXT);
    }

    @Override
    protected Path getFinalOutputPath(String key, Collection<Journal> journals) {
        return resolveOutputPath(journals, key);
    }

    @Override
    protected Path resolveOutputPath(Collection<Journal> journals, String filename) {
        return getOutputPath(journals).resolve(filename);
    }

    private Path getOutputPath(Collection<Journal> journals) {
        Optional<Journal> journal = journals.stream().findFirst();
        return journal.isPresent() ? journal.get().getJournalPath().getParent() : this.outputPath;
    }

    protected void cleanupOrphanedRolledFiles() {
        // should not do anything, but let's turn it off in case
    }

    /**
     * Sometimes the rolled files can hang around after a crash. If there is a rolled file, that means all of the files
     * coalesced successfully but cleanup failed. If there are not any files with the same name, just rename the rolled
     * file. Otherwise, the rolled file will get cleaned up with the normal process.
     */
    protected void cleanupOrphanedRolledFilesSubDirs() {
        cleanupOrphanedRolledFiles(Integer.MAX_VALUE, subDirGlob + ROLLED_EXT);
    }

    @Override
    public String toString() {
        return getClass().getName() + "{" +
                "outputPath=" + outputPath +
                ", subDirGlob='" + subDirGlob + '\'' +
                ", maxAttempts=" + maxAttempts +
                '}';
    }
}
