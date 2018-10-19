package emissary.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Common place for the logic to glue byte arrays back together This is error prone and shouldn't be thought about any
 * more than necessary
 */
public class ByteUtil {
    public static final byte Ascii_0 = '0';
    public static final byte Ascii_9 = '9';
    public static final byte Ascii_a = 'a';
    public static final byte Ascii_f = 'f';
    public static final byte Ascii_z = 'z';
    public static final byte Ascii_A = 'A';
    public static final byte Ascii_F = 'F';
    public static final byte Ascii_Z = 'Z';
    public static final byte Ascii_Slash = '/';
    public static final byte Ascii_b = 'b';
    public static final byte Ascii_ESC = 0x1b;
    public static final byte Ascii_SP = 0x20;
    public static final byte Ascii_DEL = 0x7f;
    public static final String HEX = "0123456789abcdefABCDEF";

    /**
     * @param b a byte
     * @return true if b is a hexadecimal
     */
    public static boolean isHexadecimal(byte b) {
        return (b >= Ascii_A && b <= Ascii_F) || (b >= Ascii_a && b <= Ascii_f) || isDigit(b);
    }

    /**
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
     *
     * @param c a char
     * @return true if c is a hexadecimal
     */
    public static boolean isHexadecimal(char c) {
        return HEX.indexOf(c) > -1;
    }

    /**
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
     *
     * @param b a byte
     * @return true if b is alphabetical
     */
    public static boolean isAlpha(byte b) {
        return ((b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z'));
    }

    /**
     *
     * @param b a byte
     * @return true if b is alphanumeric
     */
    public static boolean isAlNum(byte b) {
        return isAlpha(b) || isDigit(b);
    }

    /**
     *
     * @param b a byte
     * @return true if b is a digt
     */
    public static boolean isDigit(byte b) {
        // check ascii value of b for digit-ness
        return (b >= '0' && b <= '9');
    }

    /**
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
     *
     * @param b a byte array
     * @param pos a position in the byte array
     * @return true if byte at pos in array b is a control or whitespace byte
     */
    public static boolean isControlOrWhiteSpace(byte[] b, int pos) {
        if (b[pos] == Ascii_DEL || b[pos] <= Ascii_SP) {
            return true;
        }
        if (b[pos] == Ascii_b && pos > 0 && b[pos - 1] == Ascii_ESC) {
            return true;
        }

        // Check if the current pos is the first byte in a UTF-8 C1
        // control character (U+0080..U+009f).
        final int curr = ((int) b[pos]) & 0xff;
        final int next = (pos < (b.length - 1)) ? (((int) b[pos + 1]) & 0xff) : -1;
        if ((curr == 0xc2) && (next >= 0x80) && (next <= 0x9f)) {
            return true;
        }

        // Check if the current pos is the second byte in a UTF-8 C1
        // control character (U+0080..U+009f).
        final int prev = (pos > 0) ? (((int) b[pos - 1]) & 0xff) : -1;
        if ((prev == 0xc2) && (curr >= 0x80) && (curr <= 0x9f)) {
            return true;
        }

        return false;
    }

    /**
     * Glue two byte arrays together into one
     * 
     * @param a the first byte array
     * @param b the second byte array
     * @return the whole
     */
    public static byte[] glue(byte[] a, byte[] b) {
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
    public static byte[] glue(byte[] a, byte[] b, byte[] c) {
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
    public static List<byte[]> split(byte[] a, int pos) {
        List<byte[]> list = new ArrayList<byte[]>();
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

    /** This class is not meant to be instantiated. */
    private ByteUtil() {}
}
