package emissary.output.roller.coalesce;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

import emissary.output.roller.journal.Journal;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads journal files that have recorded offsets of completed writes to a set of part files. The Journal serves as a
 * write ahead log and records positions of all open file handles until rolled.
 * <p>
 * All Journals are identified and their outputs are combined into a destination filename denoted by the Journal key.
 * <p>
 * Tracks the number of times a Coalescer attempts to combine files. If the max attempts is met for a key, the journal
 * files are renamed with an error extension.
 */
public class MaxAttemptCoalescer extends Coalescer {

    private static final Logger logger = LoggerFactory.getLogger(MaxAttemptCoalescer.class);

    public static final int DEFAULT_MAX_ATTEMPTS = 3;

    /**
     * The maximum number of attempts to coalesce part files
     */
    protected int maxAttempts;
    /**
     * File extension used while cleanup of part files after coalescing has completed
     */
    public static final String ATTEMPT_EXT = ".bgattempt";

    /**
     * Take all files in a Path and combine them into a single destination file.
     *
     * @param outPath The Path to use for reading input and writing combined output
     * @throws IOException If there is some I/O problem.
     */
    public MaxAttemptCoalescer(final Path outPath) throws IOException {
        this(outPath, DEFAULT_SETUP_OUTPUT_PATH);
    }

    /**
     * Take all files in a Path and combine them into a single destination file.
     *
     * @param outPath The Path to use for reading input and writing combined output
     * @param setupOutputPath Create the output path if it does not exist
     * @throws IOException If there is some I/O problem.
     */
    public MaxAttemptCoalescer(final Path outPath, boolean setupOutputPath) throws IOException {
        this(outPath, DEFAULT_MAX_ATTEMPTS, setupOutputPath);
    }

    /**
     * Take all files in a Path and combine them into a single destination file.
     *
     * @param outPath The Path to use for reading input and writing combined output
     * @param maxAttempts The maximum number of attempts to coalesce part files
     * @throws IOException If there is some I/O problem.
     */
    public MaxAttemptCoalescer(final Path outPath, final int maxAttempts) throws IOException {
        this(outPath, maxAttempts, DEFAULT_SETUP_OUTPUT_PATH);
    }

    /**
     * Take all files in a Path and combine them into a single destination file.
     *
     * @param outPath The Path to use for reading input and writing combined output
     * @param maxAttempts The maximum number of attempts to coalesce part files
     * @param setupOutputPath Create the output path if it does not exist
     * @throws IOException If there is some I/O problem.
     */
    public MaxAttemptCoalescer(final Path outPath, final int maxAttempts, boolean setupOutputPath) throws IOException {
        super(outPath, setupOutputPath);
        this.maxAttempts = maxAttempts;
    }

    /**
     * Override the preRollHook method to track the number of coalesce attempts
     *
     * @param key The path to use for reading and writing files
     * @param journals The journal files currently needed to roll
     * @throws IOException if an error occurs
     */
    @Override
    protected void preRollHook(String key, Collection<Journal> journals) throws IOException {
        Path attemptPath = getAttemptPath(key, journals);
        int attempt = getAttempts(attemptPath);
        try {
            checkAttempts(attempt);
            incrementAttempts(key, attempt, attemptPath);
        } catch (TooManyAttemptsException e) {
            cleanupFailedAttempts(key, attemptPath, journals);
            throw new IOException("Maximum number of attempts reached for key " + key, e);
        }
    }

    /**
     * Get the path to the file containing the number of attempts
     *
     * @param key The path to use for reading and writing files
     * @param journals The journal files currently needed to roll
     * @return path to the attempt file
     * @throws IOException if an error occurs
     */
    protected Path getAttemptPath(String key, Collection<Journal> journals) throws IOException {
        String filename = key + ATTEMPT_EXT;
        logger.trace("Getting attempt path outputPath: {}, file: {}", outputPath, filename);
        return resolveOutputPath(journals, filename);
    }

    /**
     * Parse the attempt file and increment the attempt
     *
     * @param attemptsPath the attempt file
     * @return the attempt
     * @throws IOException if an error occurs
     */
    protected int getAttempts(Path attemptsPath) throws IOException {
        int attempt = 0;
        if (Files.exists(attemptsPath)) {
            try {
                logger.trace("Getting attempt from path {}", attemptsPath);
                String attemptStr = StringUtils.strip(new String(Files.readAllBytes(attemptsPath), StandardCharsets.UTF_8));
                attempt = StringUtils.isEmpty(attemptStr) ? 1 : Math.max(Integer.parseInt(attemptStr), 1);
                logger.trace("Found attempt {} from path {}", attempt, attemptsPath);
            } catch (IOException | NumberFormatException e) {
                logger.trace("The path {} exists but attempt could not be determined, setting to 1", attemptsPath, e);
                attempt = 1;
            }
        }
        return attempt;
    }

    /**
     * Check the attempt to make sure we have not tried too many times
     *
     * @param attempt the current attempt
     * @throws TooManyAttemptsException if the maximum number of attempts has been reached
     */
    protected void checkAttempts(int attempt) {
        if (attempt >= maxAttempts) {
            throw new TooManyAttemptsException("Maximum number of attempts reached");
        }
    }

    /**
     * Check the attempt to make sure we have not tried too many times
     *
     * @param key The path to use for reading and writing files
     * @param attempt the current attempt
     * @param attemptsPath the file containing the number of attempts to coalesce part files
     * @throws IOException if an error occurs
     */
    protected void incrementAttempts(String key, int attempt, Path attemptsPath) throws IOException {
        int nextAttempt = attempt + 1;
        logger.trace("Coalescing part files for key {}, attempt {} of {} ", key, nextAttempt, maxAttempts);
        Files.write(attemptsPath, Integer.toString(nextAttempt).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Check the attempt to make sure we have not tried too many times
     *
     * @param attemptsPath path to the attempt file
     * @param journals The journal files currently needed to roll
     * @throws IOException if an error occurs
     */
    protected void cleanupFailedAttempts(String key, Path attemptsPath, Collection<Journal> journals) throws IOException {
        renameJournalAndPartsToError(journals);
        removeRollFiles(attemptsPath.getParent(), key);
        Files.deleteIfExists(attemptsPath);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void finalize(Collection<Journal> journals, Path rolledOutputPath, Path finalOutputPath) throws IOException {
        // cleanup attempt file
        Path attempts = getAttemptPath(finalOutputPath.getFileName().toString(), journals);
        Files.deleteIfExists(attempts);
        super.finalize(journals, rolledOutputPath, finalOutputPath);
    }

    public static class TooManyAttemptsException extends RuntimeException {

        /**
         * Constructs a new runtime exception with the specified detail message.
         *
         * @param message the detail message. The detail message is saved for later retrieval by the getMessage() method.
         */
        public TooManyAttemptsException(String message) {
            super(message);
        }
    }

    @Override
    public String toString() {
        return getClass().getName() + "{" +
                "maxAttempts=" + maxAttempts +
                '}';
    }
}
