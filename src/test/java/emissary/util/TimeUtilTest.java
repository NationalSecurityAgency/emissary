package emissary.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.zone.ZoneRulesException;
import java.util.Date;

import emissary.test.core.UnitTest;
import org.junit.Test;

public class TimeUtilTest extends UnitTest {

    private static final ZoneId GMT = ZoneId.of("GMT");
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final LocalDateTime testLocalDate = LocalDateTime.of(2016, Month.DECEMBER, 25, 15, 30, 25);
    private static final Date testUtilDate = Date.from(testLocalDate.toInstant(ZoneOffset.UTC));
    private static final ZonedDateTime testZoneDate = ZonedDateTime.of(testLocalDate, GMT);

    @Test
    public void testGetDate() {
        assertEquals("getDate did not match", "2016 Dec 25 15 25 30", TimeUtil.getDate(testZoneDate, "yyyy MMM dd HH ss mm", GMT));
        assertEquals("getDate did not match", "2016-12-25T15:30:25 GMT", TimeUtil.getDate(testZoneDate, "yyyy-MM-dd'T'HH:mm:ss z", null));
        assertEquals("getDate did not match", "2016-12-25T14:30:25 GMT",
                TimeUtil.getDate(ZonedDateTime.of(testLocalDate, ZoneId.of("Europe/Paris")), "yyyy-MM-dd'T'HH:mm:ss z", GMT));
        assertEquals("getDate did not match", "2016-12-25T15:30:25 CET",
                TimeUtil.getDate(ZonedDateTime.of(testLocalDate, ZoneId.of("Europe/Paris")), "yyyy-MM-dd'T'HH:mm:ss z", PARIS));
        assertNull(TimeUtil.getDate(null, null, null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetDateExceptionBadFormat() {
        TimeUtil.getDate(testZoneDate, null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetDateExceptionBadFormat2() {
        TimeUtil.getDate(testZoneDate, "Bad", null);
    }


    @Test(expected = ZoneRulesException.class)
    public void testGetDateExceptionBadZone() {
        TimeUtil.getDate("yyyy", "BAD");
    }

    @Deprecated
    @Test
    public void testGetDateAsPath() {
        assertEquals("GetDateAsPath", "2016-12-25/15/30", TimeUtil.getDateAsPath(testUtilDate));
    }

    @Deprecated
    @Test
    public void testGetDateOrdinal() {
        assertEquals("GetDateOrdinal", "2016360", TimeUtil.getDateOrdinal(testUtilDate));
    }

    @Deprecated
    @Test
    public void testGetDateAsISO8601() {
        assertEquals("GetDateAsISO8601", "2016-12-25 15:30:25", TimeUtil.getDateAsISO8601(testUtilDate));
    }

    @Test
    public void testGetDateAsISO8601Long() {
        assertEquals("GetDateAsISO8601Long", "2016-12-25 15:30:25", TimeUtil.getDateAsISO8601(testUtilDate.getTime()));
    }

    @Deprecated
    @Test
    public void testGetDateAsFullISO8601() {
        assertEquals("GetDateAsFullISO8601", "2016-12-25T15:30:25Z", TimeUtil.getDateAsFullISO8601(testUtilDate));
    }

    @Deprecated
    @Test
    public void testGetDateFromISO8601() throws DateTimeParseException {
        assertEquals("GetDateFromISO8601", testUtilDate.getTime(), TimeUtil.getDateFromISO8601("2016-12-25 15:30:25").getTime());
    }

    @Deprecated
    @Test(expected = java.time.format.DateTimeParseException.class)
    public void testGetDateFromISO8601Exception() throws DateTimeParseException {
        TimeUtil.getDateFromISO8601("Bad Date");
    }

    @Deprecated
    @Test
    public void testGetZonedDateFromISO8601() {
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

    @Deprecated
    @Test
    public void testDatePath() {
        assertTrue("Date with slashes must have slashes", TimeUtil.getDateAsPath(new Date()).indexOf("/") > -1);
    }

    @Test
    public void testOrdinal() {
        assertTrue("Date with ordinal must be 7 long", TimeUtil.getCurrentDateOrdinal().length() == 7);
    }

    @Deprecated
    @Test
    public void testISO8601RoundTrip() throws Exception {
        final String s = TimeUtil.getCurrentDateISO8601();
        final Date d = TimeUtil.getDateFromISO8601(s);
        final String s2 = TimeUtil.getDateAsISO8601(d.getTime());
        assertEquals("ISO8601 utils should make round trip", s, s2);
    }

    @Test
    public void testCurrentTimeAsPath() {
        final String path = TimeUtil.getCurrentDate();
        assertTrue("Path with time should have slashes", path.indexOf("/") > -1);
    }

    @Test
    public void testTimeZoneAddedOnFormat() {
        String dt = TimeUtil.getDate("yyyy-MM-dd'T'HH:mm:ss z", null);
        assertTrue("GMT must be added by default - " + dt, dt.indexOf("GMT") > -1);

        // timezone changes due to daylight savings
        dt = TimeUtil.getDate("yyyy-MM-dd'T'HH:mm:ss z", "Europe/Paris");
        assertTrue("Specified tz must be added - " + dt, dt.indexOf("CET") > -1 || dt.indexOf("CEST") > -1);
    }

    @Test
    public void testDateAsFullIOS8601() {
        final String currentTime = TimeUtil.getCurrentDateFullISO8601();
        assertTrue("Full ISO8601 must have a 'T'", currentTime.contains("T"));
        assertTrue("Full ISO8601 must have a 'Z'", currentTime.contains("Z"));

        final String time = TimeUtil.getDateAsFullISO8601(ZonedDateTime.now());
        assertTrue("Full ISO8601 must have a 'T'", time.contains("T"));
        assertTrue("Full ISO8601 must have a 'Z'", time.contains("Z"));
    }
}
