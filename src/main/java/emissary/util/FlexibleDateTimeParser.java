package emissary.util;

import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import emissary.config.ConfigEntry;
import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Attempt to parse a date in an unknown format. This will loop through a set of configured formats and convert it into
 * a {@link ZonedDateTime}.
 * <p>
 * Other parsing libs:
 * <p>
 * Natty - It handled a good chunk of the formats but not all.
 */
public class FlexibleDateTimeParser {

    /* Logger */
    private static final Logger logger = LoggerFactory.getLogger(FlexibleDateTimeParser.class);

    /* Configuration Variables */
    private static final String CFG_FORMAT = "FORMAT_DATETIME";
    private static final String CFG_TIMEZONE = "TIMEZONE";
    private static final String DEFAULT_TIMEZONE = "GMT";
    private static final String SPACE = " ";
    private static final String EMPTY = "";

    /*
     * Three-letter time zone IDs often point to multiple time zones. Java 8 uses the time zone over the offset causing
     * problems with the date/time in verifies. Java 9 fixes this issue. Since 9 isn't released and, even if it was, it
     * would take some time to transition, a regex is used to strip out the short time zone if there is an offset present.
     * See java.util.TimeZone and java.time.ZoneId#SHORT_IDS for more info.
     */
    private static final String SHORT_TZ_PATTERN = "(\\()?([A-Z]{3})(\\))?"; // (XXX) or XXX
    private static final String OFFSET_PATTERN = "[ ]?[+-]\\d{2}(:?\\d{2}(:?\\d{2})?)?[ ]?"; // +00 +00:00 +0000
    private static final String REMOVE_STZ = "|(?<=" + OFFSET_PATTERN + ")" + SHORT_TZ_PATTERN + "|" + SHORT_TZ_PATTERN + "(?=" + OFFSET_PATTERN
            + ")";

    /* Remove all tabs and extra spaces */
    private static final Pattern REPLACE = Pattern.compile("\t+|[ ]+", Pattern.DOTALL);

    /* Remove other junk */
    private static final Pattern REMOVE = Pattern.compile("<.+?>|=0D$" + REMOVE_STZ, Pattern.DOTALL);

    /* timezone - config var: TIMEZONE */
    private static ZoneId timezone = ZoneId.of(DEFAULT_TIMEZONE);

    /* date time formats - vars: FORMAT_DATETIME */
    private static List<DateTimeFormatter> dateFormats = new ArrayList<>();

    /** init */
    static {
        configure();
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
     * Attempts to parse a string date using pre-configured patterns
     *
     * @param dateString the string to parse
     * @return the parsed immutable and thread-safe zoned-date, or null if it failed to parse
     */
    public static ZonedDateTime parse(final String dateString) {
        return parse(dateString, dateFormats);
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
    public static ZonedDateTime parse(final String dateString, final List<DateTimeFormatter> formats) {
        String cleanedDateString = cleanDateString(dateString);

        if (StringUtils.isBlank(cleanedDateString) || CollectionUtils.isEmpty(formats)) {
            return null;
        }

        for (DateTimeFormatter formatter : formats) {
            try {
                // try for a zoned date (has timezone), local date time (no time zone), or just a local date (no time)
                TemporalAccessor accessor = formatter.parseBest(cleanedDateString, ZonedDateTime::from, LocalDateTime::from, LocalDate::from);
                if (accessor instanceof ZonedDateTime) {
                    return (ZonedDateTime) accessor; // return the date time w/ timezone
                } else if (accessor instanceof LocalDateTime) {
                    return ((LocalDateTime) accessor).atZone(timezone); // set the timezone
                } else if (accessor instanceof LocalDate) {
                    return ((LocalDate) accessor).atStartOfDay(timezone); // add zeroed out time
                }

            } catch (NullPointerException | IllegalArgumentException | DateTimeParseException e) {
                // Ignore b/c failures are expected -> set to trace otherwise will be noisy
                logger.trace("Error parsing date {} with format {}", dateString, formatter == null ? null : formatter.toString());
            }
        }
        return null;
    }

    /* Private Methods */

    /**
     * Setup and configure
     */
    private static void configure() {
        try {
            // fire up the configurator
            Configurator configurator = ConfigUtil.getConfigInfo(FlexibleDateTimeParser.class);
            setupTimezone(configurator.findStringEntry(CFG_TIMEZONE, DEFAULT_TIMEZONE));
            setupDateFormats(configurator.findStringMatchEntries(CFG_FORMAT));
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not configure parser!!", e);
        }
    }

    /**
     * Set the timezone to use for parsing (needed for DateTimes that do not have timezone information)
     *
     * @param configTimezone timezone string ["GMT" or "UTC" or "+0000" or "+00:00" ...]
     */
    private static void setupTimezone(final String configTimezone) {
        if (StringUtils.isBlank(configTimezone)) {
            return;
        }

        try {
            // parse the timezone from the config
            timezone = ZoneId.of(configTimezone);
        } catch (DateTimeException e) {
            logger.error("Error parsing timezone {}, using default {}", configTimezone, timezone.toString(), e);
        }
    }

    /**
     * Override the default date formats
     *
     * @param configEntries the list of override formats from the config file
     */
    private static void setupDateFormats(final List<ConfigEntry> configEntries) {
        List<DateTimeFormatter> dateTimeFormats = getConfigFormats(configEntries);
        if (CollectionUtils.isNotEmpty(dateTimeFormats)) {
            dateFormats = Collections.unmodifiableList(dateTimeFormats);
            logger.debug("Created successfully. Created {} of {} formats from config", dateFormats.size(), configEntries.size());
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
    private static DateTimeFormatter getFormatter(ConfigEntry entry) {
        try {
            return DateTimeFormatter.ofPattern(entry.getValue());
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

        String cleanedDateString = date;
        cleanedDateString = REPLACE.matcher(cleanedDateString).replaceAll(SPACE);
        cleanedDateString = REMOVE.matcher(cleanedDateString).replaceAll(EMPTY);
        return StringUtils.trimToNull(cleanedDateString);
    }

    /**
     * This class is not meant to be instantiated
     */
    private FlexibleDateTimeParser() {}

}
