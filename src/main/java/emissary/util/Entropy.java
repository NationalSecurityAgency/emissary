/*
  $Id$
 */


package emissary.util;

public class Entropy {

    /**
     * Determine if the data is text or binary using an entropy based function.
     */
    public static boolean checkText(final byte[] data) {
        return checkText(data, data.length);
    }

    public static boolean checkText(final byte[] data, int length) {
        int bytes = 0;
        int[] histogram_array;
        double entropy = 0;
        double relative_freq;
        int size = Math.min(length, data.length);

        histogram_array = new int[256];


        // ******************************************************************
        // ****************** scan the document ******************
        // ******************************************************************
        for (int cur_pos = 0; cur_pos < size; ++cur_pos) {
            ++histogram_array[(int) data[cur_pos] & 0xff];
            ++bytes;
        }

        // ***********************************************************************
        // ***** Use relative freqs. to estimate the entropy of the file. ******
        // ***********************************************************************

        for (int i = 0; i < 256; ++i) {
            if (histogram_array[i] != 0) {
                relative_freq = (double) histogram_array[i] / (double) bytes;
                entropy = entropy + (relative_freq * Math.log(1 / relative_freq));
            }
        }

        entropy = entropy / Math.log(2);

        if (entropy < 6.0) {
            return true;
        }
        return false;
    }

    /** This class is not meant to be instantiated. */
    private Entropy() {}
}
