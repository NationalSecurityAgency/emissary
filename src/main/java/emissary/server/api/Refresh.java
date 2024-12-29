package emissary.server.api;

import emissary.server.EmissaryServer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("") // context is api
public class Refresh {

    private static final Logger LOG = LoggerFactory.getLogger(Refresh.class);

    @GET
    @Path("/refresh")
    @Produces(MediaType.TEXT_HTML)
    public Response action(@Context final HttpServletRequest request) {
        try {
            EmissaryServer.refresh();
            return Response.ok("Refreshing services").build();
        } catch (Exception e) {
            LOG.warn("Exception trying to reconfigure places", e);
            return Response.serverError().entity("error trying to reconfigure places").build();
        }
    }
}
