package emissary.server.mvc;

import java.net.URI;
import java.net.URISyntaxException;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;

import emissary.server.EmissaryServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("")
// context is emissary
public class ShutdownAction {
    private static final Logger LOG = LoggerFactory.getLogger(ShutdownAction.class);

    @GET
    @Path("/Shutdown.action")
    @Produces(MediaType.TEXT_HTML)
    public Response shutdownNow(@Context HttpServletRequest request) {
        try {
            LOG.debug("Calling the stop method");
            try {
                // redirecting back to home page so a refresh doesn't shut down again
                NewCookie cookie = new NewCookie("czar", "Emissary is shutting down.  Come again soon!", "/", "", "czar says", 1000000, false);
                return Response.seeOther(new URI("/")).cookie(cookie).build();
            } catch (URISyntaxException e) {
                e.printStackTrace();
                return Response.serverError().entity(e.getMessage()).build();
            }
        } finally {
            // need a new thread so the response will return
            new Thread(() -> {
                try {
                    EmissaryServer.stopServer();
                } catch (Exception e) {
                    // swallow
                }
                System.exit(0);
            }).start();
        }
    }

}
