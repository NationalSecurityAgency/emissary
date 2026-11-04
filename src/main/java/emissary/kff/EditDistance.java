package emissary.kff;

import jakarta.annotation.Nullable;

/**
 * A java port of the ssdeep code for "fuzzy hashing". http://ssdeep.sourceforge.net There are a number of ports out
 * there that all look basically the same. This one is from
 * https://opensourceprojects.eu/p/triplecheck/code/23/tree/tool/src/ssdeep/
 *
 * A new ssdeep hash gets calculated and saved at each level of unwrapping.
 */
@SuppressWarnings("NonFinalStaticField")
public class EditDistance {
    /*
     * This edit distance code is taken from trn3.6. A few minor modifications have been made by Andrew Tridgell
     * <tridge@samba.org> for use in spamsum.
     */


    /***************************************************************************/


    /*
     * The authors make no claims as to the fitness or correctness of this software for any use whatsoever, and it is
     * provided as is. Any use of this software is at the user's own risk.
     */

    /*
     * edit_dist -- returns the minimum edit distance between two strings
     *
     * Program by: Mark Maimone CMU Computer Science 13 Nov 89 Last Modified: 28 Jan 90
     *
     * If the input strings have length n and m, the algorithm runs in time O(nm) and space O(min(m,n)).
     *
     * HISTORY 13 Nov 89 (mwm) Created edit_dist() and set_costs().
     *
     * 28 Jan 90 (mwm) Added view_costs(). Should verify that THRESHOLD computations will work even when THRESHOLD is not a
     * multiple of sizeof(int).
     *
     * 17 May 93 (mwm) Improved performance when used with trn's newsgroup processing; assume all costs are 1, and it's okay
     * to terminate when a threshold is exceeded.
     */

    private static final int MIN_DIST = 100;

    /*
     * Use a less-general version of the routine, one that's better for trn. All change costs are 1, and it's okay to
     * terminate if the edit distance is known to exceed MIN_DIST
     */

    private static final int THRESHOLD = 4000; /*
                                                * worry about allocating more memory only when this # of bytes is exceeded
                                                */

    private static final int STRLENTHRESHOLD = (THRESHOLD / (Integer.SIZE / 8) - 3) / 2;

    // #define SAFE_ASSIGN(x,y) (((x) != NULL) ? (*(x) = (y)) : (y))

    // #define swap_int(x,y) (_iswap = (x), (x) = (y), (y) = _iswap)
    private static void swapInt(int[] x, int[] y) {
        int iswap = x[0];
        x[0] = y[0];
        y[0] = iswap;
    }

    // #define swap_char(x,y) (_cswap = (x), (x) = (y), (y) = _cswap)
    private static void swapChar(/* ref */byte[][] x, /* ref */byte[][] y) {
        byte[] cswap = x[0];
        x[0] = y[0];
        y[0] = cswap;
    }

    // #define min3(x,y,z) (_mx = (x), _my = (y), _mz = (z), (_mx < _my ? (_mx < _mz ? _mx : _mz) : (_mz < _my) ? _mz :
    // _my))
    private static int min3(int x, int y, int z) {
        int mx = x;
        int my = y;
        int mz = z;
        return mx < my ? (mx < mz ? mx : mz) : (mz < my) ? mz : my;
    }

    // #define min2(x,y) (_mx = (x), _my = (y), (_mx < _my ? _mx : _my))
    private static int min2(int x, int y) {
        int mx = x;
        int my = y;
        return mx < my ? mx : my;
    }

    static int insertCost = 1;
    static int deleteCost = 1;

    private static final ThreadLocal<CalculationState> CALCULATION_STATE = ThreadLocal.withInitial(CalculationState::new);

    private static final class CalculationState {
        private int row;
        private int col;
        private int index;
        private int radix;
        private int low;
        private int fromLen;
        private int toLen;
        private int[] buffer;
        private final int[] store = new int[THRESHOLD / (Integer.SIZE / 8)];
    }

    /* Handle trivial cases when one string is empty */

    static int ins = 1;
    static int del = 1;
    static int ch = 3;
    static int swapCost = 5;

    private static int ar(int x, int y, int index) {
        final CalculationState state = CALCULATION_STATE.get();
        return (x == 0) ? y * del : (y == 0) ? x * ins : state.buffer[index % state.radix];
    }

    private static int nw(int x, int y) {
        final CalculationState state = CALCULATION_STATE.get();
        return ar(x, y, state.index + state.fromLen + 2);
    }

    private static int n(int x, int y) {
        final CalculationState state = CALCULATION_STATE.get();
        return ar(x, y, state.index + state.fromLen + 3);
    }

    private static int w(int x, int y) {
        final CalculationState state = CALCULATION_STATE.get();
        return ar(x, y, state.index + state.radix - 1);
    }

    private static int nnww(int x, int y) {
        return ar(x, y, CALCULATION_STATE.get().index + 1);
    }

