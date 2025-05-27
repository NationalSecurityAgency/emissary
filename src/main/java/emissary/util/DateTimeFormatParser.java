package emissary.util;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;

import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Class for Parsing Dates using DateTimeFormatter. Attempts to parse a date of an unknown format with a predefined set
 * of formats.
 */
public class DateTimeFormatParser {

    protected static final List<DateTimeFormatter> dateTimeZoneFormats = new ArrayList<>();
    protected static final List<DateTimeFormatter> dateTimeOffsetFormats = new ArrayList<>();
    protected static final List<DateTimeFormatter> dateTimeFormats = new ArrayList<>();
    protected static final List<DateTimeFormatter> dateFormats = new ArrayList<>();
    @SuppressWarnings("NonFinalStaticField")
    protected static ZoneId zone;

    private static final String DATE_TIME_ZONE_FORMAT = "DATE_TIME_ZONE_FORMAT";
    private static final String DATE_TIME_OFFSET_FORMAT = "DATE_TIME_OFFSET_FORMAT";
    private static final String DATE_TIME_FORMAT = "DATE_TIME_FORMAT";
    private static final String DATE_FORMAT = "DATE_FORMAT";

    protected static final Logger logger = LoggerFactory.getLogger(DateTimeFormatParser.class);

    static {
        configure();
    }

    private DateTimeFormatParser() {}

    protected static void configure() {

        Configurator configG;
        try {
            configG = ConfigUtil.getConfigInfo(DateTimeFormatParser.class);
        } catch (IOException e) {
            logger.error("Cannot open default config file", e);
            return;
        }
        try {
            zone = ZoneId.of(configG.findStringEntry("TIME_ZONE"));
        } catch (RuntimeException e) {
            logger.error("There was an issue reading the time zone from the config file");
            return;
        }

        loadDateTimeEntries(configG, DATE_TIME_ZONE_FORMAT, dateTimeZoneFormats);
        loadDateTimeEntries(configG, DATE_TIME_OFFSET_FORMAT, dateTimeOffsetFormats);
        loadDateTimeEntries(configG, DATE_TIME_FORMAT, dateTimeFormats);
        loadDateTimeEntries(configG, DATE_FORMAT, dateFormats);
    }

    /**
     * Helper function to read the date time formats from the config file, parse them, and store them in the appropriate
     * DateTimeFormatter list for use later
     *
     * @param configG the Configurator object to load entries from
     * @param entryType the label that used in the config file for the category of format. This separates out the different
     *        formats that need to be parsed differently
     * @param dateFormats the list of DateTimeFormatter objects that corresponds to the appropriate format
     */
    private static void loadDateTimeEntries(Configurator configG, String entryType, List<DateTimeFormatter> dateFormats) {
        for (final String dateFormatEntry : configG.findEntries(entryType)) {
            try {
                DateTimeFormatter initialDtf =
                        new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(dateFormatEntry)
                                .toFormatter(Locale.getDefault());
                if (entryType.equals(DATE_TIME_ZONE_FORMAT)) {
                    initialDtf = initialDtf.withZone(zone);
                }
                final DateTimeFormatter dtf = initialDtf;
                dateFormats.add(dtf);
            } catch (RuntimeException ex) {
                logger.debug("{} entry '{}' cannot be parsed", entryType, dateFormatEntry, ex);
            }
        }
        logger.debug("Loaded {} {} entries", dateTimeZoneFormats.size(), entryType);
    }

    /**
     * Cleans up the date string by removing certain characters before attempting to parse it
     * 
     * @param dateString the date string
     * @return the cleaned date string
     */
    private static String cleanDate(String dateString) {
        // Take it apart and stick it back together to
        // get rid of multiple contiguous spaces
        String cleanedDateString = dateString.replaceAll("\t+", " "); // tabs
        cleanedDateString = cleanedDateString.replaceAll("[ ]+", " "); // multiple spaces
        cleanedDateString = cleanedDateString.replaceAll("=0D$", ""); // common qp'ified ending

        return cleanedDateString;
    }

