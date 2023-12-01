package emissary.core.sentinel.protocols.actions;

import emissary.core.sentinel.Sentinel;
import emissary.server.EmissaryServer;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Force a shutdown of the system
 */
public class Kill extends Action {

    @Override
    public void trigger(Map<String, Sentinel.Tracker> trackers) {
        logger.error("Sentinel detected unrecoverable agents {}, initiating forceful shutdown...", trackers.values());
        CompletableFuture.runAsync(EmissaryServer::stopServerForce);
    }
}
