package emissary.command;

import emissary.client.EmissaryClient;
import emissary.client.response.AgentsResponseEntity;

import com.beust.jcommander.Parameters;
import org.apache.http.client.methods.HttpGet;
import picocli.CommandLine.Command;

import static emissary.server.api.Agents.AGENTS_ENDPOINT;

@Parameters(commandDescription = "List all the agents for a given node or all nodes in the cluster")
@Command(description = "List all the agents for a given node or all nodes in the cluster",
        subcommands = {HelpCommand.class})
public class AgentsCommand extends MonitorCommand<AgentsResponseEntity> implements Runnable {

    public static String COMMAND_NAME = "agents";

    @Override
    public String getCommandName() {
        return COMMAND_NAME;
    }

    @Override
    public AgentsResponseEntity sendRequest(EmissaryClient client, String endpoint) {
        return client.send(new HttpGet(endpoint)).getContent(AgentsResponseEntity.class);
    }

    @Override
    public String getTargetEndpoint() {
        return AGENTS_ENDPOINT;
    }

    @Override
    public void run() {

    }
}
