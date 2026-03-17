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
public class ShutdownAction {

    @GET
    @Path("/Shutdown.action")
    @Produces(MediaType.TEXT_HTML)
    public Response notifyShutdown(@Context HttpServletRequest request) {
        if (!request.isUserInRole("admin") && !request.isUserInRole("emissary")) {
            return Response.status(Response.Status.FORBIDDEN).entity("Insufficient privileges").build();
        }
        Map<String, String> model = new HashMap<>();
        model.put("message", "Starting shutdown...");
        return Response.ok(new Viewable("/shutdown", model)).build();
    }

}
