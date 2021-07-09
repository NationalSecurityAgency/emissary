package emissary.server.mvc.internal;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import emissary.core.Namespace;
import emissary.directory.KeyManipulator;
import emissary.pickup.IPickUpSpace;
import emissary.pickup.WorkBundle;
import emissary.server.mvc.adapters.WorkSpaceAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("")
// context is /emissary, set in EmissaryServer
public class WorkSpaceClientEnqueueAction {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /*
     * <!-- Put a file on a work PickUpPlaceClient queue --> <Use-Case source="*" action="/WorkSpaceClientEnque.action">
     * <Work type="Bean" target="emissary.comms.http.worker.LogWorker"/> <Work type="Bean"
     * target="emissary.comms.http.worker.WorkSpaceClientEnqueWorker"/> <View status="0" view="/success.jsp"/> <View
     * status="-1" view="/error.jsp"/> </Use-Case>
     */

    // TODO This is an initial crack at the new endpoint, I haven't seen it called an am unsure when/if it does
    @POST
    @Path("/WorkSpaceClientEnqueue.action")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_PLAIN)
    public Response workspaceClientEnqueue(@FormParam(WorkSpaceAdapter.CLIENT_NAME) String clientName,
            @FormParam(WorkSpaceAdapter.WORK_BUNDLE_OBJ) String workBundleString) {
        logger.debug("TPWorker incoming execute! check prio={}", Thread.currentThread().getPriority());
        // TODO Doesn't look like anything is actually calling this, should we remove this?
        final boolean success;
        try {
            // Look up the place reference
            final String nsName = KeyManipulator.getServiceLocation(clientName);
            final IPickUpSpace place = (IPickUpSpace) Namespace.lookup(nsName);
            if (place == null) {
                throw new IllegalArgumentException("No client place found using name " + clientName);
            }

            final DataInputStream ois = new DataInputStream(new ByteArrayInputStream(workBundleString.getBytes("8859_1")));
            WorkBundle paths = WorkBundle.readFromStream(ois);
            success = place.enque(paths);
        } catch (Exception e) {
            logger.warn("WorkSpaceClientEnqueWorker exception", e);
            return Response.serverError().entity("WorkSpaceClientEnqueWorker exception:\n" + e.getMessage()).build();
        }

        if (success) {
            // old success from WorkSpaceClientEnqueWorker
            // return WORKER_SUCCESS;
            return Response.ok().entity("Successful add to the PickUpPlaceClient queue").build();
        } else {
            // old failure from WorkSpaceClientEnqueWorker
            // return new WorkerStatus(WorkerStatus.FAILURE, "WorkSpaceClientEnqueWorker failed, queue full");
            return Response.serverError().entity("WorkSpaceClientEnqueWorker failed, queue full").build();
        }
    }
}
