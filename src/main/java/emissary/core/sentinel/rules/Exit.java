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

    @Override
    public void action(Map<String, Sentinel.Tracker> tracker, String placeSimpleName, Integer counter) {
        logger.error("Sentinel detected unrecoverable agent(s) running [{}], exiting now!!", placeSimpleName);
        System.exit(1);
    }
}
