package emissary.server.api;

import static emissary.server.api.ApiUtils.lookupPeers;
import static emissary.server.api.ApiUtils.stripPeerString;

import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import emissary.client.EmissaryClient;
import emissary.client.response.PlaceList;
import emissary.client.response.PlacesResponseEntity;
import emissary.core.EmissaryException;
import emissary.core.Namespace;
import emissary.directory.EmissaryNode;
import emissary.server.EmissaryServer;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The agents Emissary API endpoint. Currently contains the local (/api/places) call and cluster (/api/clusterPlaces)
 * calls.
 */
@Path("")
// context is /api and is set in EmissaryServer.java
public class Places {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public static final String PLACES_ENDPOINT = "api/agents";


    @GET
    @Path("/places")
    @Produces(MediaType.APPLICATION_JSON)
    public Response places() {
        return Response.ok().entity(lookupPlaces()).build();
    }

    @GET
    @Path("/cluster/places")
    @Produces(MediaType.APPLICATION_JSON)
    public Response clusterPlaces() {
        try {
            // Get our local information first
            PlacesResponseEntity entity = new PlacesResponseEntity();
            entity.setLocal(lookupPlaces().getLocal());

            // Get all of our peers
            Set<String> peers = lookupPeers();
            EmissaryClient client = new EmissaryClient();
            for (String peer : peers) {
                String remoteEndPoint = stripPeerString(peer) + PLACES_ENDPOINT;
                PlacesResponseEntity remoteEntity = client.send(new HttpGet(remoteEndPoint)).getContent(PlacesResponseEntity.class);
                entity.append(remoteEntity);
            }
            return Response.ok().entity(entity).build();
        } catch (EmissaryException e) {
            // This should never happen since we already saw if it exists
            return Response.serverError().entity(e.getMessage()).build();
        }
    }


    private PlacesResponseEntity lookupPlaces() {
        PlacesResponseEntity entity = new PlacesResponseEntity();
        try {
            EmissaryServer emissaryServer = (EmissaryServer) Namespace.lookup("EmissaryServer");
            EmissaryNode localNode = emissaryServer.getNode();
            String localName = localNode.getNodeName() + ":" + localNode.getNodePort();
            PlaceList places = new PlaceList();
            places.setHost(localName);
            // Get all of our places
            for (String key : Namespace.keySet()) {
                if (key.contains("Place") || key.contains("Client")) {
                    places.addPlace(Namespace.lookup(key).toString());
                }
            }
            entity.setLocal(places);
        } catch (EmissaryException e) {
            // should never happen
            logger.error("Problem finding the emissary server or places in the namespace, something is majorly wrong", e);
            entity.addError("Problem finding the emissary server or places in the namespace: " + e.getMessage());
        }
        return entity;
    }
}
