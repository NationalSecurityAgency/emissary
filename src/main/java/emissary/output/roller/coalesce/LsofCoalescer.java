package emissary.output.roller.coalesce;

import static emissary.output.roller.journal.Journal.ERROR_EXT;
import static emissary.output.roller.journal.JournaledChannelPool.EXTENSION;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import emissary.output.roller.journal.Journal;
import emissary.util.io.ListOpenFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovers journal files on the file system and checks to see if there is an open file handler (*nix systems only).
 * Reads journal files that have recorded offsets of completed writes to a set of part files. The Journal serves as a
 * write ahead log and records positions of all open file handles until rolled.
 * <p>
 * All Journals are identified and their outputs are combined into a destination filename denoted by the Journal key.
 */
public class LsofCoalescer extends WalkDirectoryCoalescer {

    private static final Logger logger = LoggerFactory.getLogger(LsofCoalescer.class);

    /**
     * Utility to see if the file is currently open for writing
     */
    protected ListOpenFiles lsofUtil;
    /**
     * Perform match operations on part files
     */
    protected PathMatcher partMatcher;

    /**
     * Take all files that do not have an open file handle and combine them into a single destination file.
     *
     * @param outPath The Path to use for reading input and writing combined output
     * @throws IOException If there is some I/O problem.
     */
    public LsofCoalescer(final Path outPath) throws IOException {
        this(outPath, DEFAULT_SETUP_OUTPUT_PATH);
    }

    /**
     * Take all files that do not have an open file handle and combine them into a single destination file.
     *
     * @param outPath The Path to use for reading input and writing combined output
     * @param setupOutputPath Create the output path if it does not exist
     * @throws IOException If there is some I/O problem.
     */
    public LsofCoalescer(final Path outPath, boolean setupOutputPath) throws IOException {
        this(outPath, DEFAULT_MAX_ATTEMPTS, setupOutputPath);
    }

    /**
     * Take all files that do not have an open file handle and combine them into a single destination file.
     *
     * @param outPath The Path to use for reading input and writing combined output
     * @param maxAttempts The maximum number of attempts to coalesce part files
     * @throws IOException If there is some I/O problem.
     */
    public LsofCoalescer(final Path outPath, int maxAttempts) throws IOException {
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
    public LsofCoalescer(final Path outPath, int maxAttempts, boolean setupOutputPath) throws IOException {
        this(outPath, DEFAULT_SUB_DIR_MATCHER, maxAttempts, setupOutputPath);
    }

    /**
     * Take all files that do not have an open file handle and combine them into a single destination file.
     *
     * @param outPath The Path to use for reading input and writing combined output
     * @param subDirMatcher walk sub directories looking for keys to coalesce
     * @throws IOException If there is some I/O problem.
     */
    public LsofCoalescer(final Path outPath, final String subDirMatcher) throws IOException {
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
    public LsofCoalescer(final Path outPath, final String subDirMatcher, int maxAttempts) throws IOException {
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
    public LsofCoalescer(final Path outPath, final String subDirMatcher, boolean setupOutputPath) throws IOException {
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
    public LsofCoalescer(final Path outPath, final String subDirMatcher, int maxAttempts, boolean setupOutputPath) throws IOException {
        super(outPath, subDirMatcher, maxAttempts, setupOutputPath);
    }

    /**
     * Setup the open file testing utility
     */
    @Override
    protected void setup() throws IOException {
        super.setup();
        lsofUtil = new ListOpenFiles();
        partMatcher = FileSystems.getDefault().getPathMatcher("glob:" + subDirGlob + EXTENSION);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void coalesce() throws IOException {
        super.coalesce();

        // cleanup empty part files that do not have a journal
        cleanupEmptyPartFiles(this.outputPath);
    }

    /**
     * Test to see if a journal grouping is still in use
     *
     * @param key the journal grouping key
     * @param journals the journals to check
     * @return true if the journals are ready to be coalesced, false otherwise
     */
    protected boolean isJournalGroupReady(String key, Collection<Journal> journals) {
        boolean closed = true;
        for (Journal journal : journals) {
            if (lsofUtil.isOpen(journal.getJournalPath())) {
                logger.trace("Journal {} is still open, skipping", journal.getJournalPath());
                closed = false;
                break;
            }
        }
        logger.debug("All journals for key {} are closed:{}", key, closed);
        return closed;
    }

    /**
     * Cleanup any files left over from a crash
     *
     * @param outputPath the path to the parts and journals
     */
    protected void cleanupEmptyPartFiles(Path outputPath) {
        cleanupEmptyPartFiles(outputPath, Integer.MAX_VALUE, partMatcher);
    }

    /**
     * Cleanup any files left over from a crash
     *
     * @param outputPath the path to the parts and journals
     */
    protected void cleanupEmptyPartFiles(Path outputPath, int depth, PathMatcher matcher) {
        try (Stream<Path> walk = Files.find(outputPath, depth, (p, attrs) -> matcher.matches(p) && p.toFile().length() == 0)) {
            List<Path> parts = walk.collect(Collectors.toList());
            for (Path part : parts) {
                cleanupEmptyFiles(part);
            }
        } catch (IOException e) {
            logger.error("There was an error trying to cleanup empty files in output dir: {}", outputPath, e);
        }
    }

    /**
     * If there is not a file handle to the empty part file, then we can remove the files.
     *
     * @param part the part file
     * @throws IOException if an error occurs
     */
    protected void cleanupEmptyFiles(Path part) throws IOException {
        if (!lsofUtil.isOpen(part)
                && !Files.exists(Paths.get(part + Journal.EXT))
                && !Files.exists(Paths.get(part + Journal.EXT + ERROR_EXT))) {
            logger.info("Found an empty part file {}, removing", part);
            Files.deleteIfExists(part);
        }
    }

    @Override
    public String toString() {
        return getClass().getName() + "{" +
                "outputPath=" + outputPath +
                ", subDirGlob='" + subDirGlob + '\'' +
                ", maxAttempts=" + maxAttempts +
                '}';
    }

    public static void main(String[] args) throws IOException {
        // ./emissary run emissary.output.roller.coalesce.LsofCoalescer /path/to/output /optional/sub/dir
        LsofCoalescer coalescer = new LsofCoalescer(Paths.get(args[0]), args.length > 1 ? args[1] : "");
        coalescer.coalesce();
    }
}
