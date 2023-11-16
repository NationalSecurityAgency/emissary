package emissary.core.sentinel;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.Factory;
import emissary.core.IMobileAgent;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.core.sentinel.rules.Notify;
import emissary.core.sentinel.rules.Rule;
import emissary.pool.MobileAgentFactory;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Track mobile agents and log any suspicious behavior
 */
public class Sentinel implements Runnable {

    protected static final Logger logger = LoggerFactory.getLogger(Sentinel.class);

    public static final String DEFAULT_NAMESPACE_NAME = "Sentinel";

    protected static final String DEFAULT_RULE = "DEFAULT";

    // key: place, value: rule (time limits, thresholds)
    final Map<String, Rule> rules = new ConcurrentHashMap<>();

    // key: agent name, value: how long Sentinel has observed the mobile agent
    final Map<String, Tracker> tracker = new ConcurrentHashMap<>();

    // key: place simple name, value: number of agents in place
    final Map<String, Integer> counter = new ConcurrentHashMap<>();

    Configurator config;

    // how many minutes to sleep before checking the mobile agents
    protected long pollingInterval = 5;

    // Loop control
    protected boolean timeToQuit = false;

    protected boolean enabled = false;

    /**
     * Create a Sentinel - set it running and bind into the {@link Namespace}
     */
    public Sentinel() {
        configure();
        if (this.enabled) {
            final Thread thread = new Thread(this, DEFAULT_NAMESPACE_NAME);
            thread.setPriority(Thread.NORM_PRIORITY);
            thread.setDaemon(true);
            Namespace.bind(DEFAULT_NAMESPACE_NAME, this);
            thread.start();
        } else {
            logger.info("Sentinel is disabled");
        }
    }

    /**
     * Lookup the default Sentinel in the {@link Namespace}
     *
     * @return The registered Sentinel
     */
    public static Sentinel lookup() throws NamespaceException {
        return (Sentinel) Namespace.lookup(DEFAULT_NAMESPACE_NAME);
    }

    /**
     * Safely stop the monitoring Thread
     */
    public void quit() {
        logger.info("Stopping Sentinel...");
        this.timeToQuit = true;
    }

    /**
     * Runnable interface where we get to monitor stuff
     */
    @Override
    public void run() {
        logger.debug("Sentinel is starting");

        while (!this.timeToQuit) {
            // Delay this loop
            try {
                Thread.sleep(TimeUnit.MINUTES.toMillis(pollingInterval));
                watch();
            } catch (InterruptedException ignore) {
                Thread.currentThread().interrupt();
            } catch (NamespaceException e) {
                logger.error("There was an error in lookup", e);
            }
        }
        Namespace.unbind(DEFAULT_NAMESPACE_NAME);
        logger.info("Sentinel stopped.");
    }

    @Override
    public String toString() {
        return "Watching agents with " + rules.values();
    }

    /**
     * Get the Configurator
     */
    protected void configure() {
        try {
            this.config = ConfigUtil.getConfigInfo(Sentinel.class);
            init();
        } catch (IOException e) {
            logger.warn("Cannot read Sentinel.cfg, taking default values");
        }
    }

    /**
     * Initialize rule set
     */
    protected void init() {
        this.enabled = config.findBooleanEntry("ENABLED", false);
        if (this.enabled) {
            this.pollingInterval = config.findIntEntry("POLLING_INTERVAL", 5);

            logger.trace("Loading rules...");
            for (String ruleId : config.findEntries("RULE_ID")) {
                try {
                    Map<String, String> map = config.findStringMatchMap(ruleId + "_");
                    String rule = map.getOrDefault("RULE", Notify.class.getName());
                    Rule ruleImpl = (Rule) Factory.create(rule, ruleId, map.get("TIME_LIMIT"), map.get("THRESHOLD"));
                    this.rules.put(ruleId, ruleImpl);
                } catch (Exception e) {
                    logger.warn("Unable to configure Sentinel for: {}", ruleId);
                }
            }

            // if no rules, create a default one
            if (!this.rules.containsKey(DEFAULT_RULE)) {
                logger.warn("Default rule not found, creating one...");
                this.rules.put(DEFAULT_RULE, new Notify(DEFAULT_RULE, 60L, 1.0));
            }
        }
    }

