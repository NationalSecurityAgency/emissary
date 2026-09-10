package emissary.util.magic;

import jakarta.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.NoSuchElementException;

/**
 * Utility for the magic number parser. Handles things like decoding escape characters, converting text into fixed-size
 * byte arrays, formatting bytes into readable text, and flipping endianness.
 */
public class MagicMath {

    /** Prefix used to identify hexadecimal numbers in config entries. */
    public static final String HEX_PREFIX = "0x";

    private static final String ZERO = "0";
    private static final String PRE_OCT = "0";
    private static final char ESCAPE = '\\';

    /**
     * Error message used when trying to resize a byte array to be smaller than the actual number inside it.
     */
    public static final String BYTEARRAY_PRECISION_ERROR_RULE =
            "The new byte array length must fit the existing value.";

    /**
     * Turns each byte into a properly formatted hexadecimal string, starting with {@code 0x}. Each byte is written as two
     * lowercase hex digits.
     *
     * @param b the bytes to convert
     * @return the formatted string representation
     */
    public static String byteArrayToHexString(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length; i++) {
            if (i == 0) {
                sb.append(HEX_PREFIX);
            }
            sb.append(String.format("%02x", b[i] & 0xFF));
        }
        return sb.toString();
    }

    /**
     * Decodes slash-escape sequences inside a magic entry string into raw bytes. How it works:
     *
     * <ul>
     * <li>A slash followed by nothing: treats it as a space and stops.</li>
     * <li>A slash followed by a special character (like newline): maps it to its intended byte value.</li>
     * <li>A slash followed by up to three numbers: treats them as octal (e.g., \101 becomes 'A').</li>
     * <li>\x followed by two characters: treats them as hex (e.g., \xCA).</li>
     * </ul>
     *
     * Note that unlike standard programming languages, letters like \a, \b, \f, and \t just turn into the literal letters
     * themselves rather than special control codes. Unknown escapes (like \q) just drop the slash and keep the letter.
     * Running out of data mid-sequence will throw an error.
     *
     * @param s the text containing escape sequences
     * @return the decoded raw bytes
     */
    public static byte[] parseEscapedString(String s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c != ESCAPE) {
                out.write(c);
                continue;
            }
            if (i >= s.length()) {
                out.write(' ');
                break;
            }
            char next = s.charAt(i);
            int literal = escapeLiteralValue(next);
            if (literal > 0) {
                out.write(literal);
                i++;
            } else if (Character.isDigit(next)) {
                int start = i;
                int end = Math.min(s.length(), start + 3);
                while (i < end && Character.isDigit(s.charAt(i))) {
                    i++;
                }
                out.write(new BigInteger(s.substring(start, i), 8).byteValue());
            } else if (next == 'x') {
                if ((s.length() - (i + 1)) < 2) {
                    throw new NoSuchElementException();
                }
                out.write(new BigInteger(s.substring(i + 1, i + 3), 16).byteValue());
                i += 3;
            } else {
                // Unknown escape: drop the backslash only, keeping the character as-is
            }
        }
        return out.toByteArray();
    }

    /**
     * Checks if a character right after a backslash matches a known literal escape value. Returns -1 if it's not a
     * recognized special code.
     */
    private static int escapeLiteralValue(char c) {
        switch (c) {
            case ' ':
            case '!':
            case '&':
            case '<':
            case '=':
            case '>':
            case '\\':
            case '^':
            case 'a':
            case 'b':
            case 'f':
            case 't':
            case 'v':
                return c;
            case 'n':
                return '\n';
            case 'r':
                return '\r';
            default:
                return -1;
        }
    }

    /**
     * Converts a text number (written in hex starting with 0x, octal starting with 0, or regular decimal) into a compact
     * byte array.
     *
     * @param s the numeric text
     * @return the value packed into a minimal byte array
     */
    public static byte[] stringToByteArray(String s) {
        if (s.startsWith(HEX_PREFIX)) {
            return hexStringToByteArray(s);
        } else if (!s.equals(ZERO) && s.startsWith(PRE_OCT)) {
            return octalStringToByteArray(s.substring(1));
        } else {
            return decimalStringToByteArray(s);
        }
    }

    /**
     * Converts a number into a strict, fixed-size byte array (big-endian). Hex values need the 0x prefix; others are read
     * as octal if they start with zero, or decimal otherwise.
     *
     * @param arraySize the exact output size needed
     * @param stringValue the numeric text (can be null)
     * @return the fixed-size byte array, or null if no text was provided
     */
    @Nullable
    public static byte[] stringToByteArray(int arraySize, @Nullable String stringValue) {
        if (stringValue == null || stringValue.length() == 0) {
            return null;
        }
        if (stringValue.length() > 2 && HEX_PREFIX.equals(stringValue.substring(0, 2))) {
            return setLength(hexStringToByteArray(stringValue), arraySize);
        } else {
            return integerToByteArray(arraySize, stringToLong(stringValue));
        }
    }

    /**
     * Converts octal text into a byte array. Large values automatically add an extra leading zero byte to handle
     * positive/negative signs correctly.
     *
     * @param s the octal string
     * @return the resulting byte array
     */
    public static byte[] octalStringToByteArray(String s) {
        String sub = s.startsWith(PRE_OCT) ? s.substring(1) : s;
        BigInteger integer = new BigInteger(sub, 8);
        return integer.toByteArray();
    }

    /**
     * Converts regular decimal text into a byte array, ensuring proper sign handling.
     *
     * @param s the decimal string
     * @return the resulting byte array
     */
    public static byte[] decimalStringToByteArray(String s) {
        return new BigInteger(s).toByteArray();
    }

    /**
     * Parses a text number (hex, octal, or decimal) into a standard integer.
     *
     * @param s the numeric text
     * @return the parsed integer value
     */
    public static int stringToInt(String s) {
        if (s.startsWith(HEX_PREFIX)) {
            return new BigInteger(s.substring(2), 16).intValue();
        } else if (!s.equals("0") && s.startsWith(PRE_OCT)) {
            return new BigInteger(s.substring(1), 8).intValue();
        } else {
            return new BigInteger(s, 10).intValue();
        }
    }

    /**
     * Parses a text number (hex, octal, or decimal) into a long integer.
     *
     * @param stringValue the numeric text
     * @return the parsed long value
     */
    public static long stringToLong(String stringValue) {
        if (stringValue.length() > 2 && HEX_PREFIX.equals(stringValue.substring(0, 2))) {
            return Long.parseLong(stringValue.substring(2), 16);
        } else if (stringValue.length() > 1 && stringValue.charAt(0) == '0') {
            return Long.parseLong(stringValue.substring(1), 8);
        } else {
            return Long.parseLong(stringValue, 10);
        }
    }

    /**
     * Packs a long integer into a fixed-size big-endian byte array, cutting off extra bytes if it's too big to fit.
     *
     * @param arraySize the exact output size
     * @param integerValue the number to pack
     * @return the resulting byte array
     */
    public static byte[] integerToByteArray(int arraySize, long integerValue) {
        byte[] valueBytes = new byte[arraySize];
        for (int i = 0; i < arraySize; i++) {
            valueBytes[arraySize - i - 1] = (byte) (integerValue >>> (i * 8) & 0xff);
        }
        return valueBytes;
    }

    /**
     * Converts hex text into raw bytes. Odd lengths get a leading zero added automatically.
     *
     * @param s the hex text
     * @return the decoded bytes
     */
    public static byte[] hexStringToByteArray(String s) {
        String subject = s;
        if (subject.startsWith(HEX_PREFIX)) {
            subject = subject.substring(2);
        }
        if (subject.length() % 2 != 0) {
            subject = ZERO + subject;
        }
        byte[] array = new byte[subject.length() / 2];
        for (int i = 0; i < array.length; i++) {
            int b = Integer.parseInt(subject.substring(i * 2, i * 2 + 2), 16);
            array[i] = (byte) (0xff & b);
        }
        return array;
    }

    /**
     * Formats a byte array into a decimal number string using a specific number base (radix). Trims leading empty bytes
     * first; if everything is zero, it just returns "0".
     *
     * @param data the byte array
     * @param radix the number base (e.g., 10 for decimal, 16 for hex)
     * @return the string representation
     */
    public static String byteArrayToString(byte[] data, int radix) {
        int actualSize = data.length;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == 0) {
                actualSize--;
            } else {
                break;
            }
        }
        if (actualSize == 0) {
            return ZERO;
        }
        byte[] adjustedData = setLength(data, actualSize);
        BigInteger value = new BigInteger(1, adjustedData);
        return value.toString(radix);
    }

    /**
     * Resizes a byte array to a new length while keeping the data right-aligned (so the smallest part stays at the end).
     * Throws an error if you try to shrink it smaller than what the number actually requires.
     *
     * @param data the original byte array
     * @param length the target size
     * @return the resized array
     */
    public static byte[] setLength(byte[] data, int length) {
        int actualSize = data.length;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == 0) {
                actualSize--;
            } else {
                break;
            }
        }
        if (data.length == length) {
            return data;
        } else if (actualSize > length) {
            throw new ByteArrayPrecisionException(BYTEARRAY_PRECISION_ERROR_RULE);
        }

        if (length == 0) {
            return new byte[0];
        }

        byte[] newValues = new byte[length];
        int ix = data.length - 1;
        for (int i = length - 1; i >= 0; i--) {
            if (ix < 0) {
                newValues[i] = (byte) 0;
            } else {
                newValues[i] = data[ix--];
            }
        }
        return newValues;
    }

    /**
     * Swaps 4 bytes right at the given position to flip a number from big-endian to little-endian (done in-place).
     *
     * @param array the byte array to modify
     * @param offset the starting position of the 4-byte chunk
     */
    public static void longEndianSwap(byte[] array, int offset) {
        if (array.length < (offset + 4)) {
            throw new ArrayIndexOutOfBoundsException(array.length + 1);
        }
        byte t = array[offset];
        array[offset] = array[offset + 3];
        array[offset + 3] = t;
        t = array[offset + 1];
        array[offset + 1] = array[offset + 2];
        array[offset + 2] = t;
    }

    /**
     * Swaps 2 bytes right at the given position to flip a short number's endianness (done in-place).
     *
     * @param array the byte array to modify
     * @param offset the starting position of the 2-byte chunk
     */
    public static void shortEndianSwap(byte[] array, int offset) {
        if (array.length < (offset + 2)) {
            throw new ArrayIndexOutOfBoundsException(array.length + 1);
        }
        byte t = array[offset];
        array[offset] = array[offset + 1];
        array[offset + 1] = t;
    }

    /** Utility class; prevent direct instantiation. */
    private MagicMath() {}
}
