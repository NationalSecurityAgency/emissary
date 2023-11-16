package emissary.core.sentinel.rules;

import emissary.core.sentinel.Sentinel;

import java.util.Map;

public class Exit extends Rule {

    public Exit(String place, long timeLimit, double threshold) {
        super(place, timeLimit, threshold);
    }

    public Exit(String place, String timeLimit, String threshold) {
        super(place, timeLimit, threshold);
    }

    /**
     * Try to terminate the JVM
     *
     * @param trackers the listing of agents, places, and filenames that's currently processing
     * @param placeSimpleName the place name currently processing on one or more mobile agents
     * @param count number of mobile agents stuck on the place
     */
    @Override
    public void action(Map<String, Sentinel.Tracker> trackers, String placeSimpleName, Integer count) {
        logger.error("Sentinel detected {} unrecoverable agent(s) running [{}], exiting now!!", count, placeSimpleName);
        logger.debug("{}", trackers);
        System.exit(1);
    }
}
