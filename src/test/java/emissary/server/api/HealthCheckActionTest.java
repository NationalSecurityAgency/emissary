package emissary.server.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.SortedMap;
import java.util.TreeMap;

import javax.ws.rs.core.Response;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import emissary.core.MetricsManager;
import emissary.core.Namespace;
import emissary.server.mvc.EndpointTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HealthCheckActionTest extends EndpointTestBase {

    HealthCheckAction health;
    HealthCheckRegistry registry;

    @BeforeEach
    @Override
    public void setUp() {
        health = new HealthCheckAction();
        registry = mock(HealthCheckRegistry.class);
        MetricsManager manager = mock(MetricsManager.class);

        when(manager.getHealthCheckRegistry()).thenReturn(registry);

        Namespace.bind("MetricsManager", manager);
    }

    @AfterEach
    public void cleanup() {
        Namespace.unbind("MetricsManager");
    }

    @Test
    void testHealthy() {
        final SortedMap<String, HealthCheck.Result> results = new TreeMap<>();
        results.put("testing", HealthCheck.Result.healthy("Okay"));
        when(registry.runHealthChecks()).thenReturn(results);

        try (Response response = health.clusterAgents()) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            assertTrue(response.getEntity().toString().contains("isHealthy=true"));
            assertTrue(response.getEntity().toString().contains("message=Okay"));
        }
    }

    @Test
    void testUnhealthy() {
        final SortedMap<String, HealthCheck.Result> results = new TreeMap<>();
        results.put("testing", HealthCheck.Result.unhealthy("Nope"));
        when(registry.runHealthChecks()).thenReturn(results);

        try (Response response = health.clusterAgents()) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            assertTrue(response.getEntity().toString().contains("isHealthy=false"));
            assertTrue(response.getEntity().toString().contains("message=Nope"));
        }
    }

}
