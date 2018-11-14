package emissary.server.api;

import static emissary.server.api.ApiUtils.getHostAndPort;
import static emissary.server.api.ApiUtils.lookupPeers;
import static emissary.server.api.ApiUtils.stripPeerString;

import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import emissary.client.EmissaryClient;
import emissary.client.response.PeerList;
import emissary.client.response.PeersResponseEntity;
import emissary.core.EmissaryException;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The peers Emissary API endpoint.
 */
@Path("")
// context is /api and is set in EmissaryServer.java
public class Peers {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @GET
    @Path("/peers")
    @Produces(MediaType.APPLICATION_JSON)
    public Response peers() {
        PeersResponseEntity pr = new PeersResponseEntity();
        try {
            pr = new PeersResponseEntity(new PeerList(getHostAndPort(), lookupPeers()));
        } catch (EmissaryException e) {
            logger.error("Error in lookupPeers", e);
            pr.addError(e.getMessage());
        }
        return Response.ok().entity(pr).build();
    }

    @GET
    @Path("/cluster/peers")
    @Produces(MediaType.APPLICATION_JSON)
    public Response clusterPeers() {
        PeersResponseEntity entity = new PeersResponseEntity();
        Set<String> peers;
        try {
            peers = lookupPeers();
            // Get our local mobile agents
            entity.setLocal(new PeerList(getHostAndPort(), peers));
            // Get all of our peers
            EmissaryClient client = new EmissaryClient();
            for (String peer : peers) {
                String remoteEndPoint = stripPeerString(peer) + "api/peers";
                PeersResponseEntity remoteEntity = client.send(new HttpGet(remoteEndPoint)).getContent(PeersResponseEntity.class);
                entity.append(remoteEntity);
            }
        } catch (EmissaryException e) {
            logger.error("Error in clusterPeers", e);
            entity.addError(e.getMessage());
        }

        return Response.ok().entity(entity).build();
    }
}
