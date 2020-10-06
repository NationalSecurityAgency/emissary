package emissary.core;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.codahale.metrics.ExponentiallyDecayingReservoir;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import emissary.place.IServiceProviderPlace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Track mobile agents and make them obey resource limitations
 */
public class ResourceWatcher implements Runnable {
    protected static final Logger LOG = LoggerFactory.getLogger(ResourceWatcher.class);

    public static final String DEFAULT_NAMESPACE_NAME = "ResourceWatcher";

    // This is a default that can be modified for every place
    protected long timeLimitMillis = TimeUnit.SECONDS.toMillis(30);

    // Time limit can be overidden per place by config
    // This structure is a cache of place timeouts that have
    // been gathered, stored by class place name (not class name,
    // as some classes function in different ways (e.g. UnixCommandPlace)
    protected Map<String, Long> placeTimeLimits = new ConcurrentHashMap<>();

    // The thread we plan to run on
    protected transient Thread monitor = null;

    // Loop control
    protected boolean timeToQuit = false;

    protected MetricRegistry metrics;

    protected MetricsFormatter metricsFormatter = MetricsFormatter.builder().withDurationUnit(TimeUnit.MILLISECONDS).withRateUnit(TimeUnit.SECONDS)
            .build();

    // Things we are tracking
    protected Queue<TimedResource> tracking = new LinkedBlockingQueue<>();

    public ResourceWatcher() {
        this(new MetricsManager());
    }

    /**
     * Create a resource watcher set it running and bind into the NamespaceException
     */
    public ResourceWatcher(final MetricsManager metricsManager) {
        this.metrics = metricsManager.getMetricRegistry();
        final Thread thread = new Thread(this, "ResourceWatcher");
        thread.setPriority(Thread.NORM_PRIORITY);
        thread.setDaemon(true);
        Namespace.bind(DEFAULT_NAMESPACE_NAME, this);
        thread.start();
    }


    private long getPlaceDuration(final IServiceProviderPlace place) {
        String placeName = place.getPlaceName();
        Long allowedDuration = placeTimeLimits.get(placeName);
        // Read and cache the duration for this place
        if (allowedDuration == null) {
            final long d = place.getResourceLimitMillis();
            allowedDuration = d >= -1 ? d : timeLimitMillis;
            placeTimeLimits.put(placeName, allowedDuration);
        }
        return allowedDuration;
    }

    /**
     * Register an agent to start tracking it
     * 
     * @param agent the agent to track
     * @param place place executing
     * @return TimedResource for the place and agent
     */
    public TimedResource starting(final IMobileAgent agent, final IServiceProviderPlace place) {
        TimedResource tr = new TimedResource(agent, place, getPlaceDuration(place), metrics.timer(place.getPlaceName()));
        tracking.offer(tr);
        return tr;
    }

    /**
     * Lookup the default ResourceWatcher in the Namespace
     * 
     * @return The registered ResourceWatcher
     */
    public static ResourceWatcher lookup() throws NamespaceException {
        return (ResourceWatcher) Namespace.lookup(DEFAULT_NAMESPACE_NAME);
    }

    /**
     * Safely stop the monitoring Thread
     */
    public void quit() {
        this.timeToQuit = true;
    }

    /**
     * Set the default time limit in millis
     * 
     * @param limit the new value
     */
    public void setTimeLimitMillis(final long limit) {
        this.timeLimitMillis = limit;
    }

    /**
     * Get the default time limit in millis
     * 
     * @return time limit
     */
    public long getTimeLimitMillis() {
        return this.timeLimitMillis;
    }

    /**
     * Runnable interface where we get to monitor stuff
     */
    @Override
    public void run() {
        LOG.debug("ResourceWatcher is starting");

        while (!this.timeToQuit) {
            // Delay this loop
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignore) {
                // Ignore.
            }
            Iterator<TimedResource> it = tracking.iterator();
            while (it.hasNext()) {
                final long now = System.currentTimeMillis();
                final TimedResource val = it.next();
                if (val.checkState(now)) {
                    it.remove();
                }
            }
        }
    }

    public void logStats(final Logger loggerArg) {
        for (final Map.Entry<String, Timer> e : this.metrics.getTimers().entrySet()) {
            // We only want to log stats for places that have had events
            if (e.getValue().getCount() > 0) {
                loggerArg.info(this.metricsFormatter.formatTimer(e.getKey(), e.getValue()));
            }
        }
    }

    public void resetStats() {
        // We use reflection to reset the histograms that track finished events, but leaves the namespace for active timers
        for (Timer timer : this.metrics.getTimers().values()) {
            try {
                Field histogramField = Timer.class.getDeclaredField("histogram");
                histogramField.setAccessible(true);
                histogramField.set(timer, new Histogram(new ExponentiallyDecayingReservoir()));
            } catch (NoSuchFieldException | IllegalAccessException e) {
                LOG.error("Issue resetting placeStats in ResourceWatcher", e);
            }
        }
    }

    public SortedMap<String, Timer> getStats() {
        return this.metrics.getTimers();
    }

    public Timer getStat(final String statKey) {
        return this.metrics.timer(statKey);
    }

    @Override
    public String toString() {
        return "Watching " + this.tracking.size() + " agents with default time limit " + this.timeLimitMillis + "ms";
    }
}
