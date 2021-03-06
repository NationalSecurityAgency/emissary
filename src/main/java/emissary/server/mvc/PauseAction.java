package emissary.server.mvc;

import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import org.glassfish.jersey.server.mvc.Template;

@Path("")
// context is emissary
public class PauseAction {

    public static final String MESSAGE = "message";

    @GET
    @Path("/Pause.action")
    @Produces(MediaType.TEXT_HTML)
    @Template(name = "/pause")
    public Map<String, String> notifyPause(@Context HttpServletRequest request) {
        return generateMessage("Pausing server...");
    }

    @GET
    @Path("/Unpause.action")
    @Produces(MediaType.TEXT_HTML)
    @Template(name = "/unpause")
    public Map<String, String> notifyUnpause(@Context HttpServletRequest request) {
        return generateMessage("Unpausing server...");
    }

    private Map<String, String> generateMessage(String message) {
        Map<String, String> model = new HashMap<>();
        model.put(MESSAGE, message);
        return model;
    }

}
