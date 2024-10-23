package emissary.server.mvc.internal;

import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.core.ResourceWatcher;
import emissary.output.DropOffPlace;
import emissary.output.filter.IDropOffFilter;
import emissary.server.mvc.adapters.RequestUtil;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

import static emissary.util.PayloadUtil.logger;

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
            DropOffPlace dropOffPlace = (DropOffPlace) Namespace.lookup(RequestUtil.sanitizeParameter(namespaceName));

            for (String filter : outputFilterNames) {
                try {
                    IDropOffFilter f = dropOffPlace.getFilter(filter);
                    if (f != null) {
                        f.close();
                        outputNames.append(" ").append(filter);
                    }
                } catch (RuntimeException ex) {
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
        } catch (NamespaceException | RuntimeException ex) {
            logger.warn("Could not roll outputs", ex);
            return Response.ok().entity("Could not roll outputs: " + ex.toString()).build();
        }
    }
}
