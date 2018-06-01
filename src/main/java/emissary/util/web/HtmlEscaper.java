package emissary.util.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Methods for dealing with escaped HTML as bytes and strings
 */
public class HtmlEscaper {

    private static final byte[] LT_BYTES = "&lt;".getBytes();
    private static final byte[] GT_BYTES = "&gt;".getBytes();
    private static final byte[] AMPERSAND_BYTES = "&amp;".getBytes();

    /**
     * encode greater than, less than, and ampersand characters in a byte arroy.
     * 
     * @param theData input bytes
     * @return a copy of the input byte array with specific characters encoded.
     */
    public static byte[] escapeHtml(final byte[] theData) {
        ByteArrayOutputStream output = null;
        byte[] escaped = null;

        try {
            output = new ByteArrayOutputStream(theData.length);

            for (int i = 0; i < theData.length; i++) {
                if (theData[i] == '<') {
                    output.write(LT_BYTES);
                } else if (theData[i] == '>') {
                    output.write(GT_BYTES);
                } else if (theData[i] == '&') {
                    output.write(AMPERSAND_BYTES);
                } else {
                    output.write(theData[i]);
                }
            }
            escaped = output.toByteArray();

        } catch (IOException iox) {
            /* dont care */
        } finally {
            try {
                output.close();
            } catch (IOException e) {
                /* dont care */
            }
        }

        return escaped;
    }

    /**
     * Escape html string
     * 
     * @param s the input string
     * @return the escaped string
     */
    public static String escapeHtml(final String s) {
        return new String(escapeHtml(s.getBytes()));
    }

    /** This class is not meant to be instantiated. */
    private HtmlEscaper() {}
}
