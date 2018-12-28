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

public class JavascriptEscape {
    private final static String ESCAPES = "nr";

    /* our logger */
    private final static Logger logger = LoggerFactory.getLogger(JavascriptEscape.class);


    /**
     * Unescape javascript unicode characters in the form backslash-u-nnnn. Browser tests show that only lowercase "u" and
     * only four digits work. Javascript also has normal unix escapes like \n and \r.
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

    /**
     * Unescape javascript unicode characters in the form backslash-u-nnnn. Browser tests show that only lowercase "u"
     * and only four digits work. Javascript also has normal unix escapes like \n and \r.
     */
    @SuppressWarnings("resource")
    public static void unescape(InputStream data, OutputStream out) throws IOException {
        IOUtils.copyLarge(new UnEscapeInputStream(data), out);
    }

    /** This class is not meant to be instantiated. */
    private JavascriptEscape() {}

    public static void main(String[] args) throws Exception {
        int i = 0;

        for (; i < args.length; i++) {
            byte[] content = Executrix.readDataFromFile(args[i]);
            if (content == null) {
                System.out.println(args[i] + ": Unreadable");
                continue;
            }

            System.out.println(args[i]);
            byte[] escaped = JavascriptEscape.unescape(content);
            System.out.write(escaped, 0, escaped.length);
            System.out.println();
        }
    }

    static class UnEscapeInputStream extends FilterInputStream {

        byte[] heldBytes = new byte[10];
        int heldCount = 0;
        boolean finished = false;
        boolean everFinished = false;

        public UnEscapeInputStream(InputStream in) {
            super(in);
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

                if (thisByte != '\\') {
                    tempOut.write(thisByte);
                    continue;
                }
                if (i >= read + heldCount - 1 && !finished) {
                    break bytescan;

                }
                byte nextByte = getByte(b, off, i + 1);

                if (nextByte == 'u' || nextByte == 'U') {
                    // Check length
                    if (i >= read + heldCount - 5) {
                        if (finished) {
                            i += writeTail(tempOut, i, b, off, read);
                        }
                        break bytescan;
                    }

                    // process unicode escape
                    try {
                        String s = new String(getBytes(b, off, i + 2, 4), StandardCharsets.UTF_8);
                        char[] c = HtmlEscape.unescapeHtmlChar(s, true);// "X".toCharArray();//
                        if (c != null && c.length > 0) {
                            tempOut.write(new String(c).getBytes(StandardCharsets.UTF_8));
                            logger.debug("Unicode '" + s + "' ==> '" + new String(c) + "'");
                            i += 5;
                        } else {
                            tempOut.write(thisByte);
                        }
                    } catch (IOException iox) {
                        tempOut.write(thisByte);
                    }
                } else if (ESCAPES.indexOf(nextByte) != -1) {
                    if (nextByte == 'n')
                        tempOut.write('\n');
                    else if (nextByte == 'r')
                        tempOut.write('\n'); // deliberate
                    else
                        tempOut.write(nextByte);
                    i++;
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

        private int writeTail(ByteArrayOutputStream tempOut, int i, byte[] b, int off, int read) {
            int result = 0;
            if (i < heldCount) {
                tempOut.write(heldBytes, i, heldCount - i);
                result += heldCount - i;
            }
            if (read > heldCount + i) {
                tempOut.write(b, (off - heldCount) + i, read - (heldCount + i));
                result += read - (heldCount + i);
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
