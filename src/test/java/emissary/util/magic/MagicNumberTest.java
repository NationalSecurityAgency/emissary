package emissary.util.magic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import javax.xml.bind.DatatypeConverter;

import emissary.test.core.UnitTest;
import org.junit.Test;

public class MagicNumberTest extends UnitTest {

    @Test
    public void testCrLf() throws ParseException {
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 string \\r\\n\\r\\n FOO");
        assertTrue("NEW_LINE in string operators must match " + m, m.test("\r\n\r\nBadCafe".getBytes()));
        assertFalse("NEW_LINE in string operators must not match bad data " + m, m.test("x\r\n\r\nBadCafe".getBytes()));
    }

    @Test
    public void testCrLfNotAtEnd() throws ParseException {
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 string \\r\\n\\r\\nBad FOO");
        assertTrue("NEW_LINE in string operators must match " + m, m.test("\r\n\r\nBadCafe".getBytes()));
        assertFalse("NEW_LINE in string operators must not match bad data " + m, m.test("x\r\n\r\nBadCafe".getBytes()));
    }

    @Test
    public void testBelong() throws ParseException {
        // ABCD
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 belong 0x41424344 FOO");
        assertTrue("BELONG hex magic operator must match", m.test("ABCD".getBytes()));
        assertFalse("BELONG hex magic operator must not match", m.test("ABCC".getBytes()));
    }

    @Test
    public void testBelongDecimal() throws ParseException {
        // ABCD
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 belong 1094861636 FOO");
        assertTrue("BELONG decimal magic operator must match: " + m, m.test("ABCD".getBytes()));
        assertFalse("BELONG decimal magic operator must not match: " + m, m.test("ABCC".getBytes()));
    }

    @Test
    public void testLelong() throws ParseException {
        // ABCD
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 lelong 0x41424344 FOO");
        assertTrue("LELONG hex magic operator must match: " + m, m.test("DCBA".getBytes()));
        assertFalse("LELONG hex magic operator must not match: " + m, m.test("ABCC".getBytes()));
    }

    @Test
    public void testLelongDecimal() throws ParseException {
        // ABCD
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 lelong 1094861636 FOO");
        assertTrue("LELONG hex magic operator must match: " + m, m.test("DCBA".getBytes()));
        assertFalse("LELONG hex magic operator must not match: " + m, m.test("ABCC".getBytes()));
    }

    @Test
    public void testGreaterThanBelong() throws ParseException {
        // ABCD
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 belong >0x41424344 FOO");
        assertTrue("Greater than magic operator failed", m.test("ABCE".getBytes()));
        assertFalse("Greater than magic operator failed", m.test("ABCD".getBytes()));
        assertFalse("Greater than magic operator failed", m.test("ABCC".getBytes()));
    }

    @Test
    public void testGreaterEqualBelong() throws ParseException {
        // ABCD
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 belong >=0x41424344 FOO");
        assertTrue("GreaterEqual magic operator failed", m.test("ABCE".getBytes()));
        assertTrue("GreaterEqual magic operator failed", m.test("ABCD".getBytes()));
        assertFalse("GreaterEqual magic operator failed", m.test("ABCC".getBytes()));
    }

    @Test
    public void testLessThanBelong() throws ParseException {
        // ABCD
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 belong <0x41424344 FOO");
        assertTrue("Less than magic operator failed", m.test("ABCC".getBytes()));
        assertFalse("Less than magic operator failed", m.test("ABCD".getBytes()));
        assertFalse("Less than magic operator failed", m.test("ABCE".getBytes()));
    }

    @Test
    public void testLessEqualBelong() throws ParseException {
        // ABCD
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 belong <=0x41424344 FOO");
        assertTrue("LessEqual magic operator failed", m.test("ABCC".getBytes()));
        assertTrue("LessEqual magic operator failed", m.test("ABCD".getBytes()));
        assertFalse("LessEqual magic operator failed", m.test("ABCE".getBytes()));
    }

