package emissary.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

import emissary.test.core.UnitTest;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class FlexibleDateTimeParserTest extends UnitTest {

    private static final long EXPECTED_FULL = 1451931630; // 2016-01-04 18:20:30
    private static final long EXPECTED_NO_TIME = 1451865600; // 2016-01-04 00:00:00
    private static final long EXPECTED_NO_SECS = 1451931600; // 2016-01-04 18:20:00
    private static final long EXPECTED_NO_HR_SEC = 1451866800; // 2016-01-04 00:20:00
    private static final long EXPECTED_ALT_TIME = 1451894523; // 2016-01-04 08:02:03
    private static final long EXPECTED_ALT_TIME_NO_SECS = 1451894520; // 2016-01-04 08:02:00

    @BeforeClass
    public static void setupClass() {
        // "warm-up" the class, but this runs before UnitTest has
        // a chance to setup, so do that first
        new UnitTest().setupSystemProperties();
        FlexibleDateTimeParser.getTimezone();
    }

    @Test
    public void testGetTimezone() {
        assertEquals("GMT", FlexibleDateTimeParser.getTimezone().getId());
    }

    /**
     * Test the date string against the expected output
     *
     * @param date the string representation of a date
     * @param expected the expected parsed and formatted date
     * @param msg the error message to display if the test fails
     */
    private void test(String date, long expected, String msg) {
        ZonedDateTime unknownParse = FlexibleDateTimeParser.parse(date);
        Assert.assertEquals("Error on: " + msg, expected, unknownParse == null ? 0L : unknownParse.toEpochSecond());
    }

    /**
     * Test the date string against the expected output
     *
     * @param date the string representation of a date
     * @param expected the expected parsed and formatted date
     * @param formatter run a date format manually and compare the output to the unknown parser
     */
    private void test(String date, long expected, DateTimeFormatter formatter) {
        long unknownParse = FlexibleDateTimeParser.parse(date).toEpochSecond();
        assertEquals("Flexible parse failed for " + formatter, expected, unknownParse);

        long knownParse = FlexibleDateTimeParser.parse(date, formatter).toEpochSecond();
        assertEquals("Manual parse failed for " + formatter, expected, knownParse);

        // this is as close as I can get to testing the expected date format
        assertEquals("Parsed date/times are not the same", unknownParse, knownParse);
    }

    /**
     * Three-letter time zone IDs often point to multiple timezones. Java 8 uses the timezone over the offset causing
     * problems with the datetime in verifies. Java 9 fixes this issue. Since 9 isn't released and, even if it was, it would
     * take some time to transition, a regex strips out the short timezone if there is an offset present.
     * <p>
     * See {@link java.util.TimeZone} and {@link java.time.ZoneId#SHORT_IDS}
     */
    @Test
    public void stripThreeLetterTimeZonesWhenThereIsAnOffset() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("[E[,][ ]]d[ ]MMM[.][,][ ]yyyy[ H:mm[:ss][ ][a][ ][z][ ][Z][ ][[(]z[)]]]");

        // without offset we expect the default ZoneId
        test("Mon, 4 Jan 2016 13:20:30 EST", EXPECTED_FULL, pattern);

        // see if we can ignore short timezone
        test("Mon, 4 Jan 2016 18:20:30 +0000(EST)", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 16:20:30 -0200(EST)", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 18:20:30 +0000 (EST)", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 18:20:30 +0000EST", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 18:20:30 +0000 EST", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 18:20:30 EST+0000", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 18:20:30 EST +0000", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 18:20:30EST+0000", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 18:20:30EST +0000", EXPECTED_FULL, pattern);
    }

    @Test
    public void parse_yyyyMMddTHHmmssSSSX() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("yyyy-M-d[['T'][ ][/]H[:][/]m[:s][[.]SSS][ ][z][ ][Z][XXX]]");
        test("2016-01-04T18:20:30.000Z", EXPECTED_FULL, pattern);
        test("2016-01-04T18:20:30Z", EXPECTED_FULL, pattern);
        test("2016-01-04T18:20:30+00:00", EXPECTED_FULL, pattern);
        test("2016-01-04T13:20:30-05:00", EXPECTED_FULL, pattern);
        test("2016-01-04T18:20:30+0000", EXPECTED_FULL, pattern);
        test("2016-01-04T13:20:30-0500", EXPECTED_FULL, pattern);
        test("2016-01-04 18:20:30", EXPECTED_FULL, pattern);
        test("2016-01-04 18:20:30+0000", EXPECTED_FULL, pattern);
        test("2016-01-04 18:20:30 +0000", EXPECTED_FULL, pattern);
        test("2016-01-04 18:20:30 GMT+0000", EXPECTED_FULL, pattern);
        test("2016-01-04 18:20:30 GMT", EXPECTED_FULL, pattern);
        test("2016-01-04 18:20", EXPECTED_NO_SECS, pattern);
        test("2016-01-04/18/20", EXPECTED_NO_SECS, pattern);
        test("2016-01-04", EXPECTED_NO_TIME, pattern);
        test("2016-1-4T8:2:3.000Z", EXPECTED_ALT_TIME, pattern);
        test("2016-1-4 8:2:3.000", EXPECTED_ALT_TIME, pattern);
        test("2016-1-4 8:2:3", EXPECTED_ALT_TIME, pattern);
        test("2016-1-4T8:2:3Z", EXPECTED_ALT_TIME, pattern);
        test("2016-01-04T18:20:30", EXPECTED_FULL, pattern);
        test("2016-1-4T8:2:3+00:00", EXPECTED_ALT_TIME, pattern);
        test("2016-1-4T3:2:3-05:00", EXPECTED_ALT_TIME, pattern);
        test("2016-1-4T8:2:3+0000", EXPECTED_ALT_TIME, pattern);
        test("2016-1-4T3:2:3-0500", EXPECTED_ALT_TIME, pattern);
        test("2016-01-04 18:20", EXPECTED_NO_SECS, pattern);
        test("2016-01-04 18:20 GMT", EXPECTED_NO_SECS, pattern);
        test("2016-01-04 18:20 +00:00", EXPECTED_NO_SECS, pattern);
        test("2016-01-04 18:20 +0000", EXPECTED_NO_SECS, pattern);
        test("2016-1-4 8:2", EXPECTED_ALT_TIME_NO_SECS, pattern);
        test("2016-01-04", EXPECTED_NO_TIME, pattern);
        test("2016-1-4", EXPECTED_NO_TIME, pattern);
    }

    @Test
    public void parse_EdMMMyyHmmssZ() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("[E[,][ ]]d[ ]MMM[,][ ]yy[ H:mm[:ss][ ][z][ ][Z]]");
        test("Mon, 4 Jan 16 18:20:30 +0000", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 16 18:20:30+0000", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 16 18:20:30 GMT+0000", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 16 13:20:30 -0500", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 16 18:20:30", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 16 8:20:30 -1000", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 16", EXPECTED_NO_TIME, pattern);
        test("Mon,4 Jan 16", EXPECTED_NO_TIME, pattern);
        test("Mon, 4Jan16", EXPECTED_NO_TIME, pattern);
        test("4 Jan 16 18:20 UTC", EXPECTED_NO_SECS, pattern);
        test("4 Jan 16 13:20 -0500", EXPECTED_NO_SECS, pattern);
        test("4 Jan 16 18:20", EXPECTED_NO_SECS, pattern);
        test("4 Jan 16 18:20 +0000", EXPECTED_NO_SECS, pattern);
        test("4 Jan 16 18:20+0000", EXPECTED_NO_SECS, pattern);
        test("4 Jan 16 18:20 +00:00", EXPECTED_NO_SECS, pattern);
        test("4 Jan 16", EXPECTED_NO_TIME, pattern);
        test("4Jan16", EXPECTED_NO_TIME, pattern);
    }


    @Test
    public void parse_EdMMMyyyyKmmssaZ() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("[E[,][ ]]d MMM yyyy K:mm:ss a[ ][z][ ][Z]");
        test("Mon, 4 Jan 2016 06:20:30 PM +0000", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 06:20:30 PM GMT+0000", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 06:20:30 PM GMT", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 01:20:30 PM -0500", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 1:20:30 PM -0500", EXPECTED_FULL, pattern);
        test("Sun, 3 Jan 2016 7:20:00 PM -0500", EXPECTED_NO_HR_SEC, pattern);
        test("Mon, 4 Jan 2016 0:20:00 AM", EXPECTED_NO_HR_SEC, pattern);
        test("Mon,4 Jan 2016 06:20:30 PM", EXPECTED_FULL, pattern);
        test("4 Jan 2016 06:20:30 PM", EXPECTED_FULL, pattern);
        test("4 Jan 2016 06:20:30 PM +0000", EXPECTED_FULL, pattern);
    }

    @Test
    public void parse_EdMMMyyyyHmmssZz() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("[E[,][ ]]d[ ]MMM[.][,][ ]yyyy[ H:mm[:ss][ ][a][ ][z][ ][Z][ ][(z)]]");
        test("Mon, 4 Jan 2016 18:20:30 +0000 (Europe/Dublin)", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 18:20:30 +0000 (Zulu)", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 18:20:30 +0000 (UTC)", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 18:20:30 +0000 (GMT)", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 13:20:30 -0500 (EST)", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 13:20:30 -0500 (US/Eastern)", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 18:20:30 +0000", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 8:20:30 -1000", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 13:20:30 -0500", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 18:20:30 UTC", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 18:20:30 GMT", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 18:20:30 GMT+0000", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 13:20:30 -0500", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 0:20:00 +0000", EXPECTED_NO_HR_SEC, pattern);
        test("Mon, 4 Jan 2016 0:20:00 AM", EXPECTED_NO_HR_SEC, pattern);
        test("Sun, 3 Jan 2016 19:20:00 PM -0500", EXPECTED_NO_HR_SEC, pattern);
        test("Mon, 4 Jan 2016 18:20:30", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 18:20 +0000", EXPECTED_NO_SECS, pattern);
        test("Mon, 4 Jan 2016 13:20 -0500", EXPECTED_NO_SECS, pattern);
        test("Mon, 4 Jan. 2016 18:20:30 +0000", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan. 2016 13:20:30 -0500", EXPECTED_FULL, pattern);
        test("Mon 4 Jan. 2016 18:20:30 +0000", EXPECTED_FULL, pattern);
        test("Mon 4 Jan. 2016 18:20:30+0000", EXPECTED_FULL, pattern);
        test("Mon 4 Jan. 2016 13:20:30 -0500", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan, 2016 18:20:30 PM", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan, 2016", EXPECTED_NO_TIME, pattern);
        test("Mon 4Jan2016", EXPECTED_NO_TIME, pattern);
        test("Mon, 4Jan2016", EXPECTED_NO_TIME, pattern);
        test("4 Jan 2016 18:20:30 +0000", EXPECTED_FULL, pattern);
        test("4 Jan 2016 18:20:30+0000", EXPECTED_FULL, pattern);
        test("4 Jan 2016 13:20:30 -0500", EXPECTED_FULL, pattern);
        test("4 Jan 2016 18:20 UTC", EXPECTED_NO_SECS, pattern);
        test("4 Jan 2016 13:20 -0500", EXPECTED_NO_SECS, pattern);
        test("4 Jan 2016 18:20", EXPECTED_NO_SECS, pattern);
        test("4 Jan 2016", EXPECTED_NO_TIME, pattern);
        test("4Jan2016", EXPECTED_NO_TIME, pattern);
    }

    @Test
    public void parse_EMMMdyyyyKmma() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("[E[,][ ]]MMM d[,] yyyy[,] K:mm[:ss] a");
        test("Mon, Jan 4, 2016 06:20 PM", EXPECTED_NO_SECS, pattern);
        test("Mon, Jan 4, 2016 6:20 PM", EXPECTED_NO_SECS, pattern);
        test("Mon, Jan 04, 2016 06:20 PM", EXPECTED_NO_SECS, pattern);
        test("Mon Jan 04, 2016 06:20 PM", EXPECTED_NO_SECS, pattern);
        test("Mon Jan 04 2016 06:20 PM", EXPECTED_NO_SECS, pattern);
        test("Mon,Jan 04 2016 06:20 PM", EXPECTED_NO_SECS, pattern);
    }

    @Test
    public void parse_EMMMdyyyyHmmssz() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("[E[,][ ]]MMM d[,] yyyy[[,] H:mm[:ss][ ][a][ ][z][ ][Z]]");
        test("Mon, Jan 4, 2016 18:20:30 UTC", EXPECTED_FULL, pattern);
        test("Mon, Jan 04, 2016 18:20:30 UTC", EXPECTED_FULL, pattern);
        test("Mon, Jan 4, 2016 13:20:30 -0500", EXPECTED_FULL, pattern);
        test("Mon, Jan 04, 2016 13:20:30 -0500", EXPECTED_FULL, pattern);
        test("Mon, Jan 4, 2016 18:20:30", EXPECTED_FULL, pattern);
        test("Mon, Jan 04, 2016 18:20:30", EXPECTED_FULL, pattern);
        test("Mon Jan 04 2016 18:20:30", EXPECTED_FULL, pattern);
        test("Mon Jan 04 2016 18:20:30 UTC", EXPECTED_FULL, pattern);
        test("Mon Jan 04 2016 13:20:30 -0500", EXPECTED_FULL, pattern);
        test("Mon Jan 04 2016 8:20:30 -1000", EXPECTED_FULL, pattern);
        test("Mon, Jan 4, 2016, 18:20:30 PM +0000", EXPECTED_FULL, pattern);
        test("Mon, Jan 4, 2016, 18:20:30 PM GMT+0000", EXPECTED_FULL, pattern);
        test("Mon, Jan 4, 2016, 13:20:30 PM -0500", EXPECTED_FULL, pattern);
        test("Mon, Jan 4, 2016, 18:20:30 PM UTC", EXPECTED_FULL, pattern);
        test("Mon, Jan 4, 2016, 18:20:30 PM", EXPECTED_FULL, pattern);
        test("Mon, Jan 4, 2016, 18:20:30", EXPECTED_FULL, pattern);
        test("Mon, Jan 4, 2016", EXPECTED_NO_TIME, pattern);
        test("Jan 04, 2016 18:20:30", EXPECTED_FULL, pattern);
        test("Jan 04, 2016, 18:20:30", EXPECTED_FULL, pattern);
        test("Jan 04 2016 18:20:30", EXPECTED_FULL, pattern);
        test("Jan 04 2016 18:20:30 PM", EXPECTED_FULL, pattern);
        test("Jan 04 2016 18:20:30 +0000", EXPECTED_FULL, pattern);
        test("Jan 04 2016 18:20:30 GMT", EXPECTED_FULL, pattern);
        test("Jan 04 2016 18:20:30", EXPECTED_FULL, pattern);
        test("Jan 4 2016 18:20:30", EXPECTED_FULL, pattern);
        test("Jan 4, 2016", EXPECTED_NO_TIME, pattern);
    }

    @Test
    public void parse_EddMMMyyyyHmmssZ() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("[E[,][ ]]dd-MMM-yyyy[ H:mm:ss[ ][z][ ][Z]]");
        test("Mon 04-Jan-2016 18:20:30 +0000", EXPECTED_FULL, pattern);
        test("Mon 04-Jan-2016 18:20:30 GMT+0000", EXPECTED_FULL, pattern);
        test("Mon 04-Jan-2016 18:20:30 GMT", EXPECTED_FULL, pattern);
        test("Mon 04-Jan-2016 13:20:30 -0500", EXPECTED_FULL, pattern);
        test("Mon 04-Jan-2016 18:20:30", EXPECTED_FULL, pattern);
        test("Mon 04-Jan-2016 8:20:30 -1000", EXPECTED_FULL, pattern);
        test("Mon 04-Jan-2016", EXPECTED_NO_TIME, pattern);
        test("Mon, 04-Jan-2016", EXPECTED_NO_TIME, pattern);
        test("Mon,04-Jan-2016", EXPECTED_NO_TIME, pattern);
        test("04-Jan-2016 18:20:30 +0000", EXPECTED_FULL, pattern);
        test("04-Jan-2016 18:20:30 GMT+0000", EXPECTED_FULL, pattern);
        test("04-Jan-2016 18:20:30 GMT", EXPECTED_FULL, pattern);
        test("04-Jan-2016 13:20:30 -0500", EXPECTED_FULL, pattern);
        test("04-Jan-2016 18:20:30", EXPECTED_FULL, pattern);
        test("04-Jan-2016", EXPECTED_NO_TIME, pattern);
    }

    @Test
    public void parse_EMMMdHHmmsszzzyyyy() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("[E[,][ ]]MMM d H:mm[:ss][ z] yyyy");
        test("Mon Jan 04 18:20:30 GMT 2016", EXPECTED_FULL, pattern);
        test("Mon Jan 04 13:20:30 EST 2016", EXPECTED_FULL, pattern);
        test("Mon Jan 04 13:20 EST 2016", EXPECTED_NO_SECS, pattern);
        test("Mon Jan 04 18:20:30 2016", EXPECTED_FULL, pattern);
        test("Mon Jan 4 18:20:30 2016", EXPECTED_FULL, pattern);
        test("Mon Jan 4 18:20 2016", EXPECTED_NO_SECS, pattern);
        test("Mon, Jan 4 18:20 2016", EXPECTED_NO_SECS, pattern);
        test("Mon,Jan 4 18:20 2016", EXPECTED_NO_SECS, pattern);
        test("Jan 4 18:20 2016", EXPECTED_NO_SECS, pattern);
        test("Jan 04 18:20:30 GMT 2016", EXPECTED_FULL, pattern);
        test("Jan 04 13:20:30 EST 2016", EXPECTED_FULL, pattern);
        test("Jan 04 13:20 EST 2016", EXPECTED_NO_SECS, pattern);
        test("Jan 04 18:20:30 2016", EXPECTED_FULL, pattern);
    }

    @Test
    public void parse_MdyyKmma() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("M/d/yy[ ]K:mm[:ss][ ]a");
        test("01/04/16 06:20 PM", EXPECTED_NO_SECS, pattern);
        test("1/4/16 6:20 PM", EXPECTED_NO_SECS, pattern);
        test("01/04/16 06:20:30 PM", EXPECTED_FULL, pattern);
        test("1/4/16 06:20:30 PM", EXPECTED_FULL, pattern);
        test("01/04/16 00:20 AM", EXPECTED_NO_HR_SEC, pattern);
        test("01/04/1606:20 PM", EXPECTED_NO_SECS, pattern);
        test("01/04/1606:20:30 PM", EXPECTED_FULL, pattern);
        test("01/04/1600:20 AM", EXPECTED_NO_HR_SEC, pattern);
    }

    @Test
    public void parse_MdyyHmmssaz() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("M/d/yy[ ]H:mm[:ss][ ][a][ ][z][ ][Z]");
        test("01/04/16 18:20:30 GMT", EXPECTED_FULL, pattern);
        test("1/4/16 18:20:30 GMT", EXPECTED_FULL, pattern);
        test("01/04/16 18:20:30 PM +0000", EXPECTED_FULL, pattern);
        test("1/4/16 18:20:30 PM +0000", EXPECTED_FULL, pattern);
        test("1/4/16 8:20:30 AM -1000", EXPECTED_FULL, pattern);
        test("1/4/16 8:20:30 -1000", EXPECTED_FULL, pattern);
        test("01/04/16 18:20:30 PM GMT", EXPECTED_FULL, pattern);
        test("1/4/16 18:20:30 PM GMT", EXPECTED_FULL, pattern);
        test("01/04/16 18:20:30 PM GMT+0000", EXPECTED_FULL, pattern);
        test("1/4/16 18:20:30 PM GMT+0000", EXPECTED_FULL, pattern);
        test("01/04/16 18:20:30 PM", EXPECTED_FULL, pattern);
        test("01/04/16 18:20:30", EXPECTED_FULL, pattern);
        test("01/04/16 18:20", EXPECTED_NO_SECS, pattern);
        test("1/4/16 18:20", EXPECTED_NO_SECS, pattern);
        test("01/04/1618:20:30GMT", EXPECTED_FULL, pattern);
        test("01/04/1618:20:30PM+0000", EXPECTED_FULL, pattern);
        test("01/04/1618:20:30PMGMT", EXPECTED_FULL, pattern);
        test("01/04/1618:20:30PM", EXPECTED_FULL, pattern);
        test("01/04/1618:20:30", EXPECTED_FULL, pattern);
        test("01/04/1618:20", EXPECTED_NO_SECS, pattern);
    }

    @Test
    public void parse_HHmmddMMyyyy() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("[HHmm]dd[-][.][/]MM[-][.][/]yyyy");
        test("04-01-2016", EXPECTED_NO_TIME, pattern);
        test("04.01.2016", EXPECTED_NO_TIME, pattern);
        test("04/01/2016", EXPECTED_NO_TIME, pattern);
        test("182004012016", EXPECTED_NO_SECS, pattern);
    }

    @Test
    public void parse_yyyyMMddHHmmssS() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("yyyy/MM/dd[[ ]HH[:]mm[[:]ss[[.]S]][ ][z][ ][Z]]");
        test("2016/01/04 18:20:30.0", EXPECTED_FULL, pattern);
        test("2016/01/0418:20:30.0", EXPECTED_FULL, pattern);
        test("2016/01/041820300", EXPECTED_FULL, pattern);
        test("2016/01/04182030", EXPECTED_FULL, pattern);
        test("2016/01/04 18:20:30", EXPECTED_FULL, pattern);
        test("2016/01/04 18:20:30 +0000", EXPECTED_FULL, pattern);
        test("2016/01/04 18:20:30 GMT+0000", EXPECTED_FULL, pattern);
        test("2016/01/04 18:20:30 GMT", EXPECTED_FULL, pattern);
        test("2016/01/04 18:20:30+0000", EXPECTED_FULL, pattern);
        test("2016/01/0418:20:30+0000", EXPECTED_FULL, pattern);
        test("2016/01/04 182030", EXPECTED_FULL, pattern);
        test("2016/01/041820", EXPECTED_NO_SECS, pattern);
        test("2016/01/04", EXPECTED_NO_TIME, pattern);
    }

    @Test
    public void parse_yyyy_MM_ddHHmmssS() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("yyyy:MM:dd[ H:m[:ss[.S]][ ][z][ ][Z]]");
        test("2016:01:04 18:20:30.0", EXPECTED_FULL, pattern);
        test("2016:01:04 18:20:30", EXPECTED_FULL, pattern);
        test("2016:01:04 18:20:30 +0000", EXPECTED_FULL, pattern);
        test("2016:01:04 18:20:30 GMT+0000", EXPECTED_FULL, pattern);
        test("2016:01:04 18:20:30 GMT", EXPECTED_FULL, pattern);
        test("2016:01:04 18:20:30+0000", EXPECTED_FULL, pattern);
        test("2016:01:04 18:20", EXPECTED_NO_SECS, pattern);
        test("2016:01:04", EXPECTED_NO_TIME, pattern);
    }

    @Test
    public void parse_yyyyMMddHHmmss() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        test("20160104182030", EXPECTED_FULL, pattern);
    }

    @Test
    public void parse_yyyyMMdd() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("yyyyMMdd");
        test("20160104", EXPECTED_NO_TIME, pattern);
    }

    @Test
    public void parse_yyyyDDDHHmmss() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("yyyyDDDHHmmss");
        test("2016004182030", EXPECTED_FULL, pattern);
    }

    @Test
    public void parse_yyyyDDDHHmm() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("yyyyDDDHHmm");
        test("20160041820", EXPECTED_NO_SECS, pattern);
    }

    @Test
    public void parse_yyyyDDD() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("yyyyDDD");
        test("2016004", EXPECTED_NO_TIME, pattern);
    }

    @Test
    public void parse_yyyy_DDD() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("yyyy-DDD");
        test("2016-004", EXPECTED_NO_TIME, pattern);
    }

    @Test
    public void testCleanDateString() {
        test("2016-01-04 18:20<br>", EXPECTED_NO_SECS, "HTML");
        test("2016-01-04\t\t18:20", EXPECTED_NO_SECS, "TABS");
        test("2016-01-04        18:20", EXPECTED_NO_SECS, "SPACES");
        test("2016-01-04 18:20=0D", EXPECTED_NO_SECS, "qp'ified ending");
    }

    @Test
    public void testBad() {
        test("", 0L, "EMPTY");
        test(null, 0L, "NULL");
        assertNull(FlexibleDateTimeParser.parse("1234", Collections.emptyList()));
        assertNull(FlexibleDateTimeParser.parse("1234", Collections.singletonList(null)));
        test("17.Mar.2016", 0L, "UNKNOWN");
        test("Mon, 2 Feb 2017 06:20:30 PM +0000", 0L, "UNKNOWN");
    }
}
