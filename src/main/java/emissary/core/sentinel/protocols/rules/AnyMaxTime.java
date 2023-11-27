package emissary.core.sentinel.protocols.rules;

import emissary.core.sentinel.Sentinel;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Map;

/**
 * Looks at the place that has been running for the most amount of time.
 */
public class AnyMaxTime extends Rule {

    protected static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public AnyMaxTime(String place, long timeLimit, double threshold) {
        super(place, timeLimit, threshold);
    }

    public AnyMaxTime(String place, String timeLimit, String threshold) {
        super(place, timeLimit, threshold);
    }

    protected boolean overTimeLimit(Map<String, Sentinel.Tracker> trackers, String placeSimpleName) {
        return trackers.values().stream()
                .filter(t -> StringUtils.equalsIgnoreCase(t.getPlaceSimpleName(), placeSimpleName))
                .anyMatch(tracker -> tracker.getTimer() >= this.timeLimit);
    }

}
