package emissary.core.sentinel.rules;

import emissary.core.sentinel.Sentinel;

import java.util.Map;

public class Recover extends Rule {
    public Recover(String place, long timeLimit, double threshold) {
        super(place, timeLimit, threshold);
    }

    public Recover(String place, String timeLimit, String threshold) {
        super(place, timeLimit, threshold);
    }

    @Override
    public void action(Map<String, Sentinel.Tracker> tracker, String placeSimpleName, Integer counter) {
        logger.warn("Sentinel attempting recovery mode...");
        throw new UnsupportedOperationException("Recovery unavailable");
    }
}
