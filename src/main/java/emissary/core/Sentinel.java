package emissary.core;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.pool.AgentPool;
import emissary.pool.MobileAgentFactory;
import emissary.server.EmissaryServer;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static emissary.core.MobileAgent.NO_AGENT_ID;
import static emissary.core.Sentinel.Action.NOTIFY;

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

    /**
     * Create a Sentinel - set it running and bind into the {@link Namespace}
     */
    public Sentinel() {
        configure();
        final Thread thread = new Thread(this, DEFAULT_NAMESPACE_NAME);
        thread.setPriority(Thread.NORM_PRIORITY);
        thread.setDaemon(true);
        Namespace.bind(DEFAULT_NAMESPACE_NAME, this);
        thread.start();
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

        if (!this.rules.containsKey(DEFAULT_RULE)) {
            this.rules.put(DEFAULT_RULE, new Rule(DEFAULT_RULE, 60L, 1.0, NOTIFY));
        }
    }

    /**
     * Initialize rule set
     */
    protected void init() {
        for (String rule : config.findEntries("RULE")) {
            try {
                Map<String, String> map = config.findStringMatchMap(rule + "_");
                this.rules.put(rule, new Rule(rule, map.get("TIME_LIMIT"), map.get("THRESHOLD"), map.get("ACTION")));
            } catch (Exception e) {
                logger.warn("Unable to configure Sentinel for: {}", rule);
            }
        }
        this.pollingInterval = config.findIntEntry("POLLING_INTERVAL", 5);
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
        } else {
            trackedAgent.clear();
        }
        logger.debug("{}", trackedAgent);
    }

    /**
     * Run the configured rules over the watched mobile-agents
     *
     * @throws NamespaceException if there is a problem looking up resources in the {@link Namespace}
     */
    protected void check() throws NamespaceException {
        logger.debug("MobileAgents in {}", counter);
        for (Map.Entry<String, Integer> item : counter.entrySet()) {
            Rule rule = rules.getOrDefault(item.getKey(), rules.get(DEFAULT_RULE));
            logger.debug("Found {} for {}", rule, item.getKey());
            rule.run(item.getKey(), item.getValue());
        }
    }

    protected static String getPlaceSimpleName(String lastPlaceProcessed) {
        return StringUtils.substringAfterLast(lastPlaceProcessed, "/");
    }

    static class Tracker {
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
            if (StringUtils.contains(agentId, NO_AGENT_ID)) {
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

    enum Action {
        NOTIFY, RECOVER, STOP, KILL, EXIT
    }

    class Rule {

        private final String place;

        // how long to wait before alerting on stuck agents
        private final long timeLimit;

        // percentage of mobile agents that are stuck on the same place before sounding the alarm
        private final double threshold;

        // what to do in the case of rule hitting limits
        private final Action action;

        public Rule(String place, long timeLimit, double threshold, Action action) {
            this.place = place;
            this.timeLimit = timeLimit;
            this.threshold = threshold;
            this.action = action;
        }

        public Rule(String place, String timeLimit, String threshold, String action) {
            this(place, StringUtils.isBlank(timeLimit) ? 60L : Long.parseLong(timeLimit),
                    StringUtils.isBlank(threshold) ? 1.0 : Double.parseDouble(threshold),
                    StringUtils.isBlank(threshold) ? NOTIFY : Action.valueOf(action.toUpperCase()));
        }

        public String getPlace() {
            return place;
        }

        public long getTimeLimit() {
            return timeLimit;
        }

        public double getThreshold() {
            return threshold;
        }

        public Action getAction() {
            return action;
        }

        public void run(String placeSimpleName, Integer counter) throws NamespaceException {
            if (overThreshold(counter) && overTimeLimit(placeSimpleName)) {
                if (action == Action.STOP) {
                    logger.error("Sentinel detected unrecoverable agent(s) running [{}], initiating graceful shut down...", placeSimpleName);
                    EmissaryServer.stopServer();
                } else if (action == Action.KILL) {
                    logger.error("Sentinel detected unrecoverable agent(s) running [{}], initiating forceful shutdown...", placeSimpleName);
                    EmissaryServer.stopServerForce();
                } else if (action == Action.EXIT) {
                    logger.error("Sentinel detected unrecoverable agent(s) running [{}], exiting now!!", placeSimpleName);
                    System.exit(1);
                } else if (action == Action.RECOVER) {
                    logger.warn("Sentinel attempting recovery mode...");
                    throw new UnsupportedOperationException("Recovery unavailable");
                } else {
                    logger.warn("Sentinel detected locked agent(s) running [{}]", placeSimpleName);
                }
            }
        }

        private boolean overThreshold(Integer counter) throws NamespaceException {
            int poolSize = AgentPool.lookup().getCurrentPoolSize();
            logger.debug("Testing threshold for place={}, counter={}, poolSize={}, threshold={}", getPlace(), counter, poolSize,
                    getThreshold());
            return (double) counter / poolSize >= getThreshold();
        }

        private boolean overTimeLimit(String placeSimpleName) {
            long maxTimeInPlace = tracker.values().stream()
                    .filter(t -> StringUtils.equalsIgnoreCase(t.getPlaceSimpleName(), placeSimpleName))
                    .map(Tracker::getTimer)
                    .max(Comparator.naturalOrder()).orElse(0L);
            logger.debug("Testing time limit for place={}, timeLimit={}, maxTimeInPlace={}", getPlace(), maxTimeInPlace, getTimeLimit());
            return maxTimeInPlace >= getTimeLimit();
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", "Rule:" + place + "[", "]")
                    .add("timeLimit=" + timeLimit)
                    .add("threshold=" + threshold)
                    .toString();
        }
    }
}
