package emissary.util;

import ch.qos.logback.core.rolling.RollingFileAppender;

public class FifteenMinuteLogbackAppender<E> extends RollingFileAppender<E> {
    private long start = System.currentTimeMillis(); // minutes
    private static final int ROLLOVER_TIME_MINUTES = 15;

    @Override
    public void rollover() {
        long currentTime = System.currentTimeMillis();
        int maxIntervalSinceLastLoggingInMillis = ROLLOVER_TIME_MINUTES * 60 * 1000;

        if ((currentTime - start) >= maxIntervalSinceLastLoggingInMillis) {
            super.rollover();
            start = System.currentTimeMillis();
        }
    }
}
