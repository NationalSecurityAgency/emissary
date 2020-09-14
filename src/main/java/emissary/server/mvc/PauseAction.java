package emissary.server.mvc;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import emissary.server.EmissaryServer;

@Path("")
// context is emissary
public class PauseAction {

    public static final String ACTION = ".action";
    public static final String PAUSE = "Pause";
    public static final String UNPAUSE = "Unpause";

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
