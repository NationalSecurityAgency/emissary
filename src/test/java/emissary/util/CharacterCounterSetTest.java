package emissary.util;

import static org.junit.Assert.assertEquals;

import emissary.test.core.UnitTest;
import org.junit.Test;

public class CharacterCounterSetTest extends UnitTest {

    @Test
    public void testLettersAndDigits() {
        CharacterCounterSet c = new CharacterCounterSet();
        c.count("ABC123");
        assertEquals("Count of letters", 3, c.getLetterCount());
        assertEquals("Count of digits", 3, c.getDigitCount());
    }

    @Test
    public void testUTF8Letters() {
        CharacterCounterSet c = new CharacterCounterSet();
        c.count("Президент Буш");
        assertEquals("Count of letters", 12, c.getLetterCount());
        assertEquals("Count of digits", 0, c.getDigitCount());
        assertEquals("Count of whitespace", 1, c.getWhitespaceCount());
    }

    @Test
    public void testPunctuation() {
        CharacterCounterSet c = new CharacterCounterSet();
        String s = "{}[]\\|.?;:!@#$%^&*()-_=+,";
        c.count(s);
        assertEquals("Count of punctuation", s.length(), c.getPunctuationCount());
    }

    @Test
    public void testUnicodePunctuationFromGeneralPunctuationBlock() {
        CharacterCounterSet c = new CharacterCounterSet();
        c.count("\u2033");
        assertEquals("Count of punctuation", 1, c.getPunctuationCount());
    }

    @Test
    public void testUnicodePunctuationFromIsoLatinExtendedBlock() {
        CharacterCounterSet c = new CharacterCounterSet();
        c.count("¿");
        assertEquals("Count of punctuation", 1, c.getPunctuationCount());
    }

    @Test
    public void testArabicDigits() {
        CharacterCounterSet c = new CharacterCounterSet();
        String s = "\u0660\u06F0";
        c.count(s);
        assertEquals("Count of arabic digits", 2, c.getDigitCount());
    }

    @Test
    public void testFullWidthDigits() {
        CharacterCounterSet c = new CharacterCounterSet();
        c.count("\uff10");
        assertEquals("Count of full width digit", 1, c.getDigitCount());
    }

    @Test
    public void testFullWidthPunctuation() {
        CharacterCounterSet c = new CharacterCounterSet();
        c.count("\uff01");
        assertEquals("Count of full width exclamation", 1, c.getPunctuationCount());
    }

}
