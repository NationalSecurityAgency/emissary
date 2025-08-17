package emissary.server.api;

import emissary.core.MetricsManager;
import emissary.core.NamespaceException;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@Path("")
// context is /api, set in EmissaryServer
public class HealthCheckAction {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public static final String HEALTH = "health";

    @GET
    @Path("/" + HEALTH)
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    public Response healthCheck(@QueryParam("format") String format) {
        try {
            Map<String, com.codahale.metrics.health.HealthCheck.Result> results = MetricsManager.lookup().getHealthCheckRegistry().runHealthChecks();

            if ("prometheus".equals(format)) {
                return generatePrometheusResponse(results);
            } else {
                // Default to JSON format
                return generateJsonResponse(results);
            }
        } catch (NamespaceException ex) {
            logger.warn("Could not lookup MetricsManager", ex);

            if ("prometheus".equals(format)) {
                return generatePrometheusErrorResponse();
            } else {
                return generateJsonErrorResponse();
            }
        }
    }

    protected Response generateJsonResponse(Map<String, com.codahale.metrics.health.HealthCheck.Result> results) {
        return Response.ok().entity(results).build();
    }

    protected Response generateJsonErrorResponse() {
        return Response.serverError().entity("Could not lookup MetricsManager").build();
    }

    private Response generatePrometheusResponse(Map<String, com.codahale.metrics.health.HealthCheck.Result> results) {
        boolean allHealthy = results.values().stream().allMatch(com.codahale.metrics.health.HealthCheck.Result::isHealthy);

        StringBuilder metricsOutput = new StringBuilder();
        metricsOutput.append("# HELP emissary_health_status Health status of emissary service (1=healthy, 0=unhealthy)\n");
        metricsOutput.append("# TYPE emissary_health_status gauge\n");
        metricsOutput.append("emissary_health_status ").append(allHealthy ? "1" : "0").append("\n");

        if (allHealthy) {
            return Response.ok(metricsOutput.toString()).build();
        } else {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(metricsOutput.toString()).build();
        }
    }

    private Response generatePrometheusErrorResponse() {
        StringBuilder errorMetrics = new StringBuilder();
        errorMetrics.append("# HELP emissary_health_status Health status of emissary service (1=healthy, 0=unhealthy)\n");
        errorMetrics.append("# TYPE emissary_health_status gauge\n");
        errorMetrics.append("emissary_health_status 0\n");
        return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(errorMetrics.toString()).build();
    }
}
