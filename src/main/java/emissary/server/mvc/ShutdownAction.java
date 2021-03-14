package emissary.server.mvc;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import emissary.server.EmissaryServer;
import org.glassfish.jersey.server.mvc.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("")
// context is emissary
public class ShutdownAction {
    private static final Logger LOG = LoggerFactory.getLogger(ShutdownAction.class);

    @GET
    @Path("/Shutdown.action")
    @Produces(MediaType.TEXT_HTML)
    @Template(name = "/server_message")
    public Map<String, String> notifyShutdown(@Context HttpServletRequest request) {
        Map<String, String> model = new HashMap<>();
        model.put("message", "Starting shutdown...");
        return model;
    }

    @POST
    @Path("/Shutdown.action")
    @Produces(MediaType.TEXT_HTML)
    public Response shutdownNow(@Context HttpServletRequest request) {
        try {
            LOG.debug("Calling the stop method");
            // need a new thread so the response will return
            new Thread(() -> {
                try {
                    EmissaryServer.stopServer();
                } catch (Exception e) {
                    // swallow
                }
                System.exit(0);
            }).start();
            return Response.ok("Shutdown initiated. Come again soon!").build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

}
