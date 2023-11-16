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

    @Override
    public void action(Map<String, Sentinel.Tracker> tracker, String placeSimpleName, Integer counter) {
        logger.warn("Sentinel detected locked agent(s) running [{}]", placeSimpleName);
    }
}
