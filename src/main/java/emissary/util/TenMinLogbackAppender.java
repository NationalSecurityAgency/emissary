package emissary.util;

import ch.qos.logback.core.rolling.RollingFileAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TenMinLogbackAppender<E> extends RollingFileAppender<E> {
    public static final Logger logger = LoggerFactory.getLogger(TenMinLogbackAppender.class);
    private static long start = System.currentTimeMillis(); // minutes
    private static int rollOverTimeInMinutes = 10;

    @Override
    public void rollover() {
        long currentTime = System.currentTimeMillis();
        int maxIntervalSinceLastLoggingInMillis = rollOverTimeInMinutes * 60 * 1000;

        if ((currentTime - start) >= maxIntervalSinceLastLoggingInMillis) {
            logger.info("Rolling InternalProvenance Appender");
            super.rollover();
            start = System.currentTimeMillis();
        }
    }
}
