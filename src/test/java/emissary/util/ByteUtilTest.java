package emissary.util;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ByteUtilTest extends UnitTest {

    @Test
    void testGlue() {
        assertEquals("abcdef", new String(ByteUtil.glue("abc".getBytes(), "def".getBytes())), "Glue two whole arrays");
    }

    @Test
    void testGlueThree() {
        assertEquals("abcdefghi", new String(ByteUtil.glue("abc".getBytes(), "def".getBytes(), "ghi".getBytes())), "Glue three whole arrays");
    }


    @Test
    void testGlueNull() {
        assertEquals("def", new String(ByteUtil.glue(null, "def".getBytes())), "Glue with first as null");
        assertEquals("abc", new String(ByteUtil.glue("abc".getBytes(), null)), "Glue with second as null");
    }

    @Test
    void testGlueNullThree() {
        assertEquals("defghi", new String(ByteUtil.glue(null, "def".getBytes(), "ghi".getBytes())), "Glue with first as null");

        assertEquals("abcghi", new String(ByteUtil.glue("abc".getBytes(), null, "ghi".getBytes())), "Glue with second as null");

        assertEquals("abcdef", new String(ByteUtil.glue("abc".getBytes(), "def".getBytes(), null)), "Glue with third as null");
    }

    @Test
    void testGlueSections() {
        assertEquals("bcfg", new String(ByteUtil.glue("abcd".getBytes(), 1, 2, "efgh".getBytes(), 1, 2)), "Glue sections");
    }

    @Test
    void testGlueSectionsThree() {
        byte[] res = ByteUtil.glue("abcd".getBytes(), 1, 2, "efgh".getBytes(), 1, 2, "ijklm".getBytes(), 0, 2);

        assertEquals("bcfgijk", new String(res), "Glue sections");
    }

    @Test
    void testSplit() {
        byte[] input = "abcdefghijklmnopqrstuvwxyz".getBytes();
        List<byte[]> parts = ByteUtil.split(input, 5);
        assertEquals(2, parts.size(), "Two parts after split");
        assertEquals(5, parts.get(0).length, "Length of part 1 after split");
        assertEquals(21, parts.get(1).length, "Length of part 2 after split");
        assertEquals("abcde", new String(parts.get(0)), "Data in part 1 after split");
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
        byte[] input = "abcdefghijklmnopqrstuvwxyz".getBytes();
        List<byte[]> parts = ByteUtil.split(input, -1);
        assertEquals(1, parts.size(), "Two parts after split");
        assertEquals(input.length, parts.get(0).length, "Whole array on list");
    }

    @Test
    void testSplitOutOfBoundsPos() {
        byte[] input = "abcdefghijklmnopqrstuvwxyz".getBytes();
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
        assertTrue(ByteUtil.isDigit("01234".getBytes()), "Should be a digit");
        assertFalse(ByteUtil.isDigit("01234a".getBytes()), "Should not be a digit");
        assertFalse(ByteUtil.isDigit("a01234".getBytes()), "Should not be a digit");
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
        assertTrue(ByteUtil.isHexadecimal("cafebabe".getBytes()), "Should be hex");
        assertTrue(ByteUtil.isHexadecimal("CAFEBABE".getBytes()), "Should be hex");
        assertFalse(ByteUtil.isHexadecimal("MR. GOODBAR".getBytes()), "Should not be hex");
        assertFalse(ByteUtil.isHexadecimal("Mr. Goodbar".getBytes()), "Should not be hex");
        assertFalse(ByteUtil.isHexadecimal("deadbeef0123ggg".getBytes()), "Should not be hex");
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
        assertTrue(ByteUtil.isAlpha("abcABC".getBytes()), "Should be alpha");
        assertFalse(ByteUtil.isAlpha("abcABC1".getBytes()), "Should not be alpha");
        assertFalse(ByteUtil.isAlpha("1abcABC".getBytes()), "Should not be alpha");
        assertFalse(ByteUtil.isAlpha(" abcABC".getBytes()), "Should not be alpha");
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
    void testIsControlOrBlankSpace() {
        byte[] no = new byte[] {'a', 'A', 'z', 'Z', '0', '9', '~'};
        byte[] yes = new byte[] {' ', '\t', '\n', '\r', (byte) 12, (byte) (0xff & 254)};
        for (int pos = 0; pos < yes.length; pos++) {
            assertTrue(ByteUtil.isControlOrBlankSpace(yes, pos), "Should be control at pos " + pos);
        }
        for (int pos = 0; pos < no.length; pos++) {
            assertFalse(ByteUtil.isControlOrBlankSpace(no, pos), "Should not be control at pos " + pos);
        }
    }

    @Test
    void testGrabLine() {
        byte[] data = "This is line one\r\nThis is line two\nThis is line three".getBytes();
        assertEquals("This is line one\r\n", ByteUtil.grabLine(data, 0), "First line extraction");
        assertEquals("This is line two\n", ByteUtil.grabLine(data, 18), "Middle line extraction");
        assertEquals("This is line three", ByteUtil.grabLine(data, 35), "Last line extraction");
    }

    @Test
    void testContainsNonIndexableValues() {
        String newLineCarriageTab = "This is line one\r\nThis is line two\nThis is line three\n\nEnding with a tab\t";
        assertFalse(ByteUtil.hasNonPrintableValues(newLineCarriageTab.getBytes(StandardCharsets.UTF_8)));
        assertFalse(ByteUtil.containsNonIndexableBytes(newLineCarriageTab.getBytes(StandardCharsets.UTF_8)));

        // 2-byte character: € (Euro symbol)
        String euro = "€";
        assertEquals("\u20ac", euro);
        assertTrue(ByteUtil.hasNonPrintableValues(euro.getBytes(StandardCharsets.UTF_8)));
        assertFalse(ByteUtil.containsNonIndexableBytes(euro.getBytes(StandardCharsets.UTF_8)));

        // 3-byte character: (Chinese character for "hello")
        String nihao = "你好";
        assertEquals("\u4f60\u597d", nihao);
        assertTrue(ByteUtil.hasNonPrintableValues(nihao.getBytes(StandardCharsets.UTF_8)));
        assertFalse(ByteUtil.containsNonIndexableBytes(nihao.getBytes(StandardCharsets.UTF_8)));

        // 4-byte character: (Emoji: grinning face)
        String emoji = "😊";
        assertEquals("\uD83D\uDE0A", emoji);
        assertTrue(ByteUtil.hasNonPrintableValues(emoji.getBytes(StandardCharsets.UTF_8)));
        assertFalse(ByteUtil.containsNonIndexableBytes(emoji.getBytes(StandardCharsets.UTF_8)));

        // Unicode value denoting 'null'
        String uNull = " ";
        assertEquals("\u0000", uNull);
        assertTrue(ByteUtil.hasNonPrintableValues(uNull.getBytes(StandardCharsets.UTF_8)));
        assertTrue(ByteUtil.containsNonIndexableBytes(uNull.getBytes(StandardCharsets.UTF_8)));

        // Narrow No-Break Space
        String nnbsp = " ";
        assertEquals("\u202F", nnbsp);
        assertTrue(ByteUtil.hasNonPrintableValues(nnbsp.getBytes(StandardCharsets.UTF_8)));
        assertTrue(ByteUtil.containsNonIndexableBytes(nnbsp.getBytes(StandardCharsets.UTF_8)));

        // byte order mark
        String zwbsp = "﻿";
        assertEquals("\uFEFF", zwbsp);
        assertTrue(ByteUtil.hasNonPrintableValues(zwbsp.getBytes(StandardCharsets.UTF_8)));
        assertTrue(ByteUtil.containsNonIndexableBytes(zwbsp.getBytes(StandardCharsets.UTF_8)));

        // UTF-8 Error Replacement Character
        String rep = "�";
        assertEquals("\uFFFD", rep);
        assertTrue(ByteUtil.hasNonPrintableValues(rep.getBytes(StandardCharsets.UTF_8)));
        assertTrue(ByteUtil.containsNonIndexableBytes(rep.getBytes(StandardCharsets.UTF_8)));
    }

}
