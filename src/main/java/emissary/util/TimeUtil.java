package emissary.util;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.time.zone.ZoneRulesException;
import java.util.Date;

import org.apache.commons.lang.StringUtils;

public class TimeUtil {

    private static final ZoneId GMT = ZoneId.of("GMT");
    private static final DateTimeFormatter DATE_WITH_SLASHES = DateTimeFormatter.ofPattern("yyyy-MM-dd/HH/mm").withZone(GMT);
    private static final DateTimeFormatter DATE_ISO_8601 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(GMT);
    private static final DateTimeFormatter DATE_FULL_ISO_8601 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX").withZone(GMT);
    private static final DateTimeFormatter DATE_ORDINAL = DateTimeFormatter.ofPattern("yyyyDDD").withZone(GMT);

    /**
     * Get the application configured timezone
     *
     * @return the timezone
     */
    public static ZoneId getTimezone() {
        return GMT;
    }

    /**
     * Get current date/time formatted as specified
     *
     * @param format the specification
     * @param timeZone tz to use, defaults to GMT
     * @throws DateTimeException if the ID format is invalid
     * @throws ZoneRulesException if checking availability and the zone ID cannot be found
     * @throws IllegalArgumentException if the pattern is invalid
     */
    public static String getDate(final String format, final String timeZone) {
        ZoneId zoneId = timeZone == null ? GMT : ZoneId.of(timeZone);
        return getDate(ZonedDateTime.now(zoneId), format, zoneId);
    }

