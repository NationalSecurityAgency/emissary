package emissary.core.sentinel.protocols;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.Factory;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.core.sentinel.Sentinel;
import emissary.core.sentinel.protocols.actions.Action;
import emissary.core.sentinel.protocols.actions.Notify;
import emissary.core.sentinel.protocols.rules.AllMaxTime;
import emissary.core.sentinel.protocols.rules.Rule;
import emissary.directory.DirectoryPlace;
import emissary.directory.KeyManipulator;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

import static emissary.core.sentinel.Sentinel.Tracker.getPlaceName;

/**
 * This protocol buckets places that are running in mobile agents and then looks at max and min time in place and the
 * number of agents that are potentially "stuck." After places are bucketed, the place stats are run against the
 * configured rules to determine if all conditions are met. Once all rule conditions are met, then the configured action
 * will be triggered, i.e. log/notify.
 */
public class Protocol {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    protected Configurator config;
    protected boolean enabled = false;
    protected final Map<String, Rule> rules = new ConcurrentHashMap<>();
    protected Action action;

    Protocol() {}

    public Protocol(String conf) {
        configure(conf);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Run the configured rules over the watched mobile-agents
     */
    public void run(Map<String, Sentinel.Tracker> trackers) {

        Map<String, PlaceAgentStats> placeAgentStats = generatePlaceAgentStats(trackers);
        if (!placeAgentStats.isEmpty()) {
            logger.debug("Running rules on agents {}", placeAgentStats);
            if (rules.values().stream().allMatch(rule -> rule.condition(placeAgentStats.values()))) {
                logger.warn("Sentinel rules matched -- {}", rules.values());
                action.trigger(trackers);
            }
        }
    }

    /**
     * Get the place key, i.e. the simple name
     *
     * @param tracker agents, places, and filenames that's currently processing
     * @return the place key
     */
    public String getPlaceKey(Sentinel.Tracker tracker) {
        return getPlaceName(tracker.getServiceKey());
    }

    /**
     * Get the Configurator
     *
     * @param conf the location of the configuration file
     */
    protected void configure(String conf) {
        try {
            this.config = ConfigUtil.getConfigInfo(conf);
            init(this.config);
        } catch (IOException e) {
            logger.warn("Cannot read {}, skipping!!", conf);
        }
    }

    /**
     * Initialize rule set and action
     */
    protected void init(Configurator config) {
        this.enabled = config.findBooleanEntry("ENABLED", false);
        if (enabled) {

            this.action = (Action) Factory.create(config.findStringEntry("ACTION", Notify.class.getName()));

            logger.trace("Loading rules...");
            for (String ruleId : config.findEntries("RULE_ID")) {
                try {
                    if (this.rules.containsKey(ruleId)) {
                        logger.warn("Sentinel rule with ID[{}] already exists, this may result in unexpected behavior", ruleId);
                    }
                    Map<String, String> map = config.findStringMatchMap(ruleId + "_");
                    String rule = map.getOrDefault("RULE", AllMaxTime.class.getName());
                    Rule ruleImpl = (Rule) Factory.create(rule, validate(map.get("PLACE_MATCHER")), map.get("TIME_LIMIT_MINUTES"),
                            map.get("PLACE_THRESHOLD"));
                    logger.debug("Sentinel loaded rule[{}] - {}", ruleId, ruleImpl);
                    this.rules.put(ruleId, ruleImpl);
                } catch (Exception e) {
                    logger.warn("Sentinel rule[{}] is invalid: {}", ruleId, e.getMessage());
                }
            }

            // if no rules then disable protocol
            if (this.rules.isEmpty()) {
                this.enabled = false;
            }
        }
    }

    /**
     * Validate the place exists in the {@link Namespace}
     *
     * @param place the name of the place
     * @throws NamespaceException if the directory place does not exist
     * @throws IllegalStateException if the place cannot be found
     */
    protected String validate(String place) throws NamespaceException {
        // validate that the place exists
        DirectoryPlace directoryPlace = Namespace.lookup(DirectoryPlace.class).iterator().next();
        if (directoryPlace.getEntries().stream()
                .noneMatch(entry -> KeyManipulator.getServiceClassname(entry.getFullKey()).matches(place))) {
            throw new IllegalStateException("Place not found in the directory");
        }
        return place;
    }

    protected Map<String, PlaceAgentStats> generatePlaceAgentStats(Map<String, Sentinel.Tracker> trackers) {
        Map<String, PlaceAgentStats> placeAgentStats = new ConcurrentHashMap<>();
        for (Sentinel.Tracker tracker : trackers.values()) {
            String placeKey = getPlaceKey(tracker);
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
