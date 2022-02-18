package emissary.server.mvc;

import emissary.core.EmissaryException;
import emissary.core.Namespace;
import emissary.directory.DirectoryPlace;
import emissary.directory.DirectoryXmlContainer;
import emissary.directory.IDirectoryPlace;
import emissary.util.web.HtmlEscaper;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("")
// context is emissary
public class TransferDirectoryAction {
    private static final Logger LOG = LoggerFactory.getLogger(TransferDirectoryAction.class);

    public final String TARGET_DIR_PARAM = "targetDir";

    @GET
    @Path("/TransferDirectory.action")
    @Produces(MediaType.APPLICATION_XML)
    public Response dumpDirectory(@QueryParam(TARGET_DIR_PARAM) String dirname) {
        final IDirectoryPlace value;
        try {
            if (dirname == null) {
                LOG.debug("Lookup is using default name since no " + TARGET_DIR_PARAM + " was specified");
                value = DirectoryPlace.lookup();
            } else {
                LOG.debug("Lookup is using directory name " + dirname);
                value = (IDirectoryPlace) Namespace.lookup(dirname);
            }
            if (value != null) {
                LOG.debug("Lookup returned " + value);
                String msg = DirectoryXmlContainer.toXmlString(value);
                return Response.ok().entity(msg).build();
            } else {
                return Response.status(404).entity("Nothing found for " + HtmlEscaper.escapeHtml(dirname)).build();
            }
        } catch (EmissaryException e) {
            LOG.error("Problem looking up", e);
            return Response.status(500).entity("Directory lookup failed").build();

        }
    }

}
