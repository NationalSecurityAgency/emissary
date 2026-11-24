package emissary.util;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class LocaleDateTimeParsingTest extends UnitTest {

    @ParameterizedTest
    @CsvSource({
            "fr, 4 janv. 2016, 2016-01-04T00:00:00",
            "de, 4 März 2016, 2016-03-04T00:00:00",
            "fr-CA, 4 juill. 2016, 2016-07-04T00:00:00",
            "es-VE, 4 ene. 2016, 2016-01-04T00:00:00",
            "en-AU, 4 June 2016, 2016-06-04T00:00:00",
            "fr, 'lun., 4 janv. 2016 18:20:30 +0000', 2016-01-04T18:20:30",
            "fr, 4 janv. 2016 13:20:30 -0500, 2016-01-04T18:20:30",
            "fr, 4 janv. 2016 18:20 GMT, 2016-01-04T18:20:00",
            "zh-CN, 4 1月 2016 6:20:30 下午, 2016-01-04T18:20:30",
            "fr, 2016-01-04T13:20:30-05:00, 2016-01-04T18:20:30",
            "fr, 2016-01-04 13:20 America/Chicago, 2016-01-04T19:20:00",
            "de, 20160104182030, 2016-01-04T18:20:30",
            "fr, 2016-004, 2016-01-04T00:00:00"
    })
    void parseConfiguredLocales(String tag, String text, String expected) {
        assertBothParsers(text, TimeUtil.parseLocales(List.of(tag)), LocalDateTime.parse(expected));
    }

    @ParameterizedTest
    @CsvSource({
            "4 juill. 2016, 2016-07-04T00:00:00",
            "4 ene. 2016, 2016-01-04T00:00:00"
    })
    void wildcardIncludesRegionalMonthNames(String text, String expected) {
        assertBothParsers(text, TimeUtil.parseLocales(List.of("*")), LocalDateTime.parse(expected));
    }

    @Test
    void configuredLocaleOrderControlsAmbiguousZoneNames() {
        String text = "2016-01-04 13:20 CST";
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm z", Locale.UK);
        ZonedDateTime british = ZonedDateTime.parse(text, pattern);
        ZonedDateTime english = ZonedDateTime.parse(text, pattern.withLocale(Locale.ENGLISH));
        assertNotEquals(british.toInstant(), english.toInstant(), "CST has different locale-dependent interpretations");

        assertBothParsers(text, List.of(Locale.UK, Locale.ENGLISH), british.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime());
        assertBothParsers(text, List.of(Locale.ENGLISH, Locale.UK), english.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime());
    }

    @Test
    void explicitlySuppliedFormatterKeepsItsLocale() {
        String text = "2016-01-04 13:20 CST";
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm z", Locale.UK);
        assertEquals(ZonedDateTime.parse(text, pattern), FlexibleDateTimeParser.parse(text, pattern));

        DateTimeFormatter french = DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.FRENCH);
        assertEquals(LocalDateTime.of(2016, 1, 4, 0, 0), FlexibleDateTimeParser.parse("4 janvier 2016", french).toLocalDateTime());
        assertNull(FlexibleDateTimeParser.parse("4 January 2016", french), "Explicit formatters must not fall back to English");
    }

    @Test
    void extensiveParsingSupportsLongLocalizedNames() {
        ZonedDateTime parsed = FlexibleDateTimeParser.parse("lundi, 4 janvier 2016 18:20:30 GMT", true, List.of(Locale.FRENCH));
        assertNotNull(parsed);
        assertEquals(LocalDateTime.of(2016, 1, 4, 18, 20, 30).toInstant(ZoneOffset.UTC), parsed.toInstant());
    }

    @Test
    void lastDitchParsingRetainsConfiguredLocale() {
        ZonedDateTime parsed = FlexibleDateTimeParser.parse("4 janv. 2016 18:20:30 +0000 trailing junk", true, List.of(Locale.FRENCH));
        assertNotNull(parsed);
        assertEquals(LocalDateTime.of(2016, 1, 4, 18, 20, 30).toInstant(ZoneOffset.UTC), parsed.toInstant());
    }

    @Test
    void cleanupPreservesNonAsciiAmPmMarkers() {
        ZonedDateTime parsed = FlexibleDateTimeParser.parse("【4 1月 2016 6:20:30 下午】", false, List.of(Locale.SIMPLIFIED_CHINESE));
        assertNotNull(parsed);
        assertEquals(LocalDateTime.of(2016, 1, 4, 18, 20, 30).toInstant(ZoneOffset.UTC), parsed.toInstant());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not a date"})
    void invalidDatesRemainUnparseable(String text) {
        assertNull(FlexibleDateTimeParser.parse(text, true, List.of(Locale.FRENCH)));
        assertNull(DateTimeFormatParser.parseDate(text, false, List.of(Locale.FRENCH)));
    }

    private static void assertBothParsers(String text, List<Locale> locales, LocalDateTime expected) {
        ZonedDateTime flexible = FlexibleDateTimeParser.parse(text, false, locales);
        assertNotNull(flexible, "FlexibleDateTimeParser failed for " + text);
        assertEquals(expected.toInstant(ZoneOffset.UTC), flexible.toInstant());
        assertEquals(expected, DateTimeFormatParser.parseDate(text, false, locales));
    }
}
