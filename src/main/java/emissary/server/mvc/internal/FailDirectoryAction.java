package emissary.server.mvc.internal;

import static emissary.server.mvc.adapters.DirectoryAdapter.ADD_KEY;
import static emissary.server.mvc.adapters.DirectoryAdapter.ADD_PROPAGATION_FLAG;
import static emissary.server.mvc.adapters.DirectoryAdapter.FAILED_DIRECTORY_NAME;
import static emissary.server.mvc.adapters.DirectoryAdapter.TARGET_DIRECTORY;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import emissary.directory.IRemoteDirectory;
import emissary.directory.KeyManipulator;
import emissary.log.MDCConstants;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

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
        if (StringUtils.isBlank(targetDirectory) || StringUtils.isBlank(failedDirectory)) {
            return Response.serverError()
                    .entity("Bad params: " + TARGET_DIRECTORY + " - " + targetDirectory + ", or " + ADD_KEY + " - " + failedDirectory).build();
        }

        try {
            IRemoteDirectory localDirectory = new IRemoteDirectory.Lookup().getLocalDirectory(targetDirectory);
            if (localDirectory == null) {
                logger.error("No local directory found using name {}", targetDirectory);
                return Response.serverError().entity("No local directory found using name " + targetDirectory).build();
            }

            MDC.put(MDCConstants.SERVICE_LOCATION, KeyManipulator.getServiceLocation(localDirectory.getKey()));
            int count = localDirectory.irdFailRemoteDirectory(failedDirectory, propagate);
            logger.debug("Modified {} entries from {} due to failure of remote {}", count, localDirectory, targetDirectory);
            return Response.ok().entity("Modified " + count + " entries from " + localDirectory + " due to failure of remote " + failedDirectory)
                    .build();

        } finally {
            MDC.remove(MDCConstants.SERVICE_LOCATION);
        }
    }
}
