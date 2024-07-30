package emissary.server.mvc;

import emissary.core.EmissaryException;
import emissary.core.Namespace;
import emissary.directory.DirectoryPlace;
import emissary.directory.DirectoryXmlContainer;
import emissary.directory.IDirectoryPlace;
import emissary.server.mvc.adapters.RequestUtil;
import emissary.util.web.HtmlEscaper;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

@Path("")
// context is emissary
public class TransferDirectoryAction {
    private static final Logger LOG = LoggerFactory.getLogger(TransferDirectoryAction.class);

    public static final String TARGET_DIR_PARAM = "targetDir";

    @GET
    @Path("/TransferDirectory.action")
    @Produces(MediaType.APPLICATION_XML)
    public Response dumpDirectory(@Nullable @QueryParam(TARGET_DIR_PARAM) String dirname) {
        final IDirectoryPlace value;
        String cleanDirectoryName = RequestUtil.sanitizeParameter(dirname);
        try {
            if (cleanDirectoryName == null) {
                LOG.debug("Lookup is using default name since no {} was specified", TARGET_DIR_PARAM);
                value = DirectoryPlace.lookup();
            } else {
                LOG.debug("Lookup is using directory name {}", cleanDirectoryName);
                value = (IDirectoryPlace) Namespace.lookup(cleanDirectoryName);
            }
            if (value != null) {
                LOG.debug("Lookup returned {}", value);
                String msg = DirectoryXmlContainer.toXmlString(value);
                return Response.ok().entity(msg).build();
            } else {
                return Response.status(404).entity("Nothing found for " + HtmlEscaper.escapeHtml(cleanDirectoryName)).build();
            }
        } catch (EmissaryException e) {
            LOG.error("Problem looking up", e);
            return Response.status(500).entity("Directory lookup failed").build();

        }
    }

}
