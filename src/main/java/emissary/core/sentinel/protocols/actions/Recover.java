package emissary.core.sentinel.protocols.actions;

import emissary.core.IMobileAgent;
import emissary.core.Namespace;
import emissary.core.sentinel.Sentinel;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Recover extends Action {

    /**
     * Attempts to recover the mobile agents by interrupting the thread
     *
     * @param tracker the listing of agents, places, and filenames that's currently processing
     * @param placeSimpleName the place name currently processing on one or more mobile agents
     * @param counter number of mobile agents stuck on the place
     */
    @Override
    public void trigger(Map<String, Sentinel.Tracker> tracker, String placeSimpleName, Integer counter) {
        logger.warn("Sentinel detected {} locked agent(s) running [{}], attempting recovery...", counter, placeSimpleName);
        List<String> agentNames = tracker.values().stream()
                .filter(t -> t.getPlaceSimpleName().equalsIgnoreCase(placeSimpleName))
                .map(Sentinel.Tracker::getAgentName)
                .collect(Collectors.toList());

        for (String agentName : agentNames) {
            try {
                IMobileAgent mobileAgent = (IMobileAgent) Namespace.lookup(agentName);
                logger.warn("Sentinel attempting recovery for {}", agentName);
                mobileAgent.interrupt();
            } catch (Exception e) {
                throw new IllegalStateException("Recovery unavailable");
            }
        }
    }
}
