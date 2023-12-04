package emissary.core.sentinel.protocols.actions;

import emissary.core.sentinel.Sentinel;

import java.util.Map;

/**
 * Log the problem agents/threads
 */
public class Notify extends Action {

    @Override
    public void trigger(Map<String, Sentinel.Tracker> trackers) {
        logger.warn("Sentinel detected locked agents {}", trackers.values());
    }
}
