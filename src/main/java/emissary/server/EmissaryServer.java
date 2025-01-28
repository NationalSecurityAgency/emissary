package emissary.server;

import emissary.admin.Startup;
import emissary.client.EmissaryClient;
import emissary.client.EmissaryResponse;
import emissary.client.HTTPConnectionFactory;
import emissary.command.ServerCommand;
import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.EmissaryException;
import emissary.core.EmissaryRuntimeException;
import emissary.core.IPausable;
import emissary.core.MetricsManager;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.core.ResourceWatcher;
import emissary.core.sentinel.Sentinel;
import emissary.directory.DirectoryPlace;
import emissary.directory.EmissaryNode;
import emissary.place.IServiceProviderPlace;
import emissary.pool.AgentPool;
import emissary.pool.MoveSpool;
import emissary.roll.RollManager;
import emissary.server.mvc.ThreadDumpAction;
import emissary.server.mvc.ThreadDumpAction.ThreadDumpInfo;
import emissary.spi.SPILoader;

import ch.qos.logback.classic.ViewStatusMessagesServlet;
import com.google.common.annotations.VisibleForTesting;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.eclipse.jetty.http.HttpVersion;
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
import org.glassfish.jersey.server.filter.CsrfProtectionFilter;
import org.glassfish.jersey.server.mvc.mustache.MustacheMvcFeature;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.naming.directory.AttributeInUseException;

public class EmissaryServer {

    /* Default namespace name */
    public static final String DEFAULT_NAMESPACE_NAME = "EmissaryServer";

    // Our logger
    private static final Logger LOG = LoggerFactory.getLogger(EmissaryServer.class);

    // Our namespace
    @Nullable
    private String nameSpaceName = null;

    private Server server;

    private final ServerCommand cmd;

    private final EmissaryNode emissaryNode;

    @SuppressWarnings("NonFinalStaticField")
    private static EmissaryServer emissaryServer;

    private EmissaryServer(ServerCommand cmd) {
        this.cmd = cmd;
        this.emissaryNode = new EmissaryNode(cmd.getMode());
    }

    // there should be a better way to set a custom peer.cfg than this
    private EmissaryServer(ServerCommand cmd, EmissaryNode node) {
        this.cmd = cmd;
        this.emissaryNode = node;
    }

    public static EmissaryServer init(ServerCommand cmd) {
        emissaryServer = new EmissaryServer(cmd);
        return emissaryServer;
    }

    // there should be a better way to set a custom peer.cfg than this
    public static EmissaryServer init(ServerCommand cmd, EmissaryNode node) {
        emissaryServer = new EmissaryServer(cmd, node);
        return emissaryServer;
    }

    public static boolean isInitialized() {
        return emissaryServer != null;
    }

