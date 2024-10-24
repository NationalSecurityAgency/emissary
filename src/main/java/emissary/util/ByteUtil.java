package emissary.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Common place for the logic to glue byte arrays back together. This is error-prone and shouldn't be thought about any
 * more than necessary
 */
public class ByteUtil {
    public static final byte ASCII_0 = '0';
    public static final byte ASCII_9 = '9';
    public static final byte ASCII_A_LC = 'a';
    public static final byte ASCII_B_LC = 'b';
    public static final byte ASCII_F_LC = 'f';
    public static final byte ASCII_Z_LC = 'z';
    public static final byte ASCII_A_UC = 'A';
    public static final byte ASCII_F_UC = 'F';
    public static final byte ASCII_Z_UC = 'Z';
    public static final byte ASCII_SLASH = '/';
    public static final byte ASCII_ESC = 0x1b;
    public static final byte ASCII_SP = 0x20;
    public static final byte ASCII_DEL = 0x7f;
    public static final String HEX = "0123456789abcdefABCDEF";

    /**
     * Check if byte is hexadecimal
     *
     * @param b a byte
     * @return true if b is a hexadecimal
     */
    public static boolean isHexadecimal(byte b) {
        return (b >= ASCII_A_UC && b <= ASCII_F_UC) || (b >= ASCII_A_LC && b <= ASCII_F_LC) || isDigit(b);
    }

