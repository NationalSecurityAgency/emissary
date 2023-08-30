package emissary.command;

import emissary.client.EmissaryClient;
import emissary.client.response.AgentsResponseEntity;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import picocli.CommandLine.Command;

import static emissary.server.api.Agents.AGENTS_ENDPOINT;

@Command(description = "List all the agents for a given node or all nodes in the cluster",
        subcommands = {HelpCommand.class})
public class AgentsCommand extends MonitorCommand<AgentsResponseEntity> {

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
}
