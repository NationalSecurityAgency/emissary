package emissary.command;

import emissary.client.EmissaryClient;
import emissary.client.response.PeersResponseEntity;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(description = "Run a topology starting with a HTTP call to the given node",
        subcommands = {HelpCommand.class})
public class TopologyCommand extends HttpCommand {

    static final Logger LOG = LoggerFactory.getLogger(TopologyCommand.class);

    public static String COMMAND_NAME = "topology";
    public static int DEFAULT_PORT = 8001;

    @Override
    public String getCommandName() {
        return COMMAND_NAME;
    }

    @Override
    public int getDefaultPort() {
        return DEFAULT_PORT;
    }

    @Override
    public void run(CommandLine c) {
        setup();
        String endpoint = getScheme() + "://" + getHost() + ":" + getPort() + "/api/cluster/peers";
        LOG.info("Hitting " + endpoint);
        EmissaryClient client = new EmissaryClient();
        try {
            PeersResponseEntity entity = client.send(new HttpGet(endpoint)).getContent(PeersResponseEntity.class);
            entity.dumpToConsole();
        } catch (Exception e) {
            LOG.error("Problem hitting peers endpoint: " + e.getMessage());
        }
    }


    @Override
    public void setupCommand() {
        setupTopology();
    }

    public void setupTopology() {
        setupHttp();
    }
}
