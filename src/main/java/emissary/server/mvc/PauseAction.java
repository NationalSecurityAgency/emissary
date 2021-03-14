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

import emissary.core.NamespaceException;
import emissary.server.EmissaryServer;
import org.glassfish.jersey.server.mvc.Template;

@Path("")
// context is emissary
public class PauseAction {

    public static final String ACTION = ".action";
    public static final String PAUSE = "Pause";
    public static final String UNPAUSE = "Unpause";
    public static final String MESSAGE = "message";

    @GET
    @Path("/" + PAUSE + ACTION)
    @Produces(MediaType.TEXT_HTML)
    @Template(name = "/server_message")
    public Map<String, String> notifyPause(@Context HttpServletRequest request) {
        return generateMessage("Pausing server...");
    }

    @GET
    @Path("/" + UNPAUSE + ACTION)
    @Produces(MediaType.TEXT_HTML)
    @Template(name = "/server_message")
    public Map<String, String> notifyUnpause(@Context HttpServletRequest request) {
        return generateMessage("Unpausing server...");
    }

    @POST
    @Path("/" + PAUSE + ACTION)
    @Produces(MediaType.TEXT_HTML)
    public Response pause(@Context HttpServletRequest request) {
        return doAction(true);
    }

    @POST
    @Path("/" + UNPAUSE + ACTION)
    @Produces(MediaType.TEXT_HTML)
    public Response unpause(@Context HttpServletRequest request) {
        return doAction(false);
    }

    private Map<String, String> generateMessage(String message) {
        Map<String, String> model = new HashMap<>();
        model.put(MESSAGE, message);
        return model;
    }

    private Response doAction(boolean pause) {
        try {
            return Response.ok(pause ? pause() : unpause()).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

    protected String pause() throws NamespaceException {
        EmissaryServer.pause();
        return "server paused";
    }

    protected String unpause() throws NamespaceException {
        EmissaryServer.unpause();
        return "server unpaused";
    }
}
