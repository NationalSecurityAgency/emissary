package emissary.util;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.zone.ZoneRulesException;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeUtilTest extends UnitTest {

    private static final ZoneId GMT = ZoneId.of("GMT");
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final LocalDateTime testLocalDate = LocalDateTime.of(2016, Month.DECEMBER, 25, 15, 30, 25);
    private static final Date testUtilDate = Date.from(testLocalDate.toInstant(ZoneOffset.UTC));
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
        assertEquals("2016-12-25 15:30:25", TimeUtil.getDateAsISO8601(testUtilDate.getTime()), "GetDateAsISO8601Long");
    }

    @Deprecated
    @Test
    void testGetZonedDateFromISO8601() {
        ZonedDateTime zdt = TimeUtil.getZonedDateFromISO8601("2016-12-25 15:30:25");
        assertEquals(15, zdt.getHour());
        assertEquals(30, zdt.getMinute());
        assertEquals(25, zdt.getSecond());
        assertEquals(2016, zdt.getYear());
        assertEquals(12, zdt.getMonthValue());
        assertEquals(25, zdt.getDayOfMonth());
        assertEquals(360, zdt.getDayOfYear());
        assertEquals("GMT", zdt.getZone().getId());
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

        final String time = TimeUtil.getDateAsFullISO8601(ZonedDateTime.now());
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
}
