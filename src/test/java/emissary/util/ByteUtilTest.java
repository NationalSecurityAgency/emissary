package emissary.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import emissary.test.core.UnitTest;
import org.junit.Test;

public class ByteUtilTest extends UnitTest {

    @Test
    public void testGlue() {
        assertEquals("Glue two whole arrays", "abcdef", new String(ByteUtil.glue("abc".getBytes(), "def".getBytes())));
    }

    @Test
    public void testGlueThree() {
        assertEquals("Glue three whole arrays", "abcdefghi", new String(ByteUtil.glue("abc".getBytes(), "def".getBytes(), "ghi".getBytes())));
    }


    @Test
    public void testGlueNull() {
        assertEquals("Glue with first as null", "def", new String(ByteUtil.glue(null, "def".getBytes())));
        assertEquals("Glue with second as null", "abc", new String(ByteUtil.glue("abc".getBytes(), null)));
    }

    @Test
    public void testGlueNullThree() {
        assertEquals("Glue with first as null", "defghi", new String(ByteUtil.glue(null, "def".getBytes(), "ghi".getBytes())));

        assertEquals("Glue with second as null", "abcghi", new String(ByteUtil.glue("abc".getBytes(), null, "ghi".getBytes())));

        assertEquals("Glue with third as null", "abcdef", new String(ByteUtil.glue("abc".getBytes(), "def".getBytes(), null)));
    }

    @Test
    public void testGlueSections() {
        assertEquals("Glue sections", "bcfg", new String(ByteUtil.glue("abcd".getBytes(), 1, 2, "efgh".getBytes(), 1, 2)));
    }

    @Test
    public void testGlueSectionsThree() {
        byte[] res = ByteUtil.glue("abcd".getBytes(), 1, 2, "efgh".getBytes(), 1, 2, "ijklm".getBytes(), 0, 2);

        assertEquals("Glue sections", "bcfgijk", new String(res));
    }

    @Test
    public void testSplit() {
        byte[] input = "abcdefghijklmnopqrstuvwxyz".getBytes();
        List<byte[]> parts = ByteUtil.split(input, 5);
        assertEquals("Two parts after split", 2, parts.size());
        assertEquals("Length of part 1 after split", 5, parts.get(0).length);
        assertEquals("Length of part 2 after split", 21, parts.get(1).length);
        assertEquals("Data in part 1 after split", "abcde", new String(parts.get(0)));
        assertEquals("Data in part 2 after split", 'f', parts.get(1)[0]);
    }

    @Test
    public void testSplitNull() {
        List<byte[]> parts = ByteUtil.split(null, 5);
        assertEquals("Two parts after split", 1, parts.size());
        assertNull("Null array on list", parts.get(0));
    }

    @Test
    public void testSplitNegativePos() {
        byte[] input = "abcdefghijklmnopqrstuvwxyz".getBytes();
        List<byte[]> parts = ByteUtil.split(input, -1);
        assertEquals("Two parts after split", 1, parts.size());
        assertEquals("Whole array on list", input.length, parts.get(0).length);
    }

    @Test
    public void testSplitOutOfBoundsPos() {
        byte[] input = "abcdefghijklmnopqrstuvwxyz".getBytes();
        List<byte[]> parts = ByteUtil.split(input, 55);
        assertEquals("Two parts after split", 1, parts.size());
        assertEquals("Whole array on list", input.length, parts.get(0).length);
    }

    @Test
    public void testIsDigit() {
        assertTrue("Should be a digit", ByteUtil.isDigit((byte) '0'));
        assertTrue("Should be a digit", ByteUtil.isDigit((byte) '1'));
        assertTrue("Should be a digit", ByteUtil.isDigit((byte) '9'));
    }

    @Test
    public void testIsntDigit() {
        assertFalse("Should not be a digit", ByteUtil.isDigit((byte) 'a'));
        assertFalse("Should not be a digit", ByteUtil.isDigit((byte) ' '));
        assertFalse("Should not be a digit", ByteUtil.isDigit((byte) 12));
    }

    @Test
    public void testIsDigitByteArray() {
        assertTrue("Should be a digit", ByteUtil.isDigit("01234".getBytes()));
        assertFalse("Should not be a digit", ByteUtil.isDigit("01234a".getBytes()));
        assertFalse("Should not be a digit", ByteUtil.isDigit("a01234".getBytes()));
    }

    @Test
    public void testIsHexadecimal() {
        assertTrue("Should be hex", ByteUtil.isHexadecimal((byte) 'a'));
        assertTrue("Should be hex", ByteUtil.isHexadecimal((byte) 'f'));
        assertTrue("Should be hex", ByteUtil.isHexadecimal((byte) 'A'));
        assertTrue("Should be hex", ByteUtil.isHexadecimal((byte) 'F'));
        assertTrue("Should be hex", ByteUtil.isHexadecimal((byte) '0'));
        assertTrue("Should be hex", ByteUtil.isHexadecimal((byte) '9'));

        assertTrue("Should be hex", ByteUtil.isHexadecimal('a'));
        assertTrue("Should be hex", ByteUtil.isHexadecimal('b'));
        assertTrue("Should be hex", ByteUtil.isHexadecimal('f'));
        assertTrue("Should be hex", ByteUtil.isHexadecimal('A'));
        assertTrue("Should be hex", ByteUtil.isHexadecimal('B'));
        assertTrue("Should be hex", ByteUtil.isHexadecimal('F'));
        assertTrue("Should be hex", ByteUtil.isHexadecimal('0'));
        assertTrue("Should be hex", ByteUtil.isHexadecimal('1'));
        assertTrue("Should be hex", ByteUtil.isHexadecimal('9'));
    }

