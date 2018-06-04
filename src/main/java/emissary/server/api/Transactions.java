package emissary.server.api;

import emissary.client.EmissaryClient;
import emissary.client.response.TransactionList;
import emissary.client.response.TransactionsResponseEntity;
import emissary.core.EmissaryException;
import emissary.core.Namespace;
import emissary.directory.EmissaryNode;
import emissary.server.EmissaryServer;
import emissary.transaction.TransactionManager;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static emissary.server.api.ApiUtils.lookupPeers;
import static emissary.server.api.ApiUtils.stripPeerString;
import static emissary.transaction.TransactionManager.NAMESPACE;


/**
 * The transactions Emissary API endpoint. Currently contains the local (/api/transactions) call and cluster
 * (/api/clusterTransactions) calls.
 */
@Path("")
// context is /api and is set in EmissaryServer.java
public class Transactions {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public static final String TX_ENDPOINT = "api/transactions";
    public static final String TX_CLUSTER_ENDPOINT = "api/cluster/transactions";

    @GET
    @Path("/transactions")
    @Produces(MediaType.APPLICATION_JSON)
    public Response transactions() {
        return Response.ok().entity(lookupTransactions()).build();
    }

    @GET
    @Path("/cluster/transactions")
    @Produces(MediaType.APPLICATION_JSON)
    public Response clusterTransactions() {
        try {
            // Get our local information first
            TransactionsResponseEntity entity = new TransactionsResponseEntity();
            entity.setLocal(lookupTransactions().getLocal());

            // Get all of our peers
            EmissaryClient client = new EmissaryClient();
            for (String peer : lookupPeers()) {
                String remoteEndPoint = stripPeerString(peer) + TX_ENDPOINT;
                TransactionsResponseEntity remoteEntity = client.send(new HttpGet(remoteEndPoint)).getContent(TransactionsResponseEntity.class);
                entity.append(remoteEntity);
            }
            return Response.ok().entity(entity).build();
        } catch (EmissaryException e) {
            // This should never happen since we already saw if it exists
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

    @SuppressWarnings("unchecked")
    private TransactionsResponseEntity lookupTransactions() {
        TransactionsResponseEntity entity = new TransactionsResponseEntity();
        try {
            EmissaryServer emissaryServer = (EmissaryServer) Namespace.lookup("EmissaryServer");
            EmissaryNode localNode = emissaryServer.getNode();
            String localName = localNode.getNodeName() + ":" + localNode.getNodePort();
            TransactionList transactions = new TransactionList();
            transactions.setHost(localName);

            TransactionManager manager = (TransactionManager) Namespace.lookup(NAMESPACE);
            manager.getAll().forEach(tx -> transactions.addTransaction(tx.toString()));
            entity.setLocal(transactions);
        } catch (Exception e) {
            // should never happen
            logger.error("Problem finding the emissary server or transactions in the namespace, something is majorly wrong", e);
            entity.addError("Problem finding the emissary server or transactions in the namespace: " + e.getMessage());
        }
        return entity;
    }
}
