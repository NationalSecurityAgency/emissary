package emissary.kff;

import jakarta.annotation.Nullable;

/**
 * A Java port of the ssdeep edit-distance code for fuzzy hashing.
 *
 * <p>
 * Originally written by Mark Maimone (CMU, November 1989), taken from trn3.6 and modified by Andrew Tridgell for
 * spamsum. This port originated from https://opensourceprojects.eu/p/triplecheck/code/23/tree/tool/src/ssdeep/. The
 * original authors make no claims as to fitness or correctness; use is at the user's own risk.
 * </p>
 */
public final class EditDistance {

    private static final int MIN_DIST = 100;
    private static final int INSERT_COST = 1;
    private static final int DELETE_COST = 1;
    private static final int CHANGE_COST = 3;
    private static final int SWAP_COST = 5;

    private EditDistance() {}

    private static int valueAt(final int[] buffer, final int radix, final int x, final int y, final int index) {
        if (x == 0) {
            return y * DELETE_COST;
        } else if (y == 0) {
            return x * INSERT_COST;
        }
        return buffer[index % radix];
    }

    /**
     * Calculates the weighted edit distance using invocation-local working storage.
     *
     * <p>
     * Preserves the legacy early termination: when a row minimum exceeds 100, the final cell of that row is returned, which
     * is not necessarily the exact distance for the complete inputs. Input orientation is preserved, even for long inputs,
     * because the original implementation's attempted swap had no effect on its actual inputs.
     * </p>
     *
     * @param from the source bytes, or {@code null}
     * @param fromLen the number of source bytes to compare
     * @param to the target bytes, or {@code null}
     * @param toLen the number of target bytes to compare
     * @return the distance, or the legacy partial distance after early termination
     * @throws ArrayIndexOutOfBoundsException if a non-null input array is empty, as in the legacy implementation
     */
    public static int calculateEditDistance(@Nullable final byte[] from, final int fromLen, @Nullable final byte[] to, final int toLen) {
        if (from == null) {
            return (to == null) ? 0 : toLen * INSERT_COST;
        } else if (to == null) {
            return fromLen * DELETE_COST;
        }

        // Two preceding rows and one extra cell for the transposition calculation.
        final int radix = 2 * fromLen + 3;
        final int[] buffer = new int[radix];
        int index = 0;

        buffer[index++] = Math.min(INSERT_COST + DELETE_COST, from[0] == to[0] ? 0 : CHANGE_COST);

        int low = buffer[(index + radix - 1) % radix];
        for (int col = 1; col < fromLen; col++) {
            buffer[index] = Math.min(col * DELETE_COST + (from[col] == to[0] ? 0 : CHANGE_COST),
                    Math.min((col + 1) * DELETE_COST + INSERT_COST, buffer[index - 1] + DELETE_COST));
            if (buffer[index] < low) {
                low = buffer[index];
            }
            index++;
        }

        for (int row = 1; row < toLen; row++) {
            for (int col = 0; col < fromLen; col++) {
                final int northwest = valueAt(buffer, radix, row, col, index + fromLen + 2);
                final int north = valueAt(buffer, radix, row, col + 1, index + fromLen + 3);
                final int west = valueAt(buffer, radix, row + 1, col, index + radix - 1);
                buffer[index] = Math.min(northwest + (from[col] == to[row] ? 0 : CHANGE_COST),
                        Math.min(north + INSERT_COST, west + DELETE_COST));

                if (from[col] == to[row - 1] && col > 0 && from[col - 1] == to[row]) {
                    buffer[index] = Math.min(buffer[index], valueAt(buffer, radix, row - 1, col - 1, index + 1) + SWAP_COST);
                }

                if (buffer[index] < low || col == 0) {
                    low = buffer[index];
                }
                index = (index + 1) % radix;
            }
            if (low > MIN_DIST) {
                break;
            }
        }

        return buffer[(index + radix - 1) % radix];
    }
}
