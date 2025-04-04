package emissary.core.sentinel.protocols.actions;

import emissary.core.sentinel.protocols.trackers.Tracker;

import java.util.Map;

/**
 * Log the problem agents/threads
 */
public class Notify extends Action {

    @Override
    public void trigger(Map<String, Tracker> trackers) {
        logger.warn("Sentinel detected possible locked agents -- {}", format(trackers));
    }
}
