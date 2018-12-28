package emissary.util.magic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import javax.xml.bind.DatatypeConverter;

import emissary.test.core.UnitTest;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.junit.Test;

@SuppressWarnings("resource")
public class MagicNumberTest extends UnitTest {

    @Test
    public void testCrLf() throws ParseException, IOException {
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 string \\r\\n\\r\\n FOO");
        assertTrue("NEW_LINE in string operators must match " + m, m.test(new SeekableInMemoryByteChannel("\r\n\r\nBadCafe".getBytes())));
        assertFalse("NEW_LINE in string operators must not match bad data " + m,
                m.test(new SeekableInMemoryByteChannel("x\r\n\r\nBadCafe".getBytes())));
    }

    @Test
    public void testCrLfNotAtEnd() throws ParseException, IOException {
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 string \\r\\n\\r\\nBad FOO");
        assertTrue("NEW_LINE in string operators must match " + m, m.test(new SeekableInMemoryByteChannel("\r\n\r\nBadCafe".getBytes())));
        assertFalse("NEW_LINE in string operators must not match bad data " + m,
                m.test(new SeekableInMemoryByteChannel("x\r\n\r\nBadCafe".getBytes())));
    }

    @Test
    public void testBelong() throws ParseException, IOException {
        // ABCD
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 belong 0x41424344 FOO");
        assertTrue("BELONG hex magic operator must match", m.test(new SeekableInMemoryByteChannel("ABCD".getBytes())));
        assertFalse("BELONG hex magic operator must not match", m.test(new SeekableInMemoryByteChannel("ABCC".getBytes())));
    }

    @Test
    public void testBelongDecimal() throws ParseException, IOException {
        // ABCD
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 belong 1094861636 FOO");
        assertTrue("BELONG decimal magic operator must match: " + m, m.test(new SeekableInMemoryByteChannel("ABCD".getBytes())));
        assertFalse("BELONG decimal magic operator must not match: " + m, m.test(new SeekableInMemoryByteChannel("ABCC".getBytes())));
    }

    @Test
    public void testLelong() throws ParseException, IOException {
        // ABCD
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 lelong 0x41424344 FOO");
        assertTrue("LELONG hex magic operator must match: " + m, m.test(new SeekableInMemoryByteChannel("DCBA".getBytes())));
        assertFalse("LELONG hex magic operator must not match: " + m, m.test(new SeekableInMemoryByteChannel("ABCC".getBytes())));
    }

    @Test
    public void testLelongDecimal() throws ParseException, IOException {
        // ABCD
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 lelong 1094861636 FOO");
        assertTrue("LELONG hex magic operator must match: " + m, m.test(new SeekableInMemoryByteChannel("DCBA".getBytes())));
        assertFalse("LELONG hex magic operator must not match: " + m, m.test(new SeekableInMemoryByteChannel("ABCC".getBytes())));
    }

    @Test
    public void testGreaterThanBelong() throws ParseException, IOException {
        // ABCD
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 belong >0x41424344 FOO");
        assertTrue("Greater than magic operator failed", m.test(new SeekableInMemoryByteChannel("ABCE".getBytes())));
        assertFalse("Greater than magic operator failed", m.test(new SeekableInMemoryByteChannel("ABCD".getBytes())));
        assertFalse("Greater than magic operator failed", m.test(new SeekableInMemoryByteChannel("ABCC".getBytes())));
    }

    @Test
    public void testGreaterEqualBelong() throws ParseException, IOException {
        // ABCD
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 belong >=0x41424344 FOO");
        assertTrue("GreaterEqual magic operator failed", m.test(new SeekableInMemoryByteChannel("ABCE".getBytes())));
        assertTrue("GreaterEqual magic operator failed", m.test(new SeekableInMemoryByteChannel("ABCD".getBytes())));
        assertFalse("GreaterEqual magic operator failed", m.test(new SeekableInMemoryByteChannel("ABCC".getBytes())));
    }

    @Test
    public void testLessThanBelong() throws ParseException, IOException {
        // ABCD
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 belong <0x41424344 FOO");
        assertTrue("Less than magic operator failed", m.test(new SeekableInMemoryByteChannel("ABCC".getBytes())));
        assertFalse("Less than magic operator failed", m.test(new SeekableInMemoryByteChannel("ABCD".getBytes())));
        assertFalse("Less than magic operator failed", m.test(new SeekableInMemoryByteChannel("ABCE".getBytes())));
    }

    @Test
    public void testLessEqualBelong() throws ParseException, IOException {
        // ABCD
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 belong <=0x41424344 FOO");
        assertTrue("LessEqual magic operator failed", m.test(new SeekableInMemoryByteChannel("ABCC".getBytes())));
        assertTrue("LessEqual magic operator failed", m.test(new SeekableInMemoryByteChannel("ABCD".getBytes())));
        assertFalse("LessEqual magic operator failed", m.test(new SeekableInMemoryByteChannel("ABCE".getBytes())));
    }

