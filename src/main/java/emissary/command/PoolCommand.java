package emissary.command;

import emissary.client.EmissaryClient;
import emissary.client.response.MapResponseEntity;

import com.beust.jcommander.Parameters;
import org.apache.http.client.methods.HttpGet;

import static emissary.server.api.Pool.POOL_ENDPOINT;

@Parameters(commandDescription = "List the active/idle agents in the pool for a given node or all nodes in the cluster")
public class PoolCommand extends MonitorCommand<MapResponseEntity> {

    public static final String COMMAND_NAME = "pool";

    @Override
    public String getCommandName() {
        return COMMAND_NAME;
    }

    @Override
    public MapResponseEntity sendRequest(EmissaryClient client, String endpoint) {
        return client.send(new HttpGet(endpoint)).getContent(MapResponseEntity.class);
    }

    @Override
    public void setupCommand() {
        setupHttp();
    }

    @Override
    public String getTargetEndpoint() {
        return POOL_ENDPOINT;
    }


}
