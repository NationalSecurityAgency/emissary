package emissary.server.api;

import emissary.client.response.Agent;
import emissary.client.response.AgentsResponseEntity;
import emissary.client.response.ConfigsResponseEntity;
import emissary.client.response.MapResponseEntity;
import emissary.client.response.PlacesResponseEntity;
import emissary.command.ServerCommand;
import emissary.core.EmissaryException;
import emissary.core.MetricsManager;
import emissary.core.Namespace;
import emissary.directory.DirectoryPlace;
import emissary.directory.EmissaryNode;
import emissary.pool.AgentPool;
import emissary.pool.MobileAgentFactory;
import emissary.server.EmissaryServer;
import emissary.server.mvc.EndpointTestBase;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.google.common.collect.Sets;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.ws.rs.core.Response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class EmissaryApiTest extends EndpointTestBase {

    static final String TEST_HOST = "localhost";
    static final int TEST_PORT = 10978;
    static final String TEST_HOST_AND_PORT = TEST_HOST + ":" + TEST_PORT;

    @BeforeEach
    public void setup() {
        EmissaryServer mockServer = mock(EmissaryServer.class);
        EmissaryNode node = spy(new TestEmissaryNode());
        ServerCommand srvCmd = mock(ServerCommand.class);

        doReturn(TEST_HOST).when(node).getNodeName();
        doReturn(TEST_PORT).when(node).getNodePort();
        when(mockServer.getNode()).thenReturn(node);
        when(mockServer.getServerCommand()).thenReturn(srvCmd);
        when(srvCmd.getConfig()).thenReturn(Paths.get("/path/to/project/config"));
        when(srvCmd.getProjectBase()).thenReturn(Paths.get("/path/to/project"));
        when(srvCmd.getOutputDir()).thenReturn(Paths.get("/path/to/project/output"));
        when(srvCmd.getBinDir()).thenReturn(Paths.get("/path/to/project/bin"));
        when(srvCmd.getHost()).thenReturn(TEST_HOST);
        when(srvCmd.getPort()).thenReturn(TEST_PORT);
        when(srvCmd.getScheme()).thenReturn("https");

        Namespace.bind("EmissaryServer", mockServer);
    }

    @AfterEach
    public void cleanup() {
        Namespace.clear();
    }

    @Test
    void agents() {
        Agent[] expectedAgents = {
                new Agent("MobileAgent-00", "Idle"),
                new Agent("MobileAgent-01", "Idle"),
                new Agent("MobileAgent-02", "Idle"),
                new Agent("MobileAgent-03", "Idle"),
                new Agent("MobileAgent-04", "Idle"),
                new Agent("MobileAgent-05", "Idle"),
                new Agent("MobileAgent-06", "Idle"),
                new Agent("MobileAgent-07", "PHASE.FORM.Place"),
                new Agent("MobileAgent-08", "Idle"),
                new Agent("MobileAgent-09", "Idle")
        };
        AgentPool pool = new AgentPool(new MobileAgentFactory("emissary.core.HDMobileAgent"), 10);
        Namespace.bind("MobileAgent-07", "PHASE.FORM.Place");
        Namespace.bind("AgentPool", pool);
        try (Response response = target("agents").request().get()) {
            assertEquals(200, response.getStatus());
            AgentsResponseEntity entity = response.readEntity(AgentsResponseEntity.class);
            assertTrue(entity.getErrors().isEmpty());
            assertTrue(entity.getCluster().isEmpty());
            assertEquals(TEST_HOST_AND_PORT, entity.getLocal().getHost());
            assertIterableEquals(Arrays.asList(expectedAgents), entity.getLocal().getAgents());
        } finally {
            Namespace.unbind("MobileAgent-07");
            Namespace.unbind("AgentPool");
        }
    }

    @Test
    void pool() {
        AgentPool pool = new AgentPool(new MobileAgentFactory("emissary.core.HDMobileAgent"), 10);
        Namespace.bind("AgentPool", pool);
        try (Response response = target("pool").request().get()) {
            assertEquals(200, response.getStatus());
            MapResponseEntity entity = response.readEntity(MapResponseEntity.class);
            assertTrue(entity.getErrors().isEmpty());
            assertFalse(entity.getResponse().isEmpty());
            assertEquals("Poolsize active/idle: 0/10", entity.getResponse().get(TEST_HOST_AND_PORT));
        } finally {
            Namespace.unbind("AgentPool");
        }
    }

    @Test
    void places() {
        Namespace.bind("PickupPlace", "pickupPlace");
        Namespace.bind("PickupClient", "pickupClient");
        Namespace.bind("ThisWontBeAdded", "miss");
        Namespace.bind("ProcessingPlace", "processingPlace");
        try (Response response = target("places").request().get()) {
            assertEquals(200, response.getStatus());
            PlacesResponseEntity entity = response.readEntity(PlacesResponseEntity.class);
            assertEquals(TEST_HOST_AND_PORT, entity.getLocal().getHost());
            assertEquals(3, entity.getLocal().getPlaces().size());
            assertEquals(Sets.newHashSet("pickupClient", "pickupPlace", "processingPlace"), entity.getLocal().getPlaces());
            assertEquals(0, entity.getErrors().size());
            assertTrue(CollectionUtils.isEmpty(entity.getCluster()));
        } finally {
            Namespace.unbind("PickupPlace");
            Namespace.unbind("PickupClient");
            Namespace.unbind("ThisWontBeAdded");
            Namespace.unbind("ProcessingPlace");
        }
    }

    @Test
    void placesNoPlacesClientsBound() {
        try (Response response = target("places").request().get()) {
            assertEquals(200, response.getStatus());
            PlacesResponseEntity entity = response.readEntity(PlacesResponseEntity.class);
            assertEquals(TEST_HOST_AND_PORT, entity.getLocal().getHost());
            assertEquals(0, entity.getLocal().getPlaces().size());
            assertEquals(0, entity.getErrors().size());
            assertTrue(CollectionUtils.isEmpty(entity.getCluster()));
        }
    }

    @Test
    void placesNoServerBound() {
        Namespace.unbind("EmissaryServer");
        try (Response response = target("places").request().get()) {
            assertEquals(200, response.getStatus());
            PlacesResponseEntity entity = response.readEntity(PlacesResponseEntity.class);
            assertTrue(CollectionUtils.isEmpty(entity.getLocal().getPlaces()));
            assertTrue(CollectionUtils.isEmpty(entity.getCluster()));
            assertEquals(1, entity.getErrors().size());
            assertEquals(Collections.singleton("Problem finding the emissary server or places in the namespace: Not found: EmissaryServer"),
                    entity.getErrors());
        }
    }

    @Test
    void version() {
        try (Response response = target("version").request().get()) {
            assertEquals(200, response.getStatus());
            MapResponseEntity entity = response.readEntity(MapResponseEntity.class);
            assertTrue(entity.getErrors().isEmpty());
            assertFalse(entity.getResponse().isEmpty());
            assertEquals(new emissary.util.Version().getVersion(), entity.getResponse().get(TEST_HOST_AND_PORT));
        }
    }

    @Test
    void getEnvJson() {
        try (Response response = target("env").request().get()) {
            assertEquals(200, response.getStatus());
            MapResponseEntity entity = response.readEntity(MapResponseEntity.class);
            assertTrue(entity.getErrors().isEmpty());
            assertFalse(entity.getResponse().isEmpty());
            assertEquals("/path/to/project/config", entity.getResponse().get("CONFIG_DIR"));
            assertEquals("/path/to/project", entity.getResponse().get("PROJECT_BASE"));
            assertEquals("/path/to/project/output", entity.getResponse().get("OUTPUT_ROOT"));
            assertEquals("/path/to/project/bin", entity.getResponse().get("BIN_DIR"));
            assertEquals(TEST_HOST, entity.getResponse().get("HOST"));
            assertEquals(TEST_PORT, Integer.valueOf(entity.getResponse().get("PORT")));
            assertEquals("https", entity.getResponse().get("SCHEME"));
        }
    }

    @Test
    void getEnvForBash() {
        try (Response response = target("env.sh").request().get()) {
            assertEquals(200, response.getStatus());
            String entity = response.readEntity(String.class);
            assertTrue(StringUtils.isNotEmpty(entity));
            assertTrue(entity.contains("export CONFIG_DIR=\"/path/to/project/config\""));
            assertTrue(entity.contains("export PROJECT_BASE=\"/path/to/project\""));
            assertTrue(entity.contains("export OUTPUT_ROOT=\"/path/to/project/output\""));
            assertTrue(entity.contains("export BIN_DIR=\"/path/to/project/bin\""));
            assertTrue(entity.contains("export HOST=\"" + TEST_HOST + "\""));
            assertTrue(entity.contains("export PORT=\"" + TEST_PORT + "\""));
            assertTrue(entity.contains("export SCHEME=\"https\""));
        }
    }

    @Test
    void metrics() {
        MetricsAction metrics = new MetricsAction();
        MetricsManager manager = mock(MetricsManager.class);
        MetricRegistry registry = mock(MetricRegistry.class);

        Counter metric = new Counter();
        metric.inc(100);

        final SortedMap<String, Metric> results = new TreeMap<>();
        results.put("testing", metric);

        when(registry.getMetrics()).thenReturn(results);
        when(manager.getMetricRegistry()).thenReturn(registry);

        Namespace.bind("MetricsManager", manager);
        try (Response response = metrics.clusterAgents()) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            Map<String, Metric> metricsMap = ((MetricRegistry) response.getEntity()).getMetrics();
            assertEquals(100, ((Counter) metricsMap.get("testing")).getCount());
        } finally {
            Namespace.unbind("MetricsManager");
        }
    }

    @Test
    void healthcheck() {
        HealthCheckAction health = new HealthCheckAction();
        HealthCheckRegistry registry = mock(HealthCheckRegistry.class);
        MetricsManager manager = mock(MetricsManager.class);
        final SortedMap<String, HealthCheck.Result> results = new TreeMap<>();
        results.put("testing", HealthCheck.Result.healthy("Okay"));

        when(manager.getHealthCheckRegistry()).thenReturn(registry);
        when(registry.runHealthChecks()).thenReturn(results);

        Namespace.bind("MetricsManager", manager);
        try (Response response = health.clusterAgents()) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            assertTrue(response.getEntity().toString().contains("isHealthy=true"));
            assertTrue(response.getEntity().toString().contains("message=Okay"));
        } finally {
            Namespace.unbind("MetricsManager");
        }
    }

    @Test
    void apiutils() throws Exception {

        String PEER = "*.*.*.http://somehost:" + TEST_PORT + "/DirectoryPlace";
        Set<String> PEERS = Collections.singleton(PEER);

        DirectoryPlace mockDirectory = mock(DirectoryPlace.class);
        when(mockDirectory.getPeerDirectories()).thenReturn(PEERS);

        Namespace.bind("DirectoryPlace", mockDirectory);
        try {
            Set<String> results = ApiUtils.lookupPeers();
            assertEquals(PEERS, results);

            String result = ApiUtils.stripPeerString(PEER);
            assertEquals("http://somehost:" + TEST_PORT + "/", result);

            assertThrows(IndexOutOfBoundsException.class, () -> ApiUtils.stripPeerString("*.*.http://throwsException:" + TEST_PORT));

            String hostAndPort = ApiUtils.getHostAndPort();
            assertEquals(TEST_HOST_AND_PORT, hostAndPort);

            Namespace.unbind("EmissaryServer");
            result = ApiUtils.getHostAndPort();
            assertEquals("Namespace lookup error, host unknown", result);

            Namespace.unbind("DirectoryPlace");
            assertThrows(EmissaryException.class, ApiUtils::lookupPeers);
        } finally {
            Namespace.unbind("DirectoryPlace");
        }
    }

    @Test
    void configs() {
        try (Response response = target("configuration/node").request().get()) {
            assertEquals(200, response.getStatus());
            ConfigsResponseEntity entity = response.readEntity(ConfigsResponseEntity.class);
            assertTrue(entity.getErrors().isEmpty());
            assertFalse(entity.getLocal().getConfigs().isEmpty());
            assertEquals(1, entity.getLocal().getConfigs().size());
            assertEquals("[CLUSTER]", entity.getLocal().getConfigs().get(0).getFlavors().toString());
            assertEquals("[node.cfg, node-CLUSTER.cfg]", entity.getLocal().getConfigs().get(0).getConfigs().toString());
        }
    }

    @Test
    void configsDetailed() {
        try (Response response = target("configuration/detailed/node").request().get()) {
            assertEquals(200, response.getStatus());
            ConfigsResponseEntity entity = response.readEntity(ConfigsResponseEntity.class);
            assertTrue(entity.getErrors().isEmpty());
            assertFalse(entity.getLocal().getConfigs().isEmpty());
            assertEquals(3, entity.getLocal().getConfigs().size());
            // config 1
            assertEquals("[]", entity.getLocal().getConfigs().get(0).getFlavors().toString());
            assertEquals("[node.cfg]", entity.getLocal().getConfigs().get(0).getConfigs().toString());
            // config 2
            assertEquals("[CLUSTER]", entity.getLocal().getConfigs().get(1).getFlavors().toString());
            assertEquals("[node-CLUSTER.cfg]", entity.getLocal().getConfigs().get(1).getConfigs().toString());
            // combined configs
            assertEquals("[CLUSTER]", entity.getLocal().getConfigs().get(2).getFlavors().toString());
            assertEquals("[node.cfg, node-CLUSTER.cfg]", entity.getLocal().getConfigs().get(2).getConfigs().toString());
        }
    }

    private static final class TestEmissaryNode extends EmissaryNode {
        public TestEmissaryNode() {
            super();
            this.nodeName = "TestNode";
            this.nodePort = TEST_PORT;
        }

        @Override
        public boolean isStandalone() {
            return false;
        }
    }
}
