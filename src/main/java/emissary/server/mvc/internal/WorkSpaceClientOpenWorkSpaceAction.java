package emissary.server.mvc.internal;

import static emissary.server.mvc.adapters.WorkSpaceAdapter.CLIENT_NAME;
import static emissary.server.mvc.adapters.WorkSpaceAdapter.SPACE_NAME;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.pickup.IPickUpSpace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Web-tier worker to call the WorkSpaceClientPlace.openSpace coming in on a remote request
 */
@Path("")
// context is /emissary, set in EmissaryServer
public class WorkSpaceClientOpenWorkSpaceAction {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /*
     * <!-- Notify a PickUpPlaceClient of a new WorkSpace --> <Use-Case source="*"
     * action="/WorkSpaceClientOpenWorkSpace.action"> <Work type="Bean" target="emissary.comms.http.worker.LogWorker"/>
     * <Work type="Bean" target="emissary.comms.http.worker.WorkSpaceClientOpenSpaceWorker"/> <View status="0"
     * view="/success.jsp"/> <View status="-1" view="/error.jsp"/> </Use-Case>
     */

    @POST
    @Path("/WorkSpaceClientOpenWorkSpace.action")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_PLAIN)
    public Response openSpaceWorker(@FormParam(CLIENT_NAME) String placeName, @FormParam(SPACE_NAME) String spaceName) {
        try {
            return openTheSpace(placeName, spaceName);
        } catch (IllegalArgumentException | NamespaceException e) {
            logger.error("Problem opening the WorkSpace: {} - {}, {} - {}", CLIENT_NAME, placeName, SPACE_NAME, spaceName, e);
            return Response.serverError().entity("There was a problem opening the workspace: " + e.getMessage()).build();
        }
    }

    private Response openTheSpace(String placeName, String spaceName) throws IllegalArgumentException, NamespaceException {
        // TODO Figure out why we have to remove the key prefix now
        // INITIAL.FILE_PICK_UP_CLIENT.INPUT.http://localhost:8001/FilePickUpClient
        String placeKey = placeName.substring("INITIAL.FILE_PICK_UP_CLIENT.INPUT.".length());
        final IPickUpSpace place = (IPickUpSpace) Namespace.lookup(placeKey);
        if (place == null) {
            throw new IllegalArgumentException("No client place found using name " + placeName);
        }

        logger.debug("Notified {} to open space at {}", placeName, spaceName);
        place.openSpace(spaceName);
        return Response.ok().entity("Successfully opened the workspace").build();
    }

}
