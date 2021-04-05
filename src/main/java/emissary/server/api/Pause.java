package emissary.server.api;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import emissary.core.NamespaceException;
import emissary.server.EmissaryServer;

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
