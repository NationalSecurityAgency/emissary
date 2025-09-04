package emissary.util;

import emissary.test.core.junit5.UnitTest;

import jakarta.annotation.Nullable;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FlexibleDateTimeParserTest extends UnitTest {

    private static final long EXPECTED_FULL = 1451931630; // 2016-01-04 18:20:30
    private static final long EXPECTED_NO_TIME = 1451865600; // 2016-01-04 00:00:00
    private static final long EXPECTED_NO_SECS = 1451931600; // 2016-01-04 18:20:00
    private static final long EXPECTED_NO_HR_SEC = 1451866800; // 2016-01-04 00:20:00
    private static final long EXPECTED_ALT_TIME = 1451894523; // 2016-01-04 08:02:03
    private static final long EXPECTED_ALT_TIME_NO_SECS = 1451894520; // 2016-01-04 08:02:00

    @BeforeAll
    public static void setupClass() {
        // "warm-up" the class, but this runs before UnitTest has
        // a chance to set up, so do that first
        UnitTest.setupSystemProperties();
    }

    @Test
    void testGetTimezone() {
        assertEquals("GMT", FlexibleDateTimeParser.getTimezone().getId());
    }

    /**
     * Test the date string against the expected output
     *
     * @param date the string representation of a date
     * @param expected the expected parsed and formatted date
     * @param msg the error message to display if the test fails
     */
    private static void test(@Nullable String date, long expected, String msg) {
        ZonedDateTime unknownParse = FlexibleDateTimeParser.parse(date);
        assertEquals(expected, unknownParse == null ? 0L : unknownParse.toEpochSecond(), "Error on: " + msg);
    }

    /**
     * Test the date string against the expected output
     *
     * @param date the string representation of a date
     * @param expected the expected parsed and formatted date
     * @param formatter run a date format manually and compare the output to the unknown parser
     */
    private static void test(String date, long expected, DateTimeFormatter formatter) {
        long unknownParse = FlexibleDateTimeParser.parse(date).toEpochSecond();
        assertEquals(expected, unknownParse, "Flexible parse failed for " + formatter);

        long knownParse = FlexibleDateTimeParser.parse(date, formatter).toEpochSecond();
        assertEquals(expected, knownParse, "Manual parse failed for " + formatter);

        // this is as close as I can get to testing the expected date format
        assertEquals(unknownParse, knownParse, "Parsed date/times are not the same");
    }

    /**
     * Test parsing date strings with tryExtensiveParsing set to true
     *
     * @param date the string representation of a date
     * @param expected the expected parsed and formatted date
     * @param msg the error message to display if the test fails
     */
    private static void testExtensiveParsing(@Nullable String date, long expected, String msg) {
        ZonedDateTime unknownParse = FlexibleDateTimeParser.parse(date, true);
        assertEquals(expected, unknownParse == null ? 0L : unknownParse.toEpochSecond(), "Error on: " + msg);
    }


    /**
     * Helps assist in testing all the possible offset patterns that we want to attempt to parse for a given date
     * 
     * @param dateString the string representation of a date
     * @param expected the expected parsed and formatted date
     */
    private static void testAllOffsets(String dateString, long expected) {
        String[] offsets = {"EST-0500 -0500", // zZZ
                "EST EST-0500", // zzZ
                "EST-0500 EST", // zZz
                "EST-0500 -05", // zZX
                "-0500 EST-0500", // ZzZ
                "-0500 -05", // ZX
                "-05", // X
                "-05 EST", // Xz
                "-05 -0500", // XZ
                "-05 (EST-0500)"}; // XzZ

        for (String offset : offsets) {
            String dateAndOffset = dateString + " " + offset;
            testExtensiveParsing(dateAndOffset, expected, "Did not parse this string correctly: " + dateAndOffset);
        }
    }

    /**
     * Three-letter time zone IDs often point to multiple timezones. With Java 9+, there are no longer inconsistencies with
     * parsing timezones with offsets.
     * <p>
     * See {@link java.util.TimeZone} and {@link java.time.ZoneId#SHORT_IDS}
     */
    @Test
    void parseOffsetWhenThereIsAThreeLetterTimeZone() {
        DateTimeFormatter pattern =
                DateTimeFormatter.ofPattern("[E[,][ ]]d[ ]MMM[.][,][ ]yyyy[ H:mm[:ss][ ][a][ ][z][ ][Z][ ][z]]", Locale.getDefault());

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

        // additional tests with ambiguous abbreviations -- they should continue to be ignored and have consistent
        // behavior
        test("Mon, 4 Jan 2016 18:20:30 +0000 CST", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 18:20:30 +0000 ACT", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 18:20:30 +0000 BST", EXPECTED_FULL, pattern);

        // test full set of short offsets to make sure DateTimeFormatter can handle them
        // list taken from https://www.iana.org/time-zones
        test("Mon, 4 Jan 2016 07:20:30 -1100 SST", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 08:20:30 -1000 HST", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 09:20:30 -0900 HDT", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 09:20:30 -0900 AKST", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 10:20:30 -0800 AKDT", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 10:20:30 -0800 PST", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 11:20:30 -0700 PDT", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 11:20:30 -0700 MST", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 12:20:30 -0600 MDT", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 12:20:30 -0600 CST", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 13:20:30 -0500 CDT", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 13:20:30 -0500 EST", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 13:20:30 -0500 CST", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 14:20:30 -0400 CDT", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 14:20:30 -0400 AST", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 14:50:30 -0330 NST", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 15:20:30 -0300 ADT", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 15:50:30 -0230 NDT", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 18:20:30 +0000 GMT", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 19:20:30 +0100 BST", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 18:20:30 +0000 WET", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 19:20:30 +0100 WEST", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 19:20:30 +0100 CET", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 19:20:30 +0100 WAT", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 19:20:30 +0100 IST", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 20:20:30 +0200 IST", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 20:20:30 +0200 CEST", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 20:20:30 +0200 CAT", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 20:20:30 +0200 EET", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 20:20:30 +0200 SAST", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 21:20:30 +0300 EEST", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 21:20:30 +0300 IDT", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 21:20:30 +0300 EAT", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 21:20:30 +0300 MSK", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 23:20:30 +0500 PKT", EXPECTED_FULL, pattern);
        test("Mon, 4 Jan 2016 23:50:30 +0530 IST", EXPECTED_FULL, pattern);
        test("Tue, 5 Jan 2016 01:20:30 +0700 WIB", EXPECTED_FULL, pattern);
        test("Tue, 5 Jan 2016 02:20:30 +0800 AWST", EXPECTED_FULL, pattern);
        test("Tue, 5 Jan 2016 02:20:30 +0800 CST", EXPECTED_FULL, pattern);
        test("Tue, 5 Jan 2016 02:20:30 +0800 HKT", EXPECTED_FULL, pattern);
        test("Tue, 5 Jan 2016 02:20:30 +0800 PT", EXPECTED_FULL, pattern);
        test("Tue, 5 Jan 2016 02:20:30 +0800 WITA", EXPECTED_FULL, pattern);
        test("Tue, 5 Jan 2016 03:20:30 +0900 JST", EXPECTED_FULL, pattern);
        test("Tue, 5 Jan 2016 03:20:30 +0900 KST", EXPECTED_FULL, pattern);
        test("Tue, 5 Jan 2016 03:20:30 +0900 WIT", EXPECTED_FULL, pattern);
        test("Tue, 5 Jan 2016 03:50:30 +0930 ACST", EXPECTED_FULL, pattern);
        test("Tue, 5 Jan 2016 04:50:30 +1030 ACDT", EXPECTED_FULL, pattern);
        test("Tue, 5 Jan 2016 04:20:30 +1000 AEST", EXPECTED_FULL, pattern);
        test("Tue, 5 Jan 2016 04:20:30 +1000 ChST", EXPECTED_FULL, pattern);
        test("Tue, 5 Jan 2016 05:20:30 +1100 AEDT", EXPECTED_FULL, pattern);
    }

    /**
     * Test to make sure our code can successfully handle dates with shorter offsets because this bug in java 8 was
     * resolved: <a href="https://bugs.java.com/bugdatabase/view_bug.do?bug_id=8032051">view_bug</a>
     */
    @Test
    void parseShortOffsets() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-M-d[['T'][ ][/]H[:][/]m[:s][[.]SSS][ ][z][ ][Z][X]]", Locale.getDefault());

        test("2013-12-11T21:25:04+01:00", 1386793504, dtf);
        test("2013-12-11T21:25:04+01", 1386793504, dtf);
    }

    @Test
    void parse_yyyyMMddTHHmmssSSSX() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("yyyy-M-d[['T'][ ][/]H[:][/]m[:s][[.]SSS][ ][z][ ][Z][X]]", Locale.getDefault());
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
    void parse_yyyyMMddTHHmmssSSSX_Extra() {
        testExtensiveParsing("2016-01-04T18:20:30", EXPECTED_FULL, "0 digits for fraction of a second");
        testExtensiveParsing("2016-01-04T18:20:30.0", EXPECTED_FULL, "1 digit for fraction of a second");
        testExtensiveParsing("2016-01-04T18:20:30.00", EXPECTED_FULL, "2 digits for fraction of a second");
        testExtensiveParsing("2016-01-04T18:20:30.0000", EXPECTED_FULL, "4 digits for fraction of a second");
        testExtensiveParsing("2016-01-04T18:20:30.00000", EXPECTED_FULL, "5 digits for fraction of a second");
        testExtensiveParsing("2016-01-04T18:20:30.000000", EXPECTED_FULL, "6 digits for fraction of a second");
        testExtensiveParsing("2016-01-04T18:20:30.0000000", EXPECTED_FULL, "7 digits for fraction of a second");
        testExtensiveParsing("2016-01-04T18:20:30.00000000", EXPECTED_FULL, "8 digits for fraction of a second");
    }

    @Test
    void parse_EdMMMyyHmmssZ() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("[E[,][ ]]d[ ]MMM[,][ ]yy[ H:mm[:ss][ ][z][ ][Z][ ][Z]]", Locale.getDefault());
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
    void parse_EdMMMyyHmmssZ_Extra() {
        testAllOffsets("Mon, 4 Jan 16 13:20:30", EXPECTED_FULL);
        testAllOffsets("Monday, 4 Jan 16 13:20:30", EXPECTED_FULL);
        testAllOffsets("Monday, 4 January 16 13:20:30", EXPECTED_FULL);
        testAllOffsets("Mon, 4 January 16 13:20:30", EXPECTED_FULL);
    }


    @Test
    void parse_EdMMMyyyyKmmssaZ() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("[E[,][ ]]d MMM yyyy K:mm:ss a[ ][z][ ][Z][ ][Z]", Locale.getDefault());
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
    void parse_EdMMMyyyyKmmssaZ_Extra() {
        testAllOffsets("Mon, 4 Jan 2016 01:20:30 PM", EXPECTED_FULL);
        testAllOffsets("Monday, 4 Jan 2016 01:20:30 PM", EXPECTED_FULL);
        testAllOffsets("Monday, 4 January 2016 01:20:30 PM", EXPECTED_FULL);
        testAllOffsets("Mon, 4 January 2016 01:20:30 PM", EXPECTED_FULL);
    }

    @Test
    void parse_EdMMMyyyyHmmssZz() {
        DateTimeFormatter pattern =
                DateTimeFormatter.ofPattern("[E[,][ ]]d[ ]MMM[.][,][ ]yyyy[ H:mm[:ss][ ][a][ ][z][ ][Z][ ][z]]", Locale.getDefault());
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
    void parse_EdMMMyyyyHmmssZz_Extra() {
        testAllOffsets("Mon, 4 Jan 2016 13:20:30", EXPECTED_FULL);
        testAllOffsets("Monday, 4 Jan 2016 13:20:30", EXPECTED_FULL);
        testAllOffsets("Monday, 4 January 2016 13:20:30", EXPECTED_FULL);
        testAllOffsets("Mon, 4 January 2016 13:20:30", EXPECTED_FULL);
    }

    @Test
    void parse_EMMMdyyyyKmma() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("[E[,][ ]]MMM d[,] yyyy[,] K:mm[:ss] a[ ][z][ ][Z][ ][Z]", Locale.getDefault());
        test("Mon, Jan 4, 2016 06:20 PM", EXPECTED_NO_SECS, pattern);
        test("Mon, Jan 4, 2016 6:20 PM", EXPECTED_NO_SECS, pattern);
        test("Mon, Jan 04, 2016 06:20 PM", EXPECTED_NO_SECS, pattern);
        test("Mon Jan 04, 2016 06:20 PM", EXPECTED_NO_SECS, pattern);
        test("Mon Jan 04 2016 06:20 PM", EXPECTED_NO_SECS, pattern);
        test("Mon,Jan 04 2016 06:20 PM", EXPECTED_NO_SECS, pattern);
    }

    @Test
    void parse_EMMMdyyyyKmma_Extra() {
        testAllOffsets("Mon, Jan 4, 2016 01:20 PM", EXPECTED_NO_SECS);
        testAllOffsets("Monday, Jan 4, 2016 01:20 PM", EXPECTED_NO_SECS);
        testAllOffsets("Monday, January 4, 2016 01:20 PM", EXPECTED_NO_SECS);
        testAllOffsets("Mon, January 4, 2016 01:20 PM", EXPECTED_NO_SECS);
    }

    @Test
    void parse_EMMMdyyyyHmmssz() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("[E[,][ ]]MMM d[,] yyyy[[,] H:mm[:ss][ ][a][ ][z][ ][Z][ ][Z]]", Locale.getDefault());
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
    void parse_EMMMdyyyyHmmssz_Extra() {
        testAllOffsets("Mon, Jan 04, 2016 13:20:30", EXPECTED_FULL);
        testAllOffsets("Monday, Jan 04, 2016 13:20:30", EXPECTED_FULL);
        testAllOffsets("Monday, January 04, 2016 13:20:30", EXPECTED_FULL);
        testAllOffsets("Mon, January 04, 2016 13:20:30", EXPECTED_FULL);
    }

    @Test
    void parse_EddMMMyyyyHmmssZ() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("[E[,][ ]]dd-MMM-yyyy[ H:mm:ss[ ][z][ ][Z]]", Locale.getDefault());
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
    void parse_EddMMMyyyyHmmssZ_Extra() {
        testAllOffsets("Mon 04-Jan-2016 13:20:30", EXPECTED_FULL);
        testAllOffsets("Monday 04-Jan-2016 13:20:30", EXPECTED_FULL);
    }

    @Test
    void parse_EMMMdHHmmsszzzyyyy() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("[E[,][ ]]MMM d H:mm[:ss][ z] yyyy[ ][z][ ][Z][ ][Z]", Locale.getDefault());
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
    void parse_EMMMdHHmmsszzzyyyy_Extra() {
        testAllOffsets("Mon Jan 04 13:20:30 EST 2016", EXPECTED_FULL);
        testAllOffsets("Monday Jan 04 13:20:30 EST 2016", EXPECTED_FULL);
        testAllOffsets("Monday January 04 13:20:30 EST 2016", EXPECTED_FULL);
        testAllOffsets("Mon January 04 13:20:30 EST 2016", EXPECTED_FULL);
    }

    @Test
    void parse_MdyyKmma() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("M/d/yy[ ]K:mm[:ss][ ]a", Locale.getDefault());
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
    void parse_MdyyHmmssaz() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("M/d/yy[ ]H:mm[:ss][ ][a][ ][z][ ][Z][ ][Z]", Locale.getDefault());
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
    void parse_MdyyHmmssaz_Extra() {
        testAllOffsets("01/04/16 13:20:30", EXPECTED_FULL);
    }

    @Test
    void parse_MdyyyyKmma() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("M/d/yyyy[ ]K:mm[:ss][ ]a", Locale.getDefault());
        test("01/04/2016 06:20 PM", EXPECTED_NO_SECS, pattern);
        test("1/4/2016 6:20 PM", EXPECTED_NO_SECS, pattern);
        test("01/04/2016 06:20:30 PM", EXPECTED_FULL, pattern);
        test("1/4/2016 06:20:30 PM", EXPECTED_FULL, pattern);
        test("01/04/2016 00:20 AM", EXPECTED_NO_HR_SEC, pattern);
    }

    @Test
    void parse_MdyyyyHmmssaz() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("M/d/yyyy[ ]H:mm[:ss][ ][a][ ][z][ ][Z][ ][Z]", Locale.getDefault());
        test("01/04/2016 18:20:30 GMT", EXPECTED_FULL, pattern);
        test("1/4/2016 18:20:30 GMT", EXPECTED_FULL, pattern);
        test("01/04/2016 18:20:30 PM +0000", EXPECTED_FULL, pattern);
        test("1/4/2016 18:20:30 PM +0000", EXPECTED_FULL, pattern);
        test("1/4/2016 8:20:30 AM -1000", EXPECTED_FULL, pattern);
        test("1/4/2016 8:20:30 -1000", EXPECTED_FULL, pattern);
        test("01/04/2016 18:20:30 PM GMT", EXPECTED_FULL, pattern);
        test("1/4/2016 18:20:30 PM GMT", EXPECTED_FULL, pattern);
        test("01/04/2016 18:20:30 PM GMT+0000", EXPECTED_FULL, pattern);
        test("1/4/2016 18:20:30 PM GMT+0000", EXPECTED_FULL, pattern);
        test("01/04/2016 18:20:30 PM", EXPECTED_FULL, pattern);
        test("01/04/2016 18:20:30", EXPECTED_FULL, pattern);
        test("01/04/2016 18:20", EXPECTED_NO_SECS, pattern);
        test("1/4/2016 18:20", EXPECTED_NO_SECS, pattern);
    }

    @Test
    void parse_MdyyyyHmmssaz_Extra() {
        testAllOffsets("01/04/2016 13:20:30", EXPECTED_FULL);
    }

    @Test
    void parse_dMyyyKmmssa() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("d.M.yyyy[ ]K:mm[:ss][ ]a", Locale.getDefault());
        test("04.01.2016 06:20 PM", EXPECTED_NO_SECS, pattern);
        test("04.01.2016 06:20PM", EXPECTED_NO_SECS, pattern);
        test("04.01.2016 06:20:30 PM", EXPECTED_FULL, pattern);
        test("04.01.2016 06:20:30PM", EXPECTED_FULL, pattern);
        test("4.1.2016 6:20 PM", EXPECTED_NO_SECS, pattern);
        test("4.1.2016 6:20PM", EXPECTED_NO_SECS, pattern);
        test("4.1.2016 6:20:30 PM", EXPECTED_FULL, pattern);
        test("4.1.2016 6:20:30PM", EXPECTED_FULL, pattern);
    }

    @Test
    void parse_dMyyyHmmssaz() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("d.M.yyyy[ ]H:mm[:ss][ ][a][ ][z][ ][Z][ ][Z]", Locale.getDefault());
        test("04.01.2016 18:20:30", EXPECTED_FULL, pattern);
        test("4.1.2016 18:20:30", EXPECTED_FULL, pattern);
        test("4.1.2016 18:20", EXPECTED_NO_SECS, pattern);
        test("04.01.2016 18:20:30 +0000", EXPECTED_FULL, pattern);
        test("04.01.2016 18:20:30 GMT+0000", EXPECTED_FULL, pattern);
        test("04.01.2016 18:20:30 GMT", EXPECTED_FULL, pattern);
        test("04.01.2016 18:20:30+0000", EXPECTED_FULL, pattern);
    }

    @Test
    void parse_dMyyyHmmssaz_Extra() {
        testAllOffsets("04.01.2016 13:20:30", EXPECTED_FULL);
    }

    @Test
    void parse_HHmmddMMyyyy() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("[HHmm]dd[-][.][/]MM[-][.][/]yyyy", Locale.getDefault());
        test("04-01-2016", EXPECTED_NO_TIME, pattern);
        test("04.01.2016", EXPECTED_NO_TIME, pattern);
        test("04/01/2016", EXPECTED_NO_TIME, pattern);
        test("182004012016", EXPECTED_NO_SECS, pattern);
    }

    @Test
    void parse_yyyyMMddHHmmssS() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("yyyy/MM/dd[[ ]HH[:]mm[[:]ss[[.]S]][ ][z][ ][Z][ ][Z]]", Locale.getDefault());
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
    void parse_yyyyMMddHHmmssS_Extra() {
        testAllOffsets("2016/01/04 13:20:30", EXPECTED_FULL);
    }

    @Test
    void parse_yyyy_MM_ddHHmmssS() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("yyyy:MM:dd[ H:m[:ss[.S]][ ][z][ ][Z][ ][Z]]", Locale.getDefault());
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
    void parse_yyyy_MM_ddHHmmssS_Extra() {
        testAllOffsets("2016:01:04 13:20:30.0", EXPECTED_FULL);
    }

    @Test
    void parse_yyyyMMddHHmmss() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.getDefault());
        test("20160104182030", EXPECTED_FULL, pattern);
    }

    @Test
    void parse_yyyyMMdd() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.getDefault());
        test("20160104", EXPECTED_NO_TIME, pattern);
    }

    @Test
    void parse_yyyyDDDHHmmss() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("yyyyDDDHHmmss", Locale.getDefault());
        test("2016004182030", EXPECTED_FULL, pattern);
    }

    @Test
    void parse_yyyyDDDHHmm() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("yyyyDDDHHmm", Locale.getDefault());
        test("20160041820", EXPECTED_NO_SECS, pattern);
    }

    @Test
    void parse_yyyyDDD() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("yyyyDDD", Locale.getDefault());
        test("2016004", EXPECTED_NO_TIME, pattern);
    }

    @Test
    void parse_yyyy_DDD() {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("yyyy-DDD", Locale.getDefault());
        test("2016-004", EXPECTED_NO_TIME, pattern);
    }

    @Test
    void testCleanDateString() {
        test("2016-01-04 18:20<br>", EXPECTED_NO_SECS, "HTML");
        test("2016-01-04 18:20<br>br>", EXPECTED_NO_SECS, "HTML");
        test("2016-01-04\t\t18:20", EXPECTED_NO_SECS, "TABS");
        test("2016-01-04        18:20", EXPECTED_NO_SECS, "SPACES");
        test("2016-01-04 18:20=0D", EXPECTED_NO_SECS, "qp'ified ending");
        test("$$2016-01-04 18:20:00$$", EXPECTED_NO_SECS, "Extra characters at the beginning and end");
        test("2016-01-04 (18:20:00)", EXPECTED_NO_SECS, "Extra parenthesis");
        test("2016-01-04 18:20:00 [GMT]", EXPECTED_NO_SECS, "Extra brackets");
        test("\"Mon\", 4 Jan 2016 18:20 +0000 \"EST\"", EXPECTED_NO_SECS, "Extra quotes");
    }

    @Test
    void testLastDitchEffortParsing() {
        testExtensiveParsing("Jan 04 2016 18:20:30 +0000.5555555", EXPECTED_FULL, "Removing text at the end should be successful");
        testExtensiveParsing("Jan 04 2016 18:20:30 +0000 (This is not a date offset)", EXPECTED_FULL,
                "Removing text at the end should be successful");
        testExtensiveParsing("Tue, 5 Jan 2016 02:20:30 +0800 PHT", EXPECTED_FULL,
                "PHT is a timezone not parsed by DateTimeFormatter, but we should still parse correctly with the numeric time zone offset.");

        // we should not attempt to remove random text at the end when we are not specifying tryExtensiveFormatList to be true
        test("Jan 04 2016 18:20:30 +0000.5555555", 0L, "Fail to parse when tryExtensiveFormatList is false");
        test("Jan 04 2016 18:20:30 +0000 (This is not a date offset)", 0L, "Fail to parse when tryExtensiveFormatList is false");
    }

    @Test
    void testBad() {
        test("", 0L, "EMPTY");
        test(null, 0L, "NULL");
        assertNull(FlexibleDateTimeParser.parse("1234", Collections.emptyList()));
        assertNull(FlexibleDateTimeParser.parse("1234", Collections.singletonList(null)));
        test("17.Mar.2016", 0L, "UNKNOWN");
        test("Mon, 2 Feb 2017 06:20:30 PM +0000", 0L, "UNKNOWN");
        test("2016:01:04 18:20:30 GMT+0000<" + RandomStringUtils.randomAlphanumeric(75) + ">", 0L, "UNKNOWN");
    }
}
