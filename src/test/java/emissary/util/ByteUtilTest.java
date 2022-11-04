package emissary.util;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import emissary.test.core.junit5.UnitTest;
import org.junit.jupiter.api.Test;

class ByteUtilTest extends UnitTest {

    @Test
    void testGlue() {
        assertEquals("abcdef", new String(ByteUtil.glue("abc".getBytes(UTF_8), "def".getBytes(UTF_8)), UTF_8), "Glue two whole arrays");
    }

    @Test
    void testGlueThree() {
        assertEquals("abcdefghi", new String(ByteUtil.glue("abc".getBytes(UTF_8), "def".getBytes(UTF_8), "ghi".getBytes(UTF_8)), UTF_8),
                "Glue three whole arrays");
    }


    @Test
    void testGlueNull() {
        assertEquals("def", new String(ByteUtil.glue(null, "def".getBytes(UTF_8)), UTF_8), "Glue with first as null");
        assertEquals("abc", new String(ByteUtil.glue("abc".getBytes(UTF_8), null), UTF_8), "Glue with second as null");
    }

    @Test
    void testGlueNullThree() {
        assertEquals("defghi", new String(ByteUtil.glue(null, "def".getBytes(UTF_8), "ghi".getBytes(UTF_8)), UTF_8), "Glue with first as null");

        assertEquals("abcghi", new String(ByteUtil.glue("abc".getBytes(UTF_8), null, "ghi".getBytes(UTF_8)), UTF_8), "Glue with second as null");

        assertEquals("abcdef", new String(ByteUtil.glue("abc".getBytes(UTF_8), "def".getBytes(UTF_8), null), UTF_8), "Glue with third as null");
    }

    @Test
    void testGlueSections() {
        assertEquals("bcfg", new String(ByteUtil.glue("abcd".getBytes(UTF_8), 1, 2, "efgh".getBytes(UTF_8), 1, 2), UTF_8), "Glue sections");
    }

    @Test
    void testGlueSectionsThree() {
        byte[] res = ByteUtil.glue("abcd".getBytes(UTF_8), 1, 2, "efgh".getBytes(UTF_8), 1, 2, "ijklm".getBytes(UTF_8), 0, 2);

        assertEquals("bcfgijk", new String(res, UTF_8), "Glue sections");
    }

    @Test
    void testSplit() {
        byte[] input = "abcdefghijklmnopqrstuvwxyz".getBytes(UTF_8);
        List<byte[]> parts = ByteUtil.split(input, 5);
        assertEquals(2, parts.size(), "Two parts after split");
        assertEquals(5, parts.get(0).length, "Length of part 1 after split");
        assertEquals(21, parts.get(1).length, "Length of part 2 after split");
        assertEquals("abcde", new String(parts.get(0), UTF_8), "Data in part 1 after split");
        assertEquals('f', parts.get(1)[0], "Data in part 2 after split");
    }

    @Test
    void testSplitNull() {
        List<byte[]> parts = ByteUtil.split(null, 5);
        assertEquals(1, parts.size(), "Two parts after split");
        assertNull(parts.get(0), "Null array on list");
    }

    @Test
    void testSplitNegativePos() {
        byte[] input = "abcdefghijklmnopqrstuvwxyz".getBytes(UTF_8);
        List<byte[]> parts = ByteUtil.split(input, -1);
        assertEquals(1, parts.size(), "Two parts after split");
        assertEquals(input.length, parts.get(0).length, "Whole array on list");
    }

    @Test
    void testSplitOutOfBoundsPos() {
        byte[] input = "abcdefghijklmnopqrstuvwxyz".getBytes(UTF_8);
        List<byte[]> parts = ByteUtil.split(input, 55);
        assertEquals(1, parts.size(), "Two parts after split");
        assertEquals(input.length, parts.get(0).length, "Whole array on list");
    }

    @Test
    void testIsDigit() {
        assertTrue(ByteUtil.isDigit((byte) '0'), "Should be a digit");
        assertTrue(ByteUtil.isDigit((byte) '1'), "Should be a digit");
        assertTrue(ByteUtil.isDigit((byte) '9'), "Should be a digit");
    }

    @Test
    void testIsntDigit() {
        assertFalse(ByteUtil.isDigit((byte) 'a'), "Should not be a digit");
        assertFalse(ByteUtil.isDigit((byte) ' '), "Should not be a digit");
        assertFalse(ByteUtil.isDigit((byte) 12), "Should not be a digit");
    }

    @Test
    void testIsDigitByteArray() {
        assertTrue(ByteUtil.isDigit("01234".getBytes(UTF_8)), "Should be a digit");
        assertFalse(ByteUtil.isDigit("01234a".getBytes(UTF_8)), "Should not be a digit");
        assertFalse(ByteUtil.isDigit("a01234".getBytes(UTF_8)), "Should not be a digit");
    }

    @Test
    void testIsHexadecimal() {
        assertTrue(ByteUtil.isHexadecimal((byte) 'a'), "Should be hex");
        assertTrue(ByteUtil.isHexadecimal((byte) 'f'), "Should be hex");
        assertTrue(ByteUtil.isHexadecimal((byte) 'A'), "Should be hex");
        assertTrue(ByteUtil.isHexadecimal((byte) 'F'), "Should be hex");
        assertTrue(ByteUtil.isHexadecimal((byte) '0'), "Should be hex");
        assertTrue(ByteUtil.isHexadecimal((byte) '9'), "Should be hex");

        assertTrue(ByteUtil.isHexadecimal('a'), "Should be hex");
        assertTrue(ByteUtil.isHexadecimal('b'), "Should be hex");
        assertTrue(ByteUtil.isHexadecimal('f'), "Should be hex");
        assertTrue(ByteUtil.isHexadecimal('A'), "Should be hex");
        assertTrue(ByteUtil.isHexadecimal('B'), "Should be hex");
        assertTrue(ByteUtil.isHexadecimal('F'), "Should be hex");
        assertTrue(ByteUtil.isHexadecimal('0'), "Should be hex");
        assertTrue(ByteUtil.isHexadecimal('1'), "Should be hex");
        assertTrue(ByteUtil.isHexadecimal('9'), "Should be hex");
    }

