package emissary.server.api;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import emissary.core.MetricsManager;
import emissary.core.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("")
// context is /api, set in EmissaryServer
public class HealthCheckAction {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @GET
    @Path("/health")
    @Produces(MediaType.APPLICATION_JSON)
    public Response clusterAgents() {
        try {
            return Response.ok().entity(MetricsManager.lookup().getHealthCheckRegistry().runHealthChecks()).build();
        } catch (NamespaceException ex) {
            logger.warn("Could not lookup MetricsManager", ex);
            return Response.serverError().entity("Could not lookup MetricsManager").build();
        }
    }
}
