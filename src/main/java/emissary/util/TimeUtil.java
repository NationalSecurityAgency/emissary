package emissary.util;

import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.time.zone.ZoneRulesException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.IllformedLocaleException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class TimeUtil {

    /**
     * Locale used for textual date/time components (month and day names, AM/PM, zone names). A fixed locale is used instead
     * of {@link Locale#getDefault()} so results do not depend on the host environment. Use {@link Locale#ROOT} for patterns
     * that only contain numeric fields.
     */
    public static final Locale DEFAULT_DATE_LOCALE = Locale.ENGLISH;

    /**
     * Special {@code LOCALE} config value that enables generic parsing: after the configured locales fail, retry every
     * locale available in the JVM.
     */
    public static final String GENERIC_LOCALE_TAG = "*";

    private static final List<Locale> AVAILABLE_TEXT_LOCALES = buildAvailableTextLocales();

    private static final ZoneId GMT = ZoneId.of("GMT");
    public static final DateTimeFormatter DATE_WITH_SLASHES =
            DateTimeFormatter.ofPattern("yyyy-MM-dd/HH/mm", Locale.ROOT).withZone(GMT);
    public static final DateTimeFormatter DATE_ISO_8601 =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT).withZone(GMT);
    public static final DateTimeFormatter DATE_FULL_ISO_8601 =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX", Locale.ROOT).withZone(GMT);
    public static final DateTimeFormatter DATE_ORDINAL =
            DateTimeFormatter.ofPattern("yyyyDDD", Locale.ROOT).withZone(GMT);
    public static final DateTimeFormatter DATE_ISO_8601_SSS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT);
    public static final DateTimeFormatter DATE_ORDINAL_WITH_TIME =
            DateTimeFormatter.ofPattern("yyyyDDDHHmmss", Locale.ROOT);
    private static final String ISO_8601_TIME_DATE_STRING = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    private static final String HEX_REGEX = "^0x([0-9A-Fa-f]{8})([0-9A-Fa-f]{8})";
    private static final Pattern HEX_DATE_PATTERN = Pattern.compile(HEX_REGEX);
    private static final int HEX_RADIX = 16;
    private static final LocalDateTime STARTING_DATE = LocalDateTime.parse("1900-01-01 00:00:00.000", DATE_ISO_8601_SSS);
    private static final Logger logger = LoggerFactory.getLogger(TimeUtil.class);

    /**
     * Get the application configured timezone
     *
     * @return the timezone
     */
    public static ZoneId getTimezone() {
        return GMT;
    }

    /**
     * Parse a collection of BCP 47 language tags (e.g. {@code en}, {@code en-US}, {@code fr}) into locales. Blank and
     * invalid tags are ignored. The {@link #GENERIC_LOCALE_TAG} value appends every textual locale available in the JVM
     * after the explicitly configured locales, regardless of where the wildcard appears. If no valid tags are supplied, a
     * list containing only {@link #DEFAULT_DATE_LOCALE} is returned.
     *
     * @param languageTags the language tags to parse
     * @return an immutable, ordered list of locales, never empty
     */
    public static List<Locale> parseLocales(@Nullable final Collection<String> languageTags) {
        Set<Locale> locales = new LinkedHashSet<>();
        boolean generic = false;
        if (languageTags != null) {
            for (String languageTag : languageTags) {
                String tag = StringUtils.trimToNull(languageTag);
                if (tag == null) {
                    continue;
                }
                if (GENERIC_LOCALE_TAG.equals(tag)) {
                    generic = true;
                    continue;
                }
                try {
                    Locale locale = new Locale.Builder().setLanguageTag(tag).build();
                    locales.add(locale);
                } catch (IllformedLocaleException e) {
                    logger.warn("Ignoring invalid locale [{}]: {}", tag, e.getMessage());
                }
            }
        }
        if (generic) {
            locales.addAll(AVAILABLE_TEXT_LOCALES);
        }
        return locales.isEmpty() ? List.of(DEFAULT_DATE_LOCALE) : List.copyOf(locales);
    }

    /**
     * Get the locales available for generic textual date/time parsing. English is tried first, followed by locales in
     * language-tag order. Regional variants are retained because month/day names and zone interpretations can differ.
     *
     * @return an immutable list of available locales
     */
    public static List<Locale> getAvailableTextLocales() {
        return AVAILABLE_TEXT_LOCALES;
    }

    private static List<Locale> buildAvailableTextLocales() {
        return Stream.concat(Stream.of(DEFAULT_DATE_LOCALE), Arrays.stream(Locale.getAvailableLocales())
                .filter(locale -> StringUtils.isNotEmpty(locale.getLanguage()))
                .sorted(Comparator.comparing(Locale::toLanguageTag)))
                .distinct().toList();
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
    public static String getDate(final String format, @Nullable final String timeZone) {
        ZoneId zoneId = timeZone == null ? GMT : ZoneId.of(timeZone);
        return getDate(ZonedDateTime.now(zoneId), format, zoneId, DEFAULT_DATE_LOCALE);
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
    @Nullable
    public static String getDate(@Nullable final TemporalAccessor date, final String format, @Nullable final ZoneId timeZone) {
        return getDate(date, format, timeZone, DEFAULT_DATE_LOCALE);
    }

    /**
     * Given a date, format it with the given pattern using the given locale for textual fields
     *
     * @param date the date to format
     * @param format the date format
     * @param timeZone the time zone id
     * @param locale the locale used for month/day names, AM/PM, and zone names
     * @return the formatted date
     * @throws DateTimeException if the ID format is invalid
     * @throws ZoneRulesException if checking availability and the zone ID cannot be found
     * @throws IllegalArgumentException if the pattern is invalid
     */
    @Nullable
    public static String getDate(@Nullable final TemporalAccessor date, final String format, @Nullable final ZoneId timeZone,
            final Locale locale) {
        if (date == null) {
            return null;
        }

        if (StringUtils.isEmpty(format)) {
            throw new IllegalArgumentException("The date format cannot be empty!");
        }

        ZoneId zoneId = timeZone == null ? GMT : timeZone;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format, locale).withZone(zoneId);
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
     * Get specified date/time as a slash separated path component (yyyy-MM-dd/HH/mm)
     *
     * @param date to format
     * @return the formatted date in the form yyyy-MM-dd/HH/mm
     * @throws DateTimeException if an error occurs during formatting
     */
    @Nullable
    public static String getDateAsPath(@Nullable final TemporalAccessor date) {
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
     * @param date to format
     * @return the formatted date in the form yyyyjjj
     * @throws DateTimeException if an error occurs during formatting
     */
    @Nullable
    public static String getDateOrdinal(@Nullable TemporalAccessor date) {
        return date == null ? null : DATE_ORDINAL.format(date);
    }

    /**
     * Get current date/time as ISO-8601 string
     *
     * @return String in the format yyyy-MM-dd HH:mm:ss
     * @throws DateTimeException if an error occurs during formatting
     */
    @SuppressWarnings("MemberName")
    public static String getCurrentDateISO8601() {
        return getDateAsISO8601(ZonedDateTime.now(GMT));
    }

    /**
     * Get specified time as ISO-8601 string
     *
     * @return String in the format yyyy-MM-dd HH:mm:ss
     * @throws DateTimeException if an error occurs during formatting
     */
    @SuppressWarnings("MemberName")
    public static String getDateAsISO8601(final long time) {
        return getDateAsISO8601(ZonedDateTime.ofInstant(Instant.ofEpochMilli(time), GMT));
    }

    /**
     * Get specified date object as ISO-8601 string (yyyy-MM-dd HH:mm:ss)
     *
     * @param date to format
     * @return String in the format yyyy-MM-dd HH:mm:ss
     * @throws DateTimeException if an error occurs during formatting
     */
    @Nullable
    @SuppressWarnings("MemberName")
    public static String getDateAsISO8601(@Nullable final TemporalAccessor date) {
        return date == null ? null : DATE_ISO_8601.format(date);
    }

    /**
     * Get current date/time as full ISO-8601 string yyyy-MM-dd'T'HH:mm:ss'Z'
     *
     * @return String in the format yyyy-MM-dd'T'HH:mm:ss'Z'
     * @throws DateTimeException if an error occurs during formatting
     */
    @SuppressWarnings("MemberName")
    public static String getCurrentDateFullISO8601() {
        return getDateAsFullISO8601(ZonedDateTime.now(GMT));
    }

    /**
     * Get specified date object as a full ISO-8601 string (yyyy-MM-dd'T'HH:mm:ss'Z')
     *
     * @param date to format
     * @return the formatted date in yyyy-MM-dd'T'HH:mm:ss'Z'
     * @throws DateTimeException if an error occurs during formatting
     */
    @Nullable
    @SuppressWarnings("MemberName")
    public static String getDateAsFullISO8601(@Nullable final TemporalAccessor date) {
        return date == null ? null : DATE_FULL_ISO_8601.format(date);
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
    @Nullable
    @Deprecated
    @SuppressWarnings("MemberName")
    public static ZonedDateTime getZonedDateFromISO8601(@Nullable final String dateString) {
        return dateString == null ? null : ZonedDateTime.parse(dateString, DATE_ISO_8601);
    }

    /**
     * Parses hex date string into formatted string ("yyyy-MM-dd HH:mm:ss.SSS")
     *
     * @param hexDate hex string representation of the date
     * @return converted time in string format yyyy-MM-dd HH:mm:ss.SSS
     * @throws DateTimeParseException if could not parse date
     *
     */
    public static String convertHexDate(String hexDate) {
        Matcher m = HEX_DATE_PATTERN.matcher(hexDate);

        if (m.find()) {
            String dateHex = m.group(1);
            String timeHex = m.group(2);
            long daysToAdd = Long.parseLong(dateHex, HEX_RADIX);
            // timeHex represents the number of ticks (1/300 of a second) since midnight
            long millisToAdd = Math.round(Long.parseLong(timeHex, HEX_RADIX) * 10 / 3.0);

            LocalDateTime ldt;

            try {
                ldt = STARTING_DATE.plusDays(daysToAdd);
                ldt = ldt.plus(millisToAdd, ChronoUnit.MILLIS);
            } catch (DateTimeParseException ex) {
                logger.debug("Could not parse date", ex);
                throw ex;
            }

            return ldt.format(DATE_ISO_8601_SSS);
        }

        String msg = String.format("Unexpected hexDate format '%s'", hexDate);
        logger.debug(msg);
        throw new IllegalArgumentException(msg);
    }

    @SuppressWarnings("MemberName")
    public static String getISO8601DateFormatString() {
        return ISO_8601_TIME_DATE_STRING;
    }

    public static String getDateOrdinalWithTime(final Instant d) {
        return DATE_ORDINAL_WITH_TIME.format(d.atZone(ZoneId.systemDefault()));
    }

    /** This class is not meant to be instantiated. */
    private TimeUtil() {}
}
