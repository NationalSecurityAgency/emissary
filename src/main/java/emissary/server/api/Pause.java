package emissary.server.api;

import emissary.core.NamespaceException;
import emissary.server.EmissaryServer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("")
// context is api
public class Pause {

    public static final String PAUSE = "pause";
    public static final String UNPAUSE = "unpause";

    @POST
    @Path("/" + PAUSE)
    @Produces(MediaType.TEXT_HTML)
    public Response pause(@Context HttpServletRequest request) {
        return doAction(true);
    }

    @POST
    @Path("/" + UNPAUSE)
    @Produces(MediaType.TEXT_HTML)
    public Response unpause(@Context HttpServletRequest request) {
        return doAction(false);
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
