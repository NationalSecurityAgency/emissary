package emissary.server.mvc.internal;

import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.directory.DirectoryPlace;
import emissary.directory.DirectoryXmlContainer;
import emissary.directory.IRemoteDirectory;
import emissary.directory.KeyManipulator;
import emissary.log.MDCConstants;
import emissary.server.mvc.adapters.RequestUtil;
import emissary.util.web.HtmlEscaper;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static emissary.server.mvc.adapters.DirectoryAdapter.DIRECTORY_NAME;
import static emissary.server.mvc.adapters.DirectoryAdapter.TARGET_DIRECTORY;

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
        String cleanDirectoryName = RequestUtil.sanitizeParameter(directoryName);
        String cleanTargetDirectory = RequestUtil.sanitizeParameter(targetDirectory);
        if (StringUtils.isBlank(cleanDirectoryName) || StringUtils.isBlank(cleanTargetDirectory)) {
            return Response.serverError().entity(HtmlEscaper.escapeHtml(
                    "Bad Params: " + DIRECTORY_NAME + " - " + cleanDirectoryName + ", " + TARGET_DIRECTORY + " - " + cleanTargetDirectory)).build();
        }
        return processRegisterPeer(cleanDirectoryName, cleanTargetDirectory);
    }

    private Response processRegisterPeer(String peerKey, String dirName) {
        final IRemoteDirectory dir = new IRemoteDirectory.Lookup().getLocalDirectory(dirName);
        if (dir == null) {
            // If we get here, there was a problem looking up the IRemoteDirectory
            // old RegisterPlaceWorker failure
            // return new WorkerStatus(WorkerStatus.FAILURE, "Could not register peer " + peerKey);
            return Response.serverError().entity(
                    "Remote directory lookup failed for dirName: " + HtmlEscaper.escapeHtml(dirName)).build();
        }

        logger.info("Attempting to register peer {} with {}", peerKey, dirName);

        try {
            final DirectoryPlace d = (DirectoryPlace) Namespace.lookup(dirName);
            if (!d.isStaticPeer(KeyManipulator.getDefaultDirectoryKey(peerKey))) {
                logger.warn("Contact attempted from {} but it is not a configured peer", peerKey);
                return Response.serverError().entity("Registration failed for peer: " + HtmlEscaper.escapeHtml(peerKey)).build();
            }
        } catch (NamespaceException e) {
            logger.warn("Problem performing namespace lookup for {}", dirName);
            return Response.serverError().entity("Registration failed for peer: " + HtmlEscaper.escapeHtml(peerKey)).build();
        }

        final Set<String> set = new HashSet<>();
        set.add(KeyManipulator.getDefaultDirectoryKey("#" + peerKey));
        MDC.put(MDCConstants.SERVICE_LOCATION, KeyManipulator.getServiceLocation(dir.getKey()));
        try {
            dir.irdAddPeerDirectories(set);
        } finally {
            MDC.remove(MDCConstants.SERVICE_LOCATION);
        }

        logger.info("Registered peer {}", peerKey);

        return Response.ok().entity(DirectoryXmlContainer.toXmlString(dir)).build();
    }
}
