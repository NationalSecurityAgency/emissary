package emissary.server.mvc;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

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
    public Map<String, String> shutdownNow(@Context HttpServletRequest request) {
        Map<String, String> model = new HashMap<>();
        try {
            LOG.debug("Calling the stop method");
            model.put("message", "Shutdown initiated. Come again soon!");
            return model;
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
