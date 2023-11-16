package emissary.core.sentinel.rules;

import emissary.core.sentinel.Sentinel;

import java.util.Map;

public class Notify extends Rule {
    public Notify(String place, long timeLimit, double threshold) {
        super(place, timeLimit, threshold);
    }

    public Notify(String place, String timeLimit, String threshold) {
        super(place, timeLimit, threshold);
    }

    /**
     * Log the problem agents/threads
     *
     * @param tracker the listing of agents, places, and filenames that's currently processing
     * @param placeSimpleName the place name currently processing on one or more mobile agents
     * @param counter number of mobile agents stuck on the place
     */
    @Override
    public void action(Map<String, Sentinel.Tracker> tracker, String placeSimpleName, Integer counter) {
        logger.warn("Sentinel detected locked agent(s) running [{}]", placeSimpleName);
    }
}
