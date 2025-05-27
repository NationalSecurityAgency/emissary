package emissary.util;

import emissary.config.ConfigEntry;
import emissary.config.ConfigUtil;
import emissary.config.Configurator;

import jakarta.annotation.Nullable;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.toList;

/**
 * Attempt to parse a date in an unknown format. This will loop through a set of configured formats and convert it into
 * a {@link ZonedDateTime}.
 * <p>
 * Other parsing libs:
 * <p>
 * Natty - It handled a good chunk of the formats but not all.
 */
public final class FlexibleDateTimeParser {

    /* Logger */
    private static final Logger logger = LoggerFactory.getLogger(FlexibleDateTimeParser.class);

    /* Configuration Variables */
    private static final String CFG_FORMAT_MAIN = "FORMAT_DATETIME_MAIN";
    private static final String CFG_FORMAT_EXTRA = "FORMAT_DATETIME_EXTRA";
    private static final String CFG_TIMEZONE = "TIMEZONE";
    private static final String CFG_REMOVE_REGEX = "REMOVE_REGEX";
    private static final String CFG_EXTRA_TEXT_REMOVE_REGEX = "EXTRA_TEXT_REMOVE_REGEX";
    private static final String DEFAULT_TIMEZONE = "GMT";
    private static final String SPACE = " ";
    private static final String EMPTY = "";

    /* Remove all tabs and extra spaces */
    private static final Pattern REPLACE = Pattern.compile("\t+|[ ]+", Pattern.DOTALL);

    /*
     * Remove other junk -- anything in an html tag, all parenthesis and quotes, and any non-word characters at the
     * beginning or end
     */
    private static final Pattern remove;

    /*
     * This is our last ditch parsing effort if we failed to parse the string - remove all extra text after the numeric time
     * zone offset
     */
    private static final Pattern extraTextRemove;

    /* timezone - config var: TIMEZONE */
    private static final ZoneId timezone;

    /* date time formats - vars: FORMAT_DATETIME_MAIN */
    private static final List<DateTimeFormatter> dateFormatsMain;

    /* Extra date time formats - list to try if our main list has failed - vars: FORMAT_DATETIME_EXTRA */
    private static final List<DateTimeFormatter> dateFormatsExtra;

    /* init */
    static {
        try {
            // fire up the configurator
            Configurator configurator = ConfigUtil.getConfigInfo(FlexibleDateTimeParser.class);
            timezone = setupTimezone(configurator.findStringEntry(CFG_TIMEZONE, DEFAULT_TIMEZONE));

            List<ConfigEntry> configEntriesMain = configurator.findStringMatchEntries(CFG_FORMAT_MAIN);
            dateFormatsMain = setupDateFormats(configEntriesMain, getConfigFormats(configEntriesMain));

            List<ConfigEntry> configEntriesExtra = configurator.findStringMatchEntries(CFG_FORMAT_EXTRA);
            dateFormatsExtra = setupDateFormats(configEntriesExtra, getConfigFormats(configEntriesExtra));

            String removeRegex = configurator.findStringEntry(CFG_REMOVE_REGEX, "<.+?>$|=0D$|\\(|\\)|\"|\\[|]|\\W+$|^\\W+");
            remove = Pattern.compile(removeRegex, Pattern.DOTALL);

            // last ditch parsing effort if we failed to parse the string - remove all extra text after the numeric timezone offset
            String extraTextRemoveRegex = configurator.findStringEntry(CFG_EXTRA_TEXT_REMOVE_REGEX, "((\\+|-)\\d{4}).*$");
            extraTextRemove = Pattern.compile(extraTextRemoveRegex);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not configure parser!!", e);
        }
    }

    /**
     * Get the default timezone id for the application
     *
     * @return the configured immutable and thread-safe zone id
     */
    public static ZoneId getTimezone() {
        return timezone;
    }

    /**
     * Attempts to parse a string date using pre-configured patterns. Default not trying the extensive date/time format list
     *
     * @param dateString the string to parse
     * @return the parsed immutable and thread-safe zoned-date, or null if it failed to parse
     */
    public static ZonedDateTime parse(final String dateString) {
        return parse(dateString, false);
    }

