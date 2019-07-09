package emissary.command;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import emissary.client.EmissaryClient;
import emissary.client.EmissaryResponse;
import emissary.command.converter.ProjectBaseConverter;
import emissary.command.validator.ServerModeValidator;
import emissary.core.EmissaryException;
import emissary.server.EmissaryServer;
import emissary.server.mvc.PauseAction;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Parameters(commandDescription = "Start an Emissary jetty server")
public class ServerCommand extends HttpCommand {

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

    @Parameter(names = {"--pause"}, description = "do not allow the server to take work from the feeder")
    private boolean pause = false;

    public boolean getPause() {
        return pause;
    }

    @Parameter(names = {"--unpause"}, description = "allow a paused server to take work from the feeder")
    private boolean unpause = false;

    public boolean getUnpause() {
        return unpause;
    }

    @Parameter(names = {"--dumpJettyBeans"}, description = "dump all the jetty beans that loaded")
    private boolean dumpJettyBeans = false;

    public boolean shouldDumpJettyBeans() {
        return dumpJettyBeans;
    }

    @Override
    public void run(JCommander jc) {
        setup();

        // let's check to see if the server is already running
        EmissaryClient client = new EmissaryClient();
        String endpoint = getEndpoint("/api/health");
        LOG.debug("Checking to see if Emissary Server is running at {}", endpoint);
        EmissaryResponse response = client.send(new HttpGet(endpoint));
        if (response.getStatus() == 200) {
            // the server is already running so pause/unpause or fail
            if (getPause()) {
                pauseServer();
            } else if (getUnpause()) {
                unpauseServer();
            } else {
                throw new RuntimeException("Emissary server is already running");
            }
        } else {
            // no running server so fire it up
            runServer();
        }
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

    protected void runServer() {
        try {
            LOG.info("Running Emissary Server");
            new EmissaryServer(this).startServer();
        } catch (EmissaryException e) {
            LOG.error("Unable to start server", e);
        }
    }

    protected void pauseServer() {
        serverAction(PauseAction.PAUSE);
    }

    protected void unpauseServer() {
        serverAction(PauseAction.UNPAUSE);
    }

    protected void serverAction(String action) {
        EmissaryClient client = new EmissaryClient();
        String endpoint = getEndpoint("/emissary/" + action + PauseAction.ACTION);
        LOG.debug("{} Emissary Server at {}", action, endpoint);
        EmissaryResponse response = client.send(new HttpGet(endpoint));
        if (response.getStatus() != 200) {
            LOG.error("{} Emissary Server Failed: {}", action, response.getContentString());
        } else {
            LOG.info("{} Emissary Server Successful", action);
        }
    }

    protected String getEndpoint(String endpoint) {
        return getScheme() + "://" + getHost() + ":" + getPort() + endpoint;
    }
}
