package emissary.core.sentinel.protocols.rules;

import emissary.core.sentinel.protocols.AgentProtocol.PlaceAgentStats;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Collection;

/**
 * Looks at the place that has been running for the most amount of time.
 */
public class AnyMaxTime extends AgentRule {

    protected static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public AnyMaxTime(String name, String place, long timeLimit, double threshold) {
        super(name, place, timeLimit, threshold);
    }

    public AnyMaxTime(String name, String place, String timeLimit, String threshold) {
        super(name, place, timeLimit, threshold);
    }

    /**
     * Check to see if ANY places in mobile agents are over the configured time limit
     *
     * @param placeAgentStats the stats of a place that is currently processing
     * @return true if any places in mobile agents are over the configured time limit, false otherwise
     */
    @Override
    protected boolean overTimeLimit(Collection<PlaceAgentStats> placeAgentStats) {
        long maxTimeInPlace = placeAgentStats.stream().mapToLong(PlaceAgentStats::getMaxTimeInPlace).max().orElse(0);
        logger.debug("Testing timeLimit for place={}, maxTime={}, timeLimit={}", place, maxTimeInPlace, timeLimit);
        return maxTimeInPlace >= this.timeLimit;
    }

}
