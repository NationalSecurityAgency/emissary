package emissary.server.mvc;

import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.common.collect.Sets;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.pickup.file.FilePickUpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("")
// context is emissary
public class PauseAction {

    private static final Logger LOG = LoggerFactory.getLogger(PauseAction.class);

    @GET
    @Path("/Pause.action")
    @Produces(MediaType.TEXT_HTML)
    public Response pauseQueServer(@Context HttpServletRequest request) {
        try {
            LOG.debug("Pausing Emissary QueServer");
            Set<FilePickUpClient> clients = getPickupClients();
            clients.forEach(FilePickUpClient::pauseQueueServer);
            return Response.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("/Unpause.action")
    @Produces(MediaType.TEXT_HTML)
    public Response unpauseQueServer(@Context HttpServletRequest request) {
        try {
            LOG.debug("Unpausing Emissary QueServer");
            Set<FilePickUpClient> clients = getPickupClients();
            clients.forEach(FilePickUpClient::unpauseQueueServer);
            return Response.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

    protected Set<FilePickUpClient> getPickupClients() throws NamespaceException {
        Set<FilePickUpClient> clients = Sets.newHashSet();
        for (String key : Namespace.keySet()) {
            Object obj = Namespace.lookup(key);
            if (obj instanceof FilePickUpClient) {
                LOG.info("Found {}", obj);
                clients.add((FilePickUpClient) obj);
            }
        }
        return clients;
    }

}
