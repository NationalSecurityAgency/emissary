package emissary.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.naming.directory.AttributeInUseException;

import ch.qos.logback.classic.ViewStatusMessagesServlet;
import com.google.common.annotations.VisibleForTesting;
import emissary.client.EmissaryClient;
import emissary.client.EmissaryResponse;
import emissary.client.HTTPConnectionFactory;
import emissary.command.ServerCommand;
import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.EmissaryException;
import emissary.core.IPausable;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.core.ResourceWatcher;
import emissary.directory.DirectoryPlace;
import emissary.directory.EmissaryNode;
import emissary.place.IServiceProviderPlace;
import emissary.pool.AgentPool;
import emissary.pool.MoveSpool;
import emissary.roll.RollManager;
import emissary.server.mvc.ThreadDumpAction;
import emissary.server.mvc.ThreadDumpAction.ThreadDumpInfo;
import org.apache.http.client.methods.HttpGet;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.authentication.DigestAuthenticator;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.mvc.mustache.MustacheMvcFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmissaryServer {

    /* Default namespace name */
    public static final String DEFAULT_NAMESPACE_NAME = "EmissaryServer";

    // Our logger
    private static final Logger LOG = LoggerFactory.getLogger(EmissaryServer.class);

    // Our namespace
    private String nameSpaceName = null;

    private Server server;

    private final ServerCommand cmd;

    private final EmissaryNode emissaryNode;

    public EmissaryServer(ServerCommand cmd) throws EmissaryException {
        this(cmd, new EmissaryNode());
    }

    @VisibleForTesting
    public EmissaryServer(ServerCommand cmd, EmissaryNode node) throws EmissaryException {
        this.cmd = cmd;
        // See if we are an emissary node, but first setup node type
        if (cmd.getMode() != null) {
            System.setProperty("node.mode", cmd.getMode()); // TODO: clean this crap up
        }
        emissaryNode = node;

        if (!emissaryNode.isValid()) {
            LOG.error("Not an emissary node, no emissary services required.");
            LOG.error("Try setting -D{}=value to configure an emissary node", EmissaryNode.NODE_NAME_PROPERTY);
            throw new EmissaryException("Not an emissary node, no emissary services required");
        }
    }

    public EmissaryNode getNode() {
        return this.emissaryNode;
    }

    public ServerCommand getServerCommand() {
        return cmd;
    }

    /**
     * Creates and starts a server that is bound into the local Namespace using DEFAULT_NAMESPACE_NAME and returned
     *
     * 
     */
    public Server startServer() {
        // do what StartJetty and then JettyServer did to start
        try {
            // Resource.setDefaultUseCaches(false);

            // needs to be loaded first into the server as it setups up Emissary stuff
            ContextHandler emissaryHandler = buildEmissaryHandler();
            // TODO: rework this, no need for it be set with a context path but if this
            // is left out, it matches / and nothing works correctly
            emissaryHandler.setContextPath("/idontreallyservecontentnowdoi");
            ContextHandler lbConfigHandler = buildLogbackConfigHandler();
            lbConfigHandler.setContextPath("/lbConfig");
            ContextHandler apiHandler = buildApiHandler();
            apiHandler.setContextPath("/api");
            ContextHandler mvcHandler = buildMVCHandler();
            mvcHandler.setContextPath("/emissary");
            // needs to be loaded last into the server so other contexts can match or fall through
            ContextHandler staticHandler = buildStaticHandler();
            staticHandler.setContextPath("/");

            LoginService loginService = buildLoginService();
            ConstraintSecurityHandler security = buildSecurityHandler();
            security.setLoginService(loginService);

            // secure some of the contexts
            final HandlerList securedHandlers = new HandlerList();
            securedHandlers.addHandler(lbConfigHandler);
            securedHandlers.addHandler(apiHandler);
            securedHandlers.addHandler(mvcHandler);
            securedHandlers.addHandler(staticHandler);
            security.setHandler(securedHandlers);

            final HandlerList handlers = new HandlerList();
            handlers.addHandler(emissaryHandler); // not secured, no endpoints and must be loaded first
            handlers.addHandler(security);

            Server server = configureServer();
            server.setHandler(handlers);
            server.addBean(loginService);
            server.setStopAtShutdown(true);
            server.setStopTimeout(10000l);
            if (this.cmd.shouldDumpJettyBeans()) {
                server.dump(System.out);
            }
            this.server = server;
            bindServer(); // emissary specific

            server.start();
            // server.join(); // don't join so we can shutdown

            String serverLocation = cmd.getScheme() + "://" + cmd.getHost() + ":" + cmd.getPort();

            // write out env.sh file here
            Path envsh = Paths.get(ConfigUtil.getProjectBase() + File.separator + "env.sh");
            if (Files.exists(envsh)) {
                LOG.debug("Removing old {}", envsh.toAbsolutePath());
                Files.delete(envsh);
            }
            String envURI = serverLocation + "/api/env.sh";
            EmissaryResponse er = new EmissaryClient().send(new HttpGet(envURI));
            String envString = er.getContentString();
            Files.createFile(envsh);
            Files.write(envsh, envString.getBytes());
            LOG.info("Wrote {}", envsh.toAbsolutePath());
            LOG.debug(" with \n{}", envString);

            if (cmd.isPause()) {
                pause(true);
            } else {
                unpause(true);
            }

            LOG.info("Started EmissaryServer at {}", serverLocation);
            return server;
        } catch (Throwable t) {
            t.printStackTrace(System.err);
            throw new RuntimeException("Emissary server didn't start", t);
        }
    }

    /**
     * Check is server is running
     *
     * @return true if running
     */
    public boolean isServerRunning() {
        return (this.server != null) && (this.server.isStarted());
    }


    /**
     * Pause the server
     *
     * @throws NamespaceException if there is an issue
     */
    public static void pause() throws NamespaceException {
        pause(false);
    }

    /**
     * Pause the server
     *
     * @param silent true to silence {@link NamespaceException}, false otherwise
     * @throws NamespaceException if there is an issue
     */
    public static void pause(boolean silent) throws NamespaceException {
        LOG.debug("Pausing Emissary Server");
        Namespace.lookup(IPausable.class, silent).forEach(IPausable::pause);
    }

    /**
     * Unpause the server
     *
     * @throws NamespaceException if there is an issue
     */
    public static void unpause() throws NamespaceException {
        unpause(false);
    }

    /**
     * Unpause the server
     *
     * @param silent true to silence {@link NamespaceException}, false otherwise
     * @throws NamespaceException if there is an issue
     */
    public static void unpause(boolean silent) throws NamespaceException {
        LOG.debug("Unpausing Emissary Server");
        Namespace.lookup(IPausable.class, silent).forEach(IPausable::unpause);
    }

    /**
     * Stop the server running under the default name
     */
    public static void stopServer() {
        stopServer(getDefaultNamespaceName(), false);
    }

    /**
     * Stop the server running under the default name
     *
     * @param quiet be quiet about failures if true
     */
    public static void stopServer(final boolean quiet) {
        stopServer(getDefaultNamespaceName(), quiet);
    }

    /**
     * Stop the server if it is running and remove it from the namespace
     *
     * @param name the namespace name of the server
     * @param quiet be quiet about failures if true
     */
    public static void stopServer(final String name, final boolean quiet) {
        // TODO pull these out to methods and test them

        LOG.info("Beginning shutdown of EmissaryServer {}", name);
        logThreadDump("Thread dump before anything");

        try {
            pause();
            LOG.info("Done pausing server");
        } catch (Exception ex) {
            LOG.error("Error pausing server", ex);
        }

        try {
            AgentPool.lookup().close();
        } catch (Exception e) {
            LOG.warn("Problem stopping AgentPool", e);
        }

        logThreadDump("Thread dump after closing agent pool");

        try {
            DirectoryPlace.lookup().shutDown();
        } catch (Exception e) {
            LOG.warn("Problem shutting down DirectoryPlace", e);
        }

        try {
            MoveSpool.lookup().quit();
        } catch (Exception e) {
            LOG.warn("Problem stopping MoveSpool", e);
        }

        // Stop the places
        for (String key : Namespace.keySet()) {
            try {
                Object obj = Namespace.lookup(key);
                if (obj instanceof IServiceProviderPlace) {
                    LOG.info("Stopping {} ", obj);
                    ((IServiceProviderPlace) obj).shutDown();
                    LOG.info("Done stopping place: {}", key);
                }
            } catch (Exception ex) {
                LOG.error("Error shutting down " + key, ex);
            }
        }
        LOG.info("Done stopping all places");

        // Print the stats
        try {
            ResourceWatcher rw = ResourceWatcher.lookup();
            rw.logStats(LOG);
        } catch (Exception ex) {
            LOG.warn("No resource statistics available");
        }

        RollManager.shutdown();

        LOG.info("Done stopping all services");

        // thread dump now
        logThreadDump("Thread dump before stopping jetty server");

        try {
            EmissaryServer s = EmissaryServer.lookup(name);
            s.getServer().stop();
        } catch (NamespaceException e) {
            LOG.error("Unable to lookup {} ", name, e);
            e.printStackTrace();
        } catch (InterruptedException e) {
            LOG.warn("Stop interrupted. Expected?");
        } catch (Exception e) {
            LOG.warn("Unable to stop server {} ", name, e);
        }

        LOG.debug("Unbinding name: {}", name);
        Namespace.unbind(name);
        LOG.info("Emissary named {} completely stopped.", name);
    }

    @SuppressWarnings("unchecked")
    private static void logThreadDump(String initialLog) {
        if (LOG.isTraceEnabled()) {
            ThreadDumpAction tda = new ThreadDumpAction();
            Map<String, Object> dumps = tda.getThreaddumps();
            StringBuilder sb = new StringBuilder();
            sb.append("\n" + initialLog);
            sb.append("\nThread DUMP");
            sb.append("\nEmissary Version: " + dumps.get("emissary.version"));
            sb.append("\nJVM Version: " + dumps.get("java.version"));
            sb.append("\nJVM Name: " + dumps.get("java.name"));
            Map<String, Object> threadcount = (Map<String, Object>) dumps.get("threadcount");
            sb.append("\nThread count: current=" + threadcount.get("current") + " max=" + threadcount.get("max") + " daemon="
                    + threadcount.get("daemon"));
            sb.append("\nDeadlocked Threads:");
            for (ThreadDumpAction.ThreadDumpInfo tdi : (Set<ThreadDumpInfo>) dumps.get("deadlocks")) {
                sb.append("\n" + tdi.stack);
            }
            sb.append("\nThread dump:");
            for (ThreadDumpAction.ThreadDumpInfo tdi : (Set<ThreadDumpInfo>) dumps.get("threads")) {
                sb.append("\n" + tdi.stack);
            }
            LOG.trace(sb.toString());
        }
    }

    /**
     * Determine if the emissary node is idle
     */
    public boolean isIdle() {
        try {
            AgentPool pool = AgentPool.lookup();
            return pool.getNumActive() == 0;
        } catch (NamespaceException ex) {
            // empty catch block
        }
        return true;
    }

    /**
     * Non-static way to stop a server
     */
    public void stop() {
        stopServer(getNamespaceName(), false);
    }

    /**
     * Get the reference to the server
     */
    public Server getServer() {
        return this.server;
    }


    public synchronized String getNamespaceName() {
        if (this.nameSpaceName == null) {
            this.nameSpaceName = getDefaultNamespaceName();
        }
        return this.nameSpaceName;
    }

    public static String getDefaultNamespaceName() {
        return DEFAULT_NAMESPACE_NAME;
    }

    /**
     * Check if server is running
     *
     * @return true if it is in the namespace and is started
     */
    public static boolean isStarted() {
        return isStarted(getDefaultNamespaceName());
    }

    /**
     * Check if server is running
     *
     * @param name the namespace name to use as a key
     * @return true if it is in the namespace and is started
     */
    public static boolean isStarted(final String name) {
        boolean started = false;
        try {
            final Server s = lookup(name).getServer();
            if (s != null && s.isStarted()) {
                started = true;
            } else {
                LOG.debug("Server found but not started, name=" + name);
            }
        } catch (NamespaceException ex) {
            LOG.debug("No server found using name=" + name);
        }
        return started;
    }

    public static boolean exists() {
        try {
            EmissaryServer.lookup();
            return true;
        } catch (NamespaceException ex) {
            // expected
        }
        return false;
    }

    public static EmissaryServer lookup() throws NamespaceException {
        return lookup(getDefaultNamespaceName());
    }

    /**
     * Retreive instance from namespace using default name
     */
    public static EmissaryServer lookup(final String name) throws NamespaceException {
        return (EmissaryServer) Namespace.lookup(name);
    }

    private ConstraintSecurityHandler buildSecurityHandler() {
        ConstraintSecurityHandler handler = new ConstraintSecurityHandler();
        Constraint constraint = new Constraint();
        constraint.setName("auth");
        constraint.setAuthenticate(true);
        constraint.setRoles(new String[] {"everyone", "emissary", "admin", "support", "manager"});
        ConstraintMapping mapping = new ConstraintMapping();
        mapping.setPathSpec("/*");
        mapping.setConstraint(constraint);
        handler.setConstraintMappings(Collections.singletonList(mapping));
        handler.setAuthenticator(new DigestAuthenticator());
        return handler;
    }

    private LoginService buildLoginService() {
        String jettyUsersFile = ConfigUtil.getConfigFile("jetty-users.properties");
        System.setProperty("emissary.jetty.users.file", jettyUsersFile); // for EmissaryClient
        return new HashLoginService("EmissaryRealm", jettyUsersFile);
    }

    private void bindServer() throws UnknownHostException, AttributeInUseException {
        if (Namespace.exists(getDefaultNamespaceName())) {
            LOG.error("EmissaryServer already bound to namespace. This should NEVER happen.");
            throw new AttributeInUseException("EmissaryServer was already bound to the namespace using serverName: " + DEFAULT_NAMESPACE_NAME);
        }

        LOG.debug("Binding {} ", DEFAULT_NAMESPACE_NAME);
        Namespace.bind(getDefaultNamespaceName(), this);

    }

    private ContextHandler buildStaticHandler() {
        // Add pathspec for static assets
        ServletHolder homeHolder = new ServletHolder("static-holder", DefaultServlet.class);
        // If no filesytem path was passed in, load assets from the classpath
        if (null == cmd.getStaticDir()) {
            LOG.debug("Loading static resources from the classpath");
            homeHolder.setInitParameter("resourceBase", this.getClass().getClassLoader().getResource("public").toExternalForm());
        } else {
            // use --staticDir ${project_loc}/src/main/resources/public as command args
            LOG.debug("Loading static resources from staticPath: {}", cmd.getStaticDir());
            homeHolder.setInitParameter("resourceBase", cmd.getStaticDir().toAbsolutePath().toString());
        }
        homeHolder.setInitParameter("dirAllowed", "true");
        homeHolder.setInitParameter("pathInfoOnly", "true");
        // homeHolder.setInitOrder(0);

        ServletContextHandler homeContextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        homeContextHandler.addServlet(homeHolder, "/*");

        return homeContextHandler;
    }

    private ContextHandler buildApiHandler() {

        final ResourceConfig application = new ResourceConfig();
        // setup rest endpoint
        application.packages("emissary.server.api").register(JacksonFeature.class);

        ServletHolder apiHolder = new ServletHolder(new org.glassfish.jersey.servlet.ServletContainer(application));
        // apiHolder.setInitOrder(0);
        // apiHolder.setInitParameter(ServerProperties.PROVIDER_PACKAGES, "resource");

        ServletContextHandler apiHolderContext = new ServletContextHandler(ServletContextHandler.SESSIONS);
        apiHolderContext.addServlet(apiHolder, "/*");

        return apiHolderContext;
    }

    private ContextHandler buildMVCHandler() {

        final ResourceConfig application = new ResourceConfig();
        application.register(MultiPartFeature.class);
        // setup mustache templates
        application.property(MustacheMvcFeature.TEMPLATE_BASE_PATH, "/templates");
        application.register(MustacheMvcFeature.class).packages("emissary.server.mvc");

        ServletHolder mvcHolder = new ServletHolder(new org.glassfish.jersey.servlet.ServletContainer(application));
        // mvcHolder.setInitOrder(1);

        ServletContextHandler mvcHolderContext = new ServletContextHandler(ServletContextHandler.SESSIONS);
        mvcHolderContext.addServlet(mvcHolder, "/*");

        return mvcHolderContext;
    }

    private ContextHandler buildEmissaryHandler() throws EmissaryException {
        // must set these set or you are not an EmissaryNode
        String configDir = System.getProperty(ConfigUtil.CONFIG_DIR_PROPERTY, null);
        if (configDir == null || !Files.exists(Paths.get(configDir))) {
            throw new EmissaryException("Config dir error. " + ConfigUtil.CONFIG_DIR_PROPERTY + " is " + configDir);
        }
        // set number of agents if it has been set
        if (cmd.getAgents() != 0) {
            System.setProperty("agent.poolsize", Integer.toString(cmd.getAgents()));
        }

        ServletContextHandler emissaryHolderContext = new ServletContextHandler(ServletContextHandler.SESSIONS);
        emissaryHolderContext.addEventListener(new InitializeContext(emissaryNode));

        return emissaryHolderContext;
    }

    private ContextHandler buildLogbackConfigHandler() {
        ServletHolder lbHolder = new ServletHolder("logback-config-holder", ViewStatusMessagesServlet.class);
        ServletContextHandler lbHolderContext = new ServletContextHandler(ServletContextHandler.SESSIONS);
        lbHolderContext.addServlet(lbHolder, "/*");

        return lbHolderContext;
    }

    @VisibleForTesting
    protected Server configureServer() throws IOException, GeneralSecurityException {
        int maxThreads = 250;
        int minThreads = 10;
        int lowThreads = 50;
        int threadsPriority = 9;
        int idleTimeout = new Long(TimeUnit.MINUTES.toMillis(15)).intValue();

        QueuedThreadPool threadPool = new QueuedThreadPool(maxThreads, minThreads, idleTimeout);
        threadPool.setLowThreadsThreshold(lowThreads);
        threadPool.setThreadsPriority(threadsPriority);

        Server server = new Server(threadPool);

        ServerConnector connector = cmd.isSslEnabled() ? getServerConnector(server) : new ServerConnector(server);
        connector.setHost(cmd.getHost());
        connector.setPort(cmd.getPort());

        server.setConnectors(new Connector[] {connector});

        return server;
    }

    private ServerConnector getServerConnector(Server server) throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
        ServerConnector connector;
        HttpConfiguration http_config = new HttpConfiguration();
        http_config.setSecureScheme("https");
        http_config.setSecurePort(cmd.getPort());

        HttpConfiguration https_config = new HttpConfiguration(http_config);
        https_config.addCustomizer(new SecureRequestCustomizer());

        // Get keystore and truststore config from HttpConnectionFactory.cfg (probably flavored with -SSL)
        // only jks files are supported, but could be expanded
        Configurator httpConnFactCfg = ConfigUtil.getConfigInfo(HTTPConnectionFactory.class);
        String keystore = httpConnFactCfg.findStringEntry("javax.net.ssl.keyStore", "no-keystore");
        System.setProperty("javax.net.ssl.keyStore", keystore);
        String keystorePass = httpConnFactCfg.findStringEntry("javax.net.ssl.keyStorePassword", "no-keypass");
        System.setProperty("javax.net.ssl.keyStorePassword", keystorePass);
        String trustStore = httpConnFactCfg.findStringEntry("javax.net.ssl.trustStore", keystore);
        System.setProperty("javax.net.ssl.trustStore", trustStore);
        String trustStorePass = httpConnFactCfg.findStringEntry("javax.net.ssl.trustStorePassword", keystorePass);
        System.setProperty("javax.net.ssl.trustStorePassword", trustStorePass);
        // setup context to add to connector
        SslContextFactory sslContextFactory = new SslContextFactory.Server();
        sslContextFactory.setKeyStorePath(keystore);
        sslContextFactory.setKeyStorePassword(keystorePass);
        KeyStore trustStoreInstance = KeyStore.getInstance("JKS");
        try (final InputStream is = new FileInputStream(trustStore)) {
            trustStoreInstance.load(is, trustStorePass.toCharArray());
        }
        sslContextFactory.setTrustStore(trustStoreInstance);

        connector = new ServerConnector(server,
                new SslConnectionFactory(sslContextFactory, "http/1.1"),
                new HttpConnectionFactory(https_config));
        return connector;
    }
}
