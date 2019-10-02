package emissary.roll;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import emissary.config.Configurator;
import emissary.core.Factory;

/**
 * Util class to grab known parameters from configs to help configure Rollable objects.
 */
public class RollUtil {
    /**
     * returns the time period from the current Configurator using the value from ROLLABLE_TIME_PERIOD. Defaults to 10L
     * 
     * @return the configured rollable time period
     */
    public static long getPeriod(Configurator configG) {
        return configG.findLongEntry("ROLLABLE_TIME_PERIOD", 10L);
    }

    /**
     * Returns the TimeUnit by extracting the value from ROLLABLE_TIME_PERIOD. The time unit value must be one that exactly
     * matches those defined in the TimeUnit enum. Defaults to MINUTES.
     * 
     * @return the configured rollable TimeUnit
     */
    public static TimeUnit getTimeUnit(Configurator configG) {
        return getUnit(configG.findStringEntry("ROLLABLE_TIME_UNIT", "MINUTES"));
    }

    /**
     * Builds a Roller from a map of options. Specifically, this method looks for the keys<br>
     * CLASS - the fully qualified Rollable class name to instantiate <br>
     * TIME_UNIT - The time unit to roll on (MINUTES, SECONDS, etc) <br>
     * TIME_PERIOD - A long value for the TimeUnit multiplier. <br>
     * PROGRESS_MAX - A long value for progress based rolls
     * 
     * @param config a map containing expected configuration values
     * @return a newly configured Roller
     */
    public static Roller buildRoller(Map<String, String> config) {
        String clz = config.get("CLASS");
        // assumes a no-arg constructor and a Rollable instance
        Rollable r = (Rollable) Factory.create(clz);
        TimeUnit t = getUnit(config.get("TIME_UNIT"));
        long period = getLong(config.get("TIME_PERIOD"));
        long max = getLong(config.get("PROGRESS_MAX"));
        return new Roller(max, t, period, r);
    }

    // convert a long from a string or return 0
    private static long getLong(String l) {
        return l == null ? 0L : Long.parseLong(l);
    }

    // attempt to parse time unit.
    private static TimeUnit getUnit(String unit) {
        return unit == null ? null : TimeUnit.valueOf(unit);
    }

    /** This class is not meant to be instantiated. */
    private RollUtil() {}
}
