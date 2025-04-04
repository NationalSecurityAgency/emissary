package emissary.command;

import emissary.client.EmissaryResponse;
import emissary.command.converter.ModeConverter;
import emissary.command.converter.ProjectBaseConverter;
import emissary.directory.EmissaryNode;
import emissary.server.EmissaryServer;
import emissary.server.api.Pause;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static emissary.directory.EmissaryNode.STRICT_STARTUP_MODE;

@Command(description = "Start an Emissary jetty server", subcommands = {HelpCommand.class})
public class ServerCommand extends ServiceCommand {
    private static final Logger LOG = LoggerFactory.getLogger(ServerCommand.class);

    public static final String COMMAND_NAME = "server";

    public static final int DEFAULT_PORT = 8001;

    @Option(names = {"-m", "--mode"}, description = "mode: standalone or cluster\nDefault: ${DEFAULT-VALUE}", converter = ModeConverter.class,
            defaultValue = "standalone")
    private EmissaryNode.Mode mode;

    @Option(names = "--staticDir", description = "path to static assets, loaded from classpath otherwise", converter = ProjectBaseConverter.class)
    private Path staticDir;

    @Option(names = {"-a", "--agents"}, description = "number of mobile agents (default is based on memory)\nDefault: ${DEFAULT-VALUE}")
    private int agents;

    @Option(names = {"-t", "--timeout"}, description = "max amount of time to attempt a server refresh (in minutes) \nDefault: ${DEFAULT-VALUE}")
    private int timeout;

    @Option(names = {"--dumpJettyBeans"}, description = "dump all the jetty beans that loaded\nDefault: ${DEFAULT-VALUE}")
    private boolean dumpJettyBeans = false;

    @Option(names = {"--strict"}, description = "If one Place fails to start, shut down the entire server\nDefault: ${DEFAULT-VALUE}")
    private boolean strictMode = false;

    @Override
    public String getCommandName() {
        return COMMAND_NAME;
    }

    @Override
    public int getDefaultPort() {
        return DEFAULT_PORT;
    }

    public EmissaryNode.Mode getMode() {
        return mode;
    }

    public Path getStaticDir() {
        return staticDir;
    }

    public int getAgents() {
        return agents;
    }

    public long getTimeout() {
        return timeout;
    }

    public boolean shouldDumpJettyBeans() {
        return dumpJettyBeans;
    }

    /**
     * If strictMode is set to true, the server will shut down if a Place fails to start
     * 
     * @return strictMode
     */
    public boolean shouldStrictMode() {
        return strictMode;
    }

    @Override
    public void setupCommand() {
        setupHttp();
        if (getTimeout() > 0) {
            System.setProperty(EmissaryNode.NODE_REFRESH_TIMEOUT_PROPERTY, String.valueOf(TimeUnit.MINUTES.toMillis(getTimeout())));
        }

        reinitLogback();
        setupServer();
    }

    public void setupServer() {
        String flavorMode;
        if (getFlavor() == null) {
            flavorMode = getMode().toString();
        } else {
            flavorMode = getMode().toString() + "," + getFlavor();
        }

        if (shouldStrictMode()) {
            System.setProperty(STRICT_STARTUP_MODE, "true");
        }

        // Must maintain insertion order
        Set<String> flavorSet = new LinkedHashSet<>();
        for (String f : flavorMode.split(",")) {
            flavorSet.add(f.toUpperCase(Locale.getDefault()));
        }

        if (flavorSet.contains("STANDALONE") && flavorSet.contains("CLUSTER")) {
            throw new IllegalArgumentException("Can not run a server in both STANDALONE and CLUSTER");
        } else {
            overrideFlavor(String.join(",", flavorSet));
        }
    }

    @Override
    protected void startService() {
        LOG.info("Running Emissary Server");
        EmissaryServer.init(this).startServer();
    }

    @Override
    protected void refreshService() {
        EmissaryResponse response = performPost(getServiceRefreshEndpoint());
        if (response.getStatus() != 200) {
            LOG.error("Failed to {} Emissary services: {}", isInvalidate() ? "invalidate" : "refresh", response.getContentString());
        } else {
            LOG.info("{} Emissary services", isInvalidate() ? "Invalidating" : "Refreshing");
        }
    }

    @Override
    protected void pauseService() {
        setServerState(Pause.PAUSE);
    }

    @Override
    protected void unpauseService() {
        setServerState(Pause.UNPAUSE);
    }

    protected void setServerState(String state) {
        LOG.debug("Setting state to {} for EmissaryServer", state);
        EmissaryResponse response = performPost("/api/" + state);
        if (response.getStatus() != 200) {
            LOG.error("Setting Emissary server state to {} failed -- {}", state, response.getContentString());
        } else {
            LOG.info("Setting Emissary server state to {} successful", state);
        }
    }
}
