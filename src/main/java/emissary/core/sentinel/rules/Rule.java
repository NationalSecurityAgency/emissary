package emissary.core.sentinel.rules;

import emissary.core.NamespaceException;
import emissary.core.sentinel.Sentinel;
import emissary.pool.AgentPool;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Comparator;
import java.util.Map;
import java.util.StringJoiner;

public abstract class Rule {

    protected static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final String place;

    // how long to wait before alerting on stuck agents
    private final long timeLimit;

    // percentage of mobile agents that are stuck on the same place before sounding the alarm
    private final double threshold;

    public Rule(String place, long timeLimit, double threshold) {
        this.place = place;
        this.timeLimit = timeLimit;
        this.threshold = threshold;
        logger.trace("Loaded {}", this);
    }

    public Rule(String place, String timeLimit, String threshold) {
        this(place, StringUtils.isBlank(timeLimit) ? 60L : Long.parseLong(timeLimit),
                StringUtils.isBlank(threshold) ? 1.0 : Double.parseDouble(threshold));
    }

    public String getPlace() {
        return place;
    }

    public long getTimeLimit() {
        return timeLimit;
    }

    public double getThreshold() {
        return threshold;
    }

    public void run(Map<String, Sentinel.Tracker> tracker, String placeSimpleName, Integer count) throws NamespaceException {
        if (condition(tracker, placeSimpleName, count)) {
            action(tracker, placeSimpleName, count);
        }
    }

    public boolean condition(Map<String, Sentinel.Tracker> trackers, String placeSimpleName, Integer count) throws NamespaceException {
        return overThreshold(count) && overTimeLimit(trackers, placeSimpleName);
    }

    public abstract void action(Map<String, Sentinel.Tracker> trackers, String placeSimpleName, Integer count);

    protected boolean overThreshold(Integer count) throws NamespaceException {
        int poolSize = AgentPool.lookup().getCurrentPoolSize();
        logger.trace("Testing threshold for place={}, counter={}, poolSize={}, threshold={}", place, count, poolSize, threshold);
        return (double) count / poolSize >= getThreshold();
    }

    protected boolean overTimeLimit(Map<String, Sentinel.Tracker> trackers, String placeSimpleName) {
        long maxTimeInPlace = getMaxTimeInPlace(trackers, placeSimpleName);
        logger.trace("Testing time limit for place={}, maxTimeInPlace={}, timeLimit={}", place, maxTimeInPlace, timeLimit);
        return maxTimeInPlace >= getTimeLimit();
    }

    protected long getMaxTimeInPlace(Map<String, Sentinel.Tracker> trackers, String placeSimpleName) {
        return trackers.values().stream()
                .filter(t -> StringUtils.equalsIgnoreCase(t.getPlaceSimpleName(), placeSimpleName))
                .map(Sentinel.Tracker::getTimer)
                .max(Comparator.naturalOrder()).orElse(0L);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", "Rule:" + place + "[", "]")
                .add("timeLimit=" + timeLimit)
                .add("threshold=" + threshold)
                .toString();
    }
}
