package emissary.kff;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A java port of the ssdeep code for "fuzzy hashing". http://ssdeep.sourceforge.net There are a number of ports out
 * there that all look basically the same. This one is from
 * https://opensourceprojects.eu/p/triplecheck/code/23/tree/tool/src/ssdeep/
 *
 * A new ssdeep hash gets calculated and saved at each level of unwrapping.
 */
public final class Ssdeep {

    private static final Logger logger = LoggerFactory.getLogger(Ssdeep.class);

    private static final int SPAMSUM_LENGTH = 64;
    private static final int MIN_BLOCKSIZE = 3;

    public final int FUZZY_MAX_RESULT = (SPAMSUM_LENGTH + (SPAMSUM_LENGTH / 2 + 20));

    /** The window size for the rolling hash. */
    private static final int ROLLING_WINDOW_SIZE = 7;

    /** The buffer size to use when reading data from a file. */
    private static final int BUFFER_SIZE = 8192;

    /** FNV hash initial value, 32-bit unsigned. */
    private static final long HASH_INIT = 0x28021967;

    /** FNV hash prime multiplier, 32-bit unsigned. */
    private static final long HASH_PRIME = 0x01000193;

    /** Used to mask long values to 32 bits unsigned. */
    private static final long MASK32 = 0xffffffffL;

    /**
     * Base64 encoding table. Given a 5-bit value {@code n}, position {@code n} in the array is the code point (expressed as
     * a byte) that should appear.
     */
    private static final byte[] b64Table = SpamSumSignature.GetBytes("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/");

    /**
     * Get the base64 encoding of the low 6 bits of the given value.
     *
     * @param v The value to encode.
     * @return The base64 encoding of the low 6 bits of {@code v}. The returned value is a code point expressed as a byte.
     */
    private static byte b64EncodeLowBits(final long v) {
        return b64Table[((int) v) & 0x3f];
    }

    private static final class SsContext {

        /** A buffer for the main hash output. */
        private final byte[] fuzzHash1 = new byte[SPAMSUM_LENGTH + 1];

        /** A buffer for the secondary hash output. */
        private final byte[] fuzzHash2 = new byte[SPAMSUM_LENGTH / 2 + 1];

        /** The count of output bytes currently stored in {@link #fuzzHash1}, initially zero. */
        private int fuzzLen1;

        /** The count of output bytes currently stored in {@link #fuzzHash2}, initially zero. */
        private int fuzzLen2;

        private long sumHash1; // Initially zero.
        private long sumHash2; // Initially zero.
        private long blockSize; // Initialized by constructor.

        /**
         * Estimate the block size to use.
         *
         * @param expectedInputLength The expected amount of data to be processed, in bytes. A 0 value can be used if the length
         *        is unknown, in which case a default block size is returned.
         * @return The estimated block size to use.
         */
        private static long estimateBlockSize(final long expectedInputLength) {
            long blockSize = MIN_BLOCKSIZE;
            while ((blockSize * SPAMSUM_LENGTH) < expectedInputLength) {
                blockSize *= 2;
            }
            return blockSize;
        }

        /**
         * Construct a spam sum context to process a file.
         *
         * @param f The file that will be processed, if known. If non-{@code null}, the length of the file is used to guess the
         *        hash block size to use.
         */
        public SsContext(final File f) {
            final long expectedInputLength = (f != null) ? f.length() : 0;
            this.blockSize = estimateBlockSize(expectedInputLength);
        }

        /**
         * Construct a spam sum context to process a byte array.
         *
         * @param data The bytes that will be processed, if known. If non-{@code null}, the length of the array is used to guess
         *        the hash block size to use.
         */
        public SsContext(final byte[] data) {
            final long expectedInputLength = (data != null) ? data.length : 0;
            this.blockSize = estimateBlockSize(expectedInputLength);
        }

