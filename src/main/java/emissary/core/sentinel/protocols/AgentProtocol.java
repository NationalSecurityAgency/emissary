package emissary.core.sentinel.protocols;

import emissary.config.Configurator;
import emissary.core.Factory;
import emissary.core.IMobileAgent;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.core.sentinel.Sentinel;
import emissary.core.sentinel.protocols.rules.AllMaxTime;
import emissary.core.sentinel.protocols.rules.Rule;
import emissary.core.sentinel.protocols.trackers.AgentTracker;
import emissary.directory.DirectoryPlace;
import emissary.directory.KeyManipulator;
import emissary.pool.MobileAgentFactory;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * This protocol buckets places that are running in mobile agents and then looks at max and min time in place and the
 * number of agents that are potentially "stuck." After places are bucketed, the place stats are run against the
 * configured rules to determine if all conditions are met. Once all rule conditions are met, then the configured action
 * will be triggered, i.e. log/notify.
 */
public class AgentProtocol extends Protocol {

    // key: agent name, value: how long Sentinel has observed the mobile agent
    protected final Map<String, AgentTracker> trackers = new ConcurrentHashMap<>();

    public AgentProtocol() {}

    public AgentProtocol(Configurator config) {
        super(config);
    }

    @Override
    protected Rule<PlaceAgentStats> getRule(final String ruleId) throws IOException {
        try {
            Map<String, String> map = config.findStringMatchMap(ruleId + "_");
            String rule = map.getOrDefault("RULE", AllMaxTime.class.getName());
            return (Rule) Factory.create(rule, ruleId, validate(map.get("PLACE_MATCHER")), map.get("TIME_LIMIT_MINUTES"),
                    map.get("PLACE_THRESHOLD"));
        } catch (NamespaceException e) {
            throw new IOException(e);
        }
    }

    /**
     * Validate the place exists in the {@link Namespace}
     *
     * @param place the name of the place
     * @throws NamespaceException if the directory place does not exist
     * @throws IllegalStateException if the place cannot be found
     */
    protected String validate(final String place) throws NamespaceException {
        // validate that the place exists
        DirectoryPlace directoryPlace = Namespace.lookup(DirectoryPlace.class).iterator().next();
        if (directoryPlace.getEntries().stream()
                .noneMatch(entry -> KeyManipulator.getServiceClassname(entry.getFullKey()).matches(place))) {
            throw new IllegalStateException("Place not found in the directory");
        }
        return place;
    }

    /**
     * Checks to see if the mobile agents are processing the same data since the last polling event
     *
     * @throws IOException if there is a problem looking up resources in the {@link Namespace}
     */
    @Override
    public void run() throws IOException {
        try {
            List<String> agentKeys = Namespace.keySet().stream()
                    .filter(k -> k.startsWith(MobileAgentFactory.AGENT_NAME))
                    .sorted()
                    .collect(Collectors.toList());
            for (String agentKey : agentKeys) {
                updateTracker(agentKey);
            }
            runRules(trackers);
        } catch (NamespaceException e) {
            throw new IOException("There was an issue running protocol", e);
        }
    }

    /**
     * Checks to see if the mobile agent is processing the same data since the last polling event
     *
     * @param agentKey the agent key, i.e. MobileAgent-01
     * @throws NamespaceException if there is a problem looking up resources in the {@link Namespace}
     */
    protected void updateTracker(final String agentKey) throws NamespaceException {
        logger.trace("Searching for agent [{}]", agentKey);
        IMobileAgent mobileAgent = (IMobileAgent) Namespace.lookup(agentKey);
        AgentTracker trackedAgent = trackers.computeIfAbsent(mobileAgent.getName(), AgentTracker::new);
        if (mobileAgent.isInUse()) {
            if (!Objects.equals(mobileAgent.agentId(), trackedAgent.getAgentId())
                    || !Objects.equals(mobileAgent.getLastPlaceProcessed(), trackedAgent.getDirectoryEntryKey())) {
                trackedAgent.clear();
                trackedAgent.setAgentId(mobileAgent.agentId());
                trackedAgent.setDirectoryEntryKey(mobileAgent.getLastPlaceProcessed());
            }
            trackedAgent.incrementTimer(Sentinel.lookup().getPollingInterval());
            logger.trace("Agent acquired {}", trackedAgent);
        } else {
            trackedAgent.clear();
            logger.trace("Agent not in use [{}]", agentKey);
        }
    }

    /**
     * Run the configured rules over the watched mobile-agents
     */
    public void runRules(Map<String, AgentTracker> trackers) {

        Map<String, PlaceAgentStats> placeAgentStats = generatePlaceAgentStats(trackers);
        if (!placeAgentStats.isEmpty()) {
            logger.debug("Running rules on agents {}", placeAgentStats);
            if (rules.values().stream().allMatch(rule -> rule.condition(placeAgentStats.values()))) {
                logger.warn("Sentinel rules matched -- {}", rules.values());
                action.trigger((Map) trackers);
            }
        }
    }

    protected Map<String, PlaceAgentStats> generatePlaceAgentStats(Map<String, AgentTracker> trackers) {
        Map<String, PlaceAgentStats> placeAgentStats = new ConcurrentHashMap<>();
        for (AgentTracker tracker : trackers.values()) {
            String placeKey = tracker.getPlaceName();
            if (StringUtils.isNotBlank(placeKey)) {
                placeAgentStats.put(placeKey, placeAgentStats.getOrDefault(placeKey, new PlaceAgentStats(placeKey)).update(tracker.getTimer()));
            }
        }
        return placeAgentStats;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", "{", "}")
                .add("\"rules\":" + rules.values())
                .add("\"action\":" + action)
                .toString();
    }

    public static class PlaceAgentStats {

        private final String place;
        private int count;
        private long maxTimeInPlace = -1;
        private long minTimeInPlace = -1;

        public PlaceAgentStats(String place) {
            this.place = place;
        }

        public String getPlace() {
            return place;
        }

        public int getCount() {
            return count;
        }

        public long getMaxTimeInPlace() {
            return maxTimeInPlace;
        }

        public long getMinTimeInPlace() {
            return minTimeInPlace;
        }

        public PlaceAgentStats update(long timer) {
            this.count++;
            this.minTimeInPlace = this.minTimeInPlace < 0 ? timer : Math.min(this.minTimeInPlace, timer);
            this.maxTimeInPlace = Math.max(this.maxTimeInPlace, timer);
            return this;
        }
    }

}
