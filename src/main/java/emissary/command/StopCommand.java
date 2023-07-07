package emissary.command;

import emissary.client.EmissaryResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;

import static emissary.command.ServiceCommand.SERVICE_SHUTDOWN_ENDPOINT;

/**
 * Use the {@link ServiceCommand} --stop flag to stop a running server
 */
@Deprecated
@Command(description = "Stop an Emissary jetty server", subcommands = {HelpCommand.class})
public class StopCommand extends HttpCommand {

    public static String COMMAND_NAME = "stop";

    @Override
    public String getCommandName() {
        return COMMAND_NAME;
    }

    public static int DEFAULT_PORT = 8001;

    @Override
    public int getDefaultPort() {
        return DEFAULT_PORT;
    }

    public String getShutdownEndpoint() {
        return SERVICE_SHUTDOWN_ENDPOINT;
    }

    private static final Logger LOG = LoggerFactory.getLogger(StopCommand.class);


    @Override
    public void run() {
        setup();
        LOG.info("Stopping Emissary Server at {}://{}:{}", getScheme(), getHost(), getPort());
        EmissaryResponse response = performPost(getShutdownEndpoint());
        if (response.getStatus() != 200) {
            LOG.error("Problem shutting down");
            LOG.error(response.getContentString());
        } else {
            LOG.info("EmissaryServer stopped");
        }
    }


    @Override
    public void setupCommand() {
        setupHttp();
        reinitLogback();
        setupStop();
    }

    public void setupStop() {

    }
}