        /**
         * A simple non-rolling hash, based on the FNV hash
         * 
         * @param b The next byte value, assumed to be in the range 0..255.
         * @param h The existing hash value, 32-bit unsigned.
         * @return The updated hash value, 32-bit unsigned.
         */
        private static long updateSumHash(final int b, final long h) {
            return ((h * HASH_PRIME) ^ b) & MASK32;
        }

        /**
         * Apply some bytes to a SpamSum context.
         *
         * @param rollState The rolling hash state to use.
         * @param buffer A buffer containing the input bytes.
         * @param start The starting offset in {@code buffer}, inclusive.
         * @param end The ending offset in {@code buffer}, exclusive.
         */
        private void applyBytes(final RollingState rollState, final byte[] buffer, final int start, final int end) {
            // At each byte we update the rolling hash and the normal
            // hash. When the rolling hash hits the reset value, we
            // emit the normal hash as an element of the signature and
            // reset both hashes.
            for (int i = start; i < end; i++) {
                // Get the next input byte and normalize to 0..255.
                final int nextByte = ((int) buffer[i]) & 0xff;

                // Apply the next byte to the hashes.
                this.sumHash1 = updateSumHash(nextByte, this.sumHash1);
                this.sumHash2 = updateSumHash(nextByte, this.sumHash2);
                final long rollingHash = rollState.roll(nextByte);

                if ((rollingHash % this.blockSize) == (this.blockSize - 1)) {
                    // We have hit a reset point. We now emit a hash
                    // which is based on all bytes in the input
                    // between the last reset point and this one.
                    if (this.fuzzLen1 < (SPAMSUM_LENGTH - 1)) {
                        // We can have a problem with the tail
                        // overflowing. The easiest way to cope with
                        // this is to only reset the second hash if we
                        // have room for more characters in our
                        // signature. This has the effect of combining
                        // the last few pieces of the message into a
                        // single piece
                        this.fuzzHash1[this.fuzzLen1++] = b64EncodeLowBits(this.sumHash1);
                        this.sumHash1 = HASH_INIT;
                    }

                    // This produces a second signature with a block size
                    // of blockSize*2. By producing dual signatures in
                    // this way the effect of small changes in the message
                    // size near a block size boundary is greatly reduced.
                    //
                    // NOTE: we only have to check this when the main
                    // signature has hit a reset point, because
                    // mathematically:
                    //
                    // [ h === -1 (mod 2*bs) ] --implies--> [ h === -1 (mod bs) ]
                    //
                    // In other words, if this condition is true then the
                    // main signature condition must always also be true.
                    // Therefore this secondary signature condition can
                    // only potentially be true if the main signature
                    // condition (which we've already checked) is true.
                    if ((rollingHash % (this.blockSize * 2)) == ((this.blockSize * 2) - 1)) {
                        if (this.fuzzLen2 < (SPAMSUM_LENGTH / 2 - 1)) {
                            this.fuzzHash2[this.fuzzLen2++] = b64EncodeLowBits(this.sumHash2);
                            this.sumHash2 = HASH_INIT;
                        }
                    }
                }
            }
        }

        /**
         * Discard any existing hash state and prepare to compute a new hash. This should be followed by calls to
         * {@link #applyBytes(RollingState, byte[], int, int)} to provide the data, and then
         * {@link #finishHashing(RollingState)} to complete the computations.
         */
        private void beginHashing() {
            this.fuzzLen1 = 0;
            this.fuzzLen2 = 0;
            this.sumHash1 = HASH_INIT;
            this.sumHash2 = HASH_INIT;
        }

        /**
         * Truncate an array if larger than the given length.
         *
         * @param input The input array.
         * @param maxLength The desired maximum array length.
         * @return If {@code input} is no larger than {@code maxLength}, this just returns {@code input}. Otherwise this returns
         *         a new array with the same content as {@code input} but with the length truncated to {@code maxLength}.
         */
        private static byte[] truncateArray(final byte[] input, final int maxLength) {
            if (input.length == maxLength) {
                return input;
            } else {
                return Arrays.copyOf(input, maxLength);
            }
        }

