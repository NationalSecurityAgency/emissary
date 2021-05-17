package emissary.server.mvc.internal;

import static emissary.util.PayloadUtil.logger;

import java.util.List;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import emissary.core.Namespace;
import emissary.core.ResourceWatcher;
import emissary.output.DropOffPlace;
import emissary.output.filter.IDropOffFilter;

@Path("")
// context is /emissary, set in EmissaryServer
// TODO This class should go away as we are using a rollable framework.
public class RollOutputsAction {

    @GET
    @Path("/roll")
    @Produces(MediaType.TEXT_PLAIN)
    public Response rollOutputs(@QueryParam("filter") List<String> outputFilterNames,
            @QueryParam("p") @DefaultValue("DropOffPlace") String namespaceName) {
        try {
            StringBuilder outputNames = new StringBuilder();
            DropOffPlace dropOffPlace = (DropOffPlace) Namespace.lookup(namespaceName);

            for (String filter : outputFilterNames) {
                try {
                    IDropOffFilter f = dropOffPlace.getFilter(filter);
                    if (f != null) {
                        f.close();
                        outputNames.append(" ").append(filter);
                    }
                } catch (Exception ex) {
                    outputNames.append(" ").append(filter).append("-FAILED");
                    logger.error("Could not roll " + filter, ex);
                }
            }

            ResourceWatcher rw = ResourceWatcher.lookup();
            if (rw != null) {
                rw.logStats(logger);
                rw.resetStats();
                outputNames.append(" ").append("ResourceStats");
            }

            return Response.ok().entity("Output Rolled: " + outputNames).build();
        } catch (Exception ex) {
            logger.warn("Could not roll outputs", ex);
            return Response.ok().entity("Could not roll outputs: " + ex.toString()).build();
        }
    }
}
