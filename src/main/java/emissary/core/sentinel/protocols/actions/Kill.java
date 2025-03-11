package emissary.core.sentinel.protocols.actions;

import emissary.core.sentinel.protocols.trackers.Tracker;
import emissary.server.EmissaryServer;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Force a shutdown of the system
 */
public class Kill extends Action {

    @Override
    public void trigger(Map<String, Tracker> trackers) {
        logger.error("Sentinel detected unrecoverable state, initiating forceful shutdown -- {}", format(trackers));
        var unused = CompletableFuture.runAsync(EmissaryServer::stopServerForce);
    }
}
