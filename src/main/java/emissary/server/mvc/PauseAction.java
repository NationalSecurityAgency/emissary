package emissary.server.mvc;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.server.mvc.Viewable;

import java.util.HashMap;
import java.util.Map;

@Path("")
// context is emissary
public class PauseAction {

    public static final String MESSAGE = "message";

    @GET
    @Path("/Pause.action")
    @Produces(MediaType.TEXT_HTML)
    public Response notifyPause(@Context HttpServletRequest request) {
        if (!request.isUserInRole("admin") && !request.isUserInRole("emissary")) {
            return Response.status(Response.Status.FORBIDDEN).entity("Insufficient privileges").build();
        }
        return Response.ok(new Viewable("/pause", generateMessage("Pausing server..."))).build();
    }

    @GET
    @Path("/Unpause.action")
    @Produces(MediaType.TEXT_HTML)
    public Response notifyUnpause(@Context HttpServletRequest request) {
        if (!request.isUserInRole("admin") && !request.isUserInRole("emissary")) {
            return Response.status(Response.Status.FORBIDDEN).entity("Insufficient privileges").build();
        }
        return Response.ok(new Viewable("/unpause", generateMessage("Unpausing server..."))).build();
    }

    private static Map<String, String> generateMessage(String message) {
        Map<String, String> model = new HashMap<>();
        model.put(MESSAGE, message);
        return model;
    }

}
