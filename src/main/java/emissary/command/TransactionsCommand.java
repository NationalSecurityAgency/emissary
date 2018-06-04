package emissary.command;


import com.beust.jcommander.Parameters;
import emissary.client.EmissaryClient;
import emissary.client.response.TransactionsResponseEntity;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static emissary.server.api.Transactions.TX_ENDPOINT;

@Parameters(commandDescription = "List all the transactions for a given node or all nodes in the cluster")
public class TransactionsCommand extends MonitorCommand<TransactionsResponseEntity> {
    private static final Logger LOG = LoggerFactory.getLogger(TransactionsCommand.class);

    public static String COMMAND_NAME = "transactions";

    public String getCommandName() {
        return COMMAND_NAME;
    }

    private String targetEndpoint = TX_ENDPOINT;

    @Override
    public TransactionsResponseEntity sendRequest(EmissaryClient client, String endpoint) {
        return client.send(new HttpGet(endpoint)).getContent(TransactionsResponseEntity.class);
    }

    @Override
    public String getTargetEndpoint() {
        return this.targetEndpoint;
    }

}
