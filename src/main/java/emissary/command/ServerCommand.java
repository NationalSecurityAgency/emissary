package emissary.command;

import emissary.client.EmissaryResponse;
import emissary.command.converter.ProjectBaseConverter;
import emissary.command.validator.ServerModeValidator;
import emissary.core.EmissaryException;
import emissary.server.EmissaryServer;
import emissary.server.api.Pause;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

@Parameters(commandDescription = "Start an Emissary jetty server")
public class ServerCommand extends ServiceCommand {
    private static final Logger LOG = LoggerFactory.getLogger(ServerCommand.class);

    public static final String COMMAND_NAME = "server";

    public static final int DEFAULT_PORT = 8001;

    @Parameter(names = {"-m", "--mode"}, description = "mode: standalone or cluster", validateWith = ServerModeValidator.class)
    private String mode = "standalone";

    @Parameter(names = "--staticDir", description = "path to static assets, loaded from classpath otherwise", converter = ProjectBaseConverter.class)
    private Path staticDir;

    @Parameter(names = {"-a", "--agents"}, description = "number of mobile agents (default is based on memory)")
    private int agents;

    @Parameter(names = {"--dumpJettyBeans"}, description = "dump all the jetty beans that loaded")
    private boolean dumpJettyBeans = false;

    @Parameter(names = {"--strict"}, description = "If one Place fails to start, shut down the entire server")
    private boolean strictMode = false;


    @Override
    public String getCommandName() {
        return COMMAND_NAME;
    }

    @Override
    public int getDefaultPort() {
        return DEFAULT_PORT;
    }

    public String getMode() {
        return mode;
    }

    public Path getStaticDir() {
        return staticDir;
    }

    public int getAgents() {
        return agents;
    }

    public boolean shouldDumpJettyBeans() {
        return dumpJettyBeans;
    }

    public boolean shouldStrictMode() {
        return strictMode;
    }


    @Override
    public void setupCommand() {
        setupHttp();
        reinitLogback();
        try {
            setupServer();
        } catch (EmissaryException e) {
            LOG.error("Got an exception", e);
            throw new RuntimeException(e);
        }
    }

    public void setupServer() throws EmissaryException {
        String flavorMode = "";
        if (getFlavor() == null) {
            flavorMode = getMode().toUpperCase();
        } else {
            flavorMode = getMode().toUpperCase() + "," + getFlavor();
        }

        // Must maintain insertion order
        Set<String> flavorSet = new LinkedHashSet<>();
        for (String f : flavorMode.split(",")) {
            flavorSet.add(f.toUpperCase());
        }

        if (flavorSet.contains("STANDALONE") && flavorSet.contains("CLUSTER")) {
            throw new RuntimeException("Can not run a server in both STANDALONE and CLUSTER");
        } else {
            overrideFlavor(String.join(",", flavorSet));
        }

    }

    @Override
    protected void startService() {
        try {
            LOG.info("Running Emissary Server");
            new EmissaryServer(this).startServer();
        } catch (EmissaryException e) {
            LOG.error("Unable to start server", e);
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
