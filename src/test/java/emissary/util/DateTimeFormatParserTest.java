package emissary.util;

import emissary.test.core.junit5.UnitTest;

import jakarta.annotation.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DateTimeFormatParserTest extends UnitTest {

    private static final long EXPECTED_FULL = 1451931630; // 2016-01-04 18:20:30
    private static final long EXPECTED_NO_TIME = 1451865600; // 2016-01-04 00:00:00
    private static final long EXPECTED_NO_SECS = 1451931600; // 2016-01-04 18:20:00
    private static final long EXPECTED_NO_HR_SEC = 1451866800; // 2016-01-04 00:20:00
    private static final long EXPECTED_ALT_TIME = 1451894523; // 2016-01-04 08:02:03
    private static final long EXPECTED_ALT_TIME_NO_SECS = 1451894520; // 2016-01-04 08:02:00

    @BeforeAll
    public static void setupClass() {
        // "warm-up" the class, but this runs before UnitTest has
        // a chance to setup, so do that first
        UnitTest.setupSystemProperties();
    }

    /**
     * Test the date string against the expected output
     *
     * @param date the string representation of a date
     * @param expected the expected parsed and formatted date
     * @param msg the error message to display if the test fails
     */
    private static void test(@Nullable String date, long expected, String msg) {
        LocalDateTime unknownParse = DateTimeFormatParser.parseDate(date, false);
        assertEquals(expected, unknownParse == null ? 0L : unknownParse.toEpochSecond(ZoneOffset.UTC), "Error on: " + msg);
    }

    /**
     * Test the date string against the expected output
     *
     * @param date the string representation of a date
     * @param expected the expected parsed and formatted date
     */
    private static void test(String date, long expected) {
        long unknownParse = DateTimeFormatParser.parseDate(date, false).toEpochSecond(ZoneOffset.UTC);
        assertEquals(expected, unknownParse, "Flexible parse failed");

        long knownParse = DateTimeFormatParser.parseDate(date, false).toEpochSecond(ZoneOffset.UTC);
        assertEquals(expected, knownParse, "Manual parse failed");

        // this is as close as I can get to testing the expected date format
        assertEquals(unknownParse, knownParse, "Parsed date/times are not the same");
    }

    /**
     * Three-letter time zone IDs often point to multiple timezones. Java 8 uses the timezone over the offset causing
     * problems with the datetime in verifies. Java 9 fixes this issue. Since 9 isn't released and, even if it was, it would
     * take some time to transition, a regex strips out the short timezone if there is an offset present.
     * <p>
     * See {@link java.util.TimeZone} and {@link java.time.ZoneId#SHORT_IDS}
     */
    @Test
    void stripThreeLetterTimeZonesWhenThereIsAnOffset() {
        // without offset we expect the default ZoneId
        test("Mon, 4 Jan 2016 13:20:30 EST", EXPECTED_FULL);

        // see if we can ignore short timezone
        test("Mon, 4 Jan 2016 18:20:30 +0000(EST)", EXPECTED_FULL);
        test("Mon, 4 Jan 2016 16:20:30 -0200(EST)", EXPECTED_FULL);
        test("Mon, 4 Jan 2016 18:20:30 +0000 (EST)", EXPECTED_FULL);
        test("Mon, 4 Jan 2016 18:20:30 +0000EST", EXPECTED_FULL);
        test("Mon, 4 Jan 2016 18:20:30 +0000 EST", EXPECTED_FULL);
        test("Mon, 4 Jan 2016 18:20:30 EST+0000", EXPECTED_FULL);
        test("Mon, 4 Jan 2016 18:20:30 EST +0000", EXPECTED_FULL);
        test("Mon, 4 Jan 2016 18:20:30EST+0000", EXPECTED_FULL);
        test("Mon, 4 Jan 2016 18:20:30EST +0000", EXPECTED_FULL);
    }

    @Test
    void parse_yyyyMMddTHHmmssSSSX() {
        test("2016-01-04T18:20:30.000Z", EXPECTED_FULL);
        test("2016-01-04T18:20:30Z", EXPECTED_FULL);
        test("2016-01-04T18:20:30+00:00", EXPECTED_FULL);
        test("2016-01-04T13:20:30-05:00", EXPECTED_FULL);
        test("2016-01-04T18:20:30+0000", EXPECTED_FULL);
        test("2016-01-04T13:20:30-0500", EXPECTED_FULL);
        test("2016-01-04 18:20:30", EXPECTED_FULL);
        test("2016-01-04 18:20:30+0000", EXPECTED_FULL);
        test("2016-01-04 18:20:30 +0000", EXPECTED_FULL);
        test("2016-01-04 18:20:30 GMT+0000", EXPECTED_FULL);
        test("2016-01-04 18:20:30 GMT", EXPECTED_FULL);
        test("2016-01-04 18:20", EXPECTED_NO_SECS);
        test("2016-01-04/18/20", EXPECTED_NO_SECS);
        test("2016-01-04", EXPECTED_NO_TIME);
        test("2016-1-4T8:2:3.000Z", EXPECTED_ALT_TIME);
        test("2016-1-4 8:2:3.000", EXPECTED_ALT_TIME);
        test("2016-1-4 8:2:3", EXPECTED_ALT_TIME);
        test("2016-1-4T8:2:3Z", EXPECTED_ALT_TIME);
        test("2016-01-04T18:20:30", EXPECTED_FULL);
        test("2016-1-4T8:2:3+00:00", EXPECTED_ALT_TIME);
        test("2016-1-4T3:2:3-05:00", EXPECTED_ALT_TIME);
        test("2016-1-4T8:2:3+0000", EXPECTED_ALT_TIME);
        test("2016-1-4T3:2:3-0500", EXPECTED_ALT_TIME);
        test("2016-01-04 18:20", EXPECTED_NO_SECS);
        test("2016-01-04 18:20 GMT", EXPECTED_NO_SECS);
        test("2016-01-04 18:20 +00:00", EXPECTED_NO_SECS);
        test("2016-01-04 18:20 +0000", EXPECTED_NO_SECS);
        test("2016-1-4 8:2", EXPECTED_ALT_TIME_NO_SECS);
        test("2016-01-04", EXPECTED_NO_TIME);
        test("2016-1-4", EXPECTED_NO_TIME);
    }

    @Test
    void parse_EdMMMyyHmmssZ() {
        test("Mon, 4 Jan 16 18:20:30 +0000", EXPECTED_FULL);
        test("Mon, 4 Jan 16 18:20:30+0000", EXPECTED_FULL);
        test("Mon, 4 Jan 16 18:20:30 GMT+0000", EXPECTED_FULL);
        test("Mon, 4 Jan 16 13:20:30 -0500", EXPECTED_FULL);
        test("Mon, 4 Jan 16 18:20:30", EXPECTED_FULL);
        test("Mon, 4 Jan 16 8:20:30 -1000", EXPECTED_FULL);
        test("Mon, 4 Jan 16", EXPECTED_NO_TIME);
        test("Mon,4 Jan 16", EXPECTED_NO_TIME);
        test("Mon, 4Jan16", EXPECTED_NO_TIME);
        test("4 Jan 16 18:20 UTC", EXPECTED_NO_SECS);
        test("4 Jan 16 13:20 -0500", EXPECTED_NO_SECS);
        test("4 Jan 16 18:20", EXPECTED_NO_SECS);
        test("4 Jan 16 18:20 +0000", EXPECTED_NO_SECS);
        test("4 Jan 16 18:20+0000", EXPECTED_NO_SECS);
        test("4 Jan 16 18:20 +00:00", EXPECTED_NO_SECS);
        test("4 Jan 16", EXPECTED_NO_TIME);
        test("4Jan16", EXPECTED_NO_TIME);
    }


    @Test
    void parse_EdMMMyyyyKmmssaZ() {
        test("Mon, 4 Jan 2016 06:20:30 PM +0000", EXPECTED_FULL);
        test("Mon, 4 Jan 2016 06:20:30 PM GMT+0000", EXPECTED_FULL);
        test("Mon, 4 Jan 2016 06:20:30 PM GMT", EXPECTED_FULL);
        test("Mon, 4 Jan 2016 01:20:30 PM -0500", EXPECTED_FULL);
        test("Mon, 4 Jan 2016 1:20:30 PM -0500", EXPECTED_FULL);
        test("Sun, 3 Jan 2016 7:20:00 PM -0500", EXPECTED_NO_HR_SEC);
        test("Mon, 4 Jan 2016 0:20:00 AM", EXPECTED_NO_HR_SEC);
        test("Mon,4 Jan 2016 06:20:30 PM", EXPECTED_FULL);
        test("4 Jan 2016 06:20:30 PM", EXPECTED_FULL);
        test("4 Jan 2016 06:20:30 PM +0000", EXPECTED_FULL);
    }

    @Test
    void parse_EdMMMyyyyHmmssZz() {
        test("Mon, 4 Jan 2016 18:20:30 +0000 (Europe/Dublin)", EXPECTED_FULL);
        test("Mon, 4 Jan 2016 18:20:30 +0000 (Zulu)", EXPECTED_FULL);
        test("Mon, 4 Jan 2016 18:20:30 +0000 (UTC)", EXPECTED_FULL);
        test("Mon, 4 Jan 2016 18:20:30 +0000 (GMT)", EXPECTED_FULL);
        test("Mon, 4 Jan 2016 13:20:30 -0500 (EST)", EXPECTED_FULL);
        test("Mon, 4 Jan 2016 13:20:30 -0500 (US/Eastern)", EXPECTED_FULL);
        test("Mon, 4 Jan 2016 18:20:30 +0000", EXPECTED_FULL);
        test("Mon, 4 Jan 2016 8:20:30 -1000", EXPECTED_FULL);
        test("Mon, 4 Jan 2016 13:20:30 -0500", EXPECTED_FULL);
        test("Mon, 4 Jan 2016 18:20:30 UTC", EXPECTED_FULL);
        test("Mon, 4 Jan 2016 18:20:30 GMT", EXPECTED_FULL);
        test("Mon, 4 Jan 2016 18:20:30 GMT+0000", EXPECTED_FULL);
        test("Mon, 4 Jan 2016 13:20:30 -0500", EXPECTED_FULL);
        test("Mon, 4 Jan 2016 0:20:00 +0000", EXPECTED_NO_HR_SEC);
        test("Mon, 4 Jan 2016 0:20:00 AM", EXPECTED_NO_HR_SEC);
        test("Sun, 3 Jan 2016 19:20:00 PM -0500", EXPECTED_NO_HR_SEC);
        test("Mon, 4 Jan 2016 18:20:30", EXPECTED_FULL);
        test("Mon, 4 Jan 2016 18:20 +0000", EXPECTED_NO_SECS);
        test("Mon, 4 Jan 2016 13:20 -0500", EXPECTED_NO_SECS);
        test("Mon, 4 Jan. 2016 18:20:30 +0000", EXPECTED_FULL);
        test("Mon, 4 Jan. 2016 13:20:30 -0500", EXPECTED_FULL);
        test("Mon 4 Jan. 2016 18:20:30 +0000", EXPECTED_FULL);
        test("Mon 4 Jan. 2016 18:20:30+0000", EXPECTED_FULL);
        test("Mon 4 Jan. 2016 13:20:30 -0500", EXPECTED_FULL);
        test("Mon, 4 Jan, 2016 18:20:30 PM", EXPECTED_FULL);
        test("Mon, 4 Jan, 2016", EXPECTED_NO_TIME);
        test("Mon 4Jan2016", EXPECTED_NO_TIME);
        test("Mon, 4Jan2016", EXPECTED_NO_TIME);
        test("4 Jan 2016 18:20:30 +0000", EXPECTED_FULL);
        test("4 Jan 2016 18:20:30+0000", EXPECTED_FULL);
        test("4 Jan 2016 13:20:30 -0500", EXPECTED_FULL);
        test("4 Jan 2016 18:20 UTC", EXPECTED_NO_SECS);
        test("4 Jan 2016 13:20 -0500", EXPECTED_NO_SECS);
        test("4 Jan 2016 18:20", EXPECTED_NO_SECS);
        test("4 Jan 2016", EXPECTED_NO_TIME);
        test("4Jan2016", EXPECTED_NO_TIME);
    }

    @Test
    void parse_EMMMdyyyyKmma() {
        test("Mon, Jan 4, 2016 06:20 PM", EXPECTED_NO_SECS);
        test("Mon, Jan 4, 2016 6:20 PM", EXPECTED_NO_SECS);
        test("Mon, Jan 04, 2016 06:20 PM", EXPECTED_NO_SECS);
        test("Mon Jan 04, 2016 06:20 PM", EXPECTED_NO_SECS);
        test("Mon Jan 04 2016 06:20 PM", EXPECTED_NO_SECS);
        test("Mon,Jan 04 2016 06:20 PM", EXPECTED_NO_SECS);
    }

    @Test
    void parse_EMMMdyyyyHmmssz() {
        test("Mon, Jan 4, 2016 18:20:30 UTC", EXPECTED_FULL);
        test("Mon, Jan 04, 2016 18:20:30 UTC", EXPECTED_FULL);
        test("Mon, Jan 4, 2016 13:20:30 -0500", EXPECTED_FULL);
        test("Mon, Jan 04, 2016 13:20:30 -0500", EXPECTED_FULL);
        test("Mon, Jan 4, 2016 18:20:30", EXPECTED_FULL);
        test("Mon, Jan 04, 2016 18:20:30", EXPECTED_FULL);
        test("Mon Jan 04 2016 18:20:30", EXPECTED_FULL);
        test("Mon Jan 04 2016 18:20:30 UTC", EXPECTED_FULL);
        test("Mon Jan 04 2016 13:20:30 -0500", EXPECTED_FULL);
        test("Mon Jan 04 2016 8:20:30 -1000", EXPECTED_FULL);
        test("Mon, Jan 4, 2016, 18:20:30 PM +0000", EXPECTED_FULL);
        test("Mon, Jan 4, 2016, 18:20:30 PM GMT+0000", EXPECTED_FULL);
        test("Mon, Jan 4, 2016, 13:20:30 PM -0500", EXPECTED_FULL);
        test("Mon, Jan 4, 2016, 18:20:30 PM UTC", EXPECTED_FULL);
        test("Mon, Jan 4, 2016, 18:20:30 PM", EXPECTED_FULL);
        test("Mon, Jan 4, 2016, 18:20:30", EXPECTED_FULL);
        test("Mon, Jan 4, 2016", EXPECTED_NO_TIME);
        test("Jan 04, 2016 18:20:30", EXPECTED_FULL);
        test("Jan 04, 2016, 18:20:30", EXPECTED_FULL);
        test("Jan 04 2016 18:20:30", EXPECTED_FULL);
        test("Jan 04 2016 18:20:30 PM", EXPECTED_FULL);
        test("Jan 04 2016 18:20:30 +0000", EXPECTED_FULL);
        test("Jan 04 2016 18:20:30 GMT", EXPECTED_FULL);
        test("Jan 04 2016 18:20:30", EXPECTED_FULL);
        test("Jan 4 2016 18:20:30", EXPECTED_FULL);
        test("Jan 4, 2016", EXPECTED_NO_TIME);
    }

    @Test
    void parse_EddMMMyyyyHmmssZ() {
        test("Mon 04-Jan-2016 18:20:30 +0000", EXPECTED_FULL);
        test("Mon 04-Jan-2016 18:20:30 GMT+0000", EXPECTED_FULL);
        test("Mon 04-Jan-2016 18:20:30 GMT", EXPECTED_FULL);
        test("Mon 04-Jan-2016 13:20:30 -0500", EXPECTED_FULL);
        test("Mon 04-Jan-2016 18:20:30", EXPECTED_FULL);
        test("Mon 04-Jan-2016 8:20:30 -1000", EXPECTED_FULL);
        test("Mon 04-Jan-2016", EXPECTED_NO_TIME);
        test("Mon, 04-Jan-2016", EXPECTED_NO_TIME);
        test("Mon,04-Jan-2016", EXPECTED_NO_TIME);
        test("04-Jan-2016 18:20:30 +0000", EXPECTED_FULL);
        test("04-Jan-2016 18:20:30 GMT+0000", EXPECTED_FULL);
        test("04-Jan-2016 18:20:30 GMT", EXPECTED_FULL);
        test("04-Jan-2016 13:20:30 -0500", EXPECTED_FULL);
        test("04-Jan-2016 18:20:30", EXPECTED_FULL);
        test("04-Jan-2016", EXPECTED_NO_TIME);
    }

    @Test
    void parse_EMMMdHHmmsszzzyyyy() {
        test("Mon Jan 04 18:20:30 GMT 2016", EXPECTED_FULL);
        test("Mon Jan 04 13:20:30 EST 2016", EXPECTED_FULL);
        test("Mon Jan 04 13:20 EST 2016", EXPECTED_NO_SECS);
        test("Mon Jan 04 18:20:30 2016", EXPECTED_FULL);
        test("Mon Jan 4 18:20:30 2016", EXPECTED_FULL);
        test("Mon Jan 4 18:20 2016", EXPECTED_NO_SECS);
        test("Mon, Jan 4 18:20 2016", EXPECTED_NO_SECS);
        test("Mon,Jan 4 18:20 2016", EXPECTED_NO_SECS);
        test("Jan 4 18:20 2016", EXPECTED_NO_SECS);
        test("Jan 04 18:20:30 GMT 2016", EXPECTED_FULL);
        test("Jan 04 13:20:30 EST 2016", EXPECTED_FULL);
        test("Jan 04 13:20 EST 2016", EXPECTED_NO_SECS);
        test("Jan 04 18:20:30 2016", EXPECTED_FULL);
    }

    @Test
    void parse_MdyyKmma() {
        test("01/04/16 06:20 PM", EXPECTED_NO_SECS);
        test("1/4/16 6:20 PM", EXPECTED_NO_SECS);
        test("01/04/16 06:20:30 PM", EXPECTED_FULL);
        test("1/4/16 06:20:30 PM", EXPECTED_FULL);
        test("01/04/16 00:20 AM", EXPECTED_NO_HR_SEC);
        test("01/04/1606:20 PM", EXPECTED_NO_SECS);
        test("01/04/1606:20:30 PM", EXPECTED_FULL);
        test("01/04/1600:20 AM", EXPECTED_NO_HR_SEC);
    }

    @Test
    void parse_MdyyHmmssaz() {
        test("01/04/16 18:20:30 GMT", EXPECTED_FULL);
        test("1/4/16 18:20:30 GMT", EXPECTED_FULL);
        test("01/04/16 18:20:30 PM +0000", EXPECTED_FULL);
        test("1/4/16 18:20:30 PM +0000", EXPECTED_FULL);
        test("1/4/16 8:20:30 AM -1000", EXPECTED_FULL);
        test("1/4/16 8:20:30 -1000", EXPECTED_FULL);
        test("01/04/16 18:20:30 PM GMT", EXPECTED_FULL);
        test("1/4/16 18:20:30 PM GMT", EXPECTED_FULL);
        test("01/04/16 18:20:30 PM GMT+0000", EXPECTED_FULL);
        test("1/4/16 18:20:30 PM GMT+0000", EXPECTED_FULL);
        test("01/04/16 18:20:30 PM", EXPECTED_FULL);
        test("01/04/16 18:20:30", EXPECTED_FULL);
        test("01/04/16 18:20", EXPECTED_NO_SECS);
        test("1/4/16 18:20", EXPECTED_NO_SECS);
        test("01/04/1618:20:30GMT", EXPECTED_FULL);
        test("01/04/1618:20:30PM+0000", EXPECTED_FULL);
        test("01/04/1618:20:30PMGMT", EXPECTED_FULL);
        test("01/04/1618:20:30PM", EXPECTED_FULL);
        test("01/04/1618:20:30", EXPECTED_FULL);
        test("01/04/1618:20", EXPECTED_NO_SECS);
    }

    @Test
    void parse_HHmmddMMyyyy() {
        test("04-01-2016", EXPECTED_NO_TIME);
        test("04.01.2016", EXPECTED_NO_TIME);
        test("04/01/2016", EXPECTED_NO_TIME);
        test("182004012016", EXPECTED_NO_SECS);
    }

    @Test
    void parse_yyyyMMddHHmmssS() {
        test("2016/01/04 18:20:30.0", EXPECTED_FULL);
        test("2016/01/0418:20:30.0", EXPECTED_FULL);
        test("2016/01/041820300", EXPECTED_FULL);
        test("2016/01/04182030", EXPECTED_FULL);
        test("2016/01/04 18:20:30", EXPECTED_FULL);
        test("2016/01/04 18:20:30 +0000", EXPECTED_FULL);
        test("2016/01/04 18:20:30 GMT+0000", EXPECTED_FULL);
        test("2016/01/04 18:20:30 GMT", EXPECTED_FULL);
        test("2016/01/04 18:20:30+0000", EXPECTED_FULL);
        test("2016/01/0418:20:30+0000", EXPECTED_FULL);
        test("2016/01/04 182030", EXPECTED_FULL);
        test("2016/01/041820", EXPECTED_NO_SECS);
        test("2016/01/04", EXPECTED_NO_TIME);
    }

    @Test
    void parse_yyyy_MM_ddHHmmssS() {
        test("2016:01:04 18:20:30.0", EXPECTED_FULL);
        test("2016:01:04 18:20:30", EXPECTED_FULL);
        test("2016:01:04 18:20:30 +0000", EXPECTED_FULL);
        test("2016:01:04 18:20:30 GMT+0000", EXPECTED_FULL);
        test("2016:01:04 18:20:30 GMT", EXPECTED_FULL);
        test("2016:01:04 18:20:30+0000", EXPECTED_FULL);
        test("2016:01:04 18:20", EXPECTED_NO_SECS);
        test("2016:01:04", EXPECTED_NO_TIME);
    }

    @Test
    void parse_yyyyMMddHHmmss() {
        test("20160104182030", EXPECTED_FULL);
    }

    @Test
    void parse_yyyyMMdd() {
        test("20160104", EXPECTED_NO_TIME);
    }

    @Test
    void parse_yyyyDDDHHmmss() {
        test("2016004182030", EXPECTED_FULL);
    }

    @Test
    void parse_yyyyDDDHHmm() {
        test("20160041820", EXPECTED_NO_SECS);
    }

    @Test
    void parse_yyyyDDD() {
        test("2016004", EXPECTED_NO_TIME);
    }

    @Test
    void parse_yyyy_DDD() {
        test("2016-004", EXPECTED_NO_TIME);
    }


    @Test
    void testCleanDateString() {
        // test("2016-01-04 18:20<br>", EXPECTED_NO_SECS, "HTML");
        test("2016-01-04\t\t18:20", EXPECTED_NO_SECS, "TABS");
        test("2016-01-04        18:20", EXPECTED_NO_SECS, "SPACES");
        test("2016-01-04 18:20=0D", EXPECTED_NO_SECS, "qp'ified ending");
    }


    @Test
    void testBad() {
        test("", 0L, "EMPTY");
        test(null, 0L, "NULL");
        assertNull(DateTimeFormatParser.parseDate("1234", false));
        assertNull(DateTimeFormatParser.parseDate("1234", false));
        test("17.Mar.2016", 0L, "UNKNOWN");
        test("Mon, 2 Feb 2017 06:20:30 PM +0000", 0L, "UNKNOWN");
    }
}
