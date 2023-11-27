package emissary.core.sentinel.protocols.rules;

import emissary.core.NamespaceException;
import emissary.core.sentinel.Sentinel;
import emissary.pool.AgentPool;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.StringJoiner;

public abstract class Rule {

    protected static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    protected final String place;

    // how long to wait before alerting on stuck agents
    protected final long timeLimit;

    // percentage of mobile agents that are stuck on the same place before sounding the alarm
    protected final double threshold;

    public Rule(String place, long timeLimit, double threshold) {
        logger.trace("Creating rule for place={}, timeLimit={}, threshold={}", place, timeLimit, threshold);
        this.place = place;
        this.timeLimit = timeLimit;
        this.threshold = threshold;
    }

    public Rule(String place, String timeLimit, String threshold) {
        this(place, StringUtils.isBlank(timeLimit) ? 60L : Long.parseLong(timeLimit),
                StringUtils.isBlank(threshold) ? 1.0 : Double.parseDouble(threshold));
    }

    public boolean condition(Map<String, Sentinel.Tracker> trackers, String placeSimpleName, Integer count) {
        return overThreshold(count) && overTimeLimit(trackers, placeSimpleName);
    }

    protected boolean overThreshold(Integer count) {
        try {
            int poolSize = AgentPool.lookup().getCurrentPoolSize();
            logger.trace("Testing threshold for place={}, counter={}, poolSize={}, threshold={}", place, count, poolSize, threshold);
            return (double) count / poolSize >= this.threshold;
        } catch (NamespaceException ne) {
            throw new IllegalStateException(ne);
        }
    }

    protected abstract boolean overTimeLimit(Map<String, Sentinel.Tracker> trackers, String placeSimpleName);

    @Override
    public String toString() {
        return new StringJoiner(", ", getClass().getSimpleName() + "[", "]")
                .add("place=" + place)
                .add("timeLimit=" + timeLimit)
                .add("threshold=" + threshold)
                .toString();
    }
}
