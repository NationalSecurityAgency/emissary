package emissary.server.api;

import emissary.client.EmissaryClient;
import emissary.client.response.MapResponseEntity;
import emissary.core.EmissaryException;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.directory.EmissaryNode;
import emissary.server.EmissaryServer;
import emissary.util.GitRepositoryState;

import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

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

    /**
     * @deprecated in favor of the versionSimple endpoint
     */
    @GET
    @Path("/version")
    @Produces(MediaType.APPLICATION_JSON)
    @Deprecated
    public Response localVersion() {
        return Response.ok().entity(lookupVersion()).build();
    }

    @GET
    @Path("/cluster/version")
    @Produces(MediaType.APPLICATION_JSON)
    @Deprecated
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

    @GET
    @Path("/versionSimple")
    @Produces(MediaType.APPLICATION_JSON)
    public Response versionSimple() {
        try {
            return Response.ok().entity(versionResponse(false)).build();
        } catch (Exception e) {
            logger.error("Problem returning version details", e);
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("/versionDetails")
    @Produces(MediaType.APPLICATION_JSON)
    public Response versionDetails() {
        try {
            return Response.ok().entity(versionResponse(true)).build();
        } catch (Exception e) {
            logger.error("Problem returning version details", e);
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

    protected MapResponseEntity versionResponse(Boolean detail) {
        MapResponseEntity entity = new MapResponseEntity();
        entity.addKeyValue("Emissary", buildVersionResponse(GitRepositoryState.getRepositoryState("emissary.git.properties"), detail).toString());
        return entity;
    }

    protected static MapResponseEntity buildVersionResponse(GitRepositoryState gitRepoState, Boolean detail) {
        MapResponseEntity entity = new MapResponseEntity();
        entity.addKeyValue("Version", gitRepoState.getBuildVersion());
        if (detail) {
            entity.addKeyValue("Build Date", gitRepoState.getBuildTime());
            entity.addKeyValue("Build Host", gitRepoState.getBuildHost());
            entity.addKeyValue("Git Abbrev Hash", gitRepoState.getCommitIdAbbrev());
        }
        return entity;
    }
}
