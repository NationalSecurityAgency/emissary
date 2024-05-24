package emissary.core.sentinel.protocols.actions;

import emissary.core.sentinel.Sentinel;

import java.util.Map;

/**
 * Try to terminate the JVM
 */
public class Exit extends Action {

    @Override
    @SuppressWarnings("SystemExitOutsideMain")
    public void trigger(Map<String, Sentinel.Tracker> trackers) {
        logger.error("Sentinel detected unrecoverable agents, exiting now -- {}", format(trackers));
        System.exit(1);
    }
}
