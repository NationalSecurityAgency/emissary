package emissary.server.mvc.internal;

import emissary.core.EmissaryException;
import emissary.core.Namespace;
import emissary.pickup.WorkBundle;
import emissary.pickup.WorkSpace;
import emissary.server.mvc.adapters.RequestUtil;
import emissary.util.web.HtmlEscaper;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("")
// context is /emissary, set in EmissaryServer
public class WorkSpaceClientSpaceTakeAction {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    public static final String CLIENT_NAME = "pickUpClientName";
    public static final String SPACE_NAME = "workSpaceName";
    public static final String WORK_BUNDLE_OBJ = "tpObj";
    public static final String WORK_BUNDLE_XML = "tpXml";
    public static final String WORK_BUNDLE_ID = "tpId";
    public static final String WORK_BUNDLE_STATUS = "tpStatus";
    public static final String DATA_IDENTIFIER = "tdataId";

    /*
     * <!-- Take data from a WorkSpace --> <Use-Case source="*" action="/WorkSpaceClientSpaceTake.action"> <Work type="Bean"
     * target="emissary.comms.http.worker.LogWorker"/> <Work type="Bean"
     * target="emissary.comms.http.worker.WorkSpaceClientSpaceTakeWorker"/> <View status="0" view="/take.jsp"/> <View
     * status="-1" view="/error.jsp"/> </Use-Case>
     */

    @POST
    @Path("/WorkSpaceClientSpaceTake.action")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_XML)
    public Response clientSpaceTake(@FormParam(CLIENT_NAME) String placeName, @FormParam(SPACE_NAME) String spaceName) {
        String cleanPlaceName = RequestUtil.sanitizeParameter(placeName);
        String cleanSpaceName = RequestUtil.sanitizeParameter(spaceName);
        if (StringUtils.isBlank(cleanPlaceName) || StringUtils.isBlank(cleanSpaceName)) {
            return Response.serverError().entity(HtmlEscaper.escapeHtml(
                    "Bad params: " + CLIENT_NAME + " - " + cleanPlaceName + ", or " + SPACE_NAME + " - " + cleanSpaceName)).build();
        }

        try {
            return doClientSpaceTake(cleanPlaceName, cleanSpaceName);
        } catch (EmissaryException | IllegalArgumentException e) {
            logger.warn("There was an exception in the WorkSpaceClientSpaceTake", e);
            return Response.serverError().entity("There was an exception in the WorkSpaceClientSpaceTake").build();
        }
    }

    private Response doClientSpaceTake(String placeName, String spaceName) throws EmissaryException {
        // TODO Figure out why we have to remove the key prefix now
        String workspaceKey = spaceName.substring("WORKSPACE.WORK_SPACE.INPUT.".length());
        final WorkSpace space = (WorkSpace) Namespace.lookup(workspaceKey);
        if (space == null) {
            throw new IllegalArgumentException("No WorkSpace found using name " + spaceName);
        }

        final WorkBundle path = space.take(placeName);
        if (path == null) {
            throw new EmissaryException("WorkSpaceClientSpaceTakeWorker failed, no bundle to retrieve");
        }

        // old return from WorkSpaceClientSpaceTakeWorker
        // return WORKER_SUCCESS;
        return Response.ok().entity(path.toXml()).build();
    }
}