    @Test
    public void testBeshort() throws ParseException {
        // AB
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 beshort 0x4142 FOO");
        assertTrue("Greater than magic operator failed", m.test("AB".getBytes()));
        assertFalse("Greater than magic operator failed", m.test("AC".getBytes()));
    }

    @Test
    public void testGreaterThanBeshort() throws ParseException {
        // AB
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 beshort >0x4142 FOO");
        assertTrue("Greater than magic operator failed", m.test("AC".getBytes()));
        assertFalse("Greater than magic operator failed", m.test("AB".getBytes()));
        assertFalse("Greater than magic operator failed", m.test("AA".getBytes()));
    }

    @Test
    public void testGreaterEqualBeshort() throws ParseException {
        // AB
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 beshort >=0x4142 FOO");
        assertTrue("GreaterEqual magic operator failed", m.test("AB".getBytes()));
        assertTrue("GreaterEqual magic operator failed", m.test("AC".getBytes()));
        assertFalse("GreaterEqual magic operator failed", m.test("AA".getBytes()));
    }

    @Test
    public void testLessThanBeshort() throws ParseException {
        // AB
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 beshort <0x4142 FOO");
        assertTrue("Less than magic operator failed", m.test("AA".getBytes()));
        assertFalse("Less than magic operator failed", m.test("AB".getBytes()));
        assertFalse("Less than magic operator failed", m.test("AC".getBytes()));
    }

    @Test
    public void testLessEqualBeshort() throws ParseException {
        // AB
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 beshort <=0x4142 FOO");
        assertTrue("LessEqual magic operator failed", m.test("AA".getBytes()));
        assertTrue("LessEqual magic operator failed", m.test("AB".getBytes()));
        assertFalse("LessEqual magic operator failed", m.test("AC".getBytes()));
    }

    @Test
    public void testString() throws ParseException {
        // ABCD
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 string ABCD FOO");
        assertTrue("String magic operator failed", m.test("ABCD".getBytes()));
        assertFalse("String magic operator failed", m.test("ABCC".getBytes()));
    }

    @Test
    public void testSubstring() throws ParseException {
        // ABCD
        MagicNumber m = MagicNumberFactory.buildMagicNumber("1 string BCD FOO");
        assertTrue("String magic operator failed", m.test("ABCD".getBytes()));
        assertFalse("String magic operator failed", m.test("ABCC".getBytes()));
        assertFalse("String magic operator failed", m.test("BCD".getBytes()));

        m = MagicNumberFactory.buildMagicNumber("2 string CD FOO");
        assertTrue("String magic operator failed", m.test("ABCD".getBytes()));
        assertFalse("String magic operator failed", m.test("ABCC".getBytes()));
        assertFalse("String magic operator failed", m.test("CD".getBytes()));
    }

    @Test
    public void testStringWithHex() throws ParseException {
        // ABCD
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 string ABC\\x44 FOO");
        assertTrue("Greater than magic operator failed", m.test("ABCD".getBytes()));
        assertFalse("Greater than magic operator failed", m.test("ABCC".getBytes()));
    }

    @Test
    public void testByte() throws ParseException {
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 byte 0x09");
        assertTrue("Equal magic operator failed", m.test(DatatypeConverter.parseHexBinary("09")));
        assertFalse("Equal magic operator failed", m.test(DatatypeConverter.parseHexBinary("A1")));
        assertFalse("Equal magic operator failed", m.test(DatatypeConverter.parseHexBinary("AB")));

        m = MagicNumberFactory.buildMagicNumber("0 byte 0xF2");
        assertTrue("Equal magic operator failed", m.test(DatatypeConverter.parseHexBinary("F2")));
        assertFalse("Equal magic operator failed", m.test(DatatypeConverter.parseHexBinary("A1")));
        assertFalse("Equal magic operator failed", m.test(DatatypeConverter.parseHexBinary("AB")));
    }

