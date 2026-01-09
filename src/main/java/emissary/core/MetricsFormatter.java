package emissary.core;

import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Formatter for metrics data, following the existing format emissary format, established previously. Employs the
 * builder pattern of the Reporters from the coda hale metrics package. This currently supports Timers, but could be
 * expanded to support any number of metrics classes.
 */
public class MetricsFormatter {

    private static final Logger logger = LoggerFactory.getLogger(MetricsFormatter.class);

    /** Create a builder */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private TimeUnit durationUnit;
        private TimeUnit rateUnit;

        /** Specify the duration unit for the formatter to be built */
        public Builder withDurationUnit(final TimeUnit t) {
            this.durationUnit = t;
            return this;
        }

        /** Specify the rate unit for the formatter to be build */
        public Builder withRateUnit(final TimeUnit t) {
            this.rateUnit = t;
            return this;
        }

        /** Build the formatter */
        public MetricsFormatter build() {
            return new MetricsFormatter(this.rateUnit, this.durationUnit);
        }
    }

    private final double durationFactor;

    /** Use a builder to create MetricsFormatter instances */
    private MetricsFormatter(final TimeUnit rateUnit, final TimeUnit durationUnit) {
        this.durationFactor = 1.0 / durationUnit.toNanos(1);
        logger.debug("Metrics formatter in {}", rateUnit);
    }

    @SuppressWarnings("LongDoubleConversion")
    public String formatTimer(final String name, final Timer timer) {
        final Snapshot snapshot = timer.getSnapshot();
        return String.format(Locale.getDefault(), "STAT: %s => min=%2.2f,  max=%2.2f, avg=%2.2f, events=%d", name, convertDuration(snapshot.getMin()),
                convertDuration(snapshot.getMax()), convertDuration(snapshot.getMean()), timer.getCount());
    }

    protected double convertDuration(final double duration) {
        return duration * this.durationFactor;
    }
}
