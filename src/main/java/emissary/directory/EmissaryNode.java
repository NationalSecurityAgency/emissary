package emissary.directory;

import emissary.admin.Startup;
import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.core.EmissaryException;
import emissary.core.MetricsManager;
import emissary.core.ResourceWatcher;
import emissary.core.sentinel.Sentinel;
import emissary.pool.AgentPool;
import emissary.pool.MobileAgentFactory;
import emissary.pool.MoveSpool;
import emissary.roll.RollManager;
import emissary.spi.SPILoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Hold some details about being a P2P node in the emissary network The order of preference to find the node
 * configuration for an emissary node is
 * <ol>
 * <li>node-[emissary.node.name]-[emissary.node.port].cfg</li>
 * <li>node-[emissary.node.name].cfg</li>
 * <li>node-[emissary.node.port].cfg</li>
 * <li>node-[os-type].cfg</li>
 * <li>node.cfg</li>
 * </ol>
 */
public class EmissaryNode {
    private static final Logger logger = LoggerFactory.getLogger(EmissaryNode.class);

    /** Node name property is {@value} */
    public static final String NODE_NAME_PROPERTY = "emissary.node.name";

    /** Node port property is {@value} */
    public static final String NODE_PORT_PROPERTY = "emissary.node.port";

    /** Node scheme property */
    public static final String NODE_SCHEME_PROPERTY = "emissary.node.scheme";

    /** Node service type property */
    public static final String NODE_SERVICE_TYPE_PROPERTY = "emissary.node.service.type";

    /** Node service type is {@value} */
    public static final String DEFAULT_NODE_SERVICE_TYPE = "server";

    /** Node type is {@value} */
    public static final String DEFAULT_NODE_TYPE = "emissary-edge-node";

    /** Property name that can be used to disable stdout/stderr redirection */
    public static final String DISABLE_LOG_REDIRECTION_PROPERTY = "emissary.log.redirection.disabled";

    /** Property that determines if server will shut down in the event a place fails to start */
    public static final String STRICT_STARTUP_MODE = "strict.mode";

    // types are feeder, worker, standalone
    // TODO: make an enum for these
    private static final String DEFAULT_NODE_MODE = "standalone";

    @Nullable
    protected String nodeName = null;
    protected int nodePort = -1;
    @Nullable
    protected String nodeScheme = null;
    // this is the OS for all practical purposes
    @Nullable
    protected String nodeType = null;
    @Nullable
    protected String nodeMode = null; // probably better as nodeType, but that requires a refactor
    protected boolean nodeNameIsDefault = false;
    @Nullable
    protected String nodeServiceType = null;

    protected boolean strictStartupMode = false;

    public EmissaryNode() {
        this(DEFAULT_NODE_MODE);
    }

    /**
     * Construct the node. The node name and port are from system properties. The node type is based on the os.name in this
     * implementation
     */
    public EmissaryNode(String nodeMode) {
        this.nodeMode = nodeMode;
        this.nodeName = System.getProperty(NODE_NAME_PROPERTY);
        if (this.nodeName == null) {
            // Use IP Address for default node name since it is
            // the only globally addressable thing we have for sure
            try {
                final InetAddress local = InetAddress.getLocalHost();
                this.nodeName = local.getHostAddress();
                this.nodeNameIsDefault = true;
            } catch (UnknownHostException ex) {
                // empty catch block
            }
        }
        this.nodeScheme = System.getProperty(NODE_SCHEME_PROPERTY, "http");
        this.nodePort = Integer.getInteger(NODE_PORT_PROPERTY, -1).intValue();
        this.nodeType = System.getProperty("os.name", DEFAULT_NODE_TYPE).toLowerCase(Locale.getDefault()).replace(' ', '_');
        this.nodeServiceType = System.getProperty(NODE_SERVICE_TYPE_PROPERTY, DEFAULT_NODE_SERVICE_TYPE);
        this.strictStartupMode = Boolean.parseBoolean(System.getProperty(STRICT_STARTUP_MODE, String.valueOf(false)));
    }

    /**
     * The node name
     */
    public String getNodeName() {
        return this.nodeName;
    }

    /**
     * The node port
     */
    public int getNodePort() {
        return this.nodePort;
    }

    /**
     * The node type
     */
    public String getNodeType() {
        return this.nodeType;
    }

    /**
     * The node scheme
     */
    public String getNodeScheme() {
        return this.nodeScheme;
    }

    /**
     * Get the value as a url
     */
    public String asUrlKey() {
        return "http://" + getNodeName() + ":" + getNodePort() + "/";
    }

    /**
     * Get the inet socket address for this emissary node
     */
    public InetSocketAddress getInetSocketAddress() {
        return new InetSocketAddress(getNodeName(), getNodePort());
    }

