package emissary.server.mvc.internal;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import emissary.server.mvc.adapters.MoveToAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MoveToAction {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /*
     * <!-- Cause an agent to move to the remote machine/place --> <Use-Case source="*" action="/MoveTo.action"> <Work
     * type="Bean" target="emissary.comms.http.worker.LogWorker"/> <Work type="Bean"
     * target="emissary.comms.http.worker.ArrivalWorker"/> <View status="0" view="/success.jsp"/> <View status="-1"
     * view="/error.jsp"/> </Use-Case>
     */

    // TODO This is an initial crack at the new endpoint, I haven't seen it called an am unsure when/if it does
    public Response moveTo(@Context HttpServletRequest request) {
        // This is copy and pasted from the old ArrivalWorker class
        final MoveToAdapter mt = new MoveToAdapter();
        try {
            final boolean status = mt.inboundMoveTo(request);

            if (!status) {
                logger.error("MoveTo failed!");
                // return new WorkerStatus(WorkerStatus.FAILURE, "MoveTo failed");
                return Response.serverError().entity("MoveTo failed").build();
            }
        } catch (Exception e) {
            logger.error("Could not call moveTo", e);
            // return new WorkerStatus(WorkerStatus.FAILURE, "Could not call moveTo", e);
            return Response.serverError().entity("Could not call moveTo").build();
        }

        logger.debug("MoveTo succeeded");
        // return WORKER_SUCCESS;
        return Response.ok().entity("MoveTo succeeded").build();
    }

}
