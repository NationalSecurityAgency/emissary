package emissary.server.mvc.internal;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import emissary.core.NamespaceException;
import emissary.directory.IDirectoryPlace;
import emissary.place.IServiceProviderPlace;
import emissary.server.mvc.NamespaceAction;
import emissary.server.mvc.adapters.HeartbeatAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("")
// context is emissary
public class HeartbeatAction {

    private static final Logger LOG = LoggerFactory.getLogger(NamespaceAction.class);

    private Response processHeartbeat(final String fromPlace, final String toPlace) {
        final IServiceProviderPlace thePlace;
        try {
            final HeartbeatAdapter da = new HeartbeatAdapter();
            thePlace = da.inboundHeartbeat(fromPlace, toPlace);
        } catch (NamespaceException e) {
            return Response.status(500).entity("Heartbeat failed with namespace exception " + e.getMessage()).build();
        } catch (IllegalArgumentException e) {
            LOG.error("Heartbeat failed", e);
            return Response.status(500).entity("Heartbeat failed with illegal argument " + e.getMessage()).build();
        }

        // Make sure it is running if the endpoint is a directory place
        if (thePlace instanceof IDirectoryPlace && !((IDirectoryPlace) thePlace).isRunning()) {
            LOG.error("Heartbeat failed, directory not running " + thePlace.getKey());
            return Response.status(500).entity("Heartbeat failed, directory not running " + thePlace.getKey()).build();
        }

        LOG.debug("Heartbeat success: " + thePlace);
        // Custom response object not needed today, simply toString the DirectoryPlace on success
        return Response.ok().entity(thePlace.toString()).build();
    }

    @POST
    @Path("/Heartbeat.action")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_PLAIN)
    public Response heartbeatPost(@FormParam(HeartbeatAdapter.FROM_PLACE_NAME) String fromPlace,
            @FormParam(HeartbeatAdapter.TO_PLACE_NAME) String toPlace) {
        return processHeartbeat(fromPlace, toPlace);
    }

}
