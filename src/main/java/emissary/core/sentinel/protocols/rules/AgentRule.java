package emissary.core.sentinel.protocols.rules;

import emissary.core.NamespaceException;
import emissary.core.sentinel.protocols.AgentProtocol.PlaceAgentStats;
import emissary.pool.AgentPool;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.StringJoiner;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class AgentRule extends Rule<PlaceAgentStats> {

    protected static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    // the name of the rule
    protected final String name;

    // the place name to test the condition
    protected final Pattern place;

    // how long to wait before alerting on stuck agents
    protected final long timeLimit;

    // percentage of mobile agents that are stuck on the same place before sounding the alarm
    protected final double threshold;

    public AgentRule(String name, String place, long timeLimit, double threshold) {
        logger.trace("Creating rule for name={}, place={}, timeLimit={}, threshold={}", name, place, timeLimit, threshold);
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("Invalid name [" + name + "]");
        }
        if (StringUtils.isBlank(place)) {
            throw new IllegalArgumentException("Invalid place pattern [" + place + "]");
        }
        if (timeLimit <= 0) {
            throw new IllegalArgumentException("Invalid timeLimit [" + timeLimit + "], must be greater than 0");
        }
        if (threshold <= 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("Invalid threshold [" + threshold + "], expected a value > 0.0 or <= 1.0");
        }
        this.name = name;
        this.place = Pattern.compile(place);
        this.timeLimit = timeLimit;
        this.threshold = threshold;
    }

    public AgentRule(String name, String place, String timeLimit, String threshold) {
        this(name, place, StringUtils.isBlank(timeLimit) ? 60L : Long.parseLong(timeLimit),
                StringUtils.isBlank(threshold) ? 1.0 : Double.parseDouble(threshold));
    }

    /**
     * Check the rule conditions
     *
     * @param placeAgentStats collection of the stats of a place that is currently processing
     * @return true if conditions are met, false otherwise
     */
    @Override
    public boolean condition(Collection<PlaceAgentStats> placeAgentStats) {
        List<PlaceAgentStats> filtered =
                placeAgentStats.stream().filter(p -> place.matcher(p.getPlace()).matches()).collect(Collectors.toList());
        return overThreshold(filtered) && overTimeLimit(filtered);
    }

    /**
     * Check to see if the number of places in mobile agents are over the configured threshold
     *
     * @param placeAgentStats the stats of a place that is currently processing
     * @return true if the number of mobile agents stuck on the place is over the threshold, false otherwise
     */
    protected boolean overThreshold(Collection<PlaceAgentStats> placeAgentStats) {
        int count = placeAgentStats.stream().mapToInt(PlaceAgentStats::getCount).sum();
        int poolSize = getAgentCount();
        logger.debug("Testing threshold for place={}, counter={}, poolSize={}, threshold={}", place, count, poolSize, threshold);
        return (double) count / poolSize >= this.threshold;
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
    protected abstract boolean overTimeLimit(Collection<PlaceAgentStats> placeAgentStats);

    @Override
    public String toString() {
        return new StringJoiner(", ", "{", "}")
                .add("\"name\":\"" + name + "\"")
                .add("\"rule\":\"" + getClass().getSimpleName() + "\"")
                .add("\"place\":\"" + place + "\"")
                .add("\"timeLimitInMinutes\":" + timeLimit)
                .add("\"threshold\":" + threshold)
                .toString();
    }

}
