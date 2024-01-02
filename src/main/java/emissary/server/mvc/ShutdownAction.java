package emissary.server.mvc;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import org.glassfish.jersey.server.mvc.Template;

import java.util.HashMap;
import java.util.Map;

@Path("")
// context is emissary
public class ShutdownAction {

    @GET
    @Path("/Shutdown.action")
    @Produces(MediaType.TEXT_HTML)
    @Template(name = "/shutdown")
    public Map<String, String> notifyShutdown(@Context HttpServletRequest request) {
        Map<String, String> model = new HashMap<>();
        model.put("message", "Starting shutdown...");
        return model;
    }

}
