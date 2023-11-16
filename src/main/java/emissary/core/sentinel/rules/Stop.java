package emissary.core.sentinel.rules;

import emissary.core.sentinel.Sentinel;
import emissary.server.EmissaryServer;

import java.util.Map;

public class Stop extends Rule {
    public Stop(String place, long timeLimit, double threshold) {
        super(place, timeLimit, threshold);
    }

    public Stop(String place, String timeLimit, String threshold) {
        super(place, timeLimit, threshold);
    }

    @Override
    public void action(Map<String, Sentinel.Tracker> tracker, String placeSimpleName, Integer counter) {
        logger.error("Sentinel detected unrecoverable agent(s) running [{}], initiating graceful shut down...", placeSimpleName);
        EmissaryServer.stopServer();
    }
}
