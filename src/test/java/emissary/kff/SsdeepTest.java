package emissary.kff;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Random;

import emissary.test.core.UnitTest;
import emissary.util.Hexl;
import org.junit.Test;

/** Unit tests for {@link Ssdeep}. */
public final class SsdeepTest extends UnitTest {

    private final Ssdeep ss = new Ssdeep();

    private static byte[] getStringAsUtf8(final String s) {
        try {
            return s.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("no UTF-8 encoding found", e);
        }
    }

    /**
     * Hash some input text and check the result.
     *
     * @param text The input text to hash. It will be treated as UTF-8 and the resulting bytes hashed.
     * @param expectedHash The expected hash value.
     */
    private void assertHash(final String text, final String expectedHash) {
        final byte[] input = getStringAsUtf8(text);
        final String hash = ss.fuzzy_hash(input);
        if (!expectedHash.equals(hash)) {
            fail("input \"" + text + "\" hashed to " + hash + " instead of the expected " + expectedHash);
        }
    }

    @Test
    public void testHashEmptyInput() {
        assertHash("", "3::");
    }

    @Test
    public void testHashZeros() {
        assertHash("\0", "3::");
        assertHash("\0\0", "3::");
        assertHash("\0\0\0", "3::");
        assertHash("\0\0\0\0", "3::");
        assertHash("\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0", "3::");
    }

    private static final String LOREM_IPSUM = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor "
            + "incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis "
            + "nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. "
            + "Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore "
            + "eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt "
            + "in culpa qui officia deserunt mollit anim id est laborum.";

    private static final String LOREM_IPSUM_HASH =
            "6:f4kPvtHMCMubyFtcwzIY7Xc4mqQM+9RrUPNAF8JlRnLpK7HjMFFXV7dFoaEDbFHP:AkPvt4u+b7kCMmQtg28RgkjF14bO8i2";

    @Test
    public void testHashLoremIpsum() {
        assertHash(LOREM_IPSUM, LOREM_IPSUM_HASH);
    }

    // Changing any of these parameters will require a corresponding
    // update in the expected values.
    private static final int BIG_RANDOM_ARRAY_LENGTH = 1024 * 1024;
    private static final int BIG_RANDOM_ARRAY_SEED = 12345;
    private static final String BIG_RANDOM_EXPECTED_HASH = "24576:6ZZbQq41uSGNiTzW3YSFXyLs4VipO02IB12xkVa7qVu:6F6utiO3rF9HNvB12xIa7H";

    @Test
    public void testHashBigRandomArray() {
        final byte[] input = new byte[BIG_RANDOM_ARRAY_LENGTH];
        // NOTE: Java guarantees that Random is deterministic for a
        // given seed and consistent across all implementations.
        new Random(BIG_RANDOM_ARRAY_SEED).nextBytes(input);
        final String hash = ss.fuzzy_hash(input);
        if (!BIG_RANDOM_EXPECTED_HASH.equals(hash)) {
            fail("random array (length=" + BIG_RANDOM_ARRAY_LENGTH + ", seed=" + BIG_RANDOM_ARRAY_SEED + ") hashed to \"" + hash
                    + "\" instead of the expected \"" + BIG_RANDOM_EXPECTED_HASH + "\"");
        }
    }

    // Changing any of these parameters will require a corresponding
    // update in the expected values.
    private static final int MANY_RANDOM_SEED = 246810;
    private static final int MANY_RANDOM_ITERATIONS = 10000;
    private static final int MANY_RANDOM_MIN_LENGTH = 1;
    private static final int MANY_RANDOM_MAX_LENGTH = 10000;
    private static final byte[] MANY_RANDOM_HASH_TRAILER = new byte[] {'|'};
    private static final int MANY_RANDOM_EXPECTED_INPUT_BYTES = 49728342;
    private static final int MANY_RANDOM_EXPECTED_HASH_CHARS = 766956;
    private static final String MANY_RANDOM_EXPECTED_HEX_DIGEST = "049f48a823c7441e4f679a5d1d08bc3615349690";

    @Test
    public void testHashManyRandomArrays() {
        // This is going to generate a big pile of random byte arrays
        // of various lengths, concatenate all of their hashes
        // together, and generate a digest of the concatenated results
        // for comparison to an expected value. If this test passes
        // then you can be very sure that you're getting consistent
        // results, but if if fails it may be very hard to figure out
        // which particular input was hashed differently.
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 not available even though Java guarantees it: " + e);
        }
        final Random rng = new Random(MANY_RANDOM_SEED);

