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
import emissary.server.EmissaryServer;
import emissary.server.mvc.EndpointTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VersionTest extends EndpointTestBase {

    @BeforeEach
    public void setup() {
        EmissaryServer mockServer = mock(EmissaryServer.class);
        EmissaryNode node = spy(new EmissaryNode());
        doReturn("localhost").when(node).getNodeName();
        doReturn(8001).when(node).getNodePort();

        when(mockServer.getNode()).thenReturn(node);

        Namespace.bind("EmissaryServer", mockServer);
    }

    @AfterEach
    public void cleanup() {
        Namespace.unbind("EmissaryServer");
    }

    @Test
    void version() {
        try (Response response = target("version").request().get()) {
            assertEquals(200, response.getStatus());
            MapResponseEntity entity = response.readEntity(MapResponseEntity.class);
            assertTrue(entity.getErrors().isEmpty());
            assertFalse(entity.getResponse().isEmpty());
            assertEquals(new emissary.util.Version().getVersion(), entity.getResponse().get("localhost:8001"));
        }
    }
}
