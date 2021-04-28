package emissary.server.api;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import emissary.core.MetricsManager;
import emissary.core.NamespaceException;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("")
// context is /api, set in EmissaryServer
public class HealthCheckAction {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public static final String HEALTH = "health";

    @GET
    @Path("/" + HEALTH)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get health checks registered in the health check registry", tags = {"health"})
    public Response clusterAgents() {
        try {
            return Response.ok().entity(MetricsManager.lookup().getHealthCheckRegistry().runHealthChecks()).build();
        } catch (NamespaceException ex) {
            logger.warn("Could not lookup MetricsManager", ex);
            return Response.serverError().entity("Could not lookup MetricsManager").build();
        }
    }
}
