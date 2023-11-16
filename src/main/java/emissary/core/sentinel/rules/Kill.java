package emissary.core.sentinel.rules;

import emissary.core.sentinel.Sentinel;
import emissary.server.EmissaryServer;

import java.util.Map;

public class Kill extends Rule {
    public Kill(String place, long timeLimit, double threshold) {
        super(place, timeLimit, threshold);
    }

    public Kill(String place, String timeLimit, String threshold) {
        super(place, timeLimit, threshold);
    }

    /**
     * Force a shutdown of the system
     *
     * @param trackers the listing of agents, places, and filenames that's currently processing
     * @param placeSimpleName the place name currently processing on one or more mobile agents
     * @param count number of mobile agents stuck on the place
     */
    @Override
    public void action(Map<String, Sentinel.Tracker> trackers, String placeSimpleName, Integer count) {
        logger.error("Sentinel detected {} unrecoverable agent(s) running [{}], initiating forceful shutdown...", count, placeSimpleName);
        logger.debug("{}", trackers);
        EmissaryServer.stopServerForce();
    }
}
