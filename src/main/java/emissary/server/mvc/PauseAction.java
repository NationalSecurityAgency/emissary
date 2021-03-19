package emissary.server.mvc;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

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
    @Path("/Pausenow" + ACTION)
    @Produces(MediaType.TEXT_HTML)
    @Template(name = "/server_message")
    public Map<String, String> pauseNow(@Context HttpServletRequest request) {
        Map<String, String> model = new HashMap<>();
        try {
            EmissaryServer.pause();
            model.put(MESSAGE, "Pausing server");
        } catch (Exception e) {
            model.put(MESSAGE, e.getMessage());
        }
        return model;
    }

    @GET
    @Path("/Unpausenow" + ACTION)
    @Produces(MediaType.TEXT_HTML)
    @Template(name = "/server_message")
    public Map<String, String> unpauseNow(@Context HttpServletRequest request) {
        Map<String, String> model = new HashMap<>();
        try {
            EmissaryServer.unpause();
            model.put(MESSAGE, "Unpausing server");
        } catch (Exception e) {
            model.put(MESSAGE, e.getMessage());
        }
        return model;
    }

    @GET
    @Path("/" + PAUSE + ACTION)
    @Produces(MediaType.TEXT_HTML)
    public Response pauseQueServer(@Context HttpServletRequest request) {
        try {
            EmissaryServer.pause();
            return Response.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("/" + UNPAUSE + ACTION)
    @Produces(MediaType.TEXT_HTML)
    public Response unpauseQueServer(@Context HttpServletRequest request) {
        try {
            EmissaryServer.unpause();
            return Response.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(e.getMessage()).build();
        }
    }
}
