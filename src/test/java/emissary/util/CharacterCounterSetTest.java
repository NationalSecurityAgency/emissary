package emissary.util;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CharacterCounterSetTest extends UnitTest {

    static Stream<Arguments> arguments() {
        return Stream.of(
                // test: string, letter count, digit count, punctuation count, blank space count
                Arguments.of("ABC123", 3, 3, 0, 0),
                Arguments.of("Президент Буш", 12, 0, 0, 1),
                Arguments.of("{}[]\\|.?;:!@#$%^&*()-_=+,", 0, 0, 25, 0),
                Arguments.of("\u2033", 0, 0, 1, 0),
                Arguments.of("¿", 0, 0, 1, 0),
                Arguments.of("\u0660\u06F0", 0, 2, 0, 0),
                Arguments.of("\uff10", 0, 1, 0, 0),
                Arguments.of("\uff01", 0, 0, 1, 0));
    }

    @ParameterizedTest
    @MethodSource("arguments")
    void testCount(String s, int expectedLetterCount, int expectedDigitCount,
            int expectedPunctuationCount, int expectedBlankSpaceCount) {
        CharacterCounterSet c = new CharacterCounterSet();
        c.count(s);
        assertEquals(expectedLetterCount, c.getLetterCount(), "Count of letters");
        assertEquals(expectedDigitCount, c.getDigitCount(), "Count of digits");
        assertEquals(expectedPunctuationCount, c.getPunctuationCount(), "Count of punctuation");
        assertEquals(expectedBlankSpaceCount, c.getBlankSpaceCount(), "Count of blank space");
    }

}
