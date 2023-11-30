package emissary.core.sentinel.protocols.rules;

import emissary.core.NamespaceException;
import emissary.core.sentinel.protocols.Protocol;
import emissary.pool.AgentPool;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.StringJoiner;
import java.util.regex.Pattern;

public abstract class Rule {

    protected static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    // the place name to test the condition
    protected final Pattern place;

    // how long to wait before alerting on stuck agents
    protected final long timeLimit;

    // percentage of mobile agents that are stuck on the same place before sounding the alarm
    protected final double threshold;

    public Rule(String place, long timeLimit, double threshold) {
        logger.trace("Creating rule for place={}, timeLimit={}, threshold={}", place, timeLimit, threshold);
        this.place = Pattern.compile(place);
        this.timeLimit = timeLimit;
        this.threshold = threshold;
    }

    public Rule(String place, String timeLimit, String threshold) {
        this(place, StringUtils.isBlank(timeLimit) ? 60L : Long.parseLong(timeLimit),
                StringUtils.isBlank(threshold) ? 1.0 : Double.parseDouble(threshold));
    }

    /**
     * Check the rule conditions
     *
     * @param placeAgentStats collection of the stats of a place that is currently processing
     * @return true if conditions are met, false otherwise
     */
    public boolean condition(Collection<Protocol.PlaceAgentStats> placeAgentStats) {
        return placeAgentStats.stream().filter(p -> place.matcher(p.getPlace()).matches()).anyMatch(p -> overThreshold(p) && overTimeLimit(p));
    }

    /**
     * Check to see if the number of places in mobile agents are over the configured threshold
     *
     * @param placeAgentStats the stats of a place that is currently processing
     * @return true if the number of mobile agents stuck on the place is over the threshold, false otherwise
     */
    protected boolean overThreshold(Protocol.PlaceAgentStats placeAgentStats) {
        int poolSize = getAgentCount();
        logger.debug("Testing threshold for place={}, counter={}, poolSize={}, threshold={}", place, placeAgentStats.getCount(), poolSize, threshold);
        return (double) placeAgentStats.getCount() / poolSize >= this.threshold;
    }

    /**
     * Get the total number of agents, idle and active. Override this method to
     * 
     * @return the total number of agents
     */
    protected int getAgentCount() {
        try {
            return AgentPool.lookup().getCurrentPoolSize();
        } catch (NamespaceException ne) {
            throw new IllegalStateException(ne);
        }
    }

    /**
     * Check to see if the places in mobile agents are over the configured time limit
     *
     * @param placeAgentStats the stats of a place that is currently processing
     * @return true if the places in mobile agents are over the configured time limit, false otherwise
     */
    protected abstract boolean overTimeLimit(Protocol.PlaceAgentStats placeAgentStats);

    @Override
    public String toString() {
        return new StringJoiner(", ", getClass().getSimpleName() + "[", "]")
                .add("place=" + place)
                .add("timeLimit=" + timeLimit)
                .add("threshold=" + threshold)
                .toString();
    }

}
