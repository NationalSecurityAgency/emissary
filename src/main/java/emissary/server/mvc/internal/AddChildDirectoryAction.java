package emissary.server.mvc.internal;

import emissary.directory.IRemoteDirectory;
import emissary.directory.KeyManipulator;
import emissary.log.MDCConstants;
import emissary.server.mvc.adapters.DirectoryAdapter;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@Path("")
// context is /emissary, set in EmissaryServer
public class AddChildDirectoryAction {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /*
     * 
     * <!-- Look up nextKey on a remote directory --> <Use-Case source="*" action="/AddChildDirectory.action"> <Work
     * type="Bean" target="emissary.comms.http.worker.LogWorker"/> <Work type="Bean"
     * target="emissary.comms.http.worker.AddChildDirectoryWorker"/> <View status="0" view="/success.jsp"/> <View
     * status="-1" view="/error.jsp"/> </Use-Case>
     */


    // TODO This is an initial crack at the new endpoint, I haven't seen it called an am unsure when/if it does
    @POST
    @Path("/AddChildDirectory.action")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_PLAIN)
    public Response addChildDirectory(@FormParam(DirectoryAdapter.TARGET_DIRECTORY) String parent,
            @FormParam(DirectoryAdapter.DIRECTORY_NAME) String child) {
        try {
            if (child == null) {
                throw new IllegalArgumentException("Missing required parameters");
            }

            final IRemoteDirectory localDirectory = new IRemoteDirectory.Lookup().getLocalDirectory(parent);

            if (localDirectory == null) {
                throw new IllegalArgumentException("No parent directory found using name " + parent);
            }

            MDC.put(MDCConstants.SERVICE_LOCATION, KeyManipulator.getServiceLocation(localDirectory.getKey()));
            try {
                localDirectory.irdAddChildDirectory(child);
                logger.debug("addChildDirectory succeeded");
                // old success from AddChildDirectoryWorker
                // return WORKER_SUCCESS;
                return Response.ok().entity("addChildDirectory succeeded").build();
            } finally {
                MDC.remove(MDCConstants.SERVICE_LOCATION);
            }
        } catch (Exception e) {
            logger.error("Could not call addChildDirectory", e);
            // old failure from AddChildDirectoryWorker
            // return new WorkerStatus(WorkerStatus.FAILURE, "Could not call addChildDirectory", e);
            return Response.serverError().entity("Could not call addChildDirectory:\n" + e.getMessage()).build();
        }
    }
}
