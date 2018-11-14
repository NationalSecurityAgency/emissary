package emissary.server.mvc.internal;

import static emissary.server.mvc.adapters.PlaceStarterAdapter.CP_CLASS_NAME;
import static emissary.server.mvc.adapters.PlaceStarterAdapter.CP_DIRECTORY;
import static emissary.server.mvc.adapters.PlaceStarterAdapter.CP_LOCATION;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import emissary.admin.PlaceStarter;
import emissary.place.IServiceProviderPlace;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("")
// context is /emissary, set in EmissaryServer
public class CreatePlaceAction {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    public static final String EMPTY_PARAM_MSG = "Required query parameters were blank or null, verify the parameters";
    public static final String PLACE_STARTER_ERR = "Cannot create the requested place";

    /*
     * <!-- Create and register a place on a remote machine --> <Use-Case source="*" action="/CreatePlace.action"> <Work
     * type="Bean" target="emissary.comms.http.worker.LogWorker"/> <Work type="Bean"
     * target="emissary.comms.http.worker.CreatePlaceWorker"/> <View status="0" view="/createplace.jsp"/> <View status="-1"
     * view="/error.jsp"/> </Use-Case>
     */

    @POST
    @Path("/CreatePlace.action")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_PLAIN)
    public Response createPlace(@FormParam(CP_CLASS_NAME) String className, @FormParam(CP_LOCATION) String location,
            @FormParam(CP_DIRECTORY) String directory) {
        if (StringUtils.isBlank(className) || StringUtils.isBlank(location) || StringUtils.isBlank(directory)) {
            logger.error("{}: className - {}. location - {}, directory - {}", EMPTY_PARAM_MSG, className, location, directory);
            return Response.serverError().entity(EMPTY_PARAM_MSG).build();
        }


        try {
            final IServiceProviderPlace thePlace = PlaceStarter.createPlace(location, className, directory);
            // previous CreatePlaceWorker returned like this
            // Put new place on the request going forward
            // req.setAttribute(PlaceStarterAdapter.PLACE_OBJECT, thePlace);
            if (thePlace != null) {
                return Response.ok().entity(thePlace.toString()).build();
            } else {
                logger.error(PLACE_STARTER_ERR + "class - {}, location - {}, directory - {}", className, location, directory);
                return Response.serverError()
                        .entity(PLACE_STARTER_ERR + "class - " + className + ", location - " + location + ", directory - " + directory).build();
            }
        } catch (Exception e) {
            // TODO Needed because we don't do any validation of the location string in the PlaceStarter, see TODOs
            // there
            logger.error("There was an error trying to create the place. class - {}, location  - {}, directory - {}", className, location, directory,
                    e);
            return Response.serverError()
                    .entity(PLACE_STARTER_ERR + "class - " + className + ", location - " + location + ", directory - " + directory).build();
        }
    }
}
