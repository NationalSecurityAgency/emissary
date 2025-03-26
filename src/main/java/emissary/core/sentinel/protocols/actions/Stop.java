package emissary.core.sentinel.protocols.actions;

import emissary.core.sentinel.protocols.trackers.Tracker;
import emissary.server.EmissaryServer;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Attempt a graceful shutdown of the system
 */
public class Stop extends Action {

    @Override
    public void trigger(Map<String, Tracker> trackers) {
        logger.error("Sentinel detected unrecoverable state, initiating graceful shutdown -- {}", format(trackers));
        var unused = CompletableFuture.runAsync(EmissaryServer::stopServer);
    }
}
