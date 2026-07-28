package emissary.util.magic;

import emissary.test.core.junit5.UnitTest;

import jakarta.xml.bind.DatatypeConverter;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;

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

}
