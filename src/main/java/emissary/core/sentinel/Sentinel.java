package emissary.core.sentinel;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.IMobileAgent;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.core.sentinel.protocols.Protocol;
import emissary.pool.MobileAgentFactory;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Track mobile agents and take action on suspicious behavior
 */
public class Sentinel implements Runnable {

    protected static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public static final String DEFAULT_NAMESPACE_NAME = "Sentinel";

    // key: agent name, value: how long Sentinel has observed the mobile agent
    protected final Map<String, Tracker> trackers = new ConcurrentHashMap<>();

    // protocols contain an action to perform when the set of rule conditions are met
    protected final Set<Protocol> protocols = new LinkedHashSet<>();

    // the default configuration Sentinel.cfg
    protected Configurator config;

    // how many minutes to sleep before checking the mobile agents
    protected long pollingInterval = 5;

    // Loop control
    protected boolean timeToQuit = false;

    // turn on/off sentinel
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
     * Start up the Sentinel thread
     */
    public static void start() {
        new Sentinel();
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
        ThreadUtils.findThreadsByName(DEFAULT_NAMESPACE_NAME).forEach(Thread::interrupt);
    }

    /**
     * Runnable interface where we get to monitor stuff
     */
    @Override
    public void run() {
        logger.info("Sentinel is watching");
        while (!this.timeToQuit) {
            // Delay this loop
            try {
                Thread.sleep(TimeUnit.MINUTES.toMillis(pollingInterval));
                logger.debug("Sentinel is still watching");
                watch();
            } catch (InterruptedException ignore) {
                Thread.currentThread().interrupt();
            } catch (NamespaceException e) {
                logger.error("There was an error in lookup", e);
            }
        }
        Namespace.unbind(DEFAULT_NAMESPACE_NAME);
        logger.info("Sentinel stopped");
    }

    @Override
    public String toString() {
        return "Watching agents with " + protocols;
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
     * Initialize Protocols
     */
    protected void init() {
        this.enabled = config.findBooleanEntry("ENABLED", false);
        if (this.enabled) {
            this.pollingInterval = this.config.findIntEntry("POLLING_INTERVAL_MINUTES", 5);

            logger.trace("Sentinel protocols initializing...");
            for (String protocolConfig : this.config.findEntries("PROTOCOL")) {
                try {
                    Protocol protocol = new Protocol(protocolConfig);
                    if (protocol.isEnabled()) {
                        logger.debug("Sentinel protocol initialized {}", protocol);
                        this.protocols.add(protocol);
                    } else {
                        logger.debug("Sentinel protocol disabled {}", protocol);
                    }
                } catch (Exception e) {
                    logger.warn("Unable to configure Sentinel Protocol[{}]: {}", protocolConfig, e.getMessage());
                }
            }
            if (this.protocols.isEmpty()) {
                logger.warn("Sentinel initialization failed due to no protocols found, disabling");
                this.enabled = false;
            } else {
                logger.info("Sentinel initialized protocols {}", protocols);
            }
        }
    }

    /**
     * Checks to see if the mobile agents are processing the same data since the last polling event
     *
     * @throws NamespaceException if there is a problem looking up resources in the {@link Namespace}
     */
    protected void watch() throws NamespaceException {
        List<String> agentKeys = Namespace.keySet().stream()
                .filter(k -> k.startsWith(MobileAgentFactory.AGENT_NAME))
                .sorted()
                .collect(Collectors.toList());
        for (String agentKey : agentKeys) {
            watch(agentKey);
        }
        protocols.forEach(protocol -> protocol.run(trackers));
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
        Tracker trackedAgent = trackers.computeIfAbsent(mobileAgent.getName(), Tracker::new);
        if (mobileAgent.isInUse()) {
            if (!Objects.equals(mobileAgent.agentID(), trackedAgent.getAgentId())
                    || !Objects.equals(mobileAgent.getLastPlaceProcessed(), trackedAgent.getDirectoryEntryKey())) {
                trackedAgent.clear();
                trackedAgent.setAgentId(mobileAgent.agentID());
                trackedAgent.setDirectoryEntryKey(mobileAgent.getLastPlaceProcessed());
            }
            trackedAgent.incrementTimer(pollingInterval);
            logger.trace("Agent acquired {}", trackedAgent);
        } else {
            trackedAgent.clear();
            logger.trace("Agent not in use [{}]", agentKey);
        }
    }

    public static class Tracker implements Comparable<Tracker> {
        private final String agentName;
        private String agentId;
        private String shortName;
        private String directoryEntryKey;
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
                clear();
            } else {
                this.agentId = agentId;
                if (StringUtils.contains(agentId, "Agent-")) {
                    this.shortName = getShortName(agentId);
                }
            }
        }

        public String getShortName() {
            return shortName;
        }

        public static String getShortName(String agentId) {
            return StringUtils.substringAfter(StringUtils.substringAfter(agentId, "Agent-"), "-");
        }

        public String getDirectoryEntryKey() {
            return directoryEntryKey;
        }

        public void setDirectoryEntryKey(String directoryEntryKey) {
            this.directoryEntryKey = directoryEntryKey;
        }

        public String getPlaceName() {
            return getPlaceName(this.directoryEntryKey);
        }

        public static String getPlaceName(String directoryEntryKey) {
            return StringUtils.defaultString(StringUtils.substringAfterLast(directoryEntryKey, "/"), "");
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
            this.directoryEntryKey = "";
            resetTimer();
        }

        @Override
        public int compareTo(Tracker o) {
            return this.agentName.compareTo(o.agentName);
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", "{", "}")
                    .add("\"agentName\":\"" + agentName + "\"")
                    .add("\"directoryEntry\":\"" + directoryEntryKey + "\"")
                    .add("\"shortName\":\"" + shortName + "\"")
                    .add("\"timeInMinutes\":" + timer)
                    .toString();
        }
    }
}
