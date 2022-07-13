package emissary.server.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.nio.file.Paths;

import javax.ws.rs.core.Response;

import emissary.client.response.MapResponseEntity;
import emissary.command.ServerCommand;
import emissary.core.Namespace;
import emissary.directory.EmissaryNode;
import emissary.server.EmissaryServer;
import emissary.server.mvc.EndpointTestBase;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EnvTest extends EndpointTestBase {

    @BeforeEach
    public void setup() {
        EmissaryServer mockServer = mock(EmissaryServer.class);
        EmissaryNode node = spy(new EmissaryNode());
        ServerCommand srvCmd = mock(ServerCommand.class);

        doReturn("localhost").when(node).getNodeName();
        doReturn(8001).when(node).getNodePort();
        when(mockServer.getNode()).thenReturn(node);
        when(mockServer.getServerCommand()).thenReturn(srvCmd);
        when(srvCmd.getConfig()).thenReturn(Paths.get("/path/to/project/config"));
        when(srvCmd.getProjectBase()).thenReturn(Paths.get("/path/to/project"));
        when(srvCmd.getOutputDir()).thenReturn(Paths.get("/path/to/project/output"));
        when(srvCmd.getBinDir()).thenReturn(Paths.get("/path/to/project/bin"));
        when(srvCmd.getHost()).thenReturn("localhost");
        when(srvCmd.getPort()).thenReturn(8001);
        when(srvCmd.getScheme()).thenReturn("https");

        Namespace.bind("EmissaryServer", mockServer);
    }

    @AfterEach
    public void cleanup() {
        Namespace.unbind("EmissaryServer");
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
            assertEquals("localhost", entity.getResponse().get("HOST"));
            assertEquals("8001", entity.getResponse().get("PORT"));
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
            assertTrue(entity.contains("export HOST=\"localhost\""));
            assertTrue(entity.contains("export PORT=\"8001\""));
            assertTrue(entity.contains("export SCHEME=\"https\""));
        }
    }
}