    /**
     * True if we have enough configured to be a P2P emissary node
     */
    public boolean isValid() {
        return (getNodeName() != null) && (getNodePort() > 0) && (getNodeType() != null);
    }

    /**
     * True if we have enough configured to be a stand-alone (non P2P) node
     */
    public boolean isValidStandalone() {
        return (getNodeName() != null) && (getNodeType() != null);
    }

    /**
     * True if this node appears to be a stand-alone (non P2P) node
     */
    public boolean isStandalone() {
        return isValidStandalone() && getNodeMode().equals("standalone");
    }

    private Object getNodeMode() {
        return nodeMode;
    }

    public boolean isStrictStartupMode() {
        return strictStartupMode;
    }

    public void setStrictStartupMode(boolean strictStartupMode) {
        this.strictStartupMode = strictStartupMode;
    }

    @Override
    public String toString() {
        return "node-" + getNodeName() + "-" + getNodePort() + "-" + getNodeType();
    }


    /**
     * Get the configuration stream for this node
     */
    public Configurator getNodeConfigurator() throws IOException {
        return internalGetConfigurator("node");
    }

    /**
     * Get the peer configuration stream for this node
     */
    public Configurator getPeerConfigurator() throws IOException {
        if (isStandalone()) {
            // return a configurator here with just standalone, don't actually read the peer.cfg
            // This is a hack until we can TODO: refactor all this so standalone doesn't need peers
            // maybe even warn if there is a peer.cfg
            logger.debug("Node is standalone, ignoring any peer.cfg and only constructing one rendezvous peer with the local node");
            Configurator cfg = new ServiceConfigGuide();
            cfg.addEntry("RENDEZVOUS_PEER", this.asUrlKey());
            return cfg;
        }
        return internalGetConfigurator("peer");
    }

    /**
     * Internal routine to find configuration files base on node params
     * 
     * @param prefix the file prefix
     */
    protected Configurator internalGetConfigurator(final String prefix) throws IOException {
        final List<String> configPrefs = new ArrayList<>();
        configPrefs.add(prefix + "-" + getNodeName() + "-" + getNodePort() + ConfigUtil.CONFIG_FILE_ENDING);
        if (this.nodeNameIsDefault) {
            configPrefs.add(prefix + "-" + getNodeName().replace('.', '_') + "-" + getNodePort() + ConfigUtil.CONFIG_FILE_ENDING);
        }
        configPrefs.add(prefix + "-" + getNodeName() + ConfigUtil.CONFIG_FILE_ENDING);
        if (this.nodeNameIsDefault) {
            configPrefs.add(prefix + "-" + getNodeName().replace('.', '_') + ConfigUtil.CONFIG_FILE_ENDING);
        }

        configPrefs.add(prefix + "-" + getNodePort() + ConfigUtil.CONFIG_FILE_ENDING);

        configPrefs.add(prefix + "-" + getNodeType() + ConfigUtil.CONFIG_FILE_ENDING);
        configPrefs.add(prefix + ConfigUtil.CONFIG_FILE_ENDING);

        if (logger.isDebugEnabled()) {
            logger.debug("Searching for {} config in preferences {}", prefix, configPrefs);
        }

        return ConfigUtil.getConfigInfo(configPrefs);
    }

    /**
     * Start all the stuff needed by an emissary server
     */
    public void configureEmissaryServer() throws EmissaryException {
        // Create the core objects we need to get going
        // The AgentPool
        AgentPool pool = new AgentPool(new MobileAgentFactory());
        logger.debug("Setup AgentPool with {} agents...", pool.getNumIdle());

        // The MoveSpool
        MoveSpool spool = new MoveSpool();
        logger.debug("Started MoveSpool...{}", spool);

        // The metrics manager
        MetricsManager metricsManager = new MetricsManager();
        logger.debug("Started metrics manager...{}", metricsManager);

        // The resource watcher
        ResourceWatcher watcher = new ResourceWatcher(metricsManager);
        logger.debug("Started resource watcher...{}", watcher);

        // Initialize list of configured spi classes
        SPILoader.load();

        // Initialize the rolling framework
        RollManager.getManager();

        // Startup places on this emissary node
        try {
            Configurator nodeConfigStream = getNodeConfigurator();
            Startup startupEngine = new Startup(nodeConfigStream, this);
            startupEngine.start();
            logger.debug("The system is up and running fine. All ahead Warp-7.");
        } catch (IOException iox) {
            throw new EmissaryException("Unable to configure Emissary Node services", iox);
        }

        // The mobile agent watcher
        Sentinel.start();
    }
}
