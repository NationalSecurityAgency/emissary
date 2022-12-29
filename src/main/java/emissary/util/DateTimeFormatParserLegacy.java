package emissary.util;

import emissary.config.Configurator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class DateTimeFormatParserLegacy {

    protected static final Logger logger = LoggerFactory.getLogger(DateTimeFormatParserLegacy.class);

    /** Date formats we can use to parse event date strings */
    @Deprecated
    protected static final List<SimpleDateFormat> dateFormats = new ArrayList<>();

    static {
        configure();
    }

    protected static void configure() {
        try {
            Configurator configG = emissary.config.ConfigUtil.getConfigInfo(DateTimeFormatParserLegacy.class);
            for (final String dfentry : configG.findEntries("DATE_FORMAT")) {
                try {
                    final SimpleDateFormat sdf = new SimpleDateFormat(dfentry);
                    sdf.setLenient(true);
                    dateFormats.add(sdf);
                } catch (Exception ex) {
                    logger.debug("DATE_FORMAT entry '{}' cannot be parsed", dfentry, ex);
                }
            }
            logger.debug("Loaded {} DATE_FORMAT entries", dateFormats.size());
        } catch (IOException e) {
            logger.error("Cannot open default config file", e);
        }
    }


    /**
     * Parse an RFC-822 Date or one of the thousands of variants make a quick attempt to normalize the timezone information
     * and get the timestamp in GMT. Should change to pass in a default from the U124 header
     *
     * @param dateString the string date from the RFC 822 Date header
     * @param supplyDefaultOnBad when true use current date if sentDate is unparseable
     * @return the GMT time of the event or NOW if unparseable, or null if supplyDefaultOnBad is false
     */
    @Deprecated
    public static Date parseEventDate(final String dateString, final boolean supplyDefaultOnBad) {
        Date date = null;
        if (dateString != null && dateString.length() > 0) {
            // Take it apart and stick it back together to get
            // get rid of multiple contiguous spaces
            String instr = dateString.replaceAll("\t+", " "); // tabs
            instr = instr.replaceAll("[ ]+", " "); // multiple spaces
            instr = instr.replaceAll("=0D$", ""); // common qp'ified ending

            // try each date format in turn until one works
            synchronized (dateFormats) {
                for (final SimpleDateFormat sdf : dateFormats) {
                    try {
                        date = sdf.parse(instr);
                        break;
                    } catch (Exception e) {
                        // Ignore.
                        logger.debug("Error parsing date", e);
                    }
                }
            }
        }

        // Use the default if required
        if (date == null && supplyDefaultOnBad) {
            date = new Date();
        }

        // Let them have it.
        return date;
    }

}
