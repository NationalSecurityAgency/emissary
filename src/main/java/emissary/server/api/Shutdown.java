package emissary.server.api;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import emissary.server.EmissaryServer;
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
