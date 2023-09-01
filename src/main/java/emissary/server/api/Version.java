package emissary.server.api;

import emissary.client.EmissaryClient;
import emissary.client.response.MapResponseEntity;
import emissary.core.EmissaryException;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.directory.EmissaryNode;
import emissary.server.EmissaryServer;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

import static emissary.server.api.ApiUtils.lookupPeers;
import static emissary.server.api.ApiUtils.stripPeerString;

/**
 * The version Emissary API endpoint. Currently contains the local (/api/version) call and cluster (/api/clusterVersion)
 * calls.
 */
@Path("")
// context is /api
public class Version {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private static final emissary.util.Version version = new emissary.util.Version();

    @GET
    @Path("/version")
    @Produces(MediaType.APPLICATION_JSON)
    public Response localVersion() {
        return Response.ok().entity(lookupVersion()).build();
    }

    @GET
    @Path("/cluster/version")
    @Produces(MediaType.APPLICATION_JSON)
    public Response clusterVersion() {
        try {
            // Get our local information first
            MapResponseEntity entity = new MapResponseEntity();
            entity.append(lookupVersion());

            // Get all of our peers
            Set<String> peers = lookupPeers();
            EmissaryClient client = new EmissaryClient();
            for (String peer : peers) {
                String remoteEndPoint = stripPeerString(peer) + "api/version";
                MapResponseEntity remoteEntity = client.send(new HttpGet(remoteEndPoint)).getContent(MapResponseEntity.class);
                entity.append(remoteEntity);
            }
            return Response.ok().entity(entity).build();
        } catch (EmissaryException e) {
            // This should never happen since we already saw if it exists
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

    private MapResponseEntity lookupVersion() {
        MapResponseEntity entity = new MapResponseEntity();
        // Get the server so we can be ready to talk to ourself then the peers
        try {
            EmissaryServer emissaryServer = (EmissaryServer) Namespace.lookup("EmissaryServer");
            EmissaryNode localNode = emissaryServer.getNode();
            int localPort = localNode.getNodePort();
            String localName = localNode.getNodeName();
            entity.addKeyValue(localName + ":" + localPort, version.getVersion());
        } catch (NamespaceException e) {
            // should never happen
            logger.error("Problem finding the emissary server in the namespace, something is majorly wrong", e);
            entity.addError(e.getMessage());
        }
        return entity;
    }

}
