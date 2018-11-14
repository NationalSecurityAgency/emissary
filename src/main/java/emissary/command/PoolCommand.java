package emissary.command;

import static emissary.server.api.Pool.POOL_ENDPOINT;

import com.beust.jcommander.Parameters;
import emissary.client.EmissaryClient;
import emissary.client.response.MapResponseEntity;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Parameters(commandDescription = "List the active/idle agents in the pool for a given node or all nodes in the cluster")
public class PoolCommand extends MonitorCommand<MapResponseEntity> {

    static final Logger LOG = LoggerFactory.getLogger(PoolCommand.class);

    public static String COMMAND_NAME = "pool";

    public String getCommandName() {
        return COMMAND_NAME;
    }

    private String targetEndpoint = POOL_ENDPOINT;

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
        return this.targetEndpoint;
    }


}
