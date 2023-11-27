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

public class Protocol {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    protected static final String DEFAULT_RULE = "DEFAULT";
    protected Configurator config;
    protected boolean enabled = false;
    protected Action action;
    protected final Map<String, Rule> rules = new ConcurrentHashMap<>(); // key: place, value: rule
    protected final Map<String, Integer> placeAgentCounts = new ConcurrentHashMap<>(); // key: place, value: number of agents in place

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
        placeAgentCounts.clear();
        for (Sentinel.Tracker tracker : trackers.values()) {
            String placeKey = getPlaceKey(tracker);
            if (StringUtils.isNotBlank(placeKey)) {
                placeAgentCounts.put(placeKey, placeAgentCounts.getOrDefault(placeKey, 0) + 1);
            }
        }
        logger.debug("Checking agents {}", placeAgentCounts);
        for (Map.Entry<String, Integer> item : placeAgentCounts.entrySet()) {
            Rule rule = rules.getOrDefault(item.getKey(), rules.get(DEFAULT_RULE));
            logger.trace("Found {} for {}", rule, item.getKey());
            check(trackers, item.getKey(), item.getValue());
        }
    }

    public String getPlaceKey(Sentinel.Tracker tracker) {
        return getPlaceSimpleName(tracker.getPlaceName());
    }

    public static String getPlaceSimpleName(String place) {
        return StringUtils.substringAfterLast(place, "/");
    }

    protected void configure(String conf) {
        try {
            this.config = ConfigUtil.getConfigInfo(conf);
            init();
        } catch (IOException e) {
            logger.warn("Cannot read " + conf + ", skipping");
        }
    }

    protected void init() {
        this.enabled = config.findBooleanEntry("ENABLED", false);
        if (enabled) {
            logger.trace("Loading rules...");
            for (String ruleId : config.findEntries("RULE_ID")) {
                try {
                    validate(ruleId);
                    Map<String, String> map = config.findStringMatchMap(ruleId + "_");
                    String rule = map.getOrDefault("RULE", AllMaxTime.class.getName());
                    Rule ruleImpl = (Rule) Factory.create(rule, ruleId, map.get("TIME_LIMIT_MINUTES"), map.get("THRESHOLD"));
                    logger.debug("Sentinel loaded {}", ruleImpl);
                    this.rules.put(ruleId, ruleImpl);
                } catch (Exception e) {
                    logger.warn("Unable to configure Sentinel for {}: {}", ruleId, e.getMessage());
                }
            }

            // if no rules then disable protocol
            if (this.rules.isEmpty()) {
                this.enabled = false;
                return;
            }

            String action = config.findStringEntry("ACTION", Notify.class.getName());
            this.action = (Action) Factory.create(action);
        }
    }

    protected void validate(String place) throws NamespaceException {
        // validate that the place exists
        if (!DEFAULT_RULE.equalsIgnoreCase(place)) {
            DirectoryPlace directoryPlace = Namespace.lookup(DirectoryPlace.class).iterator().next();
            if (directoryPlace.getEntries().stream()
                    .noneMatch(entry -> place.equalsIgnoreCase(KeyManipulator.getServiceClassname(entry.getFullKey())))) {
                throw new IllegalStateException("Place not found in the directory");
            }
        }
    }

    protected void check(Map<String, Sentinel.Tracker> tracker, String placeSimpleName, Integer count) {
        if (rules.values().stream().allMatch(rule -> rule.condition(tracker, placeSimpleName, count))) {
            action.trigger(tracker, placeSimpleName, count);
        }
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Protocol.class.getSimpleName() + "[", "]")
                .add("rules=" + rules)
                .add("action=" + action)
                .toString();
    }
}
