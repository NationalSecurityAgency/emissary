package emissary.util.magic;

import emissary.test.core.junit5.UnitTest;

import jakarta.xml.bind.DatatypeConverter;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagicNumberTest extends UnitTest {

    @Test
    void testCrLf() throws ParseException {
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 string \\r\\n\\r\\n FOO");
        assertTrue(m.test("\r\n\r\nBadCafe".getBytes()), "NEW_LINE in string operators must match " + m);
        assertFalse(m.test("x\r\n\r\nBadCafe".getBytes()), "NEW_LINE in string operators must not match bad data " + m);
    }

    @Test
    void testCrLfNotAtEnd() throws ParseException {
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 string \\r\\n\\r\\nBad FOO");
        assertTrue(m.test("\r\n\r\nBadCafe".getBytes()), "NEW_LINE in string operators must match " + m);
        assertFalse(m.test("x\r\n\r\nBadCafe".getBytes()), "NEW_LINE in string operators must not match bad data " + m);
    }

    @Test
    void testBelong() throws ParseException {
        // ABCD
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 belong 0x41424344 FOO");
        assertTrue(m.test("ABCD".getBytes()), "BELONG hex magic operator must match");
        assertFalse(m.test("ABCC".getBytes()), "BELONG hex magic operator must not match");
    }

    @Test
    void testBelongDecimal() throws ParseException {
        // ABCD
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 belong 1094861636 FOO");
        assertTrue(m.test("ABCD".getBytes()), "BELONG decimal magic operator must match: " + m);
        assertFalse(m.test("ABCC".getBytes()), "BELONG decimal magic operator must not match: " + m);
    }

    @Test
    void testLelong() throws ParseException {
        // ABCD
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 lelong 0x41424344 FOO");
        assertTrue(m.test("DCBA".getBytes()), "LELONG hex magic operator must match: " + m);
        assertFalse(m.test("ABCC".getBytes()), "LELONG hex magic operator must not match: " + m);
    }

    @Test
    void testLelongDecimal() throws ParseException {
        // ABCD
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 lelong 1094861636 FOO");
        assertTrue(m.test("DCBA".getBytes()), "LELONG hex magic operator must match: " + m);
        assertFalse(m.test("ABCC".getBytes()), "LELONG hex magic operator must not match: " + m);
    }

    @Test
    void testGreaterThanBelong() throws ParseException {
        // ABCD
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 belong >0x41424344 FOO");
        assertTrue(m.test("ABCE".getBytes()), "Greater than magic operator failed");
        assertFalse(m.test("ABCD".getBytes()), "Greater than magic operator failed");
        assertFalse(m.test("ABCC".getBytes()), "Greater than magic operator failed");
    }

    @Test
    void testGreaterEqualBelong() throws ParseException {
        // ABCD
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 belong >=0x41424344 FOO");
        assertTrue(m.test("ABCE".getBytes()), "GreaterEqual magic operator failed");
        assertTrue(m.test("ABCD".getBytes()), "GreaterEqual magic operator failed");
        assertFalse(m.test("ABCC".getBytes()), "GreaterEqual magic operator failed");
    }

    @Test
    void testLessThanBelong() throws ParseException {
        // ABCD
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 belong <0x41424344 FOO");
        assertTrue(m.test("ABCC".getBytes()), "Less than magic operator failed");
        assertFalse(m.test("ABCD".getBytes()), "Less than magic operator failed");
        assertFalse(m.test("ABCE".getBytes()), "Less than magic operator failed");
    }

    @Test
    void testLessEqualBelong() throws ParseException, DecoderException {
        // ABCD
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 belong <=0x41424344 FOO");
        assertTrue(m.test("ABCC".getBytes()), "LessEqual magic operator failed");
        assertTrue(m.test("ABCD".getBytes()), "LessEqual magic operator failed");
        assertFalse(m.test("ABCE".getBytes()), "LessEqual magic operator failed");

        m = MagicNumberFactory.buildMagicNumber("0 belong <=0x00010000 FOO");
        assertTrue(m.test(Hex.decodeHex("00010000")), "Big Endian less than equal failed on exact match");
        assertTrue(m.test(Hex.decodeHex("0000FFFF")), "Big Endian less than equal failed");
        assertFalse(m.test(Hex.decodeHex("00020000")), "Big Endian less than equal failed");
    }

    @Test
    void testBeshort() throws ParseException {
        // AB
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 beshort 0x4142 FOO");
        assertTrue(m.test("AB".getBytes()), "Greater than magic operator failed");
        assertFalse(m.test("AC".getBytes()), "Greater than magic operator failed");
    }

    @Test
    void testGreaterThanBeshort() throws ParseException, DecoderException {
        // AB
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 beshort >0x4142 FOO");
        assertTrue(m.test("AC".getBytes()), "Greater than magic operator failed");
        assertFalse(m.test("AB".getBytes()), "Greater than magic operator failed");
        assertFalse(m.test("AA".getBytes()), "Greater than magic operator failed");

        m = MagicNumberFactory.buildMagicNumber("0 beshort >0x0000 FOO");
        assertFalse(m.test(Hex.decodeHex("0000")), "Greater than magic operator failed");
        assertTrue(m.test(Hex.decodeHex("0001")), "Greater than magic operator failed");
        assertTrue(m.test(Hex.decodeHex("0101")), "Greater than magic operator failed");
        assertTrue(m.test(Hex.decodeHex("0100")), "Greater than magic operator failed");

        m = MagicNumberFactory.buildMagicNumber("0 beshort >0x0100 FOO");
        assertTrue(m.test(Hex.decodeHex("0200")), "Big Endian greater than failed on MSB");
        assertFalse(m.test(Hex.decodeHex("00FF")), "Big Endian greater than failed on MSB tie-breaker");
        assertTrue(m.test(Hex.decodeHex("0101")), "Big Endian greater than failed on LSB tie-breaker");
    }

    @Test
    void testGreaterEqualBeshort() throws ParseException {
        // AB
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 beshort >=0x4142 FOO");
        assertTrue(m.test("AB".getBytes()), "GreaterEqual magic operator failed");
        assertTrue(m.test("AC".getBytes()), "GreaterEqual magic operator failed");
        assertFalse(m.test("AA".getBytes()), "GreaterEqual magic operator failed");
    }

    @Test
    void testLessThanBeshort() throws ParseException {
        // AB
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 beshort <0x4142 FOO");
        assertTrue(m.test("AA".getBytes()), "Less than magic operator failed");
        assertFalse(m.test("AB".getBytes()), "Less than magic operator failed");
        assertFalse(m.test("AC".getBytes()), "Less than magic operator failed");
    }

    @Test
    void testLessEqualBeshort() throws ParseException {
        // AB
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 beshort <=0x4142 FOO");
        assertTrue(m.test("AA".getBytes()), "LessEqual magic operator failed");
        assertTrue(m.test("AB".getBytes()), "LessEqual magic operator failed");
        assertFalse(m.test("AC".getBytes()), "LessEqual magic operator failed");
    }

    @Test
    void testGreaterThanLeshort() throws ParseException, DecoderException {
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 leshort >0x0001 FOO");
        assertTrue(m.test(Hex.decodeHex("0200")), "Little Endian greater than failed on LSB increment");
        assertTrue(m.test(Hex.decodeHex("0001")), "Little Endian greater than failed on MSB increment");
        assertFalse(m.test(Hex.decodeHex("0000")), "Little Endian greater than failed on lower value");
    }

    @Test
    void testLessThanEqualLelong() throws ParseException, DecoderException {
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 lelong <=0x00000100 FOO");
        assertTrue(m.test(Hex.decodeHex("00010000")), "Little Endian less than equal failed on exact match");
        assertTrue(m.test(Hex.decodeHex("FF000000")), "Little Endian less than equal failed on lower value");
        assertFalse(m.test(Hex.decodeHex("01010000")), "Little Endian less than equal failed on higher value");
    }

    @Test
    void testSameInputBigEndianVsLittleEndian() throws ParseException, DecoderException {
        MagicNumber bem = MagicNumberFactory.buildMagicNumber("0 beshort >0x0005 FOO");
        assertTrue(bem.test(Hex.decodeHex("0200")), "Big Endian failed: 0x0200 (512) should be greater than 5");

        MagicNumber lem = MagicNumberFactory.buildMagicNumber("0 leshort >0x0005 FOO");
        assertFalse(lem.test(Hex.decodeHex("0200")), "Little Endian failed: 0x0200 (2) should NOT be greater than 5");
    }

    @Test
    void testOrBeshort() throws ParseException, DecoderException {
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 beshort x0x1010 FOO");

        assertFalse(m.test(Hex.decodeHex("0000")), "OR magic operator failed");
        assertFalse(m.test(Hex.decodeHex("0001")), "OR magic operator failed");
        assertFalse(m.test(Hex.decodeHex("0101")), "OR magic operator failed");

        assertTrue(m.test(Hex.decodeHex("0010")), "OR magic operator failed");
        assertTrue(m.test(Hex.decodeHex("0011")), "OR magic operator failed");
        assertTrue(m.test(Hex.decodeHex("1000")), "OR magic operator failed");
        assertTrue(m.test(Hex.decodeHex("1010")), "OR magic operator failed");
        assertTrue(m.test(Hex.decodeHex("1100")), "OR magic operator failed");
        assertTrue(m.test(Hex.decodeHex("1111")), "OR magic operator failed");
        assertTrue(m.test(Hex.decodeHex("FFFF")), "OR magic operator failed");
    }

    @Test
    void testString() throws ParseException {
        // ABCD
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 string ABCD FOO");
        assertTrue(m.test("ABCD".getBytes()), "String magic operator failed");
        assertFalse(m.test("ABCC".getBytes()), "String magic operator failed");
    }

    @Test
    void testRepeatedByteStringMatch() throws ParseException {
        // 10 carriage returns: \(10)\x0d
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 string \\(10)\\x0d FOO");

        byte[] expected = new byte[10];
        Arrays.fill(expected, (byte) 0x0d);

        assertTrue(m.test(expected), "Repeated byte string should match");
    }

    @Test
    void testRepeatedByteAtOffset() throws ParseException {
        // 5 spaces at offset 2: 2 string \(5)\x20 FOO
        MagicNumber m = MagicNumberFactory.buildMagicNumber("2 string \\(5)\\x20 FOO");

        byte[] data = new byte[7];
        Arrays.fill(data, (byte) 0x20);
        assertTrue(m.test(data), "Repeated byte string should match at offset");

        byte[] tooShort = new byte[6];
        Arrays.fill(tooShort, (byte) 0x20);
        assertFalse(m.test(tooShort), "Repeated byte string should not match shorter array at offset");
    }

    @Test
    void testRepeatedByteCountZero() throws ParseException {
        // 0 repeats: \(0)\x0d
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 string \\(0)\\x0d FOO");
        assertTrue(m.test(new byte[0]), "Count 0 should match empty array");
        assertTrue(m.test("Any data".getBytes()), "Count 0 should match any data as it requires 0 bytes");
    }

    @Test
    void testRepeatedByteCountOne() throws ParseException {
        // 1 repeat: \(1)\x0d
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 string \\(1)\\x0d FOO");
        assertTrue(m.test(new byte[] {0x0d}), "Count 1 should match single byte");
        assertFalse(m.test(new byte[] {0x0e}), "Count 1 should not match wrong byte");
    }

    @Test
    void testRepeatedByteLiteral() throws ParseException {
        // 3 'A's: \(3)A
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 string \\(3)A FOO");
        assertTrue(m.test("AAA".getBytes()), "Repeated literal should match");
        assertFalse(m.test("AA".getBytes()), "Repeated literal should not match shorter array");
    }

    @Test
    void testRepeatedByteMalformed() throws ParseException {
        // Missing closing parenthesis: \(10\x0d
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 string \\(10\\x0d FOO");

        byte[] expected = {0x0d};
        assertTrue(m.test(expected), "Malformed repeated byte should skip the malformed count and process the rest");
    }

    @Test
    void testRepeatedByteStringTooShort() throws ParseException {
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 string \\(10)\\x0d FOO");

        byte[] tooShort = new byte[9];
        Arrays.fill(tooShort, (byte) 0x0d);
        assertFalse(m.test(tooShort), "Repeated byte string should not match shorter array");
    }

    @Test
    void testRepeatedByteStringWrongByte() throws ParseException {
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 string \\(10)\\x0d FOO");

        byte[] wrongByte = new byte[10];
        Arrays.fill(wrongByte, (byte) 0x0e);
        assertFalse(m.test(wrongByte), "Repeated byte string should not match different byte");
    }

    @Test
    void testMixedRepeatedAndNormal() throws ParseException {
        // \(3)\x41\(2)\x42\(1)\x43 -> AAABB C
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 string \\(3)\\x41\\(2)\\x42\\(1)\\x43 FOO");

        byte[] expected = {0x41, 0x41, 0x41, 0x42, 0x42, 0x43};
        assertTrue(m.test(expected), "Mixed repeated bytes should match");
    }

    @Test
    void testRepeatedByteWithOtherEscapes() throws ParseException {
        // \(3)\r\(2)\n
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 string \\(3)\\r\\(2)\\n FOO");

        byte[] expected = {0x0d, 0x0d, 0x0d, 0x0a, 0x0a};
        assertTrue(m.test(expected), "Repeated common escapes should match");
    }

    @Test
    void testSubstring() throws ParseException {
        // ABCD
        MagicNumber m = MagicNumberFactory.buildMagicNumber("1 string BCD FOO");
        assertTrue(m.test("ABCD".getBytes()), "String magic operator failed");
        assertFalse(m.test("ABCC".getBytes()), "String magic operator failed");
        assertFalse(m.test("BCD".getBytes()), "String magic operator failed");

        m = MagicNumberFactory.buildMagicNumber("2 string CD FOO");
        assertTrue(m.test("ABCD".getBytes()), "String magic operator failed");
        assertFalse(m.test("ABCC".getBytes()), "String magic operator failed");
        assertFalse(m.test("CD".getBytes()), "String magic operator failed");
    }

    @Test
    void testStringWithHex() throws ParseException {
        // ABCD
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 string ABC\\x44 FOO");
        assertTrue(m.test("ABCD".getBytes()), "Greater than magic operator failed");
        assertFalse(m.test("ABCC".getBytes()), "Greater than magic operator failed");
    }

    @Test
    void testByte() throws ParseException {
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 byte 0x09");
        assertTrue(m.test(DatatypeConverter.parseHexBinary("09")), "Equal magic operator failed");
        assertFalse(m.test(DatatypeConverter.parseHexBinary("A1")), "Equal magic operator failed");
        assertFalse(m.test(DatatypeConverter.parseHexBinary("AB")), "Equal magic operator failed");

        m = MagicNumberFactory.buildMagicNumber("0 byte 0xF2");
        assertTrue(m.test(DatatypeConverter.parseHexBinary("F2")), "Equal magic operator failed");
        assertFalse(m.test(DatatypeConverter.parseHexBinary("A1")), "Equal magic operator failed");
        assertFalse(m.test(DatatypeConverter.parseHexBinary("AB")), "Equal magic operator failed");
    }

    @Test
    void testGreaterThanByte() throws ParseException {
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 byte >0x09 FOO");
        assertTrue(m.test(DatatypeConverter.parseHexBinary("A1")), "Greater than magic operator failed");
        assertFalse(m.test(DatatypeConverter.parseHexBinary("01")), "Greater than magic operator failed");
        assertFalse(m.test(DatatypeConverter.parseHexBinary("09")), "Greater than magic operator failed");

        m = MagicNumberFactory.buildMagicNumber("0 byte >0xF2 FOO");
        assertFalse(m.test(DatatypeConverter.parseHexBinary("A1")), "Greater than magic operator failed");
        assertTrue(m.test(DatatypeConverter.parseHexBinary("F8")), "Greater than magic operator failed");
        assertFalse(m.test(DatatypeConverter.parseHexBinary("91")), "Greater than magic operator failed");
        assertFalse(m.test(DatatypeConverter.parseHexBinary("F2")), "Greater than magic operator failed");
    }

    @Test
    void testGreaterEqualByte() throws ParseException {
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 byte >=0x09 FOO");
        assertTrue(m.test(DatatypeConverter.parseHexBinary("A1")), "GreaterEqual than magic operator failed");
        assertFalse(m.test(DatatypeConverter.parseHexBinary("01")), "GreaterEqual than magic operator failed");
        assertTrue(m.test(DatatypeConverter.parseHexBinary("09")), "GreaterEqual than magic operator failed");

        m = MagicNumberFactory.buildMagicNumber("0 byte >=0xF2 FOO");
        assertFalse(m.test(DatatypeConverter.parseHexBinary("A1")), "GreaterEqual than magic operator failed");
        assertTrue(m.test(DatatypeConverter.parseHexBinary("F8")), "GreaterEqual than magic operator failed");
        assertFalse(m.test(DatatypeConverter.parseHexBinary("91")), "GreaterEqual than magic operator failed");
        assertTrue(m.test(DatatypeConverter.parseHexBinary("F2")), "GreaterEqual than magic operator failed");
    }

    @Test
    void testLessThanByte() throws ParseException {
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 byte <0x09 FOO");
        assertFalse(m.test(DatatypeConverter.parseHexBinary("A1")), "Less than magic operator failed");
        assertTrue(m.test(DatatypeConverter.parseHexBinary("01")), "Less than magic operator failed");
        assertFalse(m.test(DatatypeConverter.parseHexBinary("09")), "Less than magic operator failed");

        m = MagicNumberFactory.buildMagicNumber("0 byte <0xF2 FOO");
        assertTrue(m.test(DatatypeConverter.parseHexBinary("A1")), "Less than magic operator failed");
        assertFalse(m.test(DatatypeConverter.parseHexBinary("F8")), "Less than magic operator failed");
        assertTrue(m.test(DatatypeConverter.parseHexBinary("91")), "Less than magic operator failed");
        assertFalse(m.test(DatatypeConverter.parseHexBinary("F2")), "Less than magic operator failed");
    }

    @Test
    void testLessEqualByte() throws ParseException {
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 byte <=0x09 FOO");
        assertFalse(m.test(DatatypeConverter.parseHexBinary("A1")), "LessEqual than magic operator failed");
        assertTrue(m.test(DatatypeConverter.parseHexBinary("01")), "LessEqual than magic operator failed");
        assertTrue(m.test(DatatypeConverter.parseHexBinary("09")), "LessEqual than magic operator failed");

        m = MagicNumberFactory.buildMagicNumber("0 byte <=0xF2 FOO");
        assertTrue(m.test(DatatypeConverter.parseHexBinary("A1")), "LessEqual than magic operator failed");
        assertFalse(m.test(DatatypeConverter.parseHexBinary("F8")), "LessEqual than magic operator failed");
        assertTrue(m.test(DatatypeConverter.parseHexBinary("91")), "LessEqual than magic operator failed");
        assertTrue(m.test(DatatypeConverter.parseHexBinary("F2")), "LessEqual than magic operator failed");
    }

    @Test
    void testRepeatedByteLargeCount() throws ParseException {
        // Test large repeat count: \(100)\x00
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 string \\(100)\\x00 FOO");
        byte[] expected = new byte[100];
        assertTrue(m.test(expected), "Large repeat count should match");
        assertFalse(m.test(new byte[99]), "Large repeat count should not match shorter array");
    }

    @Test
    void testRepeatedByteMixedWithNormal() throws ParseException {
        // Test: \(2)\x41B\(1)\x43 -> AAB C
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 string \\(2)\\x41B\\(1)\\x43 FOO");
        byte[] expected = {0x41, 0x41, 0x42, 0x43};
        assertTrue(m.test(expected), "Mixed repeat and normal bytes should match");
        // "AABC" is actually 0x41, 0x41, 0x42, 0x43 which matches the pattern
        assertTrue(m.test("AABC".getBytes()), "AABC should match AABC pattern");
        assertFalse(m.test("ABCD".getBytes()), "ABCD should not match AABC pattern");
    }

    @Test
    void testRepeatedByteOctalValue() throws ParseException {
        // Test repeat with octal byte value: \(3)\040 (three spaces)
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 string \\(3)\\040 FOO");
        byte[] expected = {0x20, 0x20, 0x20};
        assertTrue(m.test(expected), "Repeat with octal value should match");
    }

    @Test
    void testRepeatedByteHexValue() throws ParseException {
        // Test repeat with hex byte value: \(3)\x20 (three spaces)
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 string \\(3)\\x20 FOO");
        byte[] expected = {0x20, 0x20, 0x20};
        assertTrue(m.test(expected), "Repeat with hex value should match");
    }

    @Test
    void testRepeatedByteAtNonZeroOffset() throws ParseException {
        // Test repeat at offset 1: >1 string \(5)\x41
        MagicNumber m = MagicNumberFactory.buildMagicNumber("1 string \\(5)\\x41 FOO");
        byte[] data = new byte[6];
        Arrays.fill(data, (byte) 0x41);
        assertTrue(m.test(data), "Repeat at offset should match");

        byte[] wrongFirst = {0x00, 0x41, 0x41, 0x41, 0x41, 0x41};
        assertTrue(m.test(wrongFirst), "Repeat at offset should match even if first byte differs");
    }

    @Test
    void testRepeatedByteMalformedNoByte() throws ParseException {
        // Test: \(10) - missing byte value after closing paren
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 string \\(10) FOO");
        // This is malformed - no byte value specified, so it should not match
        assertFalse(m.test(new byte[0]), "Malformed no byte should not match");
        assertFalse(m.test("anything".getBytes()), "Malformed no byte should not match any data");
    }

    @Test
    void testRepeatedByteCountTooLarge() throws ParseException {
        // Test: \(200)\x00 - count larger than data
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 string \\(200)\\x00 FOO");
        byte[] shortData = new byte[50];
        assertFalse(m.test(shortData), "Should not match when repeat count exceeds data length");
    }


    @Test
    void testRepeatedByteWithLiteralAfter() throws ParseException {
        // Test: \(27)\0\1\0 - 27 zeros followed by literal bytes
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 string \\(27)\\0\\1\\0 FOO");
        byte[] expected = new byte[30];
        Arrays.fill(expected, 0, 27, (byte) 0x00);
        expected[27] = 0x01;
        expected[28] = 0x00;
        assertTrue(m.test(expected), "27 zeros followed by literal should match");
    }

}
