package emissary.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import emissary.test.core.UnitTest;
import org.junit.jupiter.api.Test;

class CharacterCounterSetTest extends UnitTest {

    @Test
    void testLettersAndDigits() {
        CharacterCounterSet c = new CharacterCounterSet();
        c.count("ABC123");
        assertEquals(3, c.getLetterCount(), "Count of letters");
        assertEquals(3, c.getDigitCount(), "Count of digits");
    }

    @Test
    void testUTF8Letters() {
        CharacterCounterSet c = new CharacterCounterSet();
        c.count("Президент Буш");
        assertEquals(12, c.getLetterCount(), "Count of letters");
        assertEquals(0, c.getDigitCount(), "Count of digits");
        assertEquals(1, c.getWhitespaceCount(), "Count of whitespace");
    }

    @Test
    void testPunctuation() {
        CharacterCounterSet c = new CharacterCounterSet();
        String s = "{}[]\\|.?;:!@#$%^&*()-_=+,";
        c.count(s);
        assertEquals(s.length(), c.getPunctuationCount(), "Count of punctuation");
    }

    @Test
    void testUnicodePunctuationFromGeneralPunctuationBlock() {
        CharacterCounterSet c = new CharacterCounterSet();
        c.count("\u2033");
        assertEquals(1, c.getPunctuationCount(), "Count of punctuation");
    }

    @Test
    void testUnicodePunctuationFromIsoLatinExtendedBlock() {
        CharacterCounterSet c = new CharacterCounterSet();
        c.count("¿");
        assertEquals(1, c.getPunctuationCount(), "Count of punctuation");
    }

    @Test
    void testArabicDigits() {
        CharacterCounterSet c = new CharacterCounterSet();
        String s = "\u0660\u06F0";
        c.count(s);
        assertEquals(2, c.getDigitCount(), "Count of arabic digits");
    }

    @Test
    void testFullWidthDigits() {
        CharacterCounterSet c = new CharacterCounterSet();
        c.count("\uff10");
        assertEquals(1, c.getDigitCount(), "Count of full width digit");
    }

    @Test
    void testFullWidthPunctuation() {
        CharacterCounterSet c = new CharacterCounterSet();
        c.count("\uff01");
        assertEquals(1, c.getPunctuationCount(), "Count of full width exclamation");
    }

}
