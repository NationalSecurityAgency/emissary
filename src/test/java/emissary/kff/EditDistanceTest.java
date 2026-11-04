package emissary.kff;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditDistanceTest {

    private static final int SINGLE_BYTE_DIFFERENCE_DISTANCE = 2;

    private static byte[] bytes(final int length) {
        final byte[] bytes = new byte[length];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i * 31 + 17);
        }
        return bytes;
    }

    private static byte[] withOneChangedByte(final byte[] bytes) {
        final byte[] changed = bytes.clone();
        changed[changed.length / 2] ^= 1;
        return changed;
    }

    @Test
    void calculatesRepeatedCallsWithChangingLengths() {
        final int[] lengths = {3, 64, 499, 8, 700, 32};

        for (int iteration = 0; iteration < 20; iteration++) {
            for (int length : lengths) {
                final byte[] input = bytes(length);
                assertEquals(SINGLE_BYTE_DIFFERENCE_DISTANCE,
                        EditDistance.calculateEditDistance(input, input.length, withOneChangedByte(input), input.length));
            }
        }
    }

    @Test
    void calculatesConcurrentDistancesIndependently() throws ExecutionException, InterruptedException, TimeoutException {
        final int threadCount = 8;
        final int comparisonsPerThread = 100;
        final CountDownLatch ready = new CountDownLatch(threadCount);
        final CountDownLatch start = new CountDownLatch(1);
        final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        final List<Future<Boolean>> results = new ArrayList<>();

        try {
            for (int thread = 0; thread < threadCount; thread++) {
                final byte[] input = bytes(64 + thread);
                final byte[] changed = withOneChangedByte(input);
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    for (int comparison = 0; comparison < comparisonsPerThread; comparison++) {
                        if (EditDistance.calculateEditDistance(input, input.length, changed, changed.length) != SINGLE_BYTE_DIFFERENCE_DISTANCE) {
                            return false;
                        }
                    }
                    return true;
                }));
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS), "Comparison tasks did not start");
            start.countDown();
            for (Future<Boolean> result : results) {
                assertTrue(result.get(10, TimeUnit.SECONDS), "Concurrent edit-distance calculation produced an incorrect result");
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "Comparison executor did not terminate");
        }
    }

    @Test
    void calculatesInputsAroundTheStaticStorageThreshold() {
        for (int length : new int[] {497, 498, 499}) {
            final byte[] input = bytes(length);
            final byte[] changed = withOneChangedByte(input);

            assertEquals(SINGLE_BYTE_DIFFERENCE_DISTANCE, EditDistance.calculateEditDistance(input, input.length, changed, changed.length));
            assertEquals(SINGLE_BYTE_DIFFERENCE_DISTANCE, EditDistance.calculateEditDistance(changed, changed.length, input, input.length));

            final byte[] shorter = Arrays.copyOf(input, length - 1);
            assertEquals(1, EditDistance.calculateEditDistance(input, input.length, shorter, shorter.length));
            assertEquals(1, EditDistance.calculateEditDistance(shorter, shorter.length, input, input.length));
        }
    }

    @Test
    void preservesEmptyAndNullInputBehavior() {
        final byte[] empty = new byte[0];
        final byte[] input = bytes(3);

        assertEquals(0, EditDistance.calculateEditDistance(null, 0, null, 0));
        assertEquals(3, EditDistance.calculateEditDistance(null, 0, input, input.length));
        assertEquals(3, EditDistance.calculateEditDistance(input, input.length, null, 0));
        assertEquals(0, EditDistance.calculateEditDistance(null, 0, empty, 0));
        assertEquals(0, EditDistance.calculateEditDistance(empty, 0, null, 0));
        assertEquals(0, EditDistance.calculateEditDistance(null, 10, null, 20));
        assertEquals(2, EditDistance.calculateEditDistance(null, 0, input, 2));
        assertEquals(2, EditDistance.calculateEditDistance(input, 2, null, 0));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> EditDistance.calculateEditDistance(empty, 0, empty, 0));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> EditDistance.calculateEditDistance(empty, 0, input, input.length));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> EditDistance.calculateEditDistance(input, input.length, empty, 0));
    }

    @Test
    void calculatesKnownDistances() {
        assertDistance("a", "a", 0);
        assertDistance("a", "b", 2); // Delete plus insert is cheaper than substitution (cost 3).
        assertDistance("abc", "abc", 0);
        assertDistance("abc", "ab", 1);
        assertDistance("ab", "abc", 1);
        assertDistance("abc", "axbc", 1);
        assertDistance("axbc", "abc", 1);
        assertDistance("abc", "axc", 2);
        assertDistance("ab", "ba", 2); // Transposition costs 5; delete plus insert costs 2.
        assertDistance("abc", "xyz", 6);
    }

    @Test
    void comparesOnlySpecifiedPrefixes() {
        final byte[] from = "abcX".getBytes(StandardCharsets.UTF_8);
        final byte[] to = "abcYmore".getBytes(StandardCharsets.UTF_8);
        assertEquals(0, EditDistance.calculateEditDistance(from, 3, to, 3));
        assertEquals(1, EditDistance.calculateEditDistance(from, 3, to, 2));
        assertEquals(1, EditDistance.calculateEditDistance(from, 2, to, 3));
    }

    @Test
    void matchesIndependentReferenceForDeterministicRandomInputs() {
        final Random random = new Random(253637199116L);
        for (int iteration = 0; iteration < 1000; iteration++) {
            final byte[] from = new byte[1 + random.nextInt(48)];
            final byte[] to = new byte[1 + random.nextInt(48)];
            // A small alphabet exercises matches, repeated bytes, and transposition candidates.
            for (int i = 0; i < from.length; i++) {
                from[i] = (byte) random.nextInt(4);
            }
            for (int i = 0; i < to.length; i++) {
                to[i] = (byte) random.nextInt(4);
            }
            assertEquals(referenceDistance(from, to, false), EditDistance.calculateEditDistance(from, from.length, to, to.length),
                    "Random comparison " + iteration);
        }
    }

    @Test
    void preservesEarlyTerminationAndLongInputOrientation() {
        // Freeze the existing partial-distance behavior rather than require an exact distance
        // after the row minimum exceeds 100. The old swapping code does not swap actual inputs.
        for (int[] lengths : new int[][] {{100, 100}, {101, 101}, {102, 102}, {520, 92}, {92, 520}, {520, 520}}) {
            final byte[] from = new byte[lengths[0]];
            final byte[] to = new byte[lengths[1]];
            Arrays.fill(from, (byte) 'a');
            Arrays.fill(to, (byte) 'b');
            assertEquals(referenceDistance(from, to, true), EditDistance.calculateEditDistance(from, from.length, to, to.length),
                    "Lengths " + from.length + ", " + to.length);
        }
    }

    private static void assertDistance(final String from, final String to, final int expected) {
        final byte[] source = from.getBytes(StandardCharsets.UTF_8);
        final byte[] target = to.getBytes(StandardCharsets.UTF_8);
        assertEquals(expected, EditDistance.calculateEditDistance(source, source.length, target, target.length), from + " -> " + to);
    }

    /** Independent full-matrix implementation with optional legacy early termination. */
    private static int referenceDistance(final byte[] from, final byte[] to, final boolean stopEarly) {
        final int[][] distances = new int[to.length + 1][from.length + 1];
        for (int col = 0; col <= from.length; col++) {
            distances[0][col] = col;
        }
        for (int row = 0; row <= to.length; row++) {
            distances[row][0] = row;
        }
        for (int row = 1; row <= to.length; row++) {
            int minimum = Integer.MAX_VALUE;
            for (int col = 1; col <= from.length; col++) {
                int distance = Math.min(distances[row - 1][col] + 1, distances[row][col - 1] + 1);
                distance = Math.min(distance, distances[row - 1][col - 1] + (from[col - 1] == to[row - 1] ? 0 : 3));
                if (row > 1 && col > 1 && from[col - 1] == to[row - 2] && from[col - 2] == to[row - 1]) {
                    distance = Math.min(distance, distances[row - 2][col - 2] + 5);
                }
                distances[row][col] = distance;
                minimum = Math.min(minimum, distance);
            }
            if (stopEarly && row > 1 && minimum > 100) {
                return distances[row][from.length];
            }
        }
        return distances[to.length][from.length];
    }
}
