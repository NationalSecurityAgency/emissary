package emissary.transform.decode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import emissary.util.CharacterCounterSet;
import emissary.util.HtmlEntityMap;
import emissary.util.shell.Executrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HtmlEscape {

    /* our logger */
    private static final Logger logger = LoggerFactory.getLogger(HtmlEscape.class);

    private static final int LONGEST_ENTITY_NAME = 33; // &CounterClockwiseContourIntegral;

    /**
     * Html Entity mapper
     */
    private final static HtmlEntityMap HTML_ENTITY_MAP = new HtmlEntityMap();

    /**
     * Pattern for HTML escaped char finding in strings
     */
    private final static Pattern HESC_PATTERN = Pattern.compile("&#([xX]?)(\\p{XDigit}{2,5});");

    /**
     * Unescape some HTML data without counting what was done
     *
     * @param data the array of bytes containing HTML escaped characters
     * @return modified byte array
     */
    public static byte[] unescapeHtml(byte[] data) {
        return unescapeHtml(data, null);
    }

    /**
     * Unescape some HTML data, turning <code>&amp;#xxxx;</code> into UNICODE characters Because this operation inserts java
     * Character objects into the byte array, it probably only makes sense to send in data that already matches the platform
     * encoding (i.e. UTF-8 for normal usage). Otherwise the result will be a mixed up mess of multiple character sets that
     * cannot possibly be understood or displayed properly.
     *
     * @param data the array of bytes containing HTML escaped characters
     * @param counters to measure what is changed
     * @return modified byte array
     */
    public static byte[] unescapeHtml(byte[] data, CharacterCounterSet counters) {

        ByteArrayOutputStream baos = null;
        byte[] returnBytes = null;
        if (data == null || data.length == 0)
            return new byte[0];

        try {
            baos = new ByteArrayOutputStream();
            for (int i = 0; i < data.length; i++) {
                // Grab one encoded character
                if (data[i] == '&' && i + 3 < data.length && data[i + 1] == '#') {
                    int j = i + 2;
                    boolean isHex = false;

                    // Determine if &#xnnnn; or &#nnnn;
                    if (data[j] == 'X' || data[j] == 'x') {
                        j++;
                        isHex = true;
                    }

                    int startPos = j;

                    // Jump to end of digits, find a semi-colon
                    while (j < data.length && emissary.util.ByteUtil.isHexadecimal(data[j]) && j < startPos + 5)
                        j++;

                    if (j < data.length && data[j] == ';') {
                        // Try to convert it
                        char[] c = unescapeHtmlChar(new String(data, startPos, j - startPos), isHex);
                        if (c != null) {
                            // write a codepoint
                            String s = new String(c);
                            baos.write(s.getBytes());
                            if (counters != null) {
                                counters.count(s);
                            }
                            i = j;
                        } else {
                            // Do no harm if the conversion fails
                            baos.write(data[i]);
                        }
                    } else {
                        baos.write(data[i]);
                    }
                } else {
                    baos.write(data[i]);
                }
            }
            returnBytes = baos.toByteArray();
            baos.close();
        } catch (IOException e) {
            logger.debug("Cannot decode HTML bytes", e);
        }
        return returnBytes;
    }

    /**
     * Unescape some HTML data without counting what was changed
     *
     * @param s the string of characters possibly containing escaped HTML
     * @return the new String without escaped HTML
     */
    public static String unescapeHtml(String s) {
        return unescapeHtml(s, null);
    }

    /**
     * Unescape some HTML data, turning <code>$#xxxx;</code> into UNICODE characters
     *
     * @param s the string of characters possibly containing escaped HTML
     * @param counters to measure what is changed
     * @return the new String without escaped HTML
     */
    public static String unescapeHtml(String s, CharacterCounterSet counters) {
        if (s == null || s.length() == 0)
            return "";

        if (logger.isDebugEnabled()) {
            logger.debug("Doing unescapeHtml on length " + s.length() + ": " + s);
        }

        StringBuffer sb = new StringBuffer(s.length());
        Matcher m = HESC_PATTERN.matcher(s);

        // Match each occurrence
        while (m.find()) {
            // Grab digits from first match group
            String hexModifier = m.group(1);
            boolean isHex = hexModifier != null && hexModifier.length() > 0;
            String encodedChar = m.group(2);
            char[] c = unescapeHtmlChar(encodedChar, isHex);
            logger.debug("Found a string match for " + encodedChar + ", isHex " + isHex);
            if (c != null) {
                // Append non-matching portion plus decoded char
                m.appendReplacement(sb, "");
                sb.append(c);
                if (counters != null) {
                    counters.count(new String(c));
                }
            } else {
                // It failed, append non-match plus original match
                m.appendReplacement(sb, "$0");
            }
        }

        // Append terminal portion
        m.appendTail(sb);

        // Return the new string
        return sb.toString();
    }


    /**
     * Unescape one HTML character or null if we cannot convert
     *
     * @param s the four digit number from the HTML encoding
     * @param isHex true if the digits are in hex
     * @return the Unicode codepoint in a[] char or null
     */
    public static char[] unescapeHtmlChar(String s, boolean isHex) {
        int num = -1;
        try {
            num = Integer.parseInt(s, isHex ? 16 : 10);
        } catch (NumberFormatException ex) {
            logger.debug("Failed to parse char {}", s);
            return null;
        }
        // Turn the number into a Unicode codepoint
        logger.debug("Parsed unescapeHtmlChar for {} into {}", s, num);
        try {
            return Character.toChars(num);
        } catch (Exception ex) {
            logger.debug("Cannot toChars on {}: not a valid Unicode code point", s, ex);
            return null;
        }
    }

    /**
     * Unescape HTML entities without counting what was changed
     *
     * @param s the string to find entities in
     */
    public static String unescapeEntities(String s) {
        return unescapeEntities(s, null);
    }

    /**
     * Unescape HTML entities like &amp;nbsp; into normal characters Also handle broken entities like &amp;;nbsp; and
     * &amp;nbsp (extra semi-colon and missing semi-colon respectively)
     *
     * @param s the string to find entities in
     * @param counters to measure what was changed
     */
    public static String unescapeEntities(String s, CharacterCounterSet counters) {
        int slen = s.length();
        StringBuilder sb = new StringBuilder(s.length());

        if (logger.isDebugEnabled()) {
            logger.debug("Doing html entity normalization on string length " + slen + ": " + s);
        }

        for (int i = 0; i < slen; i++) {
            char c = s.charAt(i);
            if (c != '&') {
                sb.append(c);
            } else {
                int spos = i;
                int j = spos + 1;
                while (j < slen && j < (spos + LONGEST_ENTITY_NAME) && s.charAt(j) != ';' && s.charAt(j) != ' ')
                    j++;

                if (j < slen && j == spos + 1) // broken case with extra semicolon
                {
                    spos++;
                    j = spos + 1;
                    while (j < slen && j < (spos + LONGEST_ENTITY_NAME) && s.charAt(j) != ';' && s.charAt(j) != ' ')
                        j++;
                }

                if (j < slen + 1) {
                    String ent = null;
                    if (j < slen) {
                        if (s.charAt(j) == ';' || s.charAt(j) == ' ') {
                            ent = s.substring(spos + 1, j);
                        }
                    } else {
                        // all the rest
                        ent = s.substring(spos + 1);
                    }

                    if (ent != null) {
                        String val = getValueForHTMLEntity(ent);
                        if (val != null) {
                            sb.append(val);
                            if (counters != null) {
                                counters.count(val);
                            }
                            if (j >= slen) {
                                i = slen; // all done
                            } else {
                                i = s.charAt(j) == ' ' ? (j - 1) : j;
                            }
                        } else {
                            sb.append(c);
                        }
                    } else {
                        sb.append(c);
                    }
                } else {
                    sb.append(c);
                }
            }
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Result is " + sb.toString());
        }
        return sb.toString();
    }


    private static String getValueForHTMLEntity(String entity) {
        String s = HTML_ENTITY_MAP.getValueForHTMLEntity(entity);
        if (s != null) {
            logger.debug("Html Entity Map(" + entity + ") => '" + s + "'");
            return s;
        }
        return null;
    }

    /**
     * Unescape HTML entities without measuring what was changed
     */
    public static byte[] unescapeEntities(byte[] s) {
        return unescapeEntities(s, null);
    }

    /**
     * Unescape HTML Entities like &amp;nbsp; into normal characters Also handle broken entities like &amp;;nbsp; and
     * &amp;nbsp (extra semi-colon and missing semi-colon respectively)
     */
    public static byte[] unescapeEntities(byte[] s, CharacterCounterSet counters) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int slen = s.length;

        logger.debug("Doing html entity normalization on bytes length " + slen);

        for (int i = 0; i < slen; i++) {
            if (i + 4 < slen && s[i] == '&') {
                int spos = i;
                int epos = spos + 1;
                while (epos < slen && epos < spos + LONGEST_ENTITY_NAME && s[epos] != ';' && s[epos] != ' ')
                    epos++;

                if (epos == spos + 1) // broken case with extra semi-colon
                {
                    spos++;
                    epos = spos + 1;
                    while (epos < slen && epos < spos + LONGEST_ENTITY_NAME && s[epos] != ';' && s[epos] != ' ')
                        epos++;
                }

                String val = HTML_ENTITY_MAP.getValueForHTMLEntity(new String(s, spos + 1, epos - (spos + 1)));
                if (val != null) {
                    try {
                        baos.write(val.getBytes());
                        if (counters != null) {
                            counters.count(val);
                        }
                        // if we used the space as a terminator, keep the
                        // space in the output, even though we consumed it
                        if (epos < slen) {
                            i = s[epos] == ' ' ? (epos - 1) : epos;
                        } else {
                            i = slen;
                        }
                    } catch (IOException iox) {
                        logger.debug("Error writing unescaped bytes", iox);
                        baos.write(s[i]);
                    }
                } else {
                    baos.write(s[i]);
                }
            } else {
                baos.write(s[i]);
            }
        }
        return baos.toByteArray();
    }

    /** This class is not meant to be instantiated. */
    private HtmlEscape() {}

    public static void main(String[] args) throws Exception {
        boolean useString = false;
        int i = 0;
        if (args.length > 0 && args[i].equals("-s")) {
            System.out.println("Switching to string mode");
            useString = true;
            i++;
        }

        for (; i < args.length; i++) {
            byte[] content = Executrix.readDataFromFile(args[i]);
            if (content == null) {
                System.out.println(args[i] + ": Unreadable");
                continue;
            }

            System.out.println(args[i]);
            if (useString) {
                String escaped = HtmlEscape.unescapeHtml(new String(content));
                escaped = HtmlEscape.unescapeEntities(escaped);
                System.out.println(escaped);
            } else {
                byte[] escaped = HtmlEscape.unescapeHtml(content);
                escaped = HtmlEscape.unescapeEntities(escaped);
                System.out.write(escaped, 0, escaped.length);
                System.out.println();
            }
        }
    }
}