    /**
     * Parse an RFC-822 Date or one of the thousands of variants make a quick attempt to normalize the timezone information
     * and get the timestamp in GMT. Should change to pass in a default from the U124 header
     *
     * @param dateString the string date from the RFC 822 Date header
     * @param supplyDefaultOnBad when true use current date if sentDate cannot be parsed
     * @return the GMT time of the event or NOW if it cannot be parsed, or null if supplyDefaultOnBad is false
     */
    @Nullable
    public static LocalDateTime parseDate(final String dateString, final boolean supplyDefaultOnBad) {

        if (StringUtils.isNotEmpty(dateString)) {
            String cleanedDateString = cleanDate(dateString);

            List<Function<String, LocalDateTime>> methodList = new ArrayList<>();
            methodList.add(date -> tryParseWithDateTimeZoneFormats(date));
            methodList.add(date -> tryParseWithDateTimeOffsetFormats(date));
            methodList.add(date -> tryParseWithDateTimeFormats(date));
            methodList.add(date -> tryParseWithDateFormats(date));

            for (Function<String, LocalDateTime> method : methodList) {
                LocalDateTime date = method.apply(cleanedDateString);
                if (date != null) {
                    return date;
                }
            }

            try {
                return Instant.from(DateTimeFormatter.ISO_INSTANT.parse(cleanedDateString)).atZone(zone).toLocalDateTime();
            } catch (DateTimeParseException ignored) {
                // ignore
            }

            // If none of these methods worked, use the default if required
            if (supplyDefaultOnBad) {
                return LocalDateTime.now(ZoneId.systemDefault());
            }
        }
        return null;
    }


    /**
     * Attempt to parse the string dateString with one of the ZonedDateTime patterns
     *
     * @param dateString the string to attempt to format
     * @return the LocalDateTime object if a formatter worked, or null otherwise
     */
    @Nullable
    private static LocalDateTime tryParseWithDateTimeZoneFormats(final String dateString) {
        // formats with a time zone
        for (final DateTimeFormatter dtf : dateTimeZoneFormats) {
            try {
                ZonedDateTime zdt = ZonedDateTime.parse(dateString, dtf);
                zdt = ZonedDateTime.ofInstant(zdt.toInstant(), zone);
                return zdt.toLocalDateTime();
            } catch (DateTimeParseException ignored) {
                // ignore
            }
        }
        return null;
    }

    /**
     * Attempt to parse the string dateString with one of the LocalDateTime patterns
     *
     * @param dateString the string to attempt to format
     * @return the LocalDateTime object if a formatter worked, or null otherwise
     */
    @Nullable
    private static LocalDateTime tryParseWithDateTimeFormats(final String dateString) {
        // formats with a date and time and no zone/offset
        for (final DateTimeFormatter dtf : dateTimeFormats) {
            try {
                return LocalDateTime.parse(dateString, dtf);
            } catch (DateTimeParseException ignored) {
                // ignore
            }
        }
        return null;
    }

    /**
     * Attempt to parse the string dateString with one of the OffsetDateTime patterns
     *
     * @param dateString the string to attempt to format
     * @return the LocalDateTime object if a formatter worked, or null otherwise
     */
    @Nullable
    private static LocalDateTime tryParseWithDateTimeOffsetFormats(final String dateString) {
        // formats with a time zone offset
        for (final DateTimeFormatter dtf : dateTimeOffsetFormats) {
            try {
                OffsetDateTime odt = OffsetDateTime.parse(dateString, dtf);
                return OffsetDateTime.ofInstant(odt.toInstant(), zone).toLocalDateTime();
            } catch (DateTimeParseException ignored) {
                // ignore
            }
        }
        return null;
    }

    /**
     * Attempt to parse the string dateString with one of the LocalDate patterns
     *
     * @param dateString the string to attempt to format
     * @return the LocalDateTime object if a formatter worked, or null otherwise
     */
    @Nullable
    private static LocalDateTime tryParseWithDateFormats(final String dateString) {
        // formats with a date but no time
        for (final DateTimeFormatter dtf : dateFormats) {
            try {
                LocalDate d = LocalDate.parse(dateString, dtf);
                return d.atStartOfDay();
            } catch (DateTimeParseException ignored) {
                // ignore
            }
        }
        return null;
    }
}
