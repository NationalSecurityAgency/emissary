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
        int[] histogramArray;
        double entropy = 0;
        double relativeFreq;
        int size = Math.min(length, data.length);

        histogramArray = new int[256];


        // ******************************************************************
        // ****************** scan the document ******************
        // ******************************************************************
        for (int cur_pos = 0; cur_pos < size; ++cur_pos) {
            ++histogramArray[data[cur_pos] & 0xff];
            ++bytes;
        }

        // ***********************************************************************
        // ***** Use relative freqs. to estimate the entropy of the file. ******
        // ***********************************************************************

        for (int i = 0; i < 256; ++i) {
            if (histogramArray[i] != 0 && bytes > 0) {
                relativeFreq = (double) histogramArray[i] / (double) bytes;
                entropy = entropy + (relativeFreq * Math.log(1 / relativeFreq));
            }
        }

        entropy = entropy / Math.log(2);

        return entropy < 6.0;
    }

    /** This class is not meant to be instantiated. */
    private Entropy() {}
}