    /**
     * Checks to see if the mobile agents are processing the same data since the last polling event
     *
     * @throws NamespaceException if there is a problem looking up resources in the {@link Namespace}
     */
    protected void watch() throws NamespaceException {
        counter.clear();
        List<String> agentKeys =
                Namespace.keySet().stream().filter(k -> k.startsWith(MobileAgentFactory.AGENT_NAME)).sorted().collect(Collectors.toList());
        for (String agentKey : agentKeys) {
            watch(agentKey);
        }
        check();
    }

    /**
     * Checks to see if the mobile agent is processing the same data since the last polling event
     *
     * @param agentKey the agent key, i.e. MobileAgent-01
     * @throws NamespaceException if there is a problem looking up resources in the {@link Namespace}
     */
    protected void watch(String agentKey) throws NamespaceException {
        logger.trace("Searching for agent [{}]", agentKey);
        IMobileAgent mobileAgent = (IMobileAgent) Namespace.lookup(agentKey);
        Tracker trackedAgent = tracker.computeIfAbsent(mobileAgent.getName(), Tracker::new);
        if (mobileAgent.isInUse()) {
            if (!Objects.equals(mobileAgent.getLastPlaceProcessed(), trackedAgent.getPlaceName())
                    && !Objects.equals(mobileAgent.agentID(), trackedAgent.getAgentId())) {
                trackedAgent.setAgentId(mobileAgent.agentID());
                trackedAgent.setPlaceName(mobileAgent.getLastPlaceProcessed());
                trackedAgent.resetTimer();
            }
            trackedAgent.incrementTimer(pollingInterval);
            String placeSimpleName = getPlaceSimpleName(mobileAgent.getLastPlaceProcessed());
            counter.put(placeSimpleName, counter.getOrDefault(placeSimpleName, 0) + 1);
            logger.debug("Agent acquired {}", trackedAgent);
        } else {
            trackedAgent.clear();
            logger.debug("Agent not in use [{}]", agentKey);
        }
    }

    /**
     * Run the configured rules over the watched mobile-agents
     *
     * @throws NamespaceException if there is a problem looking up resources in the {@link Namespace}
     */
    protected void check() throws NamespaceException {
        logger.debug("Checking agents {}", counter);
        for (Map.Entry<String, Integer> item : counter.entrySet()) {
            Rule rule = rules.getOrDefault(item.getKey(), rules.get(DEFAULT_RULE));
            logger.trace("Found {} for {}", rule, item.getKey());
            rule.run(tracker, item.getKey(), item.getValue());
        }
    }

    protected static String getPlaceSimpleName(String lastPlaceProcessed) {
        return StringUtils.substringAfterLast(lastPlaceProcessed, "/");
    }

    public static class Tracker {
        private final String agentName;
        private String agentId;
        private String shortName;
        private String placeName;
        private long timer = -1;

        public Tracker(String agentName) {
            this.agentName = agentName;
        }

        public String getAgentName() {
            return agentName;
        }

        public String getAgentId() {
            return agentId;
        }

        public void setAgentId(String agentId) {
            if (StringUtils.contains(agentId, "No_AgentID_Set")) {
                this.agentId = "";
                this.shortName = "";
            } else {
                this.agentId = agentId;
                if (StringUtils.contains(agentId, "Agent-")) {
                    this.shortName = StringUtils.substringAfter(StringUtils.substringAfter(agentId, "Agent-"), "-");
                }
            }
        }

        public String getShortName() {
            return shortName;
        }

        public String getPlaceName() {
            return placeName;
        }

        public void setPlaceName(String placeName) {
            this.placeName = placeName;
        }

        public String getPlaceSimpleName() {
            return Sentinel.getPlaceSimpleName(this.placeName);
        }

        public long getTimer() {
            return timer;
        }

        public void resetTimer() {
            this.timer = -1;
        }

        public void incrementTimer(long time) {
            if (this.timer == -1) {
                this.timer = 0;
            } else {
                this.timer += time;
            }
        }

        public void clear() {
            this.agentId = "";
            this.shortName = "";
            this.placeName = "";
            resetTimer();
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", Tracker.class.getSimpleName() + "[", "]")
                    .add("agentName='" + agentName + "'")
                    .add("agentId='" + agentId + "'")
                    .add("shortName='" + shortName + "'")
                    .add("placeName='" + placeName + "'")
                    .add("timer=" + timer)
                    .toString();
        }
    }
}
