package emissary.server.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.ws.rs.core.Response;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import emissary.core.MetricsManager;
import emissary.core.Namespace;
import emissary.server.mvc.EndpointTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MetricsActionTest extends EndpointTestBase {

    MetricsAction metrics;

    @BeforeEach
    public void setup() {
        metrics = new MetricsAction();
        MetricsManager manager = mock(MetricsManager.class);
        MetricRegistry registry = mock(MetricRegistry.class);

        Counter metric = new Counter();
        metric.inc(100);

        final SortedMap<String, Metric> results = new TreeMap<>();
        results.put("testing", metric);

        when(registry.getMetrics()).thenReturn(results);
        when(manager.getMetricRegistry()).thenReturn(registry);

        Namespace.bind("MetricsManager", manager);
    }

    @AfterEach
    public void cleanup() {
        Namespace.unbind("MetricsManager");
    }

    @Test
    void testMetrics() {
        try (Response response = metrics.clusterAgents()) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            Map<String, Metric> metrics = ((MetricRegistry) response.getEntity()).getMetrics();
            assertEquals(100, ((Counter) metrics.get("testing")).getCount());
        }
    }

}