    /**
     * Attempts to parse a string date using pre-configured patterns
     *
     * @param dateString the string to parse
     * @param tryExtensiveParsing True if we want to try out complete list of date/time formats False if we only want to
     *        attempt the most common date/time formats
     * @return the parsed immutable and thread-safe zoned-date, or null if it failed to parse
     */
    public static ZonedDateTime parse(final String dateString, boolean tryExtensiveParsing) {
        ZonedDateTime zdt = parseToZonedDateTime(dateString, tryExtensiveParsing);

        if (zdt != null || !tryExtensiveParsing) {
            return zdt;
        } else {
            // if that all failed and we want to attempt extensive parsing, attempt the last ditch efforts we can try
            return lastDitchParsingEffort(dateString);
        }
    }

    /**
     * Attempts to parse a string date
     *
     * @param dateString the string to parse
     * @param format the date/time formats to use
     * @return the parsed immutable and thread-safe zoned-date, or null if it failed to parse
     */
    public static ZonedDateTime parse(final String dateString, final DateTimeFormatter format) {
        return parse(dateString, Collections.singletonList(format));
    }

    /**
     * Attempts to parse a string date
     *
     * @param dateString the string to parse
     * @param formats the date/time formats to use
     * @return the parsed immutable and thread-safe zoned-date, or null if it failed to parse
     */
    @Nullable
    public static ZonedDateTime parse(final String dateString, final List<DateTimeFormatter> formats) {
        String cleanedDateString = cleanDateString(dateString);

        if (StringUtils.isBlank(cleanedDateString) || CollectionUtils.isEmpty(formats)) {
            return null;
        }

        for (DateTimeFormatter formatter : formats) {
            if (formatter == null) {
                continue;
            }

            try {
                // try for a zoned date (has timezone), local date time (no time zone), or just a local date (no time)
                TemporalAccessor accessor =
                        formatter.parseBest(cleanedDateString, ZonedDateTime::from, OffsetDateTime::from, LocalDateTime::from, LocalDate::from);
                if (accessor instanceof ZonedDateTime) {
                    return (ZonedDateTime) accessor; // return the date time w/ timezone
                } else if (accessor instanceof OffsetDateTime) {
                    return ((OffsetDateTime) accessor).atZoneSameInstant(timezone);
                } else if (accessor instanceof LocalDateTime) {
                    return ((LocalDateTime) accessor).atZone(timezone); // set the timezone
                } else if (accessor instanceof LocalDate) {
                    return ((LocalDate) accessor).atStartOfDay(timezone); // add zeroed out time
                }

            } catch (NullPointerException | IllegalArgumentException | DateTimeParseException e) {
                // Ignore b/c failures are expected -> set to trace otherwise will be noisy
                logger.trace("Error parsing date {} with format {}", dateString, formatter);
            }
        }
        return null;
    }

    /* Private Methods */

    /**
     * If all our formats failed to parse a date string, give it one last try to parse it. Look for a numeric offset (e.g.
     * +0000) and remove all text afterward. This should cover another set of cases where there is random text appended to
     * the end of the string, as well as removing invalid non-numeric time zone offsets while still picking up the numeric
     * offset Assumption - that tryExtensiveParsing is true - we should only get to this point if we want to try our best to
     * parse
     *
     * @param date The date string to parse
     * @return the ZonedDateTime object if removing text at the end was successful, or null otherwise
     */
    @Nullable
    static ZonedDateTime lastDitchParsingEffort(final String date) {

        // Attempt to remove all text after the numeric offset and try again - this should give us a valid date string
        // to work with
        Matcher matcher = extraTextRemove.matcher(date);
        if (matcher.find()) {
            String secondChanceDate = matcher.replaceAll(matcher.group(1));
            // if we removed text, attempt to parse again to see if we are more successful this time
            return parseToZonedDateTime(secondChanceDate, true);
        }
        return null;
    }

