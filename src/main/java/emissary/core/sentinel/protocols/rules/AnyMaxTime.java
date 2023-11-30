package emissary.core.sentinel.protocols.rules;

import emissary.core.sentinel.protocols.Protocol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

/**
 * Looks at the place that has been running for the most amount of time.
 */
public class AnyMaxTime extends Rule {

    protected static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public AnyMaxTime(String place, long timeLimit, double threshold) {
        super(place, timeLimit, threshold);
    }

    public AnyMaxTime(String place, String timeLimit, String threshold) {
        super(place, timeLimit, threshold);
    }

    /**
     * Check to see if ANY places in mobile agents are over the configured time limit
     *
     * @param placeAgentStats the stats of a place that is currently processing
     * @return true if any places in mobile agents are over the configured time limit, false otherwise
     */
    protected boolean overTimeLimit(Protocol.PlaceAgentStats placeAgentStats) {
        logger.debug("Testing timeLimit for place={}, maxTime={}, timeLimit={}", place, placeAgentStats.getMaxTimeInPlace(), timeLimit);
        return placeAgentStats.getMaxTimeInPlace() >= this.timeLimit;
    }

}
