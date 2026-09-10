package emissary.util.magic;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MagicMathTest extends UnitTest {

    @Test
    void testStringToInt() {
        assertEquals(13, MagicMath.stringToInt("13"), "a plain number is read as decimal");
        assertEquals(31, MagicMath.stringToInt("0x1F"), "0x means hexadecimal");
        assertEquals(8, MagicMath.stringToInt("010"), "a leading zero means octal");
        assertEquals(-5, MagicMath.stringToInt("-5"));

        // null input blows up the same way it always has
        assertThrows(NullPointerException.class, () -> MagicMath.stringToInt(null));

        String entry = "blah";
        Exception exception = assertThrows(NumberFormatException.class, () -> MagicMath.stringToInt(entry));
        assertEquals("java.lang.NumberFormatException: For input string: \"blah\"", exception.toString());
    }

    @Test
    void testStringToLong() {
        assertEquals(13L, MagicMath.stringToLong("13"));
        assertEquals(31L, MagicMath.stringToLong("0x1F"));
        assertEquals(8L, MagicMath.stringToLong("010"));
        assertEquals(5000000000L, MagicMath.stringToLong("5000000000"), "numbers too big for an int still work");
    }

    @Test
    void testHexToBytes() {
        assertArrayEquals(new byte[] {0x41, 0x42}, MagicMath.hexStringToByteArray("0x4142"));
        assertArrayEquals(new byte[] {0x41, 0x42}, MagicMath.hexStringToByteArray("4142"), "the 0x part is optional");
        assertArrayEquals(new byte[] {0x0A, 0x42}, MagicMath.hexStringToByteArray("A42"),
                "an extra 0 is added when there is an odd number of digits");
    }

    @Test
    void testOctalToBytes() {
        assertArrayEquals(new byte[] {0x41}, MagicMath.octalStringToByteArray("0101"), "101 in octal is 65, the letter A");
        assertArrayEquals(new byte[] {0x01, (byte) 0xFF}, MagicMath.octalStringToByteArray("0777"), "777 in octal is 511");
    }

    @Test
    void testDecimalToBytes() {
        assertArrayEquals(new byte[] {0x41}, MagicMath.decimalStringToByteArray("65"));
        assertArrayEquals(new byte[] {(byte) 0xFF}, MagicMath.decimalStringToByteArray("-1"), "negative one comes out with every bit set");
    }

    @Test
    void testTextToBytesPicksTheRightNumberBase() {
        assertArrayEquals(new byte[] {0x41, 0x42}, MagicMath.stringToByteArray("0x4142"));
        assertArrayEquals(new byte[] {0x41}, MagicMath.stringToByteArray("0101"), "leading zero means octal");
        assertArrayEquals(new byte[] {0x0D}, MagicMath.stringToByteArray("13"));
        assertArrayEquals(new byte[] {0}, MagicMath.stringToByteArray("0"));
    }

    @Test
    void testTextToFixedLengthBytes() {
        assertNull(MagicMath.stringToByteArray(4, null), "no number given means no bytes");
        assertNull(MagicMath.stringToByteArray(4, ""));
        assertArrayEquals(new byte[] {0x00, 0x00, 0x00, 0x41}, MagicMath.stringToByteArray(4, "65"),
                "short values are padded with zeros on the left");
        assertArrayEquals(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF}, MagicMath.stringToByteArray(4, "-1"));
        assertArrayEquals(new byte[] {0x01, (byte) 0xFF}, MagicMath.stringToByteArray(2, "0777"), "octal works here too");
        assertArrayEquals(new byte[] {0x2C}, MagicMath.stringToByteArray(1, "300"), "values too big simply lose their highest bytes");
        assertArrayEquals(new byte[] {0x00, 0x00, 0x41, 0x42}, MagicMath.stringToByteArray(4, "0x4142"),
                "hex values padded to the requested size like decimals are");
    }

    @Test
    void testIntegerToBytes() {
        assertArrayEquals(new byte[] {0x41}, MagicMath.integerToByteArray(1, 65));
        assertArrayEquals(new byte[] {0x41, 0x42}, MagicMath.integerToByteArray(2, 0x4142), "biggest byte comes first");
        assertArrayEquals(new byte[] {0x00, 0x00, 0x41, 0x42}, MagicMath.integerToByteArray(4, 0x4142), "extra room becomes leading zero bytes");
        assertArrayEquals(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF}, MagicMath.integerToByteArray(4, -1));
        assertArrayEquals(new byte[] {0x43}, MagicMath.integerToByteArray(1, 0x143), "only the lowest byte survives a tight fit");
    }

    @Test
    void testBytesToString() {
        assertEquals("65", MagicMath.byteArrayToString(new byte[] {0x00, 0x41}, 10), "empty bytes at the front are skipped");
        assertEquals("0", MagicMath.byteArrayToString(new byte[] {0x00, 0x00}, 10), "all-empty bytes just read as zero");
        assertEquals("41", MagicMath.byteArrayToString(new byte[] {0x41}, 16));
        assertEquals("255", MagicMath.byteArrayToString(new byte[] {(byte) 0xFF}, 10), "bytes read as unsigned values");
        assertEquals("4294967295", MagicMath.byteArrayToString(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF}, 10));
    }

    @Test
    void testBytesToDebugHexString() {
        assertEquals("0x4142", MagicMath.byteArrayToHexString(new byte[] {0x41, 0x42}));
        assertEquals("0x41bb", MagicMath.byteArrayToHexString(new byte[] {0x41, (byte) 0xBB}));
    }

    @Test
    void testResizeGrowsAndPadsOnTheLeft() {
        assertArrayEquals(new byte[] {0x00, 0x00, 0x41, 0x42}, MagicMath.setLength(new byte[] {0x41, 0x42}, 4));
    }

    @Test
    void testResizeShrinksOnlyLeadingZeroBytes() {
        byte[] data = new byte[] {0x00, 0x00, 0x41, 0x42};
        assertSame(data, MagicMath.setLength(data, 4), "asking for the same size hands back the original array");
        assertArrayEquals(new byte[] {0x41, 0x42}, MagicMath.setLength(data, 2));
        assertArrayEquals(new byte[0], MagicMath.setLength(new byte[] {0x00}, 0));
    }

    @Test
    void testResizeRefusesToDropRealData() {
        Exception e = assertThrows(ByteArrayPrecisionException.class,
                () -> MagicMath.setLength(new byte[] {0x41, 0x42}, 1),
                "cutting into the actual number would lose information");
        assertEquals(MagicMath.BYTEARRAY_PRECISION_ERROR_RULE, e.getMessage());
    }

    @Test
    void testFourByteSwap() {
        byte[] data = {0x41, 0x42, 0x43, 0x44};
        MagicMath.longEndianSwap(data, 0);
        assertArrayEquals(new byte[] {0x44, 0x43, 0x42, 0x41}, data);

        byte[] padded = {0x00, 0x41, 0x42, 0x43, 0x44, 0x00};
        MagicMath.longEndianSwap(padded, 1);
        assertArrayEquals(new byte[] {0x00, 0x44, 0x43, 0x42, 0x41, 0x00}, padded, "only the four chosen bytes flip");
    }

    @Test
    void testTwoByteSwap() {
        byte[] data = {0x41, 0x42};
        MagicMath.shortEndianSwap(data, 0);
        assertArrayEquals(new byte[] {0x42, 0x41}, data);
    }

    @Test
    void testSwapsNeedEnoughRoom() {
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> MagicMath.longEndianSwap(new byte[3], 0));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> MagicMath.shortEndianSwap(new byte[1], 0));
    }

    @Test
    void testParseEscapedString() {
        assertArrayEquals("A".getBytes(), MagicMath.parseEscapedString("\\101"), "up to three octal digits become one letter");
        assertArrayEquals(new byte[] {(byte) 0xCA}, MagicMath.parseEscapedString("\\xCA"), "\\x takes exactly two hex digits");
        assertArrayEquals("A\nB".getBytes(), MagicMath.parseEscapedString("A\\nB"), "\\n is a real newline");
        assertArrayEquals("t".getBytes(), MagicMath.parseEscapedString("\\t"),
                "unlike most languages, \\t here is just the letter t");
        assertArrayEquals("q".getBytes(), MagicMath.parseEscapedString("\\q"), "unknown escapes drop only the backslash");
        assertArrayEquals(" ".getBytes(), MagicMath.parseEscapedString("\\"), "a lone backslash at the very end counts as a space");
    }

    @Test
    void testParseEscapedStringNeedsTwoHexDigits() {
        assertThrows(NoSuchElementException.class, () -> MagicMath.parseEscapedString("\\x7"), "\\x must be followed by two hex digits");
    }
}