    /**
     * Created to help against code duplication. Calls parse with the standard set of date formats, and then if that fails,
     * attempt the extra set of date formats if tryExtensiveParsing is set to true.
     *
     * @param dateString The string we are attempting to parse
     * @param tryExtensiveParsing Whether to use the extensive set of date formats
     * @return The ZonedDateTime object if our parsing was successful, or null if not
     */
    private static ZonedDateTime parseToZonedDateTime(final String dateString, boolean tryExtensiveParsing) {
        ZonedDateTime zdt = parse(dateString, dateFormatsMain);

        // if we got a successful parse or we don't want to attempt "extensive parsing", return here
        if (!tryExtensiveParsing || zdt != null) {
            return zdt;
        }
        zdt = parse(dateString, dateFormatsExtra);
        return zdt;
    }

    /**
     * Get the timezone to use for parsing (needed for DateTimes that do not have timezone information)
     *
     * @param configTimezone timezone string ["GMT" or "UTC" or "+0000" or "+00:00" ...]
     * @return timezone
     */
    private static ZoneId setupTimezone(final String configTimezone) {
        try {
            if (StringUtils.isNotBlank(configTimezone)) {
                // parse the timezone from the config
                return ZoneId.of(configTimezone);
            }
        } catch (DateTimeException e) {
            logger.error("Error parsing timezone {}, using default {}", configTimezone, timezone, e);
        }

        return ZoneId.of(DEFAULT_TIMEZONE);
    }

    /**
     * Get the overrides for the default date formats
     *
     * @param configEntries the list of main override formats from the config file
     * @param dateTimeFormats the list of datetime formats
     * @return a list of {@link DateTimeFormatter}s
     */
    private static List<DateTimeFormatter> setupDateFormats(final List<ConfigEntry> configEntries, final List<DateTimeFormatter> dateTimeFormats) {
        List<DateTimeFormatter> dateFormats;
        if (CollectionUtils.isNotEmpty(dateTimeFormats)) {
            dateFormats = Collections.unmodifiableList(dateTimeFormats);
            logger.debug("Created successfully. Created {} of {} formats from config", dateFormats.size(), configEntries.size());
            return dateFormats;
        } else {
            logger.error("Could not create with configured variables");
            throw new IllegalArgumentException("No date/time formats configured!!");
        }
    }

    /**
     * Loop through the date formats from the config file and create DateTimeFormatter objects
     *
     * @param configEntries the list of override formats from the config file
     * @return a list of {@link DateTimeFormatter}s
     */
    @Nullable
    private static List<DateTimeFormatter> getConfigFormats(final List<ConfigEntry> configEntries) {
        if (CollectionUtils.isEmpty(configEntries)) {
            return null;
        }
        return configEntries.stream().map(FlexibleDateTimeParser::getFormatter).filter(Objects::nonNull).collect(toList());
    }

    /**
     * Create the DateTimeFormatter object
     *
     * @param entry format from the config file
     * @return {@link DateTimeFormatter} if the pattern is valid, null otherwise
     */
    @Nullable
    private static DateTimeFormatter getFormatter(ConfigEntry entry) {
        try {
            return new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(entry.getValue())
                    .toFormatter(Locale.getDefault());
        } catch (IllegalArgumentException e) {
            // log the bad one and move on because there could be other possible patterns
            logger.error("Error parsing pattern [{}]: {}", entry.getValue(), e.getLocalizedMessage());
        }
        return null;
    }

    /**
     * Clean up the date string for processing (remove extra spaces, tabs, html, ...)
     *
     * @param date the date string to clean
     * @return the scrubbed date string
     */
    private static String cleanDateString(final String date) {
        if (StringUtils.isBlank(date)) {
            return date;
        }

        // date strings over 100 characters are more than likely invalid
        String cleanedDateString = StringUtils.substring(date, 0, 100);
        cleanedDateString = REPLACE.matcher(cleanedDateString).replaceAll(SPACE);
        cleanedDateString = remove.matcher(cleanedDateString).replaceAll(EMPTY);

        return StringUtils.trimToNull(cleanedDateString);
    }

    /**
     * This class is not meant to be instantiated
     */
    private FlexibleDateTimeParser() {}

}
