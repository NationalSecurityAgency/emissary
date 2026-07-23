package emissary.output.stats;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.Namespace;
import emissary.core.NamespaceException;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

import static net.logstash.logback.marker.Markers.appendEntries;

/**
 * Aggregates counts of named views emitted at drop-off/output time, broken down by
 * {@code viewName x fileType x status}, and periodically flushes an aggregated summary to a dedicated logfile before
 * resetting - so each flush line represents a rolling window (e.g. "objects in the last 10 minutes").
 *
 * <p>
 * This mirrors the {@link emissary.core.ResourceWatcher} (placeStats) pattern: a Namespace-bound singleton that
 * accumulates counts in memory and exposes {@link #logStats(Logger)} to flush-and-reset. It is deliberately decoupled
 * from any concrete output schema - callers hand it plain strings, so downstream projects can attribu`te their own view
 * names and processing statuses without this class depending on their types.
 *
 * <p>
 * The feature is off by default. It is created and bound only when enabled (see {@link #startAndBind()} callers), so
 * {@link #record(String, String, String)} is a cheap no-op whenever the singleton is not bound.
 */
public class ViewOutputStats {

    protected static final Logger LOG = LoggerFactory.getLogger(ViewOutputStats.class);

    /** Dedicated logger name, routed to its own file appender via logback (mirrors {@code objectTrace}). */
    protected static final Logger STATS_LOGGER = LoggerFactory.getLogger("dropOffStats");

    public static final String DEFAULT_NAMESPACE_NAME = "ViewOutputStats";

    /** Status values recorded against a view emission. Callers may also supply their own status strings. */
    public static final String STATUS_OK = "OK";
    public static final String STATUS_NOT_OUTPUTTABLE = "NOT_OUTPUTTABLE";
    public static final String STATUS_WRITE_FAILED = "WRITE_FAILED";

    /** Separator between the composite key components. Tab avoids collision with view/type names. */
    protected static final String KEY_SEP = "\t";

    protected final int interval;
    protected final TimeUnit intervalUnit;
    protected final long windowSeconds;

    /** Swapped out wholesale on each flush so accumulation and reset are atomic. */
    protected final AtomicReference<ConcurrentHashMap<String, LongAdder>> counts =
            new AtomicReference<>(new ConcurrentHashMap<>());

    @Nullable
    protected ScheduledExecutorService scheduler;

    @Nullable
    protected ScheduledFuture<?> scheduledTask;

    public ViewOutputStats(final int interval, final TimeUnit intervalUnit) {
        this.interval = interval;
        this.intervalUnit = intervalUnit;
        this.windowSeconds = intervalUnit.toSeconds(interval);
    }

    /**
     * Build an instance from {@code ViewOutputStats.cfg} (keys {@code INTERVAL}, {@code INTERVAL_UNIT}). Falls back to
     * defaults (10 MINUTES) if the config cannot be found, so the feature always starts when enabled.
     */
    public static ViewOutputStats fromConfig() {
        int interval = 10;
        TimeUnit unit = TimeUnit.MINUTES;
        try {
            final Configurator conf = ConfigUtil.getConfigInfo(ViewOutputStats.class);
            interval = conf.findIntEntry("INTERVAL", interval);
            unit = TimeUnit.valueOf(conf.findStringEntry("INTERVAL_UNIT", unit.name()));
        } catch (IOException e) {
            LOG.warn("No ViewOutputStats.cfg found, using defaults ({} {})", interval, unit);
        }
        return new ViewOutputStats(interval, unit);
    }

    /**
     * Bind this instance into the Namespace and start the periodic flush-and-reset thread.
     */
    public void startAndBind() {
        Namespace.bind(DEFAULT_NAMESPACE_NAME, this);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            final Thread t = new Thread(r, "ViewOutputStats");
            t.setDaemon(true);
            return t;
        });
        this.scheduledTask = this.scheduler.scheduleAtFixedRate(() -> {
            try {
                flush();
            } catch (RuntimeException e) {
                LOG.error("Issue flushing ViewOutputStats", e);
            }
        }, interval, interval, intervalUnit);
    }

    @Override
    public String toString() {
        return "ViewOutputStats flushing every " + interval + " " + intervalUnit;
    }

    /**
     * Lookup the bound singleton and record a view emission. No-op if the feature is disabled (not bound).
     *
     * @param viewName the emitted view's name
     * @param fileType the payload's file type
     * @param status the processing status of the emitted view (e.g. OK, NOT_OUTPUTTABLE)
     */
    public static void record(final String viewName, final String fileType, final String status) {
        try {
            lookup().count(viewName, fileType, status);
        } catch (NamespaceException ignored) {
            // feature disabled / not bound - nothing to do
        }
    }

    public static ViewOutputStats lookup() throws NamespaceException {
        return (ViewOutputStats) Namespace.lookup(DEFAULT_NAMESPACE_NAME);
    }

    /**
     * Increment the counter for a single view emission.
     */
    public void count(final String viewName, final String fileType, final String status) {
        final String key = viewName + KEY_SEP + fileType + KEY_SEP + status;
        this.counts.get().computeIfAbsent(key, k -> new LongAdder()).increment();
    }

    /**
     * Flush the current window to the dedicated {@code dropOffStats} logfile and reset.
     */
    public void flush() {
        logStats(STATS_LOGGER);
    }

    /**
     * Atomically swap in a fresh accumulator and return the counts observed in the window just closed, keyed by the
     * composite {@code viewName KEY_SEP fileType KEY_SEP status}. Only non-zero entries are returned.
     */
    protected Map<String, Long> snapshotAndReset() {
        final Map<String, LongAdder> window = this.counts.getAndSet(new ConcurrentHashMap<>());
        final Map<String, Long> result = new HashMap<>();
        for (final Map.Entry<String, LongAdder> e : window.entrySet()) {
            final long count = e.getValue().sum();
            if (count > 0) {
                result.put(e.getKey(), count);
            }
        }
        return result;
    }

    /**
     * Flush the current window: emit one structured line per non-zero {@code viewName x fileType x status} and reset the
     * accumulator so the next window starts from zero.
     */
    public void logStats(final Logger loggerArg) {
        for (final Map.Entry<String, Long> e : snapshotAndReset().entrySet()) {
            final String[] parts = e.getKey().split(KEY_SEP, -1);
            final Map<String, Object> fields = new HashMap<>();
            fields.put("viewName", parts[0]);
            fields.put("fileType", parts[1]);
            fields.put("status", parts[2]);
            fields.put("count", e.getValue());
            fields.put("windowSeconds", windowSeconds);
            loggerArg.info(appendEntries(fields), "");
        }
    }

    /**
     * Stop the periodic flush thread and unbind from the Namespace. Callers should invoke {@link #logStats(Logger)} for a
     * final flush beforehand if the tail window matters.
     */
    public void shutdown() {
        if (this.scheduler != null) {
            this.scheduler.shutdown();
        }
        try {
            Namespace.unbind(DEFAULT_NAMESPACE_NAME);
        } catch (RuntimeException e) {
            LOG.debug("ViewOutputStats was not bound at shutdown", e);
        }
    }
}
