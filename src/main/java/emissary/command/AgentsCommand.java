package emissary.command;

import static emissary.server.api.Agents.AGENTS_ENDPOINT;

import com.beust.jcommander.Parameters;
import emissary.client.EmissaryClient;
import emissary.client.response.AgentsResponseEntity;
import org.apache.http.client.methods.HttpGet;

@Parameters(commandDescription = "List all the agents for a given node or all nodes in the cluster")
public class AgentsCommand extends MonitorCommand<AgentsResponseEntity> {

    public static String COMMAND_NAME = "agents";

    public String getCommandName() {
        return COMMAND_NAME;
    }

    private String targetEndpoint = AGENTS_ENDPOINT;

    @Override
    public AgentsResponseEntity sendRequest(EmissaryClient client, String endpoint) {
        return client.send(new HttpGet(endpoint)).getContent(AgentsResponseEntity.class);
    }

    @Override
    public String getTargetEndpoint() {
        return this.targetEndpoint;
    }

}
