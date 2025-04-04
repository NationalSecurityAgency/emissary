package emissary.core.sentinel.protocols.actions;

import emissary.core.IMobileAgent;
import emissary.core.Namespace;
import emissary.core.sentinel.protocols.trackers.AgentTracker;
import emissary.core.sentinel.protocols.trackers.Tracker;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Attempts to recover the mobile agents by interrupting the thread
 */
public class Recover extends Action {

    @Override
    public void trigger(Map<String, Tracker> tracker) {

        logger.warn("Sentinel detected locked agents, attempting recovery...");
        List<String> agentNames = tracker.values().stream()
                .filter(AgentTracker.class::isInstance)
                .map(t -> ((AgentTracker) t).getAgentName())
                .sorted()
                .collect(Collectors.toList());

        for (String agentName : agentNames) {
            try {
                IMobileAgent mobileAgent = (IMobileAgent) Namespace.lookup(agentName);
                logger.warn("Sentinel attempting recovery for {}", agentName);
                mobileAgent.interrupt();
            } catch (Exception e) {
                throw new IllegalStateException("Recovery unavailable", e);
            }
        }
    }

}