    /**
     * Given a date, format it with the given pattern
     *
     * @param date the date to format
     * @param format the date format
     * @param timeZone the time zone id
     * @return the formatted date
     * @throws DateTimeException if the ID format is invalid
     * @throws ZoneRulesException if checking availability and the zone ID cannot be found
     * @throws IllegalArgumentException if the pattern is invalid
     */
    public static String getDate(final TemporalAccessor date, final String format, final ZoneId timeZone) {
        if (date == null) {
            return null;
        }

        if (StringUtils.isEmpty(format)) {
            throw new IllegalArgumentException("The date format cannot be empty!");
        }

        ZoneId zoneId = timeZone == null ? GMT : timeZone;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format).withZone(zoneId);
        return formatter.format(date);
    }

    /**
     * Get current date/time as a slash separated path component (yyyy-MM-dd/HH/mm)
     *
     * @return the formatted date in the form yyyy-MM-dd/HH/mm
     * @throws DateTimeException if an error occurs during formatting
     */
    public static String getCurrentDate() {
        return getDateAsPath(ZonedDateTime.now(GMT));
    }

    /**
     * Get specified date/time as a slash separated path component
     *
     * @deprecated replaced by {@link #getDateAsPath(TemporalAccessor)}
     */
    @Deprecated
    public static String getDateAsPath(final Date date) {
        return getDateAsPath(date.toInstant());
    }

    /**
     * Get specified date/time as a slash separated path component (yyyy-MM-dd/HH/mm)
     *
     * @param date to format
     * @return the formatted date in the form yyyy-MM-dd/HH/mm
     * @throws DateTimeException if an error occurs during formatting
     */
    public static String getDateAsPath(final TemporalAccessor date) {
        if (date == null) {
            return null;
        }
        String dateStringDefault = DATE_WITH_SLASHES.format(date);
        return dateStringDefault.substring(0, dateStringDefault.length() - 1) + "0";
    }

    /**
     * Get current date as yyyyjjj
     *
     * @return the formatted date in the form yyyyjjj
     * @throws DateTimeException if an error occurs during formatting
     */
    public static String getCurrentDateOrdinal() {
        return getDateOrdinal(ZonedDateTime.now(GMT));
    }

    /**
     * Get current date as yyyyjjj
     *
     * @deprecated replaced by {@link #getDateOrdinal(TemporalAccessor)}
     */
    @Deprecated
    public static String getDateOrdinal(Date date) {
        return date == null ? null : getDateOrdinal(date.toInstant());
    }

    /**
     * Get current date as yyyyjjj
     *
     * @param date to format
     * @return the formatted date in the form yyyyjjj
     * @throws DateTimeException if an error occurs during formatting
     */
    public static String getDateOrdinal(TemporalAccessor date) {
        return date == null ? null : DATE_ORDINAL.format(date);
    }

    /**
     * Get current date/time as ISO-8601 string
     *
     * @return String in the format yyyy-MM-dd HH:mm:ss
     * @throws DateTimeException if an error occurs during formatting
     */
    public static String getCurrentDateISO8601() {
        return getDateAsISO8601(ZonedDateTime.now(GMT));
    }

    /**
     * Get specified time as ISO-8601 string
     *
     * @return String in the format yyyy-MM-dd HH:mm:ss
     * @throws DateTimeException if an error occurs during formatting
     */
    public static String getDateAsISO8601(final long time) {
        return getDateAsISO8601(ZonedDateTime.ofInstant(Instant.ofEpochMilli(time), GMT));
    }

    /**
     * Get specified date object as ISO-8601 string
     *
     * @deprecated replaced by {@link #getDateAsISO8601(TemporalAccessor)}
     */
    @Deprecated
    public static String getDateAsISO8601(final Date date) {
        return date == null ? null : getDateAsISO8601(date.toInstant());
    }

    /**
     * Get specified date object as ISO-8601 string (yyyy-MM-dd HH:mm:ss)
     *
     * @param date to format
     * @return String in the format yyyy-MM-dd HH:mm:ss
     * @throws DateTimeException if an error occurs during formatting
     */
    public static String getDateAsISO8601(final TemporalAccessor date) {
        return date == null ? null : DATE_ISO_8601.format(date);
    }

    /**
     * Get current date/time as full ISO-8601 string yyyy-MM-dd'T'HH:mm:ss'Z'
     *
     * @return String in the format yyyy-MM-dd'T'HH:mm:ss'Z'
     * @throws DateTimeException if an error occurs during formatting
     */
    public static String getCurrentDateFullISO8601() {
        return getDateAsFullISO8601(ZonedDateTime.now(GMT));
    }

    /**
     * Get specified date object as a full ISO-8601 string yyyy-MM-dd'T'HH:mm:ss'Z'
     *
     * @deprecated replaced by {@link #getDateAsFullISO8601(TemporalAccessor)}
     */
    @Deprecated
    public static String getDateAsFullISO8601(final Date date) {
        return date == null ? null : getDateAsFullISO8601(date.toInstant());
    }

    /**
     * Get specified date object as a full ISO-8601 string (yyyy-MM-dd'T'HH:mm:ss'Z')
     *
     * @param date to format
     * @return the formatted date in yyyy-MM-dd'T'HH:mm:ss'Z'
     * @throws DateTimeException if an error occurs during formatting
     */
    public static String getDateAsFullISO8601(final TemporalAccessor date) {
        return date == null ? null : DATE_FULL_ISO_8601.format(date);
    }

    /**
     * Get Date object from ISO-8601 formatted string
     *
     * @deprecated replaced by {@link FlexibleDateTimeParser#parse(String)}
     */
    @Deprecated
    public static Date getDateFromISO8601(final String dateString) throws DateTimeParseException {
        return dateString == null ? null : Date.from(getZonedDateFromISO8601(dateString).toInstant());
    }

    /**
     * Get Date object from ISO-8601 formatted string ("yyyy-MM-dd HH:mm:ss")
     *
     * @param dateString the string representation of the date in the format yyyy-MM-dd HH:mm:ss
     * @return ZonedDateTime parsed from the string
     * @throws DateTimeParseException if string is not in the proper format
     *
     * @deprecated replaced by {@link FlexibleDateTimeParser#parse(String)}
     */
    @Deprecated
    public static ZonedDateTime getZonedDateFromISO8601(final String dateString) throws DateTimeParseException {
        return dateString == null ? null : ZonedDateTime.parse(dateString, DATE_ISO_8601);
    }

    /** This class is not meant to be instantiated. */
    private TimeUtil() {}
}
