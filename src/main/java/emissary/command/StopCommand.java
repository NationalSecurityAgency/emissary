package emissary.command;

import emissary.client.EmissaryResponse;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static emissary.command.ServiceCommand.SERVICE_SHUTDOWN_ENDPOINT;

/**
 * Use the {@link ServiceCommand} --stop flag to stop a running server
 */
@Deprecated
@Parameters(commandDescription = "Stop an Emissary jetty server")
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
    public void run(JCommander jc) {
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