        /**
         * Finish hashing and generate the final signature. This should be done after all bytes have been applied with
         * {@link #applyBytes(RollingState, byte[], int, int)}.
         *
         * @param rollState The rolling hash state used during hashing.
         * @return The final signature.
         */
        private SpamSumSignature finishHashing(final RollingState rollState) {
            if (rollState.getHash() != 0) {
                this.fuzzHash1[this.fuzzLen1++] = b64EncodeLowBits(this.sumHash1);
                this.fuzzHash2[this.fuzzLen2++] = b64EncodeLowBits(this.sumHash2);
            }

            final byte[] finalHash1 = truncateArray(this.fuzzHash1, this.fuzzLen1);
            final byte[] finalHash2 = truncateArray(this.fuzzHash2, this.fuzzLen2);
            return new SpamSumSignature(this.blockSize, finalHash1, finalHash2);
        }

        /**
         * Generate the hash for some input.
         *
         * <p>
         * The computations will use the current block size from the context, but any other existing hash state will be
         * discarded.
         *
         * @param data The bytes to hash.
         * @return The signature for the given data.
         */
        public SpamSumSignature generateHash(final byte[] data) {
            beginHashing();
            final RollingState rollState = new RollingState();

            if (data != null) {
                applyBytes(rollState, data, 0, data.length);
            }

            return finishHashing(rollState);
        }

        /**
         * Generate the hash for some input.
         *
         * <p>
         * The computations will use the current block size from the context, but any other existing hash state will be
         * discarded.
         *
         * @param stream A file containing the bytes to hash. Assumed non-{@code null}. The processing will start reading at the
         *        current file position and hash all of the data from there to the end of the file. The file position when this
         *        returns is unspecified. The file is not closed by this operation.
         * @return The signature for the given stream content.
         * @throws IOException If there is some I/O problem while reading the stream.
         */
        public SpamSumSignature generateHash(final RandomAccessFile stream) throws IOException {
            beginHashing();
            final RollingState rollState = new RollingState();

            final byte[] buffer = new byte[BUFFER_SIZE];
            while (true) {
                final int bytesRead = stream.read(buffer, 0, buffer.length);
                if (bytesRead <= 0) {
                    break; // No more input.
                }
                applyBytes(rollState, buffer, 0, bytesRead);
            }

            return finishHashing(rollState);
        }
    }

    /**
     * A rolling hash, based on the Adler checksum. By using a rolling hash we can perform auto resynchronisation after
     * inserts/deletes.
     */
    private static final class RollingState {

        /** Rolling window. Each value is in the range 0..255. Initially all 0. */
        private final int[] window = new int[ROLLING_WINDOW_SIZE];

        /** An index into {@link #window}. Initially 0. */
        private int windowPosition;

        /** The sum of the values in {@link #window}. Initially 0. */
        private long h1;

        /**
         * The original documentation says this is the sum of the bytes times the index, but I'm not sure about that. 32-bit
         * unsigned, initially 0.
         */
        private long h2;

        /**
         * A shift/xor based rolling hash, mostly needed to ensure that we can cope with large blocksize values. 32-bit
         * unsigned, initially 0.
         */
        private long h3;

        /**
         * Construct a new rolling hash state.
         */
        public RollingState() {}

        /**
         * Get the current hash value.
         *
         * @return The current 32-bit unsigned hash value.
         */
        public long getHash() {
            return (this.h1 + this.h2 + this.h3) & MASK32;
        }

        /**
         * Update the rolling hash state with another input byte.
         *
         * @param b The byte value to apply. Assumed to be in the range 0..255.
         * @return The state is updated and the resulting unsigned 32-bit hash value is returned.
         */
        public long roll(final int b) {
            this.h2 = (this.h2 - this.h1 + (ROLLING_WINDOW_SIZE * b)) & MASK32;
            this.h1 = (this.h1 + b - this.window[this.windowPosition]) & MASK32;
            this.window[this.windowPosition] = b;

            // Advance the window position, wrappping around at the end.
            if (this.windowPosition == (ROLLING_WINDOW_SIZE - 1)) {
                this.windowPosition = 0;
            } else {
                this.windowPosition++;
            }

            this.h3 = ((this.h3 << 5) & MASK32) ^ b;

            return (this.h1 + this.h2 + this.h3) & MASK32;
        }
    }

