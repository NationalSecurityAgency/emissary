package emissary.server.mvc;

import org.glassfish.jersey.server.mvc.Template;

import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

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
