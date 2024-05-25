package emissary.kff;

import emissary.core.channels.FileChannelFactory;
import emissary.core.channels.SeekableByteChannelFactory;
import emissary.test.core.junit5.UnitTest;
import emissary.util.io.ResourceReader;

import org.apache.commons.compress.utils.ByteUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.Validate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static emissary.kff.KffFile.DEFAULT_RECORD_LENGTH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class KffFileTest extends UnitTest {
    public static final Random RANDOM = new Random();
    private static final Logger LOGGER = LoggerFactory.getLogger(KffFileTest.class);

    private static final String ITEM_NAME = "Some_item_name";
    private static final byte[] expectedSha1Bytes = {(byte) 0, (byte) 0, (byte) 0, (byte) 32, (byte) 103, (byte) 56, (byte) 116,
            (byte) -114, (byte) -35, (byte) -110, (byte) -60, (byte) -29, (byte) -46, (byte) -24, (byte) 35, (byte) -119,
            (byte) 103, (byte) 0, (byte) -8, (byte) 73};
    private static final byte[] expectedCrcBytes = {(byte) -21, (byte) -47, (byte) 5, (byte) -96};
    private static KffFile kffFile;
    private static final String resourcePath = new ResourceReader()
            .getResource("emissary/kff/KffFileTest/tmp.bin").getPath();

    SeekableByteChannelFactory channelFactory = FileChannelFactory.create(Path.of(resourcePath));

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        kffFile = new KffFile(resourcePath, "testFilter", KffFilter.FilterType.Unknown);
        kffFile.setPreferredAlgorithm("SHA-1");
    }

    @Test
    void testKffFileCreation() {
        assertEquals("testFilter", kffFile.getName());
        kffFile.setFilterType(KffFilter.FilterType.Ignore);
        assertEquals(KffFilter.FilterType.Ignore, kffFile.getFilterType());
        assertEquals("SHA-1", kffFile.getPreferredAlgorithm());
    }

    @Test
    void testKffFileCheck() {
        ChecksumResults results = new ChecksumResults();
        results.setHash("SHA-1", expectedSha1Bytes);
        results.setHash("CRC32", expectedCrcBytes);
        try {
            assertTrue(kffFile.check(ITEM_NAME, results));
        } catch (Exception e) {
            fail(e);
        }
        byte[] incorrectSha1Bytes = {(byte) 0, (byte) 0, (byte) 0, (byte) 32, (byte) 103, (byte) 56, (byte) 116,
                (byte) -114, (byte) -35, (byte) -110, (byte) -60, (byte) -29, (byte) -46, (byte) -24, (byte) 35, (byte) -119,
                (byte) 103, (byte) 0, (byte) -8, (byte) 70};
        results = new ChecksumResults();
        results.setHash("SHA-1", incorrectSha1Bytes);
        try {
            assertFalse(kffFile.check(ITEM_NAME, results));
        } catch (Exception e) {
            fail(e);
        }
    }

    /**
     * Tests concurrent {@link KffFile#check(String, ChecksumResults)} invocations to ensure that method's thread-safety
     */
    @Test
    void testConcurrentKffFileCheckCalls() throws ExecutionException, IOException, InterruptedException {
        int EXPECTED_FAILURE_COUNT = 200;

        // the inputs we'll submit, along wth their expected KffFile.check return values
        List<CheckTestInput> testInputs = new ArrayList<>();

        // create inputs that should be found in the file
        parseRecordsFromBinaryFileAndAddToTestInputs(testInputs);
        int numberOfKffEntriesInTestFile = testInputs.size();

        // create inputs that should NOT be found in the file
        createRecordsFromRandomBytesAndAddToTestInputs(testInputs, EXPECTED_FAILURE_COUNT);

        shuffleTestInputs(testInputs);

        List<KffFileCheckTask> callables = createCallableTasksForParallelExecution(testInputs);

        logger.debug("testing {} invocations, with {} that should return true", callables.size(), numberOfKffEntriesInTestFile);

        ExecutorService executorService = null;
        try {
            executorService = Executors.newFixedThreadPool(10);
            // invoke the callable tasks concurrently using the thread pool and get their results
            List<Future<Boolean>> results = executorService.invokeAll(callables);
            for (Future<Boolean> result : results) {
                assertTrue(result.get(), "kffFile.check result didn't match expectations");
            }
        } finally {
            if (executorService != null) {
                executorService.shutdown();
            }
        }
    }

    private static void createRecordsFromRandomBytesAndAddToTestInputs(List<CheckTestInput> testInputs, int recordCount) {
        for (int i = 0; i < recordCount; i++) {
            // build a ChecksumResults entry with random bytes, and add it to our inputs with an expected value of false

            ChecksumResults csr = buildCheckSumResultsFromRandomBytes();
            CheckTestInput expectedFailure = new CheckTestInput(csr, false);
            testInputs.add(expectedFailure);
        }
    }

    private void parseRecordsFromBinaryFileAndAddToTestInputs(List<CheckTestInput> testInputs) throws IOException {
        int numberOfKffEntriesInTestFile;
        // parse "known entries" from the binary input file
        try (SeekableByteChannel byteChannel = channelFactory.create()) {
            numberOfKffEntriesInTestFile = (int) (byteChannel.size() / DEFAULT_RECORD_LENGTH);
            LOGGER.debug("test file contains {} known file entries", numberOfKffEntriesInTestFile);

            for (int i = 0; i < numberOfKffEntriesInTestFile; i++) {
                ChecksumResults csr = buildCheckSumResultsFromKffFileBytes(byteChannel, i * DEFAULT_RECORD_LENGTH);
                CheckTestInput expectedSuccess = new CheckTestInput(csr, true);
                testInputs.add(expectedSuccess);
            }
        }
    }

    /**
     * Randomly shuffles the test inputs so that expected failures are interspersed with expected successes
     * 
     * @param testInputs The collection of inputs
     */
    private static void shuffleTestInputs(List<CheckTestInput> testInputs) {
        Collections.shuffle(testInputs);
    }

    /**
     * Read a raw record from the binary KFF file on disk, and converts the raw bytes into a ChecksumResults object
     * 
     * @param sbc channel that exposes the file contents
     * @param startPosition offset within the channel at which the record begins
     * @return ChecksumResults object
     * @throws IOException if there is a problem reading bytes from the channel
     */
    private static ChecksumResults buildCheckSumResultsFromKffFileBytes(SeekableByteChannel sbc, int startPosition) throws IOException {
        sbc.position(startPosition);
        ByteBuffer buffer = ByteBuffer.wrap(new byte[DEFAULT_RECORD_LENGTH]);
        // read the "known file" entry into the buffer
        sbc.read(buffer);
        // convert the raw byte[] in a ChecksumResults object
        return buildChecksumResultsWithSha1AndCrc(buffer.array());
    }

    /**
     * Builds a {@link ChecksumResults} objects from 24 random bytes
     * 
     * @return a ChecksumResults object with contents that won't be found in the binary KFF file on disk
     */
    private static ChecksumResults buildCheckSumResultsFromRandomBytes() {
        byte[] randomBytes = new byte[DEFAULT_RECORD_LENGTH];
        RANDOM.nextBytes(randomBytes);
        // convert the raw byte[] in a ChecksumResults object
        return buildChecksumResultsWithSha1AndCrc(randomBytes);
    }

    /**
     * Creates a ChecksumResults instance from the provided bytes. The will have a SHA-1 hash value and CRC value.
     *
     * @param recordBytes input byte array, with expected length {@link KffFile#DEFAULT_RECORD_LENGTH}
     * @return the constructed ChecksumBytes instance
     */
    private static ChecksumResults buildChecksumResultsWithSha1AndCrc(byte[] recordBytes) {
        Validate.notNull(recordBytes, "recordBytes must not be null");
        Validate.isTrue(recordBytes.length == DEFAULT_RECORD_LENGTH, "recordBytes must include 24 elements");
        byte[] sha1Bytes = getSha1Bytes(recordBytes);
        byte[] crc32Bytes = getCrc32BytesLe(recordBytes);
        ChecksumResults csr = new ChecksumResults();
        csr.setHash("SHA-1", sha1Bytes);
        csr.setCrc(ByteUtils.fromLittleEndian(crc32Bytes));
        return csr;
    }

    /**
     * Retrieves the SHA-1 bytes from the provided array.
     *
     * @param recordBytes Bytes to parse
     * @return The SHA-1 bytes.
     */
    private static byte[] getSha1Bytes(byte[] recordBytes) {
        Validate.notNull(recordBytes, "recordBytes must not be null");
        Validate.isTrue(recordBytes.length == DEFAULT_RECORD_LENGTH, "recordBytes must include 24 elements");
        return Arrays.copyOfRange(recordBytes, 0, DEFAULT_RECORD_LENGTH - 4);
    }

    /**
     * Retrieves the last 4 bytes from the input array and reverses their order from big-endian to little-endian
     *
     * @param recordBytes Bytes to parse
     * @return the CRC32 bytes, in litte-endian order
     */
    private static byte[] getCrc32BytesLe(byte[] recordBytes) {
        Validate.notNull(recordBytes, "recordBytes must not be null");
        Validate.isTrue(recordBytes.length == DEFAULT_RECORD_LENGTH, "recordBytes must include 24 elements");
        byte[] result = Arrays.copyOfRange(recordBytes, DEFAULT_RECORD_LENGTH - 4, DEFAULT_RECORD_LENGTH);
        ArrayUtils.reverse(result);
        return result;
    }

    /**
     * Convert the inputs to a list of {@link Callable} tasks we can execute in parallel
     * 
     * @param testInputs List of inputs
     * @return List of Callables
     */
    private static List<KffFileCheckTask> createCallableTasksForParallelExecution(List<CheckTestInput> testInputs) {
        return testInputs.stream().map(input -> new KffFileCheckTask(kffFile, input.csr, input.expectedResult))
                .collect(Collectors.toList());
    }

    /**
     * Callable to allow for evaluation of {@link KffFile#check(String, ChecksumResults)} calls in parallel
     */
    static class KffFileCheckTask implements Callable<Boolean> {
        private final KffFile kffFile;
        private final ChecksumResults csr;
        private final Boolean expectedResult;

        KffFileCheckTask(KffFile kffFile, ChecksumResults csr, boolean expectedResult) {
            this.kffFile = kffFile;
            this.csr = csr;
            this.expectedResult = expectedResult;
        }

        /**
         * Computes a result, or throws an exception if unable to do so.
         *
         * @return computed result
         * @throws Exception if unable to compute a result
         */
        @Override
        public Boolean call() throws Exception {
            boolean actual = kffFile.check("ignored param", csr);
            // increase this log level to view stream of executions and results
            LOGGER.debug("expected {}, got {}", expectedResult, actual);
            return expectedResult.equals(actual);
        }
    }

    /**
     * Data Transfer Object (DTO) used for associating a {@link ChecksumResults} object and the expected result of
     * submitting that object to a {@link KffFile#check(String, ChecksumResults)} call
     */
    static class CheckTestInput {
        final ChecksumResults csr;
        final boolean expectedResult;

        CheckTestInput(ChecksumResults csr, boolean expectedResult) {
            this.csr = csr;
            this.expectedResult = expectedResult;
        }
    }
}
