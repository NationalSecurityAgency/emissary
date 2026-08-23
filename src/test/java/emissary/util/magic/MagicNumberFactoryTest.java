package emissary.util.magic;

import emissary.test.core.junit5.UnitTest;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagicNumberFactoryTest extends UnitTest {

    @Test
    void testByte() throws ParseException, DecoderException {
        assertTrue(MagicNumberFactory.buildMagicNumber("0 byte 0x41 FOO").test(Hex.decodeHex("41")),
                "byte should match a single byte");
        assertFalse(MagicNumberFactory.buildMagicNumber("0 byte 0x41 FOO").test(Hex.decodeHex("42")),
                "byte should not match a different single byte");
    }

    @Test
    void testShort() throws ParseException, DecoderException {
        // SHORT is big-endian 2-byte (native-endian on big-endian machines, but the code treats it as big-endian)
        assertTrue(MagicNumberFactory.buildMagicNumber("0 short 0x4142 FOO").test(Hex.decodeHex("4142")),
                "short should match two bytes big-endian");
        assertFalse(MagicNumberFactory.buildMagicNumber("0 short 0x4142 FOO").test(Hex.decodeHex("4241")),
                "short should not match reversed bytes");
    }

    @Test
    void testLong() throws ParseException, DecoderException {
        assertTrue(MagicNumberFactory.buildMagicNumber("0 long 0x41424344 FOO").test(Hex.decodeHex("41424344")),
                "long should match four bytes big-endian");
        assertFalse(MagicNumberFactory.buildMagicNumber("0 long 0x41424344 FOO").test(Hex.decodeHex("44434241")),
                "long should not match reversed bytes");
    }

    @Test
    void testString() throws ParseException {
        assertTrue(MagicNumberFactory.buildMagicNumber("0 string ABCD FOO").test("ABCD".getBytes()),
                "string should match the exact bytes");
        assertFalse(MagicNumberFactory.buildMagicNumber("0 string ABCD FOO").test("ABCE".getBytes()),
                "string should not match a slightly different string");
    }

    @Test
    void testBeshort() throws ParseException, DecoderException {
        assertTrue(MagicNumberFactory.buildMagicNumber("0 beshort 0x4142 FOO").test(Hex.decodeHex("4142")),
                "beshort should match two bytes big-endian");
        assertFalse(MagicNumberFactory.buildMagicNumber("0 beshort 0x4142 FOO").test(Hex.decodeHex("4241")),
                "beshort should not match reversed bytes");
    }

    @Test
    void testBelong() throws ParseException, DecoderException {
        assertTrue(MagicNumberFactory.buildMagicNumber("0 belong 0x41424344 FOO").test(Hex.decodeHex("41424344")),
                "belong should match four bytes big-endian");
        assertFalse(MagicNumberFactory.buildMagicNumber("0 belong 0x41424344 FOO").test(Hex.decodeHex("44434241")),
                "belong should not match reversed bytes");
    }

    @Test
    void testBelongMask() throws ParseException, DecoderException {
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 belong&0x000f0000 0x000e0000 MACHO");
        assertTrue(m.test(Hex.decodeHex("CAFEBABE")), "CAFEBABE keeps the digit E after masking, so it matches");
        assertFalse(m.test(Hex.decodeHex("CAFFBABE")), "CAFFBABE keeps F instead of E, so it must not match");
        assertFalse(m.test(Hex.decodeHex("CAFDBABE")), "CAFDBABE keeps D instead of E, so it must not match");
    }

    @Test
    void testLeshort() throws ParseException, DecoderException {
        assertTrue(MagicNumberFactory.buildMagicNumber("0 leshort 0x4142 FOO").test(Hex.decodeHex("4241")),
                "leshort should match two bytes little-endian");
        assertFalse(MagicNumberFactory.buildMagicNumber("0 leshort 0x4142 FOO").test(Hex.decodeHex("4142")),
                "leshort should not match big-endian ordering");
    }

    @Test
    void testLelong() throws ParseException, DecoderException {
        assertTrue(MagicNumberFactory.buildMagicNumber("0 lelong 0x41424344 FOO").test(Hex.decodeHex("44434241")),
                "lelong should match four bytes little-endian");
        assertFalse(MagicNumberFactory.buildMagicNumber("0 lelong 0x41424344 FOO").test(Hex.decodeHex("41424344")),
                "lelong should not match big-endian ordering");
    }

    @Test
    void testRegex() {
        ParseException e = assertThrows(ParseException.class,
                () -> MagicNumberFactory.buildMagicNumber("0 regex x FOO"),
                "regex type should be rejected");
        assertTrue(e.getMessage().contains(MagicNumberFactory.UNSUPPORTED_DATATYPE_MSG_REGEX),
                "regex must be recognized as a skippable regex-type failure, but got: " + e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"date", "bedate", "ledate", "ldate", "beldate", "leldate", "qdate", "beqdate", "leqdate", "qldate", "beldate-0x7C25B080",
            "bedate-0x7C25B080"})
    void testDate(String type) {
        ParseException e = assertThrows(ParseException.class,
                () -> MagicNumberFactory.buildMagicNumber("0 " + type + " x FOO"),
                type + ": date types should be rejected");
        assertTrue(e.getMessage().contains("Unsupported Data Type: "),
                type + " must fail with the ordinary 'Unsupported Data Type' message, but got: " + e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ubyte", "ushort", "ulong", "ubeshort", "ubelong", "uleshort", "ulelong", "udate", "uldate", "ubedate", "ubeldate",
            "uledate", "uleldate", "ulequad", "ustring"})
    void testUnsigned(String type) {
        ParseException e = assertThrows(ParseException.class,
                () -> MagicNumberFactory.buildMagicNumber("0 " + type + " x FOO"),
                type + ": unsigned types should be rejected");
        assertTrue(e.getMessage().contains(MagicNumberFactory.UNSUPPORTED_DATATYPE_MSG_UNSIGNED),
                type + " must be recognized as a skippable unsigned-type failure, but got: " + e.getMessage());
    }

    @ParameterizedTest
    @CsvSource({"BESHORT, 4142, 4142", "BELONG, 41424344, 41424344", "LESHORT, 0102, 0201", "LELONG, 01020304, 04030201"})
    void testCaseInsensitive(String type, String valueHex, String dataHex) throws ParseException, DecoderException {
        assertTrue(MagicNumberFactory.buildMagicNumber("0 " + type + " 0x" + valueHex + " FOO").test(Hex.decodeHex(dataHex)),
                type + " type should be case-insensitive");
    }

    @Test
    void testGreaterThanOperator() throws ParseException, DecoderException {
        assertTrue(MagicNumberFactory.buildMagicNumber("0 belong >10 HUGE").test(Hex.decodeHex("0000000B")),
                "11 must beat 10");
        assertFalse(MagicNumberFactory.buildMagicNumber("0 belong >10 HUGE").test(Hex.decodeHex("00000009")),
                "9 must not beat 10");
    }

    @Test
    void testLessThanOperator() throws ParseException, DecoderException {
        assertTrue(MagicNumberFactory.buildMagicNumber("0 belong <0x80000000 OLD").test(Hex.decodeHex("7FFFFFFF")),
                "'<' only matches values smaller than 0x80000000");
        assertFalse(MagicNumberFactory.buildMagicNumber("0 belong <0x80000000 OLD").test(Hex.decodeHex("80000001")),
                "'<' must reject a value bigger than 0x80000000");
    }

    @Test
    void testComparisons() throws ParseException, DecoderException {
        assertTrue(MagicNumberFactory.buildMagicNumber("0 belong >10 HUGE").test(Hex.decodeHex("FFFFFFFF")),
                "the biggest unsigned number must beat 10");
        assertFalse(MagicNumberFactory.buildMagicNumber("0 belong >10 HUGE").test(Hex.decodeHex("00000009")),
                "9 must not beat 10");
        assertTrue(MagicNumberFactory.buildMagicNumber("0 belong 0xFFFFFFFF MAXED").test(Hex.decodeHex("FFFFFFFF")),
                "an all-bits-set value must equal itself");
    }

    @Test
    void testStringSubstitution() throws ParseException {
        MagicNumber m = MagicNumberFactory.buildMagicNumber("0 string x SUBST");
        assertTrue(m.isSubstitute(), "the 'x' value should set the substitute flag");
    }
}
