package emissary.core.sentinel.protocols.actions;

import emissary.core.sentinel.Sentinel;
import emissary.server.EmissaryServer;

import java.util.Map;

/**
 * Attempt a graceful shutdown of the system
 */
public class Stop extends Action {

    @Override
    public void trigger(Map<String, Sentinel.Tracker> trackers) {
        logger.error("Sentinel detected unrecoverable agents {}, initiating graceful shutdown...", trackers.values());
        EmissaryServer.stopServer();
    }
}
