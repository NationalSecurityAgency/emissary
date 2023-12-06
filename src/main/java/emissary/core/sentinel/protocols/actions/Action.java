package emissary.core.sentinel.protocols.actions;

import emissary.core.sentinel.Sentinel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class Action {

    protected static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    /**
     * Take action when rule conditions are met
     *
     * @param trackers the listing of agents, places, and filenames that's currently processing
     */
    public abstract void trigger(Map<String, Sentinel.Tracker> trackers);

    public List<Sentinel.Tracker> format(Map<String, Sentinel.Tracker> trackers) {
        return trackers.values().stream().sorted().collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return "\"" + getClass().getSimpleName() + "\"";
    }

}