    @Test
    public void testBeshort() throws ParseException, IOException {
        // AB
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 beshort 0x4142 FOO");
        assertTrue("Greater than magic operator failed", m.test(new SeekableInMemoryByteChannel("AB".getBytes())));
        assertFalse("Greater than magic operator failed", m.test(new SeekableInMemoryByteChannel("AC".getBytes())));
    }

    @Test
    public void testGreaterThanBeshort() throws ParseException, IOException {
        // AB
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 beshort >0x4142 FOO");
        assertTrue("Greater than magic operator failed", m.test(new SeekableInMemoryByteChannel("AC".getBytes())));
        assertFalse("Greater than magic operator failed", m.test(new SeekableInMemoryByteChannel("AB".getBytes())));
        assertFalse("Greater than magic operator failed", m.test(new SeekableInMemoryByteChannel("AA".getBytes())));
    }

    @Test
    public void testGreaterEqualBeshort() throws ParseException, IOException {
        // AB
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 beshort >=0x4142 FOO");
        assertTrue("GreaterEqual magic operator failed", m.test(new SeekableInMemoryByteChannel("AB".getBytes())));
        assertTrue("GreaterEqual magic operator failed", m.test(new SeekableInMemoryByteChannel("AC".getBytes())));
        assertFalse("GreaterEqual magic operator failed", m.test(new SeekableInMemoryByteChannel("AA".getBytes())));
    }

    @Test
    public void testLessThanBeshort() throws ParseException, IOException {
        // AB
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 beshort <0x4142 FOO");
        assertTrue("Less than magic operator failed", m.test(new SeekableInMemoryByteChannel("AA".getBytes())));
        assertFalse("Less than magic operator failed", m.test(new SeekableInMemoryByteChannel("AB".getBytes())));
        assertFalse("Less than magic operator failed", m.test(new SeekableInMemoryByteChannel("AC".getBytes())));
    }

    @Test
    public void testLessEqualBeshort() throws ParseException, IOException {
        // AB
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 beshort <=0x4142 FOO");
        assertTrue("LessEqual magic operator failed", m.test(new SeekableInMemoryByteChannel("AA".getBytes())));
        assertTrue("LessEqual magic operator failed", m.test(new SeekableInMemoryByteChannel("AB".getBytes())));
        assertFalse("LessEqual magic operator failed", m.test(new SeekableInMemoryByteChannel("AC".getBytes())));
    }

    @Test
    public void testString() throws ParseException, IOException {
        // ABCD
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 string ABCD FOO");
        assertTrue("String magic operator failed", m.test(new SeekableInMemoryByteChannel("ABCD".getBytes())));
        assertFalse("String magic operator failed", m.test(new SeekableInMemoryByteChannel("ABCC".getBytes())));
    }

    @Test
    public void testSubstring() throws ParseException, IOException {
        // ABCD
        MagicNumber m = MagicNumberFactory.buildMagicNumber("1 string BCD FOO");
        assertTrue("String magic operator failed", m.test(new SeekableInMemoryByteChannel("ABCD".getBytes())));
        assertFalse("String magic operator failed", m.test(new SeekableInMemoryByteChannel("ABCC".getBytes())));
        assertFalse("String magic operator failed", m.test(new SeekableInMemoryByteChannel("BCD".getBytes())));

        m = MagicNumberFactory.buildMagicNumber("2 string CD FOO");
        assertTrue("String magic operator failed", m.test(new SeekableInMemoryByteChannel("ABCD".getBytes())));
        assertFalse("String magic operator failed", m.test(new SeekableInMemoryByteChannel("ABCC".getBytes())));
        assertFalse("String magic operator failed", m.test(new SeekableInMemoryByteChannel("CD".getBytes())));
    }

    @Test
    public void testStringWithHex() throws ParseException, IOException {
        // ABCD
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 string ABC\\x44 FOO");
        assertTrue("Greater than magic operator failed", m.test(new SeekableInMemoryByteChannel("ABCD".getBytes())));
        assertFalse("Greater than magic operator failed", m.test(new SeekableInMemoryByteChannel("ABCC".getBytes())));
    }

    @Test
    public void testByte() throws ParseException, IOException {
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 byte 0x09");
        assertTrue("Equal magic operator failed", m.test(new SeekableInMemoryByteChannel(DatatypeConverter.parseHexBinary("09"))));
        assertFalse("Equal magic operator failed", m.test(new SeekableInMemoryByteChannel(DatatypeConverter.parseHexBinary("A1"))));
        assertFalse("Equal magic operator failed", m.test(new SeekableInMemoryByteChannel(DatatypeConverter.parseHexBinary("AB"))));

        m = MagicNumberFactory.buildMagicNumber("0 byte 0xF2");
        assertTrue("Equal magic operator failed", m.test(new SeekableInMemoryByteChannel(DatatypeConverter.parseHexBinary("F2"))));
        assertFalse("Equal magic operator failed", m.test(new SeekableInMemoryByteChannel(DatatypeConverter.parseHexBinary("A1"))));
        assertFalse("Equal magic operator failed", m.test(new SeekableInMemoryByteChannel(DatatypeConverter.parseHexBinary("AB"))));
    }