    public Ssdeep() {}

    /**
     * Calculate the SpamSum hash for a byte array.
     *
     * @param data The bytes to be hashed.
     * @return The SpamSum signature for the bytes.
     */
    public String fuzzy_hash(final byte[] data) {
        final SsContext ctx = new SsContext(data);
        while (true) {
            final SpamSumSignature signature = ctx.generateHash(data);

            // Our blocksize guess may have been way off, repeat with
            // a smaller block size if necessary.
            if ((ctx.blockSize > MIN_BLOCKSIZE) && (ctx.fuzzLen1 < (SPAMSUM_LENGTH / 2))) {
                ctx.blockSize = ctx.blockSize / 2;
            } else {
                return signature.toString();
            }
        }
    }

    /**
     * Calculates the SpamSum hash for specified stream.
     * 
     * @param file The input file to be hashed.
     * @return The SpamSum signature for the file.
     * @throws IOException If there is some I/O problem accessing the file.
     */
    public String fuzzy_hash_file(final File file) throws IOException {
        final RandomAccessFile stream = new RandomAccessFile(file, "r");
        try {
            final SsContext ctx = new SsContext(file);
            while (true) {
                stream.seek(0);
                final SpamSumSignature signature = ctx.generateHash(stream);

                // Our blocksize guess may have been way off, repeat with
                // a smaller block size if necessary.
                if ((ctx.blockSize > MIN_BLOCKSIZE) && (ctx.fuzzLen1 < (SPAMSUM_LENGTH / 2))) {
                    ctx.blockSize = ctx.blockSize / 2;
                } else {
                    return signature.toString();
                }
            }
        } finally {
            stream.close();
        }
    }

    /**
     * Calculates the SpamSum hash for specified file.
     *
     * @param fileName The path to the file to be hashed.
     * @return The SpamSum signature for the file.
     * @throws IOException If there is some I/O problem accessing the file.
     */
    public String fuzzy_hash_file(final String fileName) throws IOException {
        return this.fuzzy_hash_file(new File(fileName));
    }

    /**
     * Search an array for a subsequence of another array.
     *
     * @param haystack The array to search.
     * @param needle The array containing the sequence to search for.
     * @param needleStart The starting offset of the sequence to search for, inclusive. Assumed to be in range for
     *        {@code needle}.
     * @param length The length of the sequence to search for. Assumed to be in range for {@code needle}. Assumed greater
     *        than zero.
     * @return If the subsequence of {@code needle} is present in {@code haystack}, this returns the least offset where the
     *         subsequence occurs. Otherwise -1.
     */
    private static int indexOfSubSequence(final byte[] haystack, final byte[] needle, final int needleStart, final int length) {
        final int lastCandidatePos = haystack.length - length;
        final byte firstNeedleByte = needle[needleStart];
        NEXT_CANDIDATE: for (int candidatePos = 0; candidatePos <= lastCandidatePos; candidatePos++) {
            if (haystack[candidatePos] == firstNeedleByte) {
                // The first needle byte matches at this candidate
                // position, so look for the rest of the needle
                // following it.
                for (int needlePos = 1; needlePos < length; needlePos++) {
                    if (haystack[candidatePos + needlePos] != needle[needleStart + needlePos]) {
                        continue NEXT_CANDIDATE; // Needle mismatch.
                    }
                }
                // If we reach here, the entire needle subsequence
                // matched in the haystack at the candidate position.
                return candidatePos;
            }
        }
        return -1;
    }

