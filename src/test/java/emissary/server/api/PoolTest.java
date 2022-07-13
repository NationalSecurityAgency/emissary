package emissary.server.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import javax.ws.rs.core.Response;

import emissary.client.response.MapResponseEntity;
import emissary.core.Namespace;
import emissary.directory.EmissaryNode;
import emissary.pool.AgentPool;
import emissary.pool.MobileAgentFactory;
import emissary.server.EmissaryServer;
import emissary.server.mvc.EndpointTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PoolTest extends EndpointTestBase {

    @BeforeEach
    public void setup() {
        EmissaryServer mockServer = mock(EmissaryServer.class);
        EmissaryNode node = spy(new EmissaryNode());
        AgentPool pool = new AgentPool(new MobileAgentFactory("emissary.core.HDMobileAgent"), 10);

        doReturn("localhost").when(node).getNodeName();
        doReturn(8001).when(node).getNodePort();
        when(mockServer.getNode()).thenReturn(node);

        Namespace.bind("EmissaryServer", mockServer);
        Namespace.bind("AgentPool", pool);
    }

    @AfterEach
    public void cleanup() {
        Namespace.unbind("EmissaryServer");
        Namespace.unbind("AgentPool");
    }

    @Test
    void pool() {
        try (Response response = target("pool").request().get()) {
            assertEquals(200, response.getStatus());
            MapResponseEntity entity = response.readEntity(MapResponseEntity.class);
            assertTrue(entity.getErrors().isEmpty());
            assertFalse(entity.getResponse().isEmpty());
            assertEquals("Poolsize active/idle: 0/10", entity.getResponse().get("localhost:8001"));
        }
    }

}