    @Test
    public void testGreaterThanByte() throws ParseException, IOException {
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 byte >0x09 FOO");
        assertTrue("Greater than magic operator failed", m.test(new SeekableInMemoryByteChannel(DatatypeConverter.parseHexBinary("A1"))));
        assertFalse("Greater than magic operator failed", m.test(new SeekableInMemoryByteChannel(DatatypeConverter.parseHexBinary("01"))));
        assertFalse("Greater than magic operator failed", m.test(new SeekableInMemoryByteChannel(DatatypeConverter.parseHexBinary("09"))));

        m = MagicNumberFactory.buildMagicNumber("0 byte >0xF2 FOO");
        assertFalse("Greater than magic operator failed", m.test(new SeekableInMemoryByteChannel(DatatypeConverter.parseHexBinary("A1"))));
        assertTrue("Greater than magic operator failed", m.test(new SeekableInMemoryByteChannel(DatatypeConverter.parseHexBinary("F8"))));
        assertFalse("Greater than magic operator failed", m.test(new SeekableInMemoryByteChannel(DatatypeConverter.parseHexBinary("91"))));
        assertFalse("Greater than magic operator failed", m.test(new SeekableInMemoryByteChannel(DatatypeConverter.parseHexBinary("F2"))));
    }

    @Test
    public void testGreaterEqualByte() throws ParseException, IOException {
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 byte >=0x09 FOO");
        assertTrue("GreaterEqual than magic operator failed", m.test(new SeekableInMemoryByteChannel(DatatypeConverter.parseHexBinary("A1"))));
        assertFalse("GreaterEqual than magic operator failed", m.test(new SeekableInMemoryByteChannel(DatatypeConverter.parseHexBinary("01"))));
        assertTrue("GreaterEqual than magic operator failed", m.test(new SeekableInMemoryByteChannel(DatatypeConverter.parseHexBinary("09"))));

        m = MagicNumberFactory.buildMagicNumber("0 byte >=0xF2 FOO");
        assertFalse("GreaterEqual than magic operator failed", m.test(new SeekableInMemoryByteChannel(DatatypeConverter.parseHexBinary("A1"))));
        assertTrue("GreaterEqual than magic operator failed", m.test(new SeekableInMemoryByteChannel(DatatypeConverter.parseHexBinary("F8"))));
        assertFalse("GreaterEqual than magic operator failed", m.test(new SeekableInMemoryByteChannel(DatatypeConverter.parseHexBinary("91"))));
        assertTrue("GreaterEqual than magic operator failed", m.test(new SeekableInMemoryByteChannel(DatatypeConverter.parseHexBinary("F2"))));
    }

    @Test
    public void testLessThanByte() throws ParseException, IOException {
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 byte <0x09 FOO");
        assertFalse("Less than magic operator failed", m.test(new SeekableInMemoryByteChannel(DatatypeConverter.parseHexBinary("A1"))));
        assertTrue("Less than magic operator failed", m.test(new SeekableInMemoryByteChannel(DatatypeConverter.parseHexBinary("01"))));
        assertFalse("Less than magic operator failed", m.test(new SeekableInMemoryByteChannel(DatatypeConverter.parseHexBinary("09"))));

        m = MagicNumberFactory.buildMagicNumber("0 byte <0xF2 FOO");
        assertTrue("Less than magic operator failed", m.test(new SeekableInMemoryByteChannel(DatatypeConverter.parseHexBinary("A1"))));
        assertFalse("Less than magic operator failed", m.test(new SeekableInMemoryByteChannel(DatatypeConverter.parseHexBinary("F8"))));
        assertTrue("Less than magic operator failed", m.test(new SeekableInMemoryByteChannel(DatatypeConverter.parseHexBinary("91"))));
        assertFalse("Less than magic operator failed", m.test(new SeekableInMemoryByteChannel(DatatypeConverter.parseHexBinary("F2"))));
    }

    @Test
    public void testLessEqualByte() throws ParseException, IOException {
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 byte <=0x09 FOO");
        assertFalse("LessEqual than magic operator failed", m.test(new SeekableInMemoryByteChannel(DatatypeConverter.parseHexBinary("A1"))));
        assertTrue("LessEqual than magic operator failed", m.test(new SeekableInMemoryByteChannel(DatatypeConverter.parseHexBinary("01"))));
        assertTrue("LessEqual than magic operator failed", m.test(new SeekableInMemoryByteChannel(DatatypeConverter.parseHexBinary("09"))));

        m = MagicNumberFactory.buildMagicNumber("0 byte <=0xF2 FOO");
        assertTrue("LessEqual than magic operator failed", m.test(new SeekableInMemoryByteChannel(DatatypeConverter.parseHexBinary("A1"))));
        assertFalse("LessEqual than magic operator failed", m.test(new SeekableInMemoryByteChannel(DatatypeConverter.parseHexBinary("F8"))));
        assertTrue("LessEqual than magic operator failed", m.test(new SeekableInMemoryByteChannel(DatatypeConverter.parseHexBinary("91"))));
        assertTrue("LessEqual than magic operator failed", m.test(new SeekableInMemoryByteChannel(DatatypeConverter.parseHexBinary("F2"))));
    }

}