    /**
     * Search two arrays for a common subsequence of the given length.
     *
     * @param s1 The first byte array for comparison.
     * @param s2 The second byte array for comparison.
     * @param length The substring length to look for. Assumed greater than zero.
     * @return {@code true} iff {@code s1} and {@code s2} have at least one byte sequence of length {@code length} in common
     *         at arbitrary offsets.
     */
    private static boolean hasCommonSequence(final byte[] s1, final byte[] s2, final int length) {
        if ((s1.length < length) || (s2.length < length)) {
            return false; // The strings are not large enough.
        }

        // This is just a brute-force approach. We move a window of
        // the specified length through s1 and check whether it exists
        // anywhere in s2.
        final int lastS1Pos = s1.length - length;
        for (int s1Pos = 0; s1Pos <= lastS1Pos; s1Pos++) {
            final int s2Pos = indexOfSubSequence(s2, s1, s1Pos, length);
            if (s2Pos != -1) {
                if (logger.isDebugEnabled()) {
                    logger.debug("found common sequence " + new String(s1, s1Pos, length) + " in " + new String(s1) + " at " + s1Pos + " and "
                            + new String(s2) + " at " + s2Pos);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Truncate sequences of longer than 3 identical bytes. These sequences contain very little information so they tend to
     * just bias the result unfairly.
     *
     * @param in The input bytes.
     * @return An array containing the same content as {@code in}, except that any sequences of more than 3 identical bytes
     *         are truncated to 3 bytes. For example "aaabbbbcddddd" becomes "aaabbbcddd".
     */
    private static byte[] eliminateLongSequences(final byte[] in) {
        if (in.length < 4) {
            return in; // There is not enough input to require any change.
        }

        // We just need to initialize prev to something other than in[0].
        byte prev = (in[0] != 0) ? 0 : (byte) 1;
        int repeatCount = 0;

        // Scan the input looking for the index of the first byte that
        // will need to be removed.
        int inPos = 0;
        while (true) {
            if (inPos == in.length) {
                // We didn't find anything that needed to be removed.
                return in;
            } else if (in[inPos] == prev) {
                // This is a repeat of the previous byte.
                repeatCount++;
                if (repeatCount == 3) {
                    break; // inPos needs to be removed.
                }
            } else {
                // This is not a repeat of the previous byte.
                prev = in[inPos];
                repeatCount = 0;
            }
            inPos++;
        }

        // At this point inPos is the first index that needs to be
        // removed, prev is set to its byte value, and repeatCount is
        // set to 3. Start an output array and copy everything up to
        // but not including inPos.
        final byte[] out = new byte[in.length - 1];
        System.arraycopy(in, 0, out, 0, inPos);
        int outPos = inPos;

        // Continue scanning and copying to output.
        while (++inPos < in.length) {
            if (in[inPos] == prev) {
                repeatCount++;
            } else {
                prev = in[inPos];
                repeatCount = 0;
            }
            if (repeatCount < 3) {
                out[outPos++] = in[inPos];
            }
        }

        return (outPos == out.length) ? out : Arrays.copyOf(out, outPos);
    }

    /**
     * This is the low level string scoring algorithm. It takes two strings and scores them on a scale of 0-100 where 0 is a
     * terrible match and 100 is a great match. The blockSize is used to cope with very small messages.
     */
    private static long scoreStrings(final byte[] s1, final byte[] s2, final long blockSize) {
        final int len1 = s1.length;
        final int len2 = s2.length;

        if ((len1 > SPAMSUM_LENGTH) || (len2 > SPAMSUM_LENGTH)) {
            // not a real spamsum signature?
            return 0;
        }

        // The two strings must have a common substring of length
        // ROLLING_WINDOW_SIZE to be candidates.
        if (!hasCommonSequence(s1, s2, ROLLING_WINDOW_SIZE)) {
            if (logger.isDebugEnabled()) {
                logger.debug("no common substring for '" + new String(s1) + "' and '" + new String(s2) + "'");
            }
            return 0;
        }

        // Compute the edit distance between the two strings. The edit
        // distance gives us a pretty good idea of how closely related
        // the two strings are.
        long score = EditDistance.edit_distn(s1, len1, s2, len2);
        if (logger.isDebugEnabled()) {
            logger.debug("edit_dist: " + score);
        }

        // Scale the edit distance by the lengths of the two
        // strings. This changes the score to be a measure of the
        // proportion of the message that has changed rather than an
        // absolute quantity. It also copes with the variability of
        // the string lengths.
        score = (score * SPAMSUM_LENGTH) / (len1 + len2);

        // At this stage the score occurs roughly on a 0-64 scale,
        // with 0 being a good match and 64 being a complete mismatch.

        // Rescale to a 0-100 scale (friendlier to humans).
        score = (100 * score) / 64;

        // It is possible to get a score above 100 here, but it is a
        // really terrible match.
        if (score >= 100) {
            return 0;
        }

        // Now re-scale on a 0-100 scale with 0 being a poor match and
        // 100 being a excellent match.
        score = 100 - score;

        // When the blocksize is small we don't want to exaggerate the
        // match size.
        if (score > (blockSize / MIN_BLOCKSIZE * Math.min(len1, len2))) {
            score = blockSize / MIN_BLOCKSIZE * Math.min(len1, len2);
        }
        return score;
    }

    /**
     * Given two spamsum signature return a value indicating the degree to which they match.
     *
     * @param signature1 The first signature.
     * @param signature2 The second signature.
     * @return The score for the two signatures. The value is in the range 0..100, where 0 is a terrible match and 100 is a
     *         great match.
     */
    public int Compare(final SpamSumSignature signature1, final SpamSumSignature signature2) {
        if ((null == signature1) || (null == signature2)) {
            return -1;
        }
        final long blockSize1 = signature1.getBlockSize();
        final long blockSize2 = signature2.getBlockSize();

        // We require the block sizes to either be equal, or for one
        // to be twice the other. If the blocksizes don't match then
        // we are comparing apples to oranges. This isn't an 'error'
        // per se. We could have two valid signatures, but they can't
        // be compared.
        if ((blockSize1 != blockSize2) && (blockSize1 != (blockSize2 * 2)) && (blockSize2 != (blockSize1 * 2))) {
            if (logger.isDebugEnabled()) {
                logger.debug("block sizes too different: " + blockSize1 + " " + blockSize2);
            }
            return 0;
        }

        // There is very little information content is sequences of
        // the same character like 'LLLLL'. Eliminate any sequences
        // longer than 3. This is especially important when combined
        // with the hasCommonSequence() test.
        final byte[] s1First = eliminateLongSequences(signature1.getHashPart1());
        final byte[] s1Second = eliminateLongSequences(signature1.getHashPart2());
        final byte[] s2First = eliminateLongSequences(signature2.getHashPart1());
        final byte[] s2Second = eliminateLongSequences(signature2.getHashPart2());

        // Each signature has a string for two block sizes. We now
        // choose how to combine the two block sizes. We checked above
        // that they have at least one block size in common.
        final long score;
        if (blockSize1 == blockSize2) {
            // The signature block sizes are equal.
            final long score1 = scoreStrings(s1First, s2First, blockSize1);
            final long score2 = scoreStrings(s1Second, s2Second, blockSize2);
            score = Math.max(score1, score2);
        } else if (blockSize1 == (blockSize2 * 2)) {
            // The first signature has twice the block size of the second.
            score = scoreStrings(s1First, s2Second, blockSize1);
        } else {
            // The second signature has twice the block size of the first.
            score = scoreStrings(s1Second, s2First, blockSize2);
        }

        return (int) score;
    }

    public static void main(final String[] args) throws Exception {
        final Ssdeep ss = new Ssdeep();
        for (final String f : args) {
            final FileInputStream is = new FileInputStream(f);
            final byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            // output format matches the original ssdeep program
            System.out.println(ss.fuzzy_hash(buffer) + ",\"" + f + "\"");
        }
    }
}