    @Test
    public void testGreaterThanByte() throws ParseException {
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 byte >0x09 FOO");
        assertTrue("Greater than magic operator failed", m.test(DatatypeConverter.parseHexBinary("A1")));
        assertFalse("Greater than magic operator failed", m.test(DatatypeConverter.parseHexBinary("01")));
        assertFalse("Greater than magic operator failed", m.test(DatatypeConverter.parseHexBinary("09")));

        m = MagicNumberFactory.buildMagicNumber("0 byte >0xF2 FOO");
        assertFalse("Greater than magic operator failed", m.test(DatatypeConverter.parseHexBinary("A1")));
        assertTrue("Greater than magic operator failed", m.test(DatatypeConverter.parseHexBinary("F8")));
        assertFalse("Greater than magic operator failed", m.test(DatatypeConverter.parseHexBinary("91")));
        assertFalse("Greater than magic operator failed", m.test(DatatypeConverter.parseHexBinary("F2")));
    }

    @Test
    public void testGreaterEqualByte() throws ParseException {
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 byte >=0x09 FOO");
        assertTrue("GreaterEqual than magic operator failed", m.test(DatatypeConverter.parseHexBinary("A1")));
        assertFalse("GreaterEqual than magic operator failed", m.test(DatatypeConverter.parseHexBinary("01")));
        assertTrue("GreaterEqual than magic operator failed", m.test(DatatypeConverter.parseHexBinary("09")));

        m = MagicNumberFactory.buildMagicNumber("0 byte >=0xF2 FOO");
        assertFalse("GreaterEqual than magic operator failed", m.test(DatatypeConverter.parseHexBinary("A1")));
        assertTrue("GreaterEqual than magic operator failed", m.test(DatatypeConverter.parseHexBinary("F8")));
        assertFalse("GreaterEqual than magic operator failed", m.test(DatatypeConverter.parseHexBinary("91")));
        assertTrue("GreaterEqual than magic operator failed", m.test(DatatypeConverter.parseHexBinary("F2")));
    }

    @Test
    public void testLessThanByte() throws ParseException {
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 byte <0x09 FOO");
        assertFalse("Less than magic operator failed", m.test(DatatypeConverter.parseHexBinary("A1")));
        assertTrue("Less than magic operator failed", m.test(DatatypeConverter.parseHexBinary("01")));
        assertFalse("Less than magic operator failed", m.test(DatatypeConverter.parseHexBinary("09")));

        m = MagicNumberFactory.buildMagicNumber("0 byte <0xF2 FOO");
        assertTrue("Less than magic operator failed", m.test(DatatypeConverter.parseHexBinary("A1")));
        assertFalse("Less than magic operator failed", m.test(DatatypeConverter.parseHexBinary("F8")));
        assertTrue("Less than magic operator failed", m.test(DatatypeConverter.parseHexBinary("91")));
        assertFalse("Less than magic operator failed", m.test(DatatypeConverter.parseHexBinary("F2")));
    }

    @Test
    public void testLessEqualByte() throws ParseException {
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 byte <=0x09 FOO");
        assertFalse("LessEqual than magic operator failed", m.test(DatatypeConverter.parseHexBinary("A1")));
        assertTrue("LessEqual than magic operator failed", m.test(DatatypeConverter.parseHexBinary("01")));
        assertTrue("LessEqual than magic operator failed", m.test(DatatypeConverter.parseHexBinary("09")));

        m = MagicNumberFactory.buildMagicNumber("0 byte <=0xF2 FOO");
        assertTrue("LessEqual than magic operator failed", m.test(DatatypeConverter.parseHexBinary("A1")));
        assertFalse("LessEqual than magic operator failed", m.test(DatatypeConverter.parseHexBinary("F8")));
        assertTrue("LessEqual than magic operator failed", m.test(DatatypeConverter.parseHexBinary("91")));
        assertTrue("LessEqual than magic operator failed", m.test(DatatypeConverter.parseHexBinary("F2")));
    }

}
