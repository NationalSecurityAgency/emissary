package emissary.core.sentinel.protocols.actions;

import emissary.core.sentinel.protocols.trackers.Tracker;

import java.util.Map;

/**
 * Try to terminate the JVM
 */
public class Exit extends Action {

    @Override
    @SuppressWarnings("SystemExitOutsideMain")
    public void trigger(Map<String, Tracker> trackers) {
        logger.error("Sentinel detected unrecoverable state, exiting now -- {}", format(trackers));
        System.exit(1);
    }
}