    @Test
    void testIsntHexadecimal() {
        assertFalse(ByteUtil.isHexadecimal((byte) ' '), "Should not be a hex");
        assertFalse(ByteUtil.isHexadecimal((byte) 'G'), "Should not be a hex");
        assertFalse(ByteUtil.isHexadecimal((byte) '\t'), "Should not be a hex");
        assertFalse(ByteUtil.isHexadecimal((byte) '-'), "Should not be a hex");
        assertFalse(ByteUtil.isHexadecimal((byte) (0xff & 254)), "Should not be a hex");
        assertFalse(ByteUtil.isHexadecimal((byte) 'g'), "Should not be a hex");

        assertFalse(ByteUtil.isHexadecimal(' '), "Should not be a hex");
        assertFalse(ByteUtil.isHexadecimal('G'), "Should not be a hex");
        assertFalse(ByteUtil.isHexadecimal('\t'), "Should not be a hex");
        assertFalse(ByteUtil.isHexadecimal('-'), "Should not be a hex");
        assertFalse(ByteUtil.isHexadecimal((char) (0xff & 254)), "Should not be a hex");
        assertFalse(ByteUtil.isHexadecimal('g'), "Should not be a hex");
    }

    @Test
    void testIsHexadecimalArray() {
        assertTrue(ByteUtil.isHexadecimal("cafebabe".getBytes(UTF_8)), "Should be hex");
        assertTrue(ByteUtil.isHexadecimal("CAFEBABE".getBytes(UTF_8)), "Should be hex");
        assertFalse(ByteUtil.isHexadecimal("MR. GOODBAR".getBytes(UTF_8)), "Should not be hex");
        assertFalse(ByteUtil.isHexadecimal("Mr. Goodbar".getBytes(UTF_8)), "Should not be hex");
        assertFalse(ByteUtil.isHexadecimal("deadbeef0123ggg".getBytes(UTF_8)), "Should not be hex");
    }

    @Test
    void testIsAlpha() {
        assertTrue(ByteUtil.isAlpha((byte) 'a'), "Should be alpha");
        assertTrue(ByteUtil.isAlpha((byte) 'g'), "Should be alpha");
        assertTrue(ByteUtil.isAlpha((byte) 'z'), "Should be alpha");
        assertTrue(ByteUtil.isAlpha((byte) 'A'), "Should be alpha");
        assertTrue(ByteUtil.isAlpha((byte) 'G'), "Should be alpha");
        assertTrue(ByteUtil.isAlpha((byte) 'Z'), "Should be alpha");
    }

    @Test
    void testIsntAlpha() {
        assertFalse(ByteUtil.isAlpha((byte) ' '), "Should not be an alpha");
        assertFalse(ByteUtil.isAlpha((byte) (0xff & 254)), "Should not be an alpha");
        assertFalse(ByteUtil.isAlpha((byte) '-'), "Should not be an alpha");
    }

    @Test
    void testIsAlphaArray() {
        assertTrue(ByteUtil.isAlpha("abcABC".getBytes(UTF_8)), "Should be alpha");
        assertFalse(ByteUtil.isAlpha("abcABC1".getBytes(UTF_8)), "Should not be alpha");
        assertFalse(ByteUtil.isAlpha("1abcABC".getBytes(UTF_8)), "Should not be alpha");
        assertFalse(ByteUtil.isAlpha(" abcABC".getBytes(UTF_8)), "Should not be alpha");
    }

    @Test
    void testIsAlNum() {
        assertTrue(ByteUtil.isAlNum((byte) 'M'), "Should be alnum");
        assertTrue(ByteUtil.isAlNum((byte) '0'), "Should be alnum");
    }

    @Test
    void testIsntAlNum() {
        assertFalse(ByteUtil.isAlNum((byte) '.'), "Should not be alnum");
    }

    @Test
    void testIsControlOrWhitespace() {
        byte[] no = new byte[] {'a', 'A', 'z', 'Z', '0', '9', '~'};
        byte[] yes = new byte[] {' ', '\t', '\n', '\r', (byte) 12, (byte) (0xff & 254)};
        for (int pos = 0; pos < yes.length; pos++) {
            assertTrue(ByteUtil.isControlOrWhiteSpace(yes, pos), "Should be control at pos " + pos);
        }
        for (int pos = 0; pos < no.length; pos++) {
            assertFalse(ByteUtil.isControlOrWhiteSpace(no, pos), "Should not be control at pos " + pos);
        }
    }

    @Test
    void testGrabLine() {
        byte[] data = "This is line one\r\nThis is line two\nThis is line three".getBytes(UTF_8);
        assertEquals("This is line one\r\n", ByteUtil.grabLine(data, 0), "First line extraction");
        assertEquals("This is line two\n", ByteUtil.grabLine(data, 18), "Middle line extraction");
        assertEquals("This is line three", ByteUtil.grabLine(data, 35), "Last line extraction");
    }

}
