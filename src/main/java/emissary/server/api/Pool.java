package emissary.server.api;

import static emissary.server.api.ApiUtils.lookupPeers;
import static emissary.server.api.ApiUtils.stripPeerString;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import emissary.client.EmissaryClient;
import emissary.client.response.MapResponseEntity;
import emissary.core.EmissaryException;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.directory.EmissaryNode;
import emissary.pool.AgentPool;
import emissary.pool.MobileAgentFactory;
import emissary.server.EmissaryServer;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The agents Emissary API endpoint. Currently contains the local (/api/pool) call and cluster (/api/clusterPool) calls.
 */
@Path("")
// context is /api and is set in EmissaryServer.java
public class Pool {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public static final String POOL_ENDPOINT = "api/pool";
    public static final String POOL_CLUSTER_ENDPOINT = "api/cluster/pool";

    @GET
    @Path("/pool")
    @Produces(MediaType.APPLICATION_JSON)
    public Response pool() {
        return Response.ok().entity(this.lookupPool()).build();
    }

    @GET
    @Path("/cluster/pool")
    @Produces(MediaType.APPLICATION_JSON)
    public Response clusterPool() {
        MapResponseEntity entity = new MapResponseEntity();
        try {
            // Get our local mobile agents
            entity.append(this.lookupPool());
            // Get all of our peers agents
            EmissaryClient client = new EmissaryClient();
            for (String peer : lookupPeers()) {
                String remoteEndPoint = stripPeerString(peer) + "api/pool";
                MapResponseEntity remoteEntity = client.send(new HttpGet(remoteEndPoint)).getContent(MapResponseEntity.class);
                entity.append(remoteEntity);
            }
            return Response.ok().entity(entity).build();
        } catch (EmissaryException e) {
            // This should never happen since we already saw if it exists
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

    private MapResponseEntity lookupPool() {
        MapResponseEntity entity = new MapResponseEntity();
        // Get the server so we can be ready to talk to ourself then the peers
        try {
            EmissaryServer emissaryServer = (EmissaryServer) Namespace.lookup("EmissaryServer");
            EmissaryNode localNode = emissaryServer.getNode();
            String nodeName = localNode.getNodeName() + ":" + localNode.getNodePort();
            int active = 0;
            int idle = 0;
            try {
                for (int i = 0; i < AgentPool.lookup().getMaxTotal(); i++) {
                    String agentKey = MobileAgentFactory.AGENT_NAME + "-" + String.format("%02d", i);
                    if (Namespace.exists(agentKey)) {
                        if (Namespace.lookup(agentKey).toString().startsWith("Idle")) {
                            idle++;
                        } else {
                            active++;
                        }
                    } else {
                        // TODO Handle this any better?
                        logger.error("Missing an agent in the Namespace: {}", agentKey);
                        entity.addError("Missing an agent in the Namespace: " + agentKey);
                        idle++;
                    }
                }
                entity.addKeyValue(nodeName, "Poolsize active/idle: " + active + "/" + idle);
            } catch (EmissaryException e) {
                // TODO Figure out what we really want to do here in the event a node crashes
                logger.error("Problem when looking up the pool", e);
                entity.addError("Problem when looking up the pool on " + localNode + ": " + e.getMessage());
            }
        } catch (NamespaceException e) {
            // should never happen
            logger.error("Problem finding the emissary server in the namespace, something is majorly wrong", e);
            entity.addError("Problem finding the emissary server in the namespace: " + e.getMessage());
        }
        return entity;
    }
}