    public static EmissaryServer getInstance() {
        if (emissaryServer == null) {
            throw new AssertionError("EmissaryServer has not yet been instantiated!");
        }
        return emissaryServer;
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
            ContextHandler mvcHandler = buildMvcHandler();
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

            Server configuredServer = configureServer();
            configuredServer.setHandler(handlers);
            configuredServer.addBean(loginService);
            configuredServer.setStopAtShutdown(true);
            configuredServer.setStopTimeout(10000L);
            if (this.cmd.shouldDumpJettyBeans() && LOG.isInfoEnabled()) {
                LOG.info(configuredServer.dump());
            }
            this.server = configuredServer;
            bindServer(); // emissary specific

            configuredServer.start();
            // server.join(); // don't join so we can shutdown

            String serverLocation = cmd.getScheme() + "://" + cmd.getHost() + ":" + cmd.getPort();

            // write out env.sh file here
            Path envsh = Paths.get(ConfigUtil.getProjectBase() + File.separator + "env.sh");
            if (Files.exists(envsh)) {
                LOG.debug("Removing old {}", envsh.toAbsolutePath());
                Files.delete(envsh);
            }
            String envUri = serverLocation + "/api/env.sh";
            EmissaryResponse er = new EmissaryClient().send(new HttpGet(envUri));
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

            // check if invisible place start-ups occurred on strict server start-up, and shut down server if so.
            if (Startup.isInvisPlacesStartedInStrictMode() && this.server.isStarted()) {
                EmissaryServer.stopServer(true);
                LOG.info("Server shut down due to invisible place startups on strict-mode: {}", Startup.getInvisPlaces());
            }

            return configuredServer;
        } catch (Throwable t) {
            String errorMsg = "Emissary server didn't start";
            throw new EmissaryRuntimeException(errorMsg, t);
        }
    }

    /**
     * Check is server is running
     *
     * @return true if running
     */
    public boolean isServerRunning() {
        return (this.server != null) && this.server.isStarted();
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
     * Stop the server
     */
    public static void stopServer() {
        stopServer(false);
    }

    /**
     * Stop the server running under the default name
     *
     * @param force force shutdown
     * @param quiet be quiet about failures if true
     */
    @Deprecated
    public static void stopServer(final boolean force, final boolean quiet) {
        stopServer(getDefaultNamespaceName(), force, quiet);
    }

    /**
     * Stop the server if it is running and remove it from the namespace
     *
     * @param name the namespace name of the server
     * @param quiet be quiet about failures if true
     */
    @Deprecated
    public static void stopServer(final String name, final boolean quiet) {
        stopServer(name, false, quiet);
    }

    /**
     * Stop the server if it is running and remove it from the namespace
     *
     * @param name the namespace name of the server
     * @param force force shutdown
     * @param quiet be quiet about failures if true
     */
    @Deprecated
    public static void stopServer(final String name, final boolean force, final boolean quiet) {
        stopServer(force);
    }

    /**
     * Stop the server with an optional force flag
     *
     * @param force force shutdown
     */
    public static void stopServer(final boolean force) {
        // TODO pull these out to methods and test them

        LOG.info("Beginning shutdown of EmissaryServer");
        logThreadDump("Thread dump before anything");

        try {
            pause();
            LOG.info("Done pausing server");
        } catch (Exception ex) {
            LOG.error("Error pausing server", ex);
        }

        try {
            Sentinel sentinel = Sentinel.lookup();
            sentinel.quit();
        } catch (Exception ex) {
            LOG.warn("No sentinel available");
        }

        try {
            if (force) {
                AgentPool.lookup().kill();
            } else {
                AgentPool.lookup().close();
            }
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
                    // make sure key is removed from namespace
                    Namespace.unbind(key);
                    LOG.info("Done stopping place: {}", key);
                }
            } catch (Exception ex) {
                LOG.error("Error shutting down " + key, ex);
            }
        }
        LOG.info("Done stopping all places");

        RollManager.shutdown();

        // Print the stats
        try {
            ResourceWatcher rw = ResourceWatcher.lookup();
            rw.logStats(LOG);
            rw.quit();
        } catch (Exception ex) {
            LOG.warn("No resource statistics available");
        }

        try {
            MetricsManager.lookup().shutdown();
        } catch (Exception ex) {
            LOG.warn("No metrics manager available");
        }

        SPILoader.unload();

        LOG.info("Done stopping all services");

        // thread dump now
        logThreadDump("Thread dump before stopping jetty server");

        try {
            EmissaryServer.getInstance().getServer().stop();
        } catch (InterruptedException e) {
            LOG.warn("Interrupted! Expected?");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOG.warn("Unable to stop EmissaryServer", e);
        }

        LOG.debug("Unbinding name: {}", getDefaultNamespaceName());
        Namespace.unbind(getDefaultNamespaceName());
        Namespace.clear();
        LOG.info("EmissaryServer completely stopped");
    }

    /**
     * Forcibly stop the server running under the default name
     */
    public static void stopServerForce() {
        stopServer(true);
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
            for (ThreadDumpInfo tdi : (Set<ThreadDumpInfo>) dumps.get("deadlocks")) {
                sb.append("\n" + tdi.stack);
            }
            sb.append("\nThread dump:");
            for (ThreadDumpInfo tdi : (Set<ThreadDumpInfo>) dumps.get("threads")) {
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
        stopServer(false);
    }

    /**
     * Get the reference to the server
     */
    public Server getServer() {
        return this.server;
    }

    @Deprecated
    public synchronized String getNamespaceName() {
        if (this.nameSpaceName == null) {
            this.nameSpaceName = getDefaultNamespaceName();
        }
        return this.nameSpaceName;
    }

    @Deprecated
    public static String getDefaultNamespaceName() {
        return DEFAULT_NAMESPACE_NAME;
    }

    /**
     * Check if server is running
     *
     * @return true if it is in the namespace and is started
     * @deprecated use {@link #isServerRunning()}
     */
    @Deprecated
    public static boolean isStarted() {
        return isStarted(getDefaultNamespaceName());
    }

    /**
     * Check if server is running
     *
     * @param name the namespace name to use as a key
     * @return true if it is in the namespace and is started
     */
    @Deprecated
    public static boolean isStarted(final String name) {
        boolean started = false;
        try {
            final Server s = lookup(name).getServer();
            if (s != null && s.isStarted()) {
                started = true;
            } else {
                LOG.debug("Server found but not started, name={}", name);
            }
        } catch (NamespaceException ex) {
            LOG.debug("No server found using name={}", name);
        }
        return started;
    }

    @Deprecated
    public static boolean exists() {
        try {
            EmissaryServer.lookup();
            return true;
        } catch (NamespaceException ex) {
            // expected
        }
        return false;
    }

    @Deprecated
    public static EmissaryServer lookup() throws NamespaceException {
        return lookup(getDefaultNamespaceName());
    }

    /**
     * Retrieve instance from namespace using default name
     */
    @Deprecated
    public static EmissaryServer lookup(final String name) throws NamespaceException {
        return (EmissaryServer) Namespace.lookup(name);
    }

    private static ConstraintSecurityHandler buildSecurityHandler() {
        ConstraintSecurityHandler handler = new ConstraintSecurityHandler();

        Constraint authConstraint = new Constraint();
        authConstraint.setName("auth");
        authConstraint.setAuthenticate(true);
        authConstraint.setRoles(new String[] {"everyone", "emissary", "admin", "support", "manager"});

        Constraint noAuthConstraint = new Constraint();
        noAuthConstraint.setName("no_auth");
        noAuthConstraint.setAuthenticate(false);

        ConstraintMapping mapping = new ConstraintMapping();
        mapping.setPathSpec("/*");
        mapping.setConstraint(authConstraint);

        ConstraintMapping health = new ConstraintMapping();
        health.setPathSpec("/api/health");
        health.setConstraint(noAuthConstraint);

        handler.setConstraintMappings(new ConstraintMapping[] {mapping, health});
        handler.setAuthenticator(new DigestAuthenticator());
        return handler;
    }

    private static LoginService buildLoginService() {
        String jettyUsersFile = ConfigUtil.getConfigFile("jetty-users.properties");
        System.setProperty("emissary.jetty.users.file", jettyUsersFile); // for EmissaryClient
        return new HashLoginService("EmissaryRealm", jettyUsersFile);
    }

    private void bindServer() throws AttributeInUseException {
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
        application.setApplicationName("api");
        application.register(MultiPartFeature.class);
        // setup rest endpoint
        application.packages("emissary.server.api").register(JacksonFeature.class);
        csrfFilter(application);

        ServletHolder apiHolder = new ServletHolder(new ServletContainer(application));

        ServletContextHandler apiHolderContext = new ServletContextHandler(ServletContextHandler.SESSIONS);
        apiHolderContext.addServlet(apiHolder, "/*");

        return apiHolderContext;
    }

    private ContextHandler buildMvcHandler() {

        final ResourceConfig application = new ResourceConfig();
        application.setApplicationName("mvc");
        application.register(MultiPartFeature.class);
        // setup mustache templates
        application.property(MustacheMvcFeature.TEMPLATE_BASE_PATH, "/templates");
        application.register(MustacheMvcFeature.class).packages("emissary.server.mvc");
        csrfFilter(application);

        ServletHolder mvcHolder = new ServletHolder(new ServletContainer(application));
        // mvcHolder.setInitOrder(1);

        ServletContextHandler mvcHolderContext = new ServletContextHandler(ServletContextHandler.SESSIONS);
        mvcHolderContext.addServlet(mvcHolder, "/*");

        return mvcHolderContext;
    }

    protected void csrfFilter(ResourceConfig application) {
        if (this.cmd.isCsrf()) {
            LOG.debug("Enabling csrf protection filter for {}", application.getApplicationName());
            application.register(CsrfProtectionFilter.class);
        } else {
            LOG.debug("Disabling csrf protection filter for {}", application.getApplicationName());
        }
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

    private static ContextHandler buildLogbackConfigHandler() {
        ServletHolder lbHolder = new ServletHolder("logback-config-holder", ViewStatusMessagesServlet.class);
        ServletContextHandler lbHolderContext = new ServletContextHandler(ServletContextHandler.SESSIONS);
        lbHolderContext.addServlet(lbHolder, "/*");

        return lbHolderContext;
    }

    @VisibleForTesting
    protected Server configureServer() throws IOException {
        int maxThreads = 250;
        int minThreads = 10;
        int lowThreads = 50;
        int threadsPriority = 9;
        int idleTimeout = (int) TimeUnit.MINUTES.toMillis(15);

        QueuedThreadPool threadPool = new QueuedThreadPool(maxThreads, minThreads, idleTimeout);
        threadPool.setLowThreadsThreshold(lowThreads);
        threadPool.setThreadsPriority(threadsPriority);

        Server configuredServer = new Server(threadPool);
        configuredServer.setConnectors(new Connector[] {
                createServerConnector(configuredServer)
        });
        return configuredServer;
    }

    /**
     * Create a server connector, insecure (http) or secure (https) depending on {@link ServerCommand#isSslEnabled()}
     *
     * @param server the Jetty HTTP Servlet Server
     * @return server connector that is the primary connector for the Jetty server over TCP/IP
     * @throws IOException if there is an error
     */
    private ServerConnector createServerConnector(Server server) throws IOException {
        ServerConnector connector = cmd.isSslEnabled() ? createHttpsConnector(server) : createHttpConnector(server);
        connector.setHost(cmd.getHost());
        connector.setPort(cmd.getPort());
        return connector;
    }

    /**
     * Create an insecure http connector
     *
     * @param server the Jetty HTTP Servlet Server
     * @return server connector that is the primary connector for the Jetty server over TCP/IP
     */
    private static ServerConnector createHttpConnector(Server server) {
        return new ServerConnector(server);
    }

    /**
     * Create a secure https connector
     *
     * @param server the Jetty HTTP Servlet Server
     * @return ServerConnector is the primary connector for the Jetty server over TCP/IP
     * @throws IOException if there is an error
     */
    private ServerConnector createHttpsConnector(Server server) throws IOException {
        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setSecureScheme("https");
        httpConfig.setSecurePort(cmd.getPort());

        HttpConfiguration httpsConfig = new HttpConfiguration(httpConfig);
        httpsConfig.addCustomizer(createSecureRequestCustomizer());

        return new ServerConnector(server,
                new SslConnectionFactory(getSslContextFactory(), HttpVersion.HTTP_1_1.asString()),
                new HttpConnectionFactory(httpsConfig));
    }

    /**
     * SecureRequestCustomizer extracts the attribute from an SSLContext and sets them on the request according to Servlet
     * Specification Requirements. Jetty defaults for the SecureRequestCustomizer are:
     * <ul>
     * <li>isSniRequired() => false // Server Name Indication (SNI) is required
     * <li>isSniHostCheck() => true // SNI Host name must match when there is an SNI certificate
     * <li>getStsMaxAge() => -1 (no max age) // Strict-Transport-Security (STS) max age
     * <li>isStsIncludeSubDomains() => false // include-subdomain property is sent with any STS header
     * </ul>
     *
     * @return a secure request customizer
     */
    private SecureRequestCustomizer createSecureRequestCustomizer() {
        SecureRequestCustomizer secureRequestCustomizer = new SecureRequestCustomizer();
        secureRequestCustomizer.setSniHostCheck(cmd.isSniHostCheckEnabled());
        return secureRequestCustomizer;
    }

    /**
     * Create a {@link SslContextFactory.Server} using keystore and truststore config from HttpConnectionFactory.cfg,
     * probably flavored with '-SSL'. Note: only jks files are supported, but could be expanded
     *
     * @return SslContextFactory that is used to configure SSL parameters to be used by server connectors
     * @throws IOException if there is an error getting the context factory
     */
    private static SslContextFactory.Server getSslContextFactory() throws IOException {
        Configurator httpConnFactCfg = ConfigUtil.getConfigInfo(HTTPConnectionFactory.class);
        String keystore = httpConnFactCfg.findStringEntry("javax.net.ssl.keyStore", "no-keystore");
        System.setProperty("javax.net.ssl.keyStore", keystore);
        String keystorePass = httpConnFactCfg.findStringEntry("javax.net.ssl.keyStorePassword", "no-keypass");
        System.setProperty("javax.net.ssl.keyStorePassword", keystorePass);
        String trustStore = httpConnFactCfg.findStringEntry("javax.net.ssl.trustStore", keystore);
        System.setProperty("javax.net.ssl.trustStore", trustStore);
        String trustStorePass = httpConnFactCfg.findStringEntry("javax.net.ssl.trustStorePassword", keystorePass);
        System.setProperty("javax.net.ssl.trustStorePassword", trustStorePass);

        SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
        sslContextFactory.setKeyStorePath(keystore);
        sslContextFactory.setKeyStorePassword(keystorePass);

        KeyStore trustStoreInstance;
        try (InputStream is = Files.newInputStream(Paths.get(trustStore))) {
            trustStoreInstance = KeyStore.getInstance("JKS");
            trustStoreInstance.load(is, trustStorePass.toCharArray());
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException e) {
            throw new IOException("There was an issue loading the truststore", e);
        }

        sslContextFactory.setTrustStore(trustStoreInstance);
        return sslContextFactory;
    }

}
