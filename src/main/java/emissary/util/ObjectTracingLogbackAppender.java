package emissary.util;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;

import ch.qos.logback.core.rolling.RollingFileAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ObjectTracingLogbackAppender<E> extends RollingFileAppender<E> {
    private long start = System.currentTimeMillis();
    private int rolloverTime = 15;
    private boolean isConfigured = false;

    protected static final Logger logger = LoggerFactory.getLogger(ObjectTracingLogbackAppender.class);

    public void configure() {
        try {
            Configurator configG = ConfigUtil.getConfigInfo(getClass());
            rolloverTime = configG.findIntEntry("ROLLOVER_TIME_MINUTES", rolloverTime);
        } catch (IOException e) {
            logger.error("Could not read from the config for the ObjectTracingLogbackAppender ", e);
        }
    }

    @Override
    public void rollover() {

        if (!isConfigured) {
            configure();
            isConfigured = true;
        }

        long currentTime = System.currentTimeMillis();
        int maxIntervalSinceLastLoggingInMillis = rolloverTime * 60 * 1000;

        if ((currentTime - start) >= maxIntervalSinceLastLoggingInMillis) {
            super.rollover();
            start = System.currentTimeMillis();
        }
    }
}
