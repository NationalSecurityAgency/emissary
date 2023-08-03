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

import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static emissary.kff.KffFile.DEFAULT_RECORD_LENGTH;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class KffFileTest extends UnitTest {
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

    @Test
    void testKffFileMain() {
        String[] args = {resourcePath, resourcePath};
        assertDoesNotThrow(() -> KffFile.main(args));
    }

    @Test
    /**
     * Tests concurrent KffFile.check invocations to ensure thread-safety
     */
    void testConcurrentKffFileCheckCalls() throws Exception {

        final Random RANDOM = new Random();
        ExecutorService executorService = null;

        // the inputs we'll submit, along wth their expected KffFile.check return values
        Map<ChecksumResults, Boolean> kffRecords = new HashMap<>();

        // parse "known entries" from the binary input file
        try (SeekableByteChannel byteChannel = channelFactory.create()) {
            int recordCount = (int) (byteChannel.size() / DEFAULT_RECORD_LENGTH);
            LOGGER.debug("test file contains {} known file entries", recordCount);

            byte[] recordBytes = new byte[DEFAULT_RECORD_LENGTH];
            ByteBuffer buffer = ByteBuffer.wrap(recordBytes);

            for (int i = 0; i < recordCount; i++) {
                buffer.clear();

                // parse the next "known file" entry and add it to our inputs, with an expected value of true
                byteChannel.position(i * DEFAULT_RECORD_LENGTH);
                // read the value into recordBytes
                byteChannel.read(buffer);
                ChecksumResults csr = buildChecksumResultsWithSha1AndCRC(recordBytes);
                kffRecords.put(csr, true);
            }
        }

        int EXPECTED_FAILURE_COUNT = 500;
        byte[] recordBytes = new byte[DEFAULT_RECORD_LENGTH];
        for (int j = 0; j < EXPECTED_FAILURE_COUNT; j++) {
            // build a ChecksumResults entry with random bytes, and add it to our inputs with an expected value of false
            RANDOM.nextBytes(recordBytes);
            ChecksumResults csr = buildChecksumResultsWithSha1AndCRC(recordBytes);
            kffRecords.put(csr, false);
        }

        // convert collection of inputs to a list of callable tasks we can execute in parallel
        List<KffFileCheckTask> callables = kffRecords.entrySet().stream()
                .map(entry -> new KffFileCheckTask(kffFile, entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        // shuffle the callables, so we have expected failures interspersed with expected successes
        Collections.shuffle(callables);

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

    /**
     * Creates a ChecksumResults instance from the provided bytes. The will have a SHA-1 hash value and CRC value.
     *
     * @param recordBytes input byte array, with expected length {@link KffFile#DEFAULT_RECORD_LENGTH}
     * @return the constructed ChecksumBytes instance
     */
    private static ChecksumResults buildChecksumResultsWithSha1AndCRC(byte[] recordBytes) {
        Validate.notNull(recordBytes, "recordBytes must not be null");
        Validate.isTrue(recordBytes.length == DEFAULT_RECORD_LENGTH, "recordBytes must include 24 elements");
        byte[] sha1Bytes = getSha1Bytes(recordBytes);
        byte[] crc32Bytes = getCrc32BytesLE(recordBytes);
        ChecksumResults csr = new ChecksumResults();
        csr.setHash("SHA-1", sha1Bytes);
        csr.setCrc(ByteUtils.fromLittleEndian(crc32Bytes));
        return csr;
    }

    /**
     * Callable to allow for evaluation of {@link KffFile#check(String, ChecksumResults)} calls in parallel
     */
    static class KffFileCheckTask implements Callable<Boolean> {
        private final KffFile kffFile;
        private final ChecksumResults csum;
        private final Boolean expectedResult;

        KffFileCheckTask(KffFile kffFile, ChecksumResults csum, boolean expectedResult) {
            this.kffFile = kffFile;
            this.csum = csum;
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
            boolean actual = kffFile.check("ignored param", csum);
            LOGGER.debug("expected {}, got {}", expectedResult, actual);
            return expectedResult.equals(actual);
        }
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
    private static byte[] getCrc32BytesLE(byte[] recordBytes) {
        Validate.notNull(recordBytes, "recordBytes must not be null");
        Validate.isTrue(recordBytes.length == DEFAULT_RECORD_LENGTH, "recordBytes must include 24 elements");
        byte[] result = Arrays.copyOfRange(recordBytes, DEFAULT_RECORD_LENGTH - 4, DEFAULT_RECORD_LENGTH);
        ArrayUtils.reverse(result);
        return result;
    }


}
