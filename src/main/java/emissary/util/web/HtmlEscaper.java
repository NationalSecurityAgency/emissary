package emissary.util.web;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Methods for dealing with escaped HTML as bytes and strings
 */
public class HtmlEscaper {

    private static final byte[] LT_BYTES = "&lt;".getBytes(UTF_8);
    private static final byte[] GT_BYTES = "&gt;".getBytes(UTF_8);
    private static final byte[] AMPERSAND_BYTES = "&amp;".getBytes(UTF_8);

    /**
     * encode greater than, less than, and ampersand characters in a byte arroy.
     * 
     * @param theData input bytes
     * @return a copy of the input byte array with specific characters encoded.
     */
    public static byte[] escapeHtml(final byte[] theData) {
        byte[] escaped = null;

        try (ByteArrayOutputStream output = new ByteArrayOutputStream(theData.length)) {
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
        return new String(escapeHtml(s.getBytes(UTF_8)), UTF_8);
    }

    /** This class is not meant to be instantiated. */
    private HtmlEscaper() {}
}
