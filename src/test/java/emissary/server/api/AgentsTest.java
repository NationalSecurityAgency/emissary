package emissary.server.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import javax.ws.rs.core.Response;

import emissary.client.response.AgentsResponseEntity;
import emissary.core.Namespace;
import emissary.directory.EmissaryNode;
import emissary.pool.AgentPool;
import emissary.pool.MobileAgentFactory;
import emissary.server.EmissaryServer;
import emissary.server.mvc.EndpointTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class AgentsTest extends EndpointTestBase {

    private static final String[] EXPECTED_AGENTS = {"MobileAgent-00: Idle", "MobileAgent-01: Idle", "MobileAgent-02: Idle", "MobileAgent-03: Idle",
            "MobileAgent-04: Idle", "MobileAgent-05: Idle", "MobileAgent-06: Idle", "MobileAgent-07: PHASE.FORM.Place", "MobileAgent-08: Idle",
            "MobileAgent-09: Idle"};

    @BeforeEach
    public void setup() {
        EmissaryServer mockServer = mock(EmissaryServer.class);
        EmissaryNode node = spy(new EmissaryNode());
        doReturn("localhost").when(node).getNodeName();
        doReturn(8001).when(node).getNodePort();
        AgentPool pool = new AgentPool(new MobileAgentFactory("emissary.core.HDMobileAgent"), 10);
        when(mockServer.getNode()).thenReturn(node);
        Namespace.bind("EmissaryServer", mockServer);
        Namespace.bind("MobileAgent-07", "PHASE.FORM.Place");
        Namespace.bind("AgentPool", pool);
    }

    @AfterEach
    public void cleanup() {
        Namespace.unbind("EmissaryServer");
        Namespace.unbind("AgentPool");
    }


    @Disabled("stop mocking and run a server since we made it so easy")
    @Test
    void agents() {
        // test
        Response response = target("agents").request().get();

        // verify
        assertEquals(200, response.getStatus());
        AgentsResponseEntity entity = response.readEntity(AgentsResponseEntity.class);
        assertTrue(entity.getErrors().isEmpty());
        assertTrue(entity.getCluster().isEmpty());
        assertEquals("localhost:8001", entity.getLocal().getHost());
        assertIterableEquals(Arrays.asList(EXPECTED_AGENTS), entity.getLocal().getAgents());
    }

    @Disabled("Look at putting this into an integration test with two real EmissaryServers stood up")
    @Test
    void clusterAgents() {}

}
