package emissary.util.magic;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import emissary.test.core.junit5.UnitTest;
import jakarta.xml.bind.DatatypeConverter;

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
    void testLessEqualBelong() throws ParseException {
        // ABCD
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 belong <=0x41424344 FOO");
        assertTrue(m.test("ABCC".getBytes()), "LessEqual magic operator failed");
        assertTrue(m.test("ABCD".getBytes()), "LessEqual magic operator failed");
        assertFalse(m.test("ABCE".getBytes()), "LessEqual magic operator failed");
    }

    @Test
    void testBeshort() throws ParseException {
        // AB
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 beshort 0x4142 FOO");
        assertTrue(m.test("AB".getBytes()), "Greater than magic operator failed");
        assertFalse(m.test("AC".getBytes()), "Greater than magic operator failed");
    }

    @Test
    void testGreaterThanBeshort() throws ParseException {
        // AB
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 beshort >0x4142 FOO");
        assertTrue(m.test("AC".getBytes()), "Greater than magic operator failed");
        assertFalse(m.test("AB".getBytes()), "Greater than magic operator failed");
        assertFalse(m.test("AA".getBytes()), "Greater than magic operator failed");
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
        assertFalse(m.test(DatatypeConverter.parseHexBinary("A1")));
        assertTrue(m.test(DatatypeConverter.parseHexBinary("01")));
        assertTrue(m.test(DatatypeConverter.parseHexBinary("09")));

        m = MagicNumberFactory.buildMagicNumber("0 byte <=0xF2 FOO");
        assertTrue(m.test(DatatypeConverter.parseHexBinary("A1")));
        assertFalse(m.test(DatatypeConverter.parseHexBinary("F8")));
        assertTrue(m.test(DatatypeConverter.parseHexBinary("91")));
        assertTrue(m.test(DatatypeConverter.parseHexBinary("F2")));
    }

}
