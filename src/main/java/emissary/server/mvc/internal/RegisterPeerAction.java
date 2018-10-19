package emissary.server.mvc.internal;

import static emissary.server.mvc.adapters.DirectoryAdapter.DIRECTORY_NAME;
import static emissary.server.mvc.adapters.DirectoryAdapter.TARGET_DIRECTORY;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import emissary.directory.DirectoryXmlContainer;
import emissary.directory.IRemoteDirectory;
import emissary.directory.KeyManipulator;
import emissary.log.MDCConstants;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@Path("")
// context is /emissary, set in EmissaryServer
public class RegisterPeerAction {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /*
     * <!-- Register a peer with the local directory and return the xml --> <Use-Case source="*"
     * action="/RegisterPeer.action"> <Work type="Bean" target="emissary.comms.http.worker.LogWorker"/> <Work type="Bean"
     * target="emissary.comms.http.worker.LookupDirectoryWorker"/> <Work type="Bean"
     * target="emissary.comms.http.worker.RegisterPeerWorker"/> <Work type="Bean"
     * target="emissary.comms.http.worker.SetProxyWorker"/> <View status="0" view="/xmldir.jsp"/> <View status="-1"
     * view="/error.jsp"/> </Use-Case>
     */

    @POST
    @Path("/RegisterPeer.action")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_XML)
    public Response registerPeerPost(@FormParam(DIRECTORY_NAME) String directoryName, @FormParam(TARGET_DIRECTORY) String targetDirectory) {
        if (StringUtils.isBlank(directoryName) || StringUtils.isBlank(targetDirectory)) {
            return Response.serverError()
                    .entity("Bad Params: " + DIRECTORY_NAME + " - " + directoryName + ", " + TARGET_DIRECTORY + " - " + targetDirectory).build();
        }
        return processRegisterPeer(directoryName, targetDirectory);
    }

    private Response processRegisterPeer(String peerKey, String dirName) {
        final IRemoteDirectory dir = new IRemoteDirectory.Lookup().getLocalDirectory(dirName);
        if (dir == null) {
            // If we get here, there was a problem looking up the IRemoteDirectory
            // old RegisterPlaceWorker failure
            // return new WorkerStatus(WorkerStatus.FAILURE, "Could not register peer " + peerKey);
            return Response.serverError().entity("Remote directory lookup failed for dirName: " + dirName).build();
        }

        final Set<String> set = new HashSet<String>();
        set.add(KeyManipulator.getDefaultDirectoryKey(peerKey));
        MDC.put(MDCConstants.SERVICE_LOCATION, KeyManipulator.getServiceLocation(dir.getKey()));
        try {
            dir.irdAddPeerDirectories(set);
        } finally {
            MDC.remove(MDCConstants.SERVICE_LOCATION);
        }

        logger.debug("Registered peer {} with local dir", peerKey);
        return Response.ok().entity(DirectoryXmlContainer.toXmlString(dir)).build();
    }
}
