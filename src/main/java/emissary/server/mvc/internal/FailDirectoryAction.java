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

import static emissary.server.mvc.adapters.DirectoryAdapter.ADD_KEY;
import static emissary.server.mvc.adapters.DirectoryAdapter.ADD_PROPAGATION_FLAG;
import static emissary.server.mvc.adapters.DirectoryAdapter.FAILED_DIRECTORY_NAME;
import static emissary.server.mvc.adapters.DirectoryAdapter.TARGET_DIRECTORY;

@Path("")
// context is /emissary, set in EmissaryServer
public class FailDirectoryAction {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /*
     * <!-- Propagate a directory failure --> <Use-Case source="*" action="/FailDirectory.action"> <Work type="Bean"
     * target="emissary.comms.http.worker.LogWorker"/> <Work type="Bean"
     * target="emissary.comms.http.worker.DirectoryFailureWorker"/> <View status="0" view="/success.jsp"/> <View status="-1"
     * view="/error.jsp"/> </Use-Case>
     */
    // Call like this
    // http://localhost:8001/emissary/FailDirectory.action?targetDir=http://localhost:8001/DirectoryPlace&dirFailName=EMISSARY_DIRECTORY_SERVICES.DIRECTORY.STUDY.http://localhost:7001/DirectoryPlace&dirAddPropFlag=true
    // NOTE: This requires a second instance running
    // NOTE: If you do not make propagate true, nothing appears to be modified. It looks like it just tries to adjust
    // the places cost. Will not do anything for local keys.
    @POST
    @Path("/FailDirectory.action")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_PLAIN)
    public Response failDirectory(@FormParam(TARGET_DIRECTORY) String targetDirectory, @FormParam(FAILED_DIRECTORY_NAME) String failedDirectory,
            @FormParam(ADD_PROPAGATION_FLAG) boolean propagate) {
        String cleanTargetDirectory = RequestUtil.sanitizeParameter(targetDirectory);
        String cleanFailedDirectory = RequestUtil.sanitizeParameter(failedDirectory);
        if (StringUtils.isBlank(cleanTargetDirectory) || StringUtils.isBlank(cleanFailedDirectory)) {
            return Response.serverError().entity(
                    "Bad params: " + TARGET_DIRECTORY + " - " + cleanTargetDirectory + ", or " + ADD_KEY + " - " + cleanFailedDirectory).build();
        }

        try {
            IRemoteDirectory localDirectory = new IRemoteDirectory.Lookup().getLocalDirectory(cleanTargetDirectory);
            if (localDirectory == null) {
                logger.error("No local directory found using name {}", cleanTargetDirectory);
                return Response.serverError().entity("No local directory found using name " + cleanTargetDirectory).build();
            }

            MDC.put(MDCConstants.SERVICE_LOCATION, KeyManipulator.getServiceLocation(localDirectory.getKey()));
            int count = localDirectory.irdFailDirectory(cleanFailedDirectory, propagate);
            logger.debug("Modified {} entries from {} due to failure of remote {}", count, localDirectory, cleanTargetDirectory);
            return Response.ok().entity(
                    "Modified " + count + " entries from " + localDirectory + " due to failure of remote " + cleanFailedDirectory).build();

        } finally {
            MDC.remove(MDCConstants.SERVICE_LOCATION);
        }
    }
}
