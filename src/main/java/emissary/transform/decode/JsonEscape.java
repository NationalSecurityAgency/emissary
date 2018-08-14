package emissary.transform.decode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import emissary.util.shell.Executrix;

public class JsonEscape {
    /* our logger */
    private final static Logger logger = LoggerFactory.getLogger(JsonEscape.class);

    private final static String ESCAPES = "ntr\"'/";

    /**
     * Unescape a bunch of JSON bytes that might have \\uxxxx character values. Should already be UTF-8 since JSON is
     * specified as UTF-8 by RFC 4627
     */
    public static byte[] unescape(byte[] data) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length);
                ByteArrayInputStream in = new ByteArrayInputStream(data)) {
            unescape(in, baos);
            return baos.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    @SuppressWarnings("resource")
    public static void unescape(InputStream data, OutputStream out) throws IOException {
        IOUtils.copyLarge(new UnEscapeInputStream(data), out);
    }

    protected static boolean isOctalDigit(byte b) {
        return (b >= '0' && b <= '7');
    }

    /** This class is not meant to be instantiated. */
    private JsonEscape() {}

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < args.length; i++) {
            byte[] content = Executrix.readDataFromFile(args[i]);
            if (content == null) {
                System.out.println(args[i] + ": Unreadable");
                continue;
            }

            System.out.println(args[i]);
            byte[] escaped = JsonEscape.unescape(content);
            System.out.write(escaped, 0, escaped.length);
            System.out.println();
        }
    }

    private static class UnEscapeInputStream extends FilterInputStream {

        byte[] heldBytes = new byte[10];
        int heldCount = 0;
        boolean finished = false;

        public UnEscapeInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (finished) {
                return -1;
            }
            int read = super.read(b, off, len);
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

            bytescan: for (int i = 0; i < read + heldCount; i++) {
                byte thisByte = getByte(b, off, i);

                if (thisByte != '\\') {
                    tempOut.write(thisByte);
                    continue;
                }
                if (i >= read + heldCount - 1 && !finished) {
                    heldBytes[0] = thisByte;
                    heldCount = 1;
                    heldThisTime = true;
                    break bytescan;
                }
                byte nextByte = getByte(b, off, i + 1);

                if (nextByte == 'u' || nextByte == 'U') {
                    // Check length
                    if (i >= read + heldCount - 5) {
                        if (!finished) {
                            // copy to held bytes
                            int remaining = read + heldCount - i;
                            heldBytes = getBytes(b, off, i, remaining);
                            heldCount = remaining;
                            heldThisTime = true;
                            break bytescan;
                        } else {
                            tempOut.write(getBytes(b, off, i, read + heldCount - i));
                            break bytescan;
                        }
                    }

                    // process unicode escape
                    try {
                        String s = new String(getBytes(b, off, i + 2, 4), StandardCharsets.UTF_8);
                        char[] c = HtmlEscape.unescapeHtmlChar(s, true);
                        if (c != null && c.length > 0) {
                            tempOut.write(new String(c).getBytes(StandardCharsets.UTF_8));

                            System.err.println("Unicode '" + s + "' ==> '" + new String(c) + "'");
                            logger.debug("Unicode '" + s + "' ==> '" + new String(c) + "'");
                            i += 5;
                        } else {
                            tempOut.write(thisByte);
                        }
                    } catch (IOException iox) {
                        tempOut.write(thisByte);
                    }
                } else if (isOctalDigit(nextByte)) {
                    int count = 1;
                    // Process octal escape
                    octalscan: for (; count <= 3; count++) {
                        // Check length
                        if (i >= read + heldCount + 1 - count) {
                            if (!finished) {
                                // copy to held bytes
                                int remaining = read + heldCount - i;
                                heldBytes = getBytes(b, off, i, remaining);
                                heldCount = remaining;
                                heldThisTime = true;
                                break bytescan;
                            } else {
                                break octalscan;
                            }
                        }
                        if (count > 1 && !isOctalDigit(getByte(b, off, i + count))) {
                            break octalscan;
                        }
                    }
                    count--;

                    String s = new String(getBytes(b, off, i + 1, count), StandardCharsets.UTF_8);
                    try {
                        int num = Integer.parseInt(s, 8);
                        char[] ch = Character.toChars(num);
                        tempOut.write(new String(ch).getBytes(StandardCharsets.UTF_8));
                        logger.debug("Octal '" + s + "' ==> '" + new String(ch) + "'");
                        i += count;
                    } catch (Exception ex) {
                        tempOut.write(thisByte);
                    }
                } else if (ESCAPES.indexOf(nextByte) != -1) {
                    if (nextByte == 'n')
                        tempOut.write('\n');
                    else if (nextByte == 't')
                        tempOut.write('\t');
                    else if (nextByte == 'r')
                        tempOut.write('\r');
                    else
                        tempOut.write(nextByte);
                    i++;
                } else {
                    tempOut.write(thisByte);
                }
            }

            if (!heldThisTime) {
                heldCount = 0;
            }

            byte[] tempBytes = tempOut.toByteArray();
            System.arraycopy(tempBytes, 0, b, off, tempBytes.length);
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
    }
}
