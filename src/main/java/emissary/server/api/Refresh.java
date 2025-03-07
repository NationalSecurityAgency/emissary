package emissary.server.api;

import emissary.server.EmissaryServer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

@Path("") // context is api
public class Refresh {

    private static final Logger LOG = LoggerFactory.getLogger(Refresh.class);

    public static final String INVALIDATE = "invalidate";
    public static final String REFRESH = "refresh";

    @POST
    @Path("/" + INVALIDATE)
    @Produces(MediaType.TEXT_HTML)
    public Response invalidatePlaces(@Context HttpServletRequest request) {
        try {
            EmissaryServer.invalidate();
            return Response.ok("Invalidated services").build();
        } catch (Exception e) {
            LOG.warn("Exception trying to invalidate places", e);
            return Response.serverError().entity("error trying to invalidate places").build();
        }
    }

    @POST
    @Path("/" + REFRESH)
    @Produces(MediaType.TEXT_HTML)
    public Response refreshPlaces(@Context HttpServletRequest request) {
        try {
            var unused = CompletableFuture.runAsync(EmissaryServer::refresh);
            return Response.ok("Refreshing services").build();
        } catch (RuntimeException e) {
            LOG.warn("Exception trying to refresh places", e);
            return Response.serverError().entity("error trying to reconfigure places").build();
        }
    }
}
