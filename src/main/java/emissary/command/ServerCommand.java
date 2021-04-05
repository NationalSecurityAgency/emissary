package emissary.command;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import emissary.client.EmissaryResponse;
import emissary.command.converter.ProjectBaseConverter;
import emissary.command.validator.ServerModeValidator;
import emissary.core.EmissaryException;
import emissary.server.EmissaryServer;
import emissary.server.api.Pause;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Parameters(commandDescription = "Start an Emissary jetty server")
public class ServerCommand extends ServiceCommand {

    public static String COMMAND_NAME = "server";

    public String getCommandName() {
        return COMMAND_NAME;
    }

    public static int DEFAULT_PORT = 8001;

    public int getDefaultPort() {
        return DEFAULT_PORT;
    }

    private static final Logger LOG = LoggerFactory.getLogger(ServerCommand.class);

    @Parameter(names = {"-m", "--mode"}, description = "mode: standalone or cluster", validateWith = ServerModeValidator.class)
    private String mode = "standalone";

    public String getMode() {
        return mode;
    }

    @Parameter(names = "--staticDir", description = "path to static assets, loaded from classpath otherwise", converter = ProjectBaseConverter.class)
    private Path staticDir;

    public Path getStaticDir() {
        return staticDir;
    }

    @Parameter(names = {"-a", "--agents"}, description = "number of mobile agents (default is based on memory)")
    private int agents;

    public int getAgents() {
        return agents;
    }

    @Parameter(names = {"--dumpJettyBeans"}, description = "dump all the jetty beans that loaded")
    private boolean dumpJettyBeans = false;

    public boolean shouldDumpJettyBeans() {
        return dumpJettyBeans;
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