    /**
     * Check if all bytes in array are hexadecimal
     *
     * @param array a byte array
     * @return true if all bytes in array are hexadecimal
     */
    public static boolean isHexadecimal(byte[] array) {
        for (byte b : array) {
            if (!isHexadecimal(b)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if character is hexadecimal
     *
     * @param c a char
     * @return true if c is a hexadecimal
     */
    public static boolean isHexadecimal(char c) {
        return HEX.indexOf(c) > -1;
    }

    /**
     * Check if all bytes are alphabetical
     *
     * @param array a byte array
     * @return true if all bytes in array are alpha
     */
    public static boolean isAlpha(byte[] array) {
        for (byte b : array) {
            if (!isAlpha(b)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if byte is alphabetical
     *
     * @param b a byte
     * @return true if b is alphabetical
     */
    public static boolean isAlpha(byte b) {
        return ((b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z'));
    }

    /**
     * Check if byte is alphanumeric
     *
     * @param b a byte
     * @return true if b is alphanumeric
     */
    public static boolean isAlNum(byte b) {
        return isAlpha(b) || isDigit(b);
    }

    /**
     * Check if byte is a digit
     *
     * @param b a byte
     * @return true if b is a digit
     */
    public static boolean isDigit(byte b) {
        // check ascii value of b for digit-ness
        return (b >= '0' && b <= '9');
    }

    /**
     * Check if all bytes are digits
     *
     * @param array a byte array
     * @return true if all bytes in array are digits
     */
    public static boolean isDigit(byte[] array) {
        for (byte b : array) {
            if (!isDigit(b)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if byte at position in array is a control or blank space byte
     *
     * @param b a byte array
     * @param pos a position in the byte array
     * @return true if byte at pos in array b is a control or blank space byte
     */
    public static boolean isControlOrBlankSpace(byte[] b, int pos) {
        if (b[pos] == ASCII_DEL || b[pos] <= ASCII_SP) {
            return true;
        }
        if (b[pos] == ASCII_B_LC && pos > 0 && b[pos - 1] == ASCII_ESC) {
            return true;
        }

        // Check if the current pos is the first byte in a UTF-8 C1
        // control character (U+0080..U+009f).
        final int curr = b[pos] & 0xff;
        final int next = (pos < (b.length - 1)) ? (b[pos + 1] & 0xff) : -1;
        if ((curr == 0xc2) && (next >= 0x80) && (next <= 0x9f)) {
            return true;
        }

        // Check if the current pos is the second byte in a UTF-8 C1
        // control character (U+0080..U+009f).
        final int prev = (pos > 0) ? (b[pos - 1] & 0xff) : -1;
        return (prev == 0xc2) && (curr >= 0x80) && (curr <= 0x9f);
    }

    /**
     * Glue two byte arrays together into one
     * 
     * @param a the first byte array
     * @param b the second byte array
     * @return the whole
     */
    public static byte[] glue(@Nullable byte[] a, @Nullable byte[] b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return glue(a, 0, a.length - 1, b, 0, b.length - 1);
    }

    /**
     * Glue three byte arrays together into one
     * 
     * @param a the first byte array
     * @param b the second byte array
     * @param c the third byte array
     * @return the whole
     */
    public static byte[] glue(@Nullable byte[] a, @Nullable byte[] b, @Nullable byte[] c) {
        if (a == null) {
            return glue(b, c);
        }
        if (b == null) {
            return glue(a, c);
        }
        if (c == null) {
            return glue(a, b);
        }
        return glue(a, 0, a.length - 1, b, 0, b.length - 1, c, 0, c.length - 1);
    }

    /**
     * Glue two byte arrays together into one
     * 
     * @param a the first byte array
     * @param astart starting position in a
     * @param aend ending position in a
     * @param b the second byte array
     * @param bstart starting position in b
     * @param bend ending position in b
     * @return the whole
     */
    @SuppressWarnings("InconsistentOverloads")
    public static byte[] glue(byte[] a, int astart, int aend, byte[] b, int bstart, int bend) {
        int alen = aend - astart + 1;
        int blen = bend - bstart + 1;

        byte[] rslt = new byte[alen + blen];
        System.arraycopy(a, astart, rslt, 0, alen);
        System.arraycopy(b, bstart, rslt, alen, blen);
        return rslt;
    }

    /**
     * Glue three byte arrays together into one
     * 
     * @param a the first byte array
     * @param astart starting position in a
     * @param aend ending position in a
     * @param b the second byte array
     * @param bstart starting position in b
     * @param bend ending position in b
     * @param c the third byte array
     * @param cstart starting position in c
     * @param cend ending position in c
     * @return the whole
     */
    @SuppressWarnings("InconsistentOverloads")
    public static byte[] glue(byte[] a, int astart, int aend, byte[] b, int bstart, int bend, byte[] c, int cstart, int cend) {
        int alen = aend - astart + 1;
        int blen = bend - bstart + 1;
        int clen = cend - cstart + 1;

        byte[] rslt = new byte[alen + blen + clen];
        System.arraycopy(a, astart, rslt, 0, alen);
        System.arraycopy(b, bstart, rslt, alen, blen);
        System.arraycopy(c, cstart, rslt, alen + blen, clen);
        return rslt;
    }

    /**
     * Split a byte array at the specified position
     * 
     * @param a the byte array
     * @param pos the split position (a[pos] goes to the second part)
     */
    public static List<byte[]> split(@Nullable byte[] a, int pos) {
        List<byte[]> list = new ArrayList<>();
        if (a != null && pos > 0 && pos <= a.length) {
            byte[] part1 = new byte[pos];
            byte[] part2 = new byte[a.length - pos];
            System.arraycopy(a, 0, part1, 0, pos);
            System.arraycopy(a, pos, part2, 0, part2.length);
            list.add(part1);
            list.add(part2);
        } else {
            // Just give back the original
            list.add(a);
        }
        return list;
    }

    /**
     * Given a byte-array and a start offset, return a string of the bytes between the start position and a carriage return
     * byte. In essence, this is grabbing a line of input where the byte array is composed of several lines of input.
     * 
     * @param data The byte array of input data.
     * @param pos The initial start offset.
     * @return A string created from the bytes found from the start offset to the carriage return byte.
     */
    public static String grabLine(byte[] data, int pos) {
        String ret = null;
        int eolnPos = -1;
        for (int i = pos; i < data.length; i++) {
            if (data[i] == '\n') {
                eolnPos = i;
                break;
            }
        }
        if (eolnPos != -1) {
            // String up to the found \n pos
            ret = new String(data, pos, eolnPos - pos + 1);
        } else {
            // String to end of buffer
            ret = new String(data, pos, data.length - pos);
        }
        return ret;
    }

    /**
     * Scans a byte array looking for non-printable values.
     * 
     * @param utf8Bytes the bytes to be scanned.
     * @return regardless of whether there were non-printable values.
     */
    public static boolean hasNonPrintableValues(final byte[] utf8Bytes) {
        int i = 0;
        while (i < utf8Bytes.length) {
            int codePoint;

            // Check for single-byte characters
            if (utf8Bytes[i] >= 0) {
                codePoint = utf8Bytes[i];
                i++;
            } else {
                // Check for multibyte characters
                int numBytes = countUtf8Bytes(utf8Bytes[i]);

                if (numBytes == 2) {
                    codePoint = decodeUtf8(utf8Bytes, i, 2);
                    i += 2;
                } else if (numBytes == 3) {
                    codePoint = decodeUtf8(utf8Bytes, i, 3);
                    i += 3;
                } else if (numBytes == 4) {
                    codePoint = decodeUtf8(utf8Bytes, i, 4);
                    i += 4;
                } else {
                    // Invalid UTF-8 sequence
                    return true;
                }
            }

            // Check if the code point is printable
            if (!isPrintable(codePoint)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check the first bits of the byte to determine the number of bytes
     *
     * @param b byte to determine number of characters
     * @return number of bytes in the byte sequence
     */
    private static int countUtf8Bytes(byte b) {
        if ((b & 0x80) == 0) {
            return 1;
        } else if ((b & 0xE0) == 0xC0) {
            return 2;
        } else if ((b & 0xF0) == 0xE0) {
            return 3;
        } else if ((b & 0xF8) == 0xF0) {
            return 4;
        } else {
            // Invalid UTF-8 sequence
            return 0;
        }
    }

    /**
     * Decodes a UTF-8 character from the byte array.
     *
     * @param bytes byte array containing the utf-8 encoded data
     * @param offset starting position in the byte array
     * @param numBytes number of bytes to decode
     *
     * @return code point
     */
    private static int decodeUtf8(byte[] bytes, int offset, int numBytes) {
        int codePoint = 0;
        for (int i = 0; i < numBytes; i++) {
            // shift the current value 6 bits to make room for the next 6 bits
            codePoint <<= 0x6;
            // add the lower 6 bits of the current byte to the codepoint. Only user the lower 6 bits.
            codePoint |= bytes[offset + i] & 0x3f;
        }
        // remove any extra bits that were added during shifting
        codePoint &= (0x1 << (0x6 * numBytes)) - 1;

        return codePoint;
    }

    /**
     * Check if the code point is a control character or surrogate pair
     * <a href="https://en.wikipedia.org/wiki/Unicode_block">...</a>
     *
     * @param codePoint codePoint to check
     *
     * @return if code-point is a printable character
     */
    private static boolean isPrintable(int codePoint) {

        return codePoint >= 0x20 && codePoint <= 0x7E || // basic latin
                codePoint >= 0xA0 && codePoint <= 0xD7FF || // extended characters
                codePoint >= 0xE000 && codePoint <= 0xFFFD ||
                codePoint >= 0x10000 && codePoint <= 0x10FFFF;
    }

    /**
     * Creates a hex string of a sha256 hash for a byte[].
     * 
     * @param bytes to be hashed
     * @return the hex string of a sha256 hash of the bytes.
     */
    @Nullable
    public static String sha256Bytes(final byte[] bytes) {
        try {
            final MessageDigest md = MessageDigest.getInstance("SHA-256");
            final byte[] hash = md.digest(bytes);

            final StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                final String hex = Integer.toHexString(0xff & b);

                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    /** This class is not meant to be instantiated. */
    private ByteUtil() {}
}