    private static int mod(int x) {
        return x % CALCULATION_STATE.get().radix;
    }

    /*
     * returns the edit distance between two strings, or -1 on failure
     */
    public static int calculateEditDistance(@Nullable byte[] from, int fromLen, @Nullable byte[] to, int toLen) {
        final CalculationState state = CALCULATION_STATE.get();
        state.index = 0;
        state.fromLen = fromLen;
        state.toLen = toLen;

        if (from == null) {
            if (to == null) {
                return 0;
            } else {
                return state.toLen * insertCost;
            }
        } else if (to == null) {
            return state.fromLen * deleteCost;
        }

        /* Initialize registers */

        state.radix = 2 * state.fromLen + 3;

        /* Make from short enough to fit in the static storage, if it's at all possible */

        if (state.fromLen > state.toLen && state.fromLen > STRLENTHRESHOLD) {
            int[] x = new int[1];
            int[] y = new int[1];
            x[0] = state.fromLen;
            y[0] = state.toLen;
            swapInt(x, y);
            byte[][] xx = new byte[1][];
            byte[][] yy = new byte[1][];
            xx[0] = from;
            yy[0] = to;
            swapChar(xx, yy);
        } // if from_len > to_len

        /* Allocate the array storage (from the heap if necessary) */

        if (state.fromLen <= STRLENTHRESHOLD) {
            state.buffer = state.store;
        } else {
            state.buffer = new int[state.radix];
        }

        /*
         * Here's where the fun begins. We will find the minimum edit distance using dynamic programming. We only need to store
         * two rows of the matrix at a time, since we always progress down the matrix. For example, given the strings "one" and
         * "two", and insert, delete and change costs equal to 1:
         *
         * _ o n e _ 0 1 2 3 t 1 1 2 3 w 2 2 2 3 o 3 2 3 3
         *
         * The dynamic programming recursion is defined as follows:
         *
         * ar(x,0) := x * insert_cost ar(0,y) := y * delete_cost ar(x,y) := min(a(x - 1, y - 1) + (from[x] == to[y] ? 0 :
         * change), a(x - 1, y) + insert_cost, a(x, y - 1) + delete_cost, a(x - 2, y - 2) + (from[x] == to[y-1] && from[x-1] ==
         * to[y] ? swap_cost : infinity))
         *
         * Since this only looks at most two rows and three columns back, we need only store the values for the two preceeding
         * rows. In this implementation, we do not explicitly store the zero column, so only 2 * from_len + 2 words are needed.
         * However, in the implementation of the swap_cost check, the current matrix value is used as a buffer; we can't
         * overwrite the earlier value until the swap_cost check has been performed. So we use 2 * from_len + 3 elements in the
         * buffer.
         */

        // /#define ar(x,y,index) (((x) == 0) ? (y) * del : (((y) == 0) ? (x) * ins :
        // \ buffer[mod(index)]))
        // /#define NW(x,y) ar(x, y, index + from_len + 2)
        // /#define N(x,y) ar(x, y, index + from_len + 3)
        // /#define W(x,y) ar(x, y, index + radix - 1)
        // /#define NNWW(x,y) ar(x, y, index + 1)
        // /#define mod(x) ((x) % radix)


        state.buffer[state.index++] = min2(ins + del, from[0] == to[0] ? 0 : ch);

        state.low = state.buffer[mod(state.index + state.radix - 1)];
        for (state.col = 1; state.col < state.fromLen; state.col++) {
            state.buffer[state.index] = min3(state.col * del + ((from[state.col] == to[0]) ? 0 : ch),
                    (state.col + 1) * del + ins, state.buffer[state.index - 1] + del);
            if (state.buffer[state.index] < state.low) {
                state.low = state.buffer[state.index];
            }
            state.index++;
        }

        /* Now handle the rest of the matrix */
        for (state.row = 1; state.row < state.toLen; state.row++) {
            for (state.col = 0; state.col < state.fromLen; state.col++) {
                state.buffer[state.index] = min3(nw(state.row, state.col) + ((from[state.col] == to[state.row]) ? 0 : ch),
                        n(state.row, state.col + 1) + ins, w(state.row + 1, state.col) + del);

                if (from[state.col] == to[state.row - 1] && state.col > 0 && from[state.col - 1] == to[state.row]) {
                    state.buffer[state.index] = min2(state.buffer[state.index], nnww(state.row - 1, state.col - 1) + swapCost);
                }

                if (state.buffer[state.index] < state.low || state.col == 0) {
                    state.low = state.buffer[state.index];
                }
                state.index = mod(state.index + 1);
            }
            if (state.low > MIN_DIST) {
                break;
            }
        }

        state.row = state.buffer[mod(state.index + state.radix - 1)];

        return state.row;
    } // edit_distn

    /** This class is not meant to be instantiated. */
    private EditDistance() {}
}