    @Test
    public void testIsntHexadecimal() {
        assertFalse("Should not be a hex", ByteUtil.isHexadecimal((byte) ' '));
        assertFalse("Should not be a hex", ByteUtil.isHexadecimal((byte) 'G'));
        assertFalse("Should not be a hex", ByteUtil.isHexadecimal((byte) '\t'));
        assertFalse("Should not be a hex", ByteUtil.isHexadecimal((byte) '-'));
        assertFalse("Should not be a hex", ByteUtil.isHexadecimal((byte) (0xff & 254)));
        assertFalse("Should not be a hex", ByteUtil.isHexadecimal((byte) 'g'));

        assertFalse("Should not be a hex", ByteUtil.isHexadecimal(' '));
        assertFalse("Should not be a hex", ByteUtil.isHexadecimal('G'));
        assertFalse("Should not be a hex", ByteUtil.isHexadecimal('\t'));
        assertFalse("Should not be a hex", ByteUtil.isHexadecimal('-'));
        assertFalse("Should not be a hex", ByteUtil.isHexadecimal((char) (0xff & 254)));
        assertFalse("Should not be a hex", ByteUtil.isHexadecimal('g'));
    }

    @Test
    public void testIsHexadecimalArray() {
        assertTrue("Should be hex", ByteUtil.isHexadecimal("cafebabe".getBytes()));
        assertTrue("Should be hex", ByteUtil.isHexadecimal("CAFEBABE".getBytes()));
        assertFalse("Should not be hex", ByteUtil.isHexadecimal("MR. GOODBAR".getBytes()));
        assertFalse("Should not be hex", ByteUtil.isHexadecimal("Mr. Goodbar".getBytes()));
        assertFalse("Should not be hex", ByteUtil.isHexadecimal("deadbeef0123ggg".getBytes()));
    }

    @Test
    public void testIsAlpha() {
        assertTrue("Should be alpha", ByteUtil.isAlpha((byte) 'a'));
        assertTrue("Should be alpha", ByteUtil.isAlpha((byte) 'g'));
        assertTrue("Should be alpha", ByteUtil.isAlpha((byte) 'z'));
        assertTrue("Should be alpha", ByteUtil.isAlpha((byte) 'A'));
        assertTrue("Should be alpha", ByteUtil.isAlpha((byte) 'G'));
        assertTrue("Should be alpha", ByteUtil.isAlpha((byte) 'Z'));
    }

    @Test
    public void testIsntAlpha() {
        assertFalse("Should not be an alpha", ByteUtil.isAlpha((byte) ' '));
        assertFalse("Should not be an alpha", ByteUtil.isAlpha((byte) (0xff & 254)));
        assertFalse("Should not be an alpha", ByteUtil.isAlpha((byte) '-'));
    }

    @Test
    public void testIsAlphaArray() {
        assertTrue("Should be alpha", ByteUtil.isAlpha("abcABC".getBytes()));
        assertFalse("Should not be alpha", ByteUtil.isAlpha("abcABC1".getBytes()));
        assertFalse("Should not be alpha", ByteUtil.isAlpha("1abcABC".getBytes()));
        assertFalse("Should not be alpha", ByteUtil.isAlpha(" abcABC".getBytes()));
    }

    @Test
    public void testIsAlNum() {
        assertTrue("Should be alnum", ByteUtil.isAlNum((byte) 'M'));
        assertTrue("Should be alnum", ByteUtil.isAlNum((byte) '0'));
    }

    @Test
    public void testIsntAlNum() {
        assertFalse("Should not be alnum", ByteUtil.isAlNum((byte) '.'));
    }

    @Test
    public void testIsControlOrWhitespace() {
        byte[] no = new byte[] {'a', 'A', 'z', 'Z', '0', '9', '~'};
        byte[] yes = new byte[] {' ', '\t', '\n', '\r', (byte) 12, (byte) (0xff & 254)};
        for (int pos = 0; pos < yes.length; pos++) {
            assertTrue("Should be control at pos " + pos, ByteUtil.isControlOrWhiteSpace(yes, pos));
        }
        for (int pos = 0; pos < no.length; pos++) {
            assertFalse("Should not be control at pos " + pos, ByteUtil.isControlOrWhiteSpace(no, pos));
        }
    }

    @Test
    public void testGrabLine() {
        byte[] data = "This is line one\r\nThis is line two\nThis is line three".getBytes();
        assertEquals("First line extraction", "This is line one\r\n", ByteUtil.grabLine(data, 0));
        assertEquals("Middle line extraction", "This is line two\n", ByteUtil.grabLine(data, 18));
        assertEquals("Last line extraction", "This is line three", ByteUtil.grabLine(data, 35));
    }

}
