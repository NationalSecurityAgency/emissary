package emissary.util;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.zone.ZoneRulesException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeUtilTest extends UnitTest {

    private static final ZoneId GMT = ZoneId.of("GMT");
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final LocalDateTime testLocalDate = LocalDateTime.of(2016, Month.DECEMBER, 25, 15, 30, 25);
    private static final Instant testUtilDate = testLocalDate.toInstant(ZoneOffset.UTC);
    private static final ZonedDateTime testZoneDate = ZonedDateTime.of(testLocalDate, GMT);

    @Test
    void testGetDate() {
        assertEquals("2016 Dec 25 15 25 30", TimeUtil.getDate(testZoneDate, "yyyy MMM dd HH ss mm", GMT), "getDate did not match");
        assertEquals("2016-12-25T15:30:25 GMT", TimeUtil.getDate(testZoneDate, "yyyy-MM-dd'T'HH:mm:ss z", null), "getDate did not match");
        assertEquals("2016-12-25T14:30:25 GMT",
                TimeUtil.getDate(ZonedDateTime.of(testLocalDate, ZoneId.of("Europe/Paris")), "yyyy-MM-dd'T'HH:mm:ss z", GMT),
                "getDate did not match");
        assertEquals("2016-12-25T15:30:25 CET",
                TimeUtil.getDate(ZonedDateTime.of(testLocalDate, ZoneId.of("Europe/Paris")), "yyyy-MM-dd'T'HH:mm:ss z", PARIS),
                "getDate did not match");
        assertNull(TimeUtil.getDate(null, null, null));
    }

    @Test
    void testGetDateExceptionBadFormat() {
        assertThrows(IllegalArgumentException.class, () -> TimeUtil.getDate(testZoneDate, null, null));
    }

    @Test
    void testGetDateExceptionBadFormat2() {
        assertThrows(IllegalArgumentException.class, () -> TimeUtil.getDate(testZoneDate, "ThisReallyShouldntWork", null));
    }


    @Test
    void testGetDateExceptionBadZone() {
        assertThrows(ZoneRulesException.class, () -> TimeUtil.getDate("yyyy", "BAD"));
    }

    @Test
    void testGetDateAsISO8601Long() {
        assertEquals("2016-12-25 15:30:25", TimeUtil.getDateAsISO8601(testUtilDate), "GetDateAsISO8601Long");
    }

    @Test
    void testOrdinal() {
        assertEquals(7, TimeUtil.getCurrentDateOrdinal().length(), "Date with ordinal must be 7 long");
    }

    @Test
    void testCurrentTimeAsPath() {
        final String path = TimeUtil.getCurrentDate();
        assertTrue(path.contains("/"), "Path with time should have slashes");
    }

    @Test
    void testTimeZoneAddedOnFormat() {
        String dt = TimeUtil.getDate("yyyy-MM-dd'T'HH:mm:ss z", null);
        assertTrue(dt.contains("GMT"), "GMT must be added by default - " + dt);

        // timezone changes due to daylight savings
        dt = TimeUtil.getDate("yyyy-MM-dd'T'HH:mm:ss z", "Europe/Paris");
        assertTrue(dt.contains("CET") || dt.contains("CEST"), "Specified tz must be added - " + dt);
    }

    @Test
    void testDateAsFullIOS8601() {
        final String currentTime = TimeUtil.getCurrentDateFullISO8601();
        assertTrue(currentTime.contains("T"), "Full ISO8601 must have a 'T'");
        assertTrue(currentTime.contains("Z"), "Full ISO8601 must have a 'Z'");

        final String time = TimeUtil.getDateAsFullISO8601(ZonedDateTime.now(ZoneId.systemDefault()));
        assertTrue(time.contains("T"), "Full ISO8601 must have a 'T'");
        assertTrue(time.contains("Z"), "Full ISO8601 must have a 'Z'");
    }

    @Test
    void testConvertHexDate() {
        String hexDate1 = "0x00009FF700F77536";
        String hexDate2 = "0x0000A07800E93033";
        assertEquals("2012-02-14 15:00:57.993", TimeUtil.convertHexDate(hexDate1), "convertHexDate conversion did not match");
        assertEquals("2012-06-22 14:09:00.757", TimeUtil.convertHexDate(hexDate2), "convertHexDate conversion did not match");

        String invalidHexDate1 = "0x00009FF700F7753"; // too few characters
        String invalidHexDate2 = "0x00009FF_00F77536"; // format doesn't support any non-hex characters
        assertThrows(IllegalArgumentException.class, () -> TimeUtil.convertHexDate(invalidHexDate1));
        assertThrows(IllegalArgumentException.class, () -> TimeUtil.convertHexDate(invalidHexDate2));
    }

    @Test
    void testGetDateOrdinalWithTime() {
        assertEquals("2016360153025", TimeUtil.getDateOrdinalWithTime(testUtilDate), "Date Ordinal with Time did not match");
    }

    @Test
    void testParseLocalesDefaultsToEnglish() {
        assertEquals(List.of(TimeUtil.DEFAULT_DATE_LOCALE), TimeUtil.parseLocales(null));
        assertEquals(List.of(TimeUtil.DEFAULT_DATE_LOCALE), TimeUtil.parseLocales(List.of()));
        assertEquals(List.of(TimeUtil.DEFAULT_DATE_LOCALE), TimeUtil.parseLocales(List.of(" ", "not a tag")));
    }

    @Test
    void testParseLocales() {
        assertEquals(List.of(Locale.ENGLISH, Locale.FRENCH, Locale.GERMAN),
                TimeUtil.parseLocales(List.of("en", "fr", "de")));
        assertEquals(List.of(Locale.forLanguageTag("en-US")), TimeUtil.parseLocales(List.of("en-US", "en-US")));
    }

    @Test
    void testGetDateWithLocale() {
        assertEquals("December", TimeUtil.getDate(testZoneDate, "MMMM", GMT, Locale.ENGLISH));
        assertEquals("décembre", TimeUtil.getDate(testZoneDate, "MMMM", GMT, Locale.FRENCH));
        assertEquals("December", TimeUtil.getDate(testZoneDate, "MMMM", GMT));
    }

    @Test
    void testGenericLocaleRequest() {
        List<Locale> generic = TimeUtil.parseLocales(List.of(" * "));
        assertTrue(generic.stream().anyMatch(locale -> "en".equals(locale.getLanguage())), "Generic locales should include English");
        assertTrue(generic.stream().anyMatch(locale -> "fr".equals(locale.getLanguage())), "Generic locales should include French");
    }

    @Test
    void testAvailableTextLocales() {
        List<Locale> locales = TimeUtil.getAvailableTextLocales();
        assertEquals(Locale.ENGLISH, locales.getFirst());
        assertTrue(locales.containsAll(Arrays.stream(Locale.getAvailableLocales())
                .filter(locale -> !locale.getLanguage().isEmpty()).toList()), "All regional variants must be retained");
        assertEquals(locales.stream().skip(1).sorted(Comparator.comparing(Locale::toLanguageTag)).toList(), locales.subList(1, locales.size()));
        assertEquals(locales.size(), locales.stream().distinct().count());
        assertFalse(locales.isEmpty(), "Available locales should not be empty");
    }

    @Test
    void testWildcardKeepsExplicitLocalesFirst() {
        List<Locale> locales = TimeUtil.parseLocales(List.of("*", "fr-CA", "en-GB", "fr-CA", "*"));
        assertEquals(List.of(Locale.CANADA_FRENCH, Locale.UK), locales.subList(0, 2));
        assertTrue(locales.containsAll(TimeUtil.getAvailableTextLocales()));
        assertEquals(locales.size(), locales.stream().distinct().count());
        assertThrows(UnsupportedOperationException.class, () -> locales.add(Locale.GERMAN));
    }

    @Test
    void testInvalidLocalesDoNotDiscardValidEntries() {
        assertEquals(List.of(Locale.FRENCH, Locale.UK), TimeUtil.parseLocales(Arrays.asList(null, " ", "fr", "not a tag", "en-GB")));
    }
}
