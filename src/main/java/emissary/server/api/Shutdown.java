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

@Path("")
// context is api
public class Shutdown {

    private static final Logger LOG = LoggerFactory.getLogger(Shutdown.class);

    public static final String SHUTDOWN = "shutdown";

    @POST
    @Path("/" + SHUTDOWN)
    @Produces(MediaType.TEXT_HTML)
    public Response shutdownNow(@Context HttpServletRequest request) {
        return shutdown(request, false);
    }

    @POST
    @Path("/" + SHUTDOWN + "/force")
    @Produces(MediaType.TEXT_HTML)
    public Response forceShutdown(@Context HttpServletRequest request) {
        return shutdown(request, true);
    }

    protected Response shutdown(HttpServletRequest request, boolean force) {
        try {
            LOG.debug("Calling the stop method");
            // need a new thread so the response will return
            new Thread(() -> {
                try {
                    if (force) {
                        EmissaryServer.stopServerForce();
                    } else {
                        EmissaryServer.stopServer();
                    }
                } catch (Exception e) {
                    // swallow
                }
                System.exit(0);
            }).start();
            return Response.ok("Shutdown initiated. Come again soon!").build();
        } catch (Exception e) {
            LOG.warn("Exception trying to initiate shutdown: {}", e.getMessage());
            return Response.serverError().entity("Error trying to initiate shutdown").build();
        }
    }

}
