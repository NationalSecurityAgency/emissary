package emissary.server.mvc.internal;

import emissary.directory.IRemoteDirectory;
import emissary.directory.KeyManipulator;
import emissary.log.MDCConstants;
import emissary.server.mvc.adapters.RequestUtil;

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
import org.slf4j.MDC;

import java.util.List;

import static emissary.server.mvc.adapters.DirectoryAdapter.ADD_KEY;
import static emissary.server.mvc.adapters.DirectoryAdapter.TARGET_DIRECTORY;

@Path("")
// context is /emissary, set in EmissaryServer
public class DeregisterPlaceAction {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /*
     * <!-- Deregister place from the specified directory, primarily used for the local DirectoryPlace to removePlace from
     * peer or relay --> <Use-Case source="*" action="/DeregisterPlace.action"> <Work type="Bean"
     * target="emissary.comms.http.worker.LogWorker"/> <Work type="Bean"
     * target="emissary.comms.http.worker.DeregisterPlaceWorker"/> <View status="0" view="/success.jsp"/> <View status="-1"
     * view="/error.jsp"/> </Use-Case>
     */
    // call like this
    // http://localhost:8001/emissary/DeregisterPlace.action?targetDir=http://localhost:8001/DirectoryPlace&dirAddKey=UPPER_CASE.TO_LOWER.TRANSFORM.http://localhost:8001/ToLowerPlace
    // haven't tried with more than one dirAddKey parameter
    @POST
    @Path("/DeregisterPlace.action")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_PLAIN)
    public Response deregisterPlace(@FormParam(TARGET_DIRECTORY) String targetDir, @FormParam(ADD_KEY) List<String> dirAddKeys) {
        String cleanTargetDirectory = RequestUtil.sanitizeParameter(targetDir);
        List<String> cleanDirAddKeys = RequestUtil.sanitizeParametersStringList(dirAddKeys);
        if (StringUtils.isBlank(cleanTargetDirectory) || cleanDirAddKeys.isEmpty()) {
            return Response.serverError().entity(
                    "Bad params: " + TARGET_DIRECTORY + " - " + cleanTargetDirectory + ", or " + ADD_KEY + " - " + cleanDirAddKeys).build();
        }

        final IRemoteDirectory directory = new IRemoteDirectory.Lookup().getLocalDirectory(cleanTargetDirectory);
        if (directory == null) {
            logger.error("No directory found using name {}", cleanTargetDirectory);
            return Response.serverError().entity("No directory found using name " + cleanTargetDirectory).build();
        }

        MDC.put(MDCConstants.SERVICE_LOCATION, KeyManipulator.getServiceLocation(directory.getKey()));
        try {
            int numRemoved = directory.irdRemovePlaces(cleanDirAddKeys, false);
            // TODO should we also try and unbind the entry from the Namespace?
            // old success return from DeregisterPlaceWorker
            // return WorkerStatus.WORKER_SUCCESS;
            return Response.ok().entity("Successfully removed " + numRemoved + " place(s) with keys: " + cleanDirAddKeys).build();
        } finally {
            MDC.remove(MDCConstants.SERVICE_LOCATION);
        }

    }
}
