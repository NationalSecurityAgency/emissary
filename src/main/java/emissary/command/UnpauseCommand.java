package emissary.command;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameters;
import emissary.client.EmissaryClient;
import emissary.client.EmissaryResponse;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Parameters(commandDescription = "Allow an Emissary to start taking work")
public class UnpauseCommand extends HttpCommand {

    public static String COMMAND_NAME = "unpause";

    public String getCommandName() {
        return COMMAND_NAME;
    }

    public static int DEFAULT_PORT = 8001;

    public int getDefaultPort() {
        return DEFAULT_PORT;
    }

    private static final Logger LOG = LoggerFactory.getLogger(UnpauseCommand.class);


    @Override
    public void run(JCommander jc) {
        setup();
        LOG.info("Unpausing Emissary QueServer at {}://{}:{}", getScheme(), getHost(), getPort());
        EmissaryClient client = new EmissaryClient();
        String endpoint = getScheme() + "://" + getHost() + ":" + getPort() + "/emissary/Unpause.action";
        EmissaryResponse response = client.send(new HttpGet(endpoint));
        if (response.getStatus() != 200) {
            LOG.error("Problem unpausing");
            LOG.error(response.getContentString());
        } else {
            LOG.info("Emissary QueServer unpaused");
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
