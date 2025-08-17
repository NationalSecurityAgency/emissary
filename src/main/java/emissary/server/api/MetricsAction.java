package emissary.server.api;

import emissary.core.MetricsManager;
import emissary.core.NamespaceException;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.dropwizard.DropwizardExports;
import io.prometheus.client.exporter.common.TextFormat;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;

@Path("")
// context is /api, set in EmissaryServer
public class MetricsAction {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @GET
    @Path("/metrics")
    public Response metrics(@QueryParam("format") String format) {
        try {
            if ("prometheus".equals(format)) {
                return prometheusMetrics();
            } else {
                return jsonMetrics();
            }
        } catch (Exception e) {
            logger.warn("Could not retrieve metrics", e);
            return Response.serverError().entity("Could not retrieve metrics: " + e.getMessage()).build();
        }
    }

    private static Response jsonMetrics() throws NamespaceException {
        return Response.ok()
                .entity(MetricsManager.lookup().getMetricRegistry())
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    private static Response prometheusMetrics() throws NamespaceException, IOException {
        CollectorRegistry registry = new CollectorRegistry();
        registry.register(new DropwizardExports(MetricsManager.lookup().getMetricRegistry()));
        StringWriter writer = new StringWriter();
        TextFormat.writeFormat(TextFormat.CONTENT_TYPE_004, writer, registry.metricFamilySamples());
        return Response.ok(writer.toString())
                .type(TextFormat.CONTENT_TYPE_004)
                .build();
    }
}
