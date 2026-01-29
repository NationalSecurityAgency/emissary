package emissary.core.sentinel.protocols.rules;

import emissary.core.sentinel.protocols.trackers.AgentTracker;

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
     * @param agentTrackers the stats of a place that is currently processing
     * @return true if any places in mobile agents are over the configured time limit, false otherwise
     */
    @Override
    protected boolean overTimeLimit(final Collection<AgentTracker> agentTrackers) {
        logger.debug("Testing timeLimit for place={}, timeLimit={}", place, timeLimit);
        boolean over = false;
        for (AgentTracker agentTracker : agentTrackers) {
            if (agentTracker.getTimer() >= timeLimit) {
                agentTracker.flag();
                over = true;
            }
        }
        return over;
    }

}
