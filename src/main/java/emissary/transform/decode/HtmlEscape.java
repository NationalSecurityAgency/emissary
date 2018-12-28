package emissary.transform.decode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import emissary.util.ByteUtil;
import emissary.util.CharacterCounterSet;
import emissary.util.HtmlEntityMap;
import emissary.util.shell.Executrix;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HtmlEscape {

    /* our logger */
    private static final Logger logger = LoggerFactory.getLogger(HtmlEscape.class);

    private static final int LONGEST_ENTITY_NAME = 33; // &CounterClockwiseContourIntegral;

    /**
     * Html Entity mapper
     */
    private final static HtmlEntityMap HTML_ENTITY_MAP = new HtmlEntityMap();// null;//

    /**
     * Pattern for HTML escaped char finding in strings
     */
    private final static Pattern HESC_PATTERN = Pattern.compile("&#([xX]?)(\\p{XDigit}{2,5});");

    /**
     * Unescape some HTML data, turning <code>&#xxxx;</code> or <code>&amp;eacute;</code> into UNICODE characters Because
     * this operation inserts java Character objects into the byte array, it probably only makes sense to send in data that
     * already matches the platform encoding (i.e. UTF-8 for normal usage). Otherwise the result will be a mixed up mess of
     * multiple character sets that cannot possibly be understood or displayed properly.
     *
     * @param data The source of the data. The data will be consumed from the current position until the end of the stream.
     * @param out Where to write the transformed bytes to.
     * @param entities Whether to change named entities such as <code>&amp;eacute;</code>.
     * @param numeric Whether to change numeric escapes such as <code>&#xxxx;</code>.
     * @param counters An object that may be mutated to record metrics.
     * @throws IOException If there is a problem consuming or writing the streams.
     */
    @SuppressWarnings("resource")
    public static void unescape(InputStream data, OutputStream out, boolean entities, boolean numeric, Optional<CharacterCounterSet> counters)
            throws IOException {
        IOUtils.copyLarge(new UnEscapeInputStream(data, entities, numeric, counters), out);
    }

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
     * Unescape some HTML data, turning <code>&#xxxx;</code> into UNICODE characters Because this operation inserts java
     * Character objects into the byte array, it probably only makes sense to send in data that already matches the platform
     * encoding (i.e. UTF-8 for normal usage). Otherwise the result will be a mixed up mess of multiple character sets that
     * cannot possibly be understood or displayed properly.
     *
     * @param data the array of bytes containing HTML escaped characters
     * @param counters to measure what is changed. May be null.
     * @return modified byte array
     */
    public static byte[] unescapeHtml(byte[] data, CharacterCounterSet counters) {
        if (data == null) {
            return new byte[0];
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length);
                ByteArrayInputStream in = new ByteArrayInputStream(data)) {
            unescape(in, baos, false, true, Optional.ofNullable(counters));
            return baos.toByteArray();
        } catch (IOException e) {
            return null;
        }

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
    public static byte[] unescapeEntities(byte[] data, CharacterCounterSet counters) {
        if (data == null) {
            return new byte[0];
        }
        logger.debug("Doing html entity normalization on bytes length " + data.length);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length);
                ByteArrayInputStream in = new ByteArrayInputStream(data)) {
            unescape(in, baos, true, false, Optional.ofNullable(counters));
            return baos.toByteArray();
        } catch (IOException e) {
            return null;
        }
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

    static class UnEscapeInputStream extends FilterInputStream {

        byte[] heldBytes = new byte[10];
        int heldCount = 0;
        boolean finished = false;
        boolean everFinished = false;
        private boolean entities;
        private boolean numeric;
        private Optional<CharacterCounterSet> counters;

        public UnEscapeInputStream(InputStream in, boolean entities, boolean numeric, Optional<CharacterCounterSet> counters) {
            super(in);
            this.entities = entities;
            this.numeric = numeric;
            this.counters = counters != null ? counters : Optional.empty();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (finished) {
                return -1;
            }
            int read = everFinished ? -1 : in.read(b, off, len);
            if (read == -1) {
                if (heldCount > 0) {
                    finished = true;
                    read = 0;
                } else {
                    return -1;
                }
            }

            ByteArrayOutputStream tempOut = new ByteArrayOutputStream(read + heldCount);
            boolean heldThisTime = false;

            int i = 0;
            bytescan: for (i = 0; i < read + heldCount && tempOut.size() < len; i++) {
                byte thisByte = getByte(b, off, i);
                // char debugThis = (char) thisByte;

                if (thisByte != '&') {
                    tempOut.write(thisByte);
                    continue;
                }
                if (i >= read + heldCount - 1 && !finished) {
                    break bytescan;

                }
                byte nextByte = getByte(b, off, i + 1);

                if (nextByte == '#' && numeric) {
                    boolean isHex = false;
                    int count = 2;
                    boolean terminated = false;
                    // Process numeric escape
                    numscan: for (; count <= 8; count++) {
                        // Check length
                        if (i >= read + heldCount - count) {
                            if (!finished) {
                                break bytescan;
                            } else {
                                break numscan;
                            }
                        }
                        byte testByte = getByte(b, off, i + count);
                        // char debugTestByte = (char) testByte;
                        if (count == 2) {
                            if (testByte == 'X' || testByte == 'x') {
                                isHex = true;
                                continue numscan;
                            }
                            if (!ByteUtil.isDigit(testByte)) {
                                break numscan;
                            }
                        } else {
                            if (!isHex && ByteUtil.isDigit(testByte)) {
                                continue numscan;
                            }
                            if (isHex && ByteUtil.isHexadecimal(testByte)) {
                                continue numscan;
                            }
                            if (testByte == ';') {
                                terminated = true;
                                break numscan;
                            }
                            break numscan;
                        }
                    }

                    if (!terminated) {
                        tempOut.write(thisByte);
                        continue bytescan;
                    }

                    int delta = isHex ? 3 : 2;
                    String s = new String(getBytes(b, off, i + delta, count - delta), StandardCharsets.UTF_8);
                    try {
                        // Try to convert it
                        char[] c = unescapeHtmlChar(s, isHex);
                        if (c != null) {
                            // write a codepoint
                            String s2 = new String(c);
                            tempOut.write(s2.getBytes(StandardCharsets.UTF_8));
                            counters.ifPresent(cc -> cc.count(s2));
                            i += count;
                        }
                    } catch (Exception ex) {
                        tempOut.write(thisByte);

                    }
                } else if (entities) {
                    int count = 1;
                    int start = 1;
                    // Process numeric escape
                    numscan: for (; count <= LONGEST_ENTITY_NAME + 1; count++) {
                        // Check length
                        if (i >= read + heldCount - count) {
                            if (!finished) {
                                break bytescan;
                            } else {
                                break numscan;
                            }
                        }
                        byte testByte = getByte(b, off, i + count);
                        // char debugTestByte = (char) testByte;
                        if (testByte == ';') {
                            if (count == 1) {// broken case with extra semi-colon
                                start++;
                            } else {
                                break numscan;
                            }
                        }
                        if (testByte == ' ') {
                            break numscan;
                        }
                    }

                    String val =
                            HTML_ENTITY_MAP.getValueForHTMLEntity(new String(getBytes(b, off, i + start, count - start), StandardCharsets.UTF_8));// "X";//
                    if (val != null) {
                        try {
                            tempOut.write(val.getBytes());
                            counters.ifPresent(cc -> cc.count(val));
                            i += count;
                            // if we used the space as a terminator, keep the
                            // space in the output, even though we consumed it
                            if (getByte(b, off, i) == ' ') {
                                i--;
                            }
                        } catch (IOException iox) {
                            logger.debug("Error writing unescaped bytes", iox);
                            tempOut.write(thisByte);
                        }
                    } else {
                        tempOut.write(thisByte);
                    }

                } else {
                    tempOut.write(thisByte);
                }
            }

            if (i < heldCount + read) {
                heldThisTime = true;
                byte[] newHeld = new byte[heldCount + read - i];
                if (i <= heldCount) {
                    System.arraycopy(heldBytes, i, newHeld, 0, heldCount - i);
                    System.arraycopy(b, 0, newHeld, heldCount - i, read);
                } else {
                    System.arraycopy(b, i, newHeld, 0, read - i);
                }
                heldCount = newHeld.length;
                heldBytes = newHeld;
            }

            if (!heldThisTime) {
                heldCount = 0;
            }

            everFinished |= finished;
            finished &= heldCount == 0;

            byte[] tempBytes = tempOut.toByteArray();
            System.arraycopy(tempBytes, 0, b, off, Math.min(tempBytes.length, len));
            if (len < tempBytes.length) {
                heldCount = tempBytes.length - len;
                System.arraycopy(tempBytes, len, heldBytes, 0, heldCount);
                finished = false;
                return len;
            }
            return tempBytes.length;
        }

        private byte getByte(byte[] b, int off, int index) {
            return index < heldCount ? heldBytes[index] : b[index + off - heldCount];
        }

        private byte[] getBytes(byte[] b, int off, int index, int len) {
            byte[] result = new byte[len]; // TODO re-use
            for (int i = 0; i < len; i++) {
                result[i] = getByte(b, off, index + i);
            }
            return result;
        }

        @Override
        public int read() throws IOException {
            byte[] b = new byte[1];
            int read = 0;
            while (read == 0) {
                read = read(b, 0, 1);
            }
            return read == -1 ? -1 : b[0];
        }
    }
}
