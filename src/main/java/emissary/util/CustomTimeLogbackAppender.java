package emissary.util;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;

import ch.qos.logback.core.rolling.RollingFileAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class CustomTimeLogbackAppender<E> extends RollingFileAppender<E> {
    public static final Logger logger = LoggerFactory.getLogger(CustomTimeLogbackAppender.class);
    private static long start = System.currentTimeMillis(); // minutes
    protected static final String ROLL_INTERVAL = "ROLL_INTERVAL";
    protected static final Integer DEFAULT_ROOL_INTERVAL = 10;
    private static int rollOverTimeInMinutes;

    static {
        configure();
    }

    protected static void configure() {
        Configurator configG = null;
        try {
            configG = ConfigUtil.getConfigInfo(CustomTimeLogbackAppender.class);
        } catch (IOException e) {
            logger.error("Cannot open default config file", e);
        }
        if (configG != null) {
            rollOverTimeInMinutes = configG.findIntEntry(ROLL_INTERVAL, DEFAULT_ROOL_INTERVAL);
        }

    }

    @Override
    public void rollover() {
        long currentTime = System.currentTimeMillis();
        int maxIntervalSinceLastLoggingInMillis = rollOverTimeInMinutes * 60 * 1000;

        if ((currentTime - start) >= maxIntervalSinceLastLoggingInMillis) {
            super.rollover();
            start = System.currentTimeMillis();
        }
    }
}
