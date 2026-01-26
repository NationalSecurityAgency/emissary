package emissary.core.sentinel.protocols.actions;

import emissary.core.sentinel.protocols.trackers.AgentTracker;
import emissary.core.sentinel.protocols.trackers.Tracker;

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
     * @param trackers the listing of agents, places, filenames, etc. that's currently processing
     */
    public abstract void trigger(Map<String, Tracker> trackers);

    public List<Tracker> format(Map<String, Tracker> trackers) {
        return trackers.values().stream().sorted().collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return "\"" + getClass().getSimpleName() + "\"";
    }

    protected List<String> getAgentNames(Map<String, Tracker> trackers) {
        return trackers.values().stream()
                .filter(AgentTracker.class::isInstance)
                .map(t -> ((AgentTracker) t).getAgentName())
                .sorted()
                .collect(Collectors.toList());
    }
}
