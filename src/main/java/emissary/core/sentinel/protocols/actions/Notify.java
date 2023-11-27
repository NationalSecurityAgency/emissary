package emissary.core.sentinel.protocols.actions;

import emissary.core.sentinel.Sentinel;

import java.util.Map;

public class Notify extends Action {

    /**
     * Log the problem agents/threads
     *
     * @param trackers the listing of agents, places, and filenames that's currently processing
     * @param placeSimpleName the place name currently processing on one or more mobile agents
     * @param count number of mobile agents stuck on the place
     */
    @Override
    public void trigger(Map<String, Sentinel.Tracker> trackers, String placeSimpleName, Integer count) {
        logger.warn("Sentinel detected {} locked agent(s) running [{}]", count, placeSimpleName);
        logger.debug("{}", trackers);
    }
}
