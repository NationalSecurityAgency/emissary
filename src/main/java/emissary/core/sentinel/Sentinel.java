package emissary.core.sentinel;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.core.sentinel.protocols.Protocol;
import emissary.core.sentinel.protocols.ProtocolFactory;

import org.apache.commons.lang3.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Track mobile agents and take action on suspicious behavior
 */
public class Sentinel implements Runnable {

    protected static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public static final String DEFAULT_NAMESPACE_NAME = "Sentinel";

    // protocols contain an action to perform when the set of rule conditions are met
    protected final Set<Protocol> protocols = new LinkedHashSet<>();

    // the default configuration Sentinel.cfg
    protected Configurator config;

    // how many minutes to sleep before checking protocols
    protected long pollingInterval = 5;

    // Loop control
    protected boolean timeToQuit = false;

    // turn on/off sentinel
    protected boolean enabled = false;

    /**
     * Create a Sentinel - set it running and bind into the {@link Namespace}
     */
    @SuppressWarnings("ThreadPriorityCheck")
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
     * Get the currently configured polling interval
     *
     * @return the currently configured polling interval
     */
    public long getPollingInterval() {
        return pollingInterval;
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
                protocols.forEach(this::run);
            } catch (InterruptedException ignore) {
                Thread.currentThread().interrupt();
            }
        }
        Namespace.unbind(DEFAULT_NAMESPACE_NAME);
        logger.info("Sentinel stopped");
    }

    protected void run(final Protocol protocol) {
        try {
            protocol.run();
        } catch (IOException e) {
            logger.error("There was an error running protocol", e);
        }
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
                    Protocol protocol = ProtocolFactory.get(protocolConfig);
                    if (protocol.isEnabled()) {
                        logger.debug("Sentinel protocol initialized {}", protocol);
                        this.protocols.add(protocol);
                    } else {
                        logger.debug("Sentinel protocol disabled {}", protocol);
                    }
                } catch (RuntimeException e) {
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

}
