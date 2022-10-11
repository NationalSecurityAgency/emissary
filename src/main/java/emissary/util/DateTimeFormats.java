package emissary.util;

import java.io.IOException;
import java.util.HashMap;

import emissary.config.ConfigEntry;
import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DateTimeFormats {

    protected final Logger logger = LoggerFactory.getLogger(DateTimeFormats.class);
    private static HashMap<String, String> formats;

    public DateTimeFormats() {

        formats = new HashMap<>();

        try {
            Configurator configG = ConfigUtil.getConfigInfo(DateTimeFormats.class);
            for (ConfigEntry entry : configG.getEntries()) {
                formats.put(entry.getKey(), entry.getValue());
            }
        } catch (IOException e) {
            logger.error("There was an error loading DateTime formats: {}", e);
        }
    }

    public static DateTimeFormats initialize() {
        DateTimeFormats dtf = new DateTimeFormats();
        return dtf;
    }

    /**
     * Given the name of a date/time format, return the format as a string
     * 
     * @param formatName The name of the format to retrieve
     * @return The corresponding date/time format, or null if it does not exist
     */
    public static String getFormat(String formatName) {
        return formats.get(formatName);
    }

}
