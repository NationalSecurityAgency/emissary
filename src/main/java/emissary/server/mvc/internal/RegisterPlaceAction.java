package emissary.server.mvc.internal;

import static emissary.server.mvc.adapters.DirectoryAdapter.TARGET_DIRECTORY;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import emissary.server.mvc.adapters.DirectoryAdapter;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("")
// context is /emissary, set in EmissaryServer
public class RegisterPlaceAction {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    public static final String ADD_SUCCESS = "Directory.addPlace succeeded";
    public static final String ADD_FAILURE = "Directory.addPlace failed";
    public static final String CALL_FAILURE = "Could not call addPlace with directory: ";

    /*
     * <!-- Register place with the specified directory, primarily used for the local DirectoryPlace to addPlace with a
     * remote peer relay --> <Use-Case source="*" action="/RegisterPlace.action"> <Work type="Bean"
     * target="emissary.comms.http.worker.LogWorker"/> <Work type="Bean"
     * target="emissary.comms.http.worker.RegisterPlaceWorker"/> <View status="0" view="/success.jsp"/> <View status="-1"
     * view="/error.jsp"/> </Use-Case>
     */

    // TODO This is an initial crack at the new endpoint, I haven't seen it called an am unsure when/if it does
    // Take a look at outboundAddPlaces in DirectoryAdaptor. The way that the parameters are being setup and added
    // needs to be re-worked. Look at AddPlacesRequestBean in DirectoryAdaptor to see how the old params were being
    // consumted.
    // Currently, we run with the disableAddPlaces flag set to true so this isn't used in current production. However,
    // should we want to re-enable the moveTo, this logic will need to be refactored and made performant. The old logic
    // used a copy-on-write array and each time a place would go to look for a key, it would cause major performance
    // degradation.
    @POST
    @Path("/RegisterPlace.action")
    @Produces(MediaType.TEXT_PLAIN)
    public Response registerPlace(@Context HttpServletRequest request, @FormParam(TARGET_DIRECTORY) String directory) {
        if (StringUtils.isBlank(directory)) {
            logger.error(CreatePlaceAction.EMPTY_PARAM_MSG);
            return Response.serverError().entity(CreatePlaceAction.EMPTY_PARAM_MSG).build();
        }

        try {
            final DirectoryAdapter da = new DirectoryAdapter();
            // TODO look at breaking all the logic/param maniupulation in this class
            final boolean status = da.inboundAddPlaces(request, directory);

            if (status) {
                logger.debug(ADD_SUCCESS);
                return Response.ok().entity(ADD_SUCCESS).build();
            } else {
                logger.error(ADD_FAILURE);
                return Response.serverError().entity(ADD_FAILURE).build();
            }
        } catch (Exception e) {
            logger.error(CALL_FAILURE + directory, e);
            return Response.serverError().entity(CALL_FAILURE + directory).build();
        }
    }
}