        int totalInputBytes = 0;
        int totalHashChars = 0;
        for (int i = 0; i < MANY_RANDOM_ITERATIONS; i++) {
            final int len = rng.nextInt(MANY_RANDOM_MAX_LENGTH - MANY_RANDOM_MIN_LENGTH + 1) + MANY_RANDOM_MIN_LENGTH;
            final byte[] input = new byte[len];
            totalInputBytes += len;
            rng.nextBytes(input);
            final String hash = ss.fuzzy_hash(input);
            totalHashChars += hash.length();
            final byte[] hashBytes = getStringAsUtf8(hash);
            digest.update(hashBytes);
            digest.update(MANY_RANDOM_HASH_TRAILER);
        }
        final byte[] digestBytes = digest.digest();
        final String digestHex = Hexl.toUnformattedHexString(digestBytes);

        if ((totalInputBytes != MANY_RANDOM_EXPECTED_INPUT_BYTES) || (totalHashChars != MANY_RANDOM_EXPECTED_HASH_CHARS)
                || !digestHex.equals(MANY_RANDOM_EXPECTED_HEX_DIGEST)) {
            fail("mismatch in random arrays: expected " + MANY_RANDOM_EXPECTED_INPUT_BYTES + " bytes, " + MANY_RANDOM_EXPECTED_HASH_CHARS
                    + " hash characters, digest \"" + MANY_RANDOM_EXPECTED_HEX_DIGEST + "\" but got " + totalInputBytes + ", " + totalHashChars
                    + ", \"" + digestHex + "\"");
        }
    }

    @Test
    public void testCompareEqualHashes() {
        final SpamSumSignature hash1 = new SpamSumSignature(ss.fuzzy_hash(getStringAsUtf8(LOREM_IPSUM)));
        final SpamSumSignature hash2 = new SpamSumSignature(ss.fuzzy_hash(getStringAsUtf8(LOREM_IPSUM)));
        assertEquals("signatures from identical strings should produce a perfect score", 100, ss.Compare(hash1, hash2));
    }

    @Test
    public void testCompareCommutative() {
        final SpamSumSignature hash1 = new SpamSumSignature(ss.fuzzy_hash(getStringAsUtf8(LOREM_IPSUM)));
        final SpamSumSignature hash2 = new SpamSumSignature(ss.fuzzy_hash(getStringAsUtf8(LOREM_IPSUM + "x")));
        assertEquals("signature comparisons should not depend on the order", ss.Compare(hash1, hash2), ss.Compare(hash2, hash1));
    }

    // Changing the parameters will require a corresponding update in the expected scores.
    private static final int RANDOM_COMPARE_SEED = 13579;
    private static final int RANDOM_COMPARE_LENGTH = 400;
    private static final int RANDOM_COMPARE_MIN_CHANGE = 0;
    private static final int RANDOM_COMPARE_MAX_CHANGE = 10;
    private static final int[] RANDOM_COMPARE_EXPECTED_SCORES = {65, 80, 77, 96, 88, 74, 96, 88, 94, 93, 91, 100, 85, 85, 82, 75, 0, 77, 71, 82, 100,
            80, 88, 79, 75, 91, 79, 93, 96, 80, 83, 72, 99, 93, 66, 100, 91, 72, 80, 68,};

    @Test
    public void testCompareRandomHashes() {
        // We generate a sequence of random byte arrays and hash them,
        // then compare the adjacent hashes to generate a sequence of
        // scores. We generate the same number of scores as in the
        // expected score array.
        final int[] scores = new int[RANDOM_COMPARE_EXPECTED_SCORES.length];
        final Random rng = new Random(RANDOM_COMPARE_SEED);
        final byte[] input = new byte[RANDOM_COMPARE_LENGTH];
        rng.nextBytes(input);
        SpamSumSignature prevHash = new SpamSumSignature(ss.fuzzy_hash(input));
        for (int scoreIdx = 0; scoreIdx < scores.length; scoreIdx++) {
            // Generate the next input by adjusting some bytes in the
            // previous input. We want the inputs to be relatively
            // similar so they don't all just produce zero scores
            // every time.
            final int changeCount = rng.nextInt(RANDOM_COMPARE_MAX_CHANGE - RANDOM_COMPARE_MIN_CHANGE + 1) + RANDOM_COMPARE_MIN_CHANGE;
            for (int i = 0; i < changeCount; i++) {
                input[rng.nextInt(input.length)] = (byte) rng.nextInt();
            }
            final SpamSumSignature hash = new SpamSumSignature(ss.fuzzy_hash(input));
            scores[scoreIdx] = ss.Compare(prevHash, hash);
            prevHash = hash;
        }

        // Check that the scores match the expected scores.
        if (!Arrays.equals(RANDOM_COMPARE_EXPECTED_SCORES, scores)) {
            final StringBuilder b = new StringBuilder();
            b.append("mismatched random scores:");
            for (int i = 0; i < scores.length; i++) {
                if (RANDOM_COMPARE_EXPECTED_SCORES[i] != scores[i]) {
                    b.append(String.format(" (%d: got %d expected %d)", i, scores[i], RANDOM_COMPARE_EXPECTED_SCORES[i]));
                }
            }
            fail(b.toString());
        }
    }
}
