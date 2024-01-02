package emissary.server.mvc.internal;

import emissary.config.ConfigUtil;
import emissary.core.Namespace;
import emissary.directory.DirectoryPlace;
import emissary.directory.EmissaryNode;
import emissary.server.mvc.EndpointTestBase;
import emissary.server.mvc.adapters.HeartbeatAdapter;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HeartbeatActionTest extends EndpointTestBase {

    private MultivaluedHashMap<String, String> formParams;
    private static final String FROM_PLACE = "EMISSARY_DIRECTORY_SERVICES.DIRECTORY.STUDY.http://remoteHost:8888/DirectoryPlace";
    private static final String TO_PLACE = "EMISSARY_DIRECTORY_SERVICES.DIRECTORY.STUDY.http://localhost:9999/DirectoryPlace";
    private static final String HEARTBEAT_ACTION = "Heartbeat.action";
    private DirectoryPlace dp;
    private EmissaryNode node;

    @BeforeEach
    public void setup() throws Exception {
        formParams = new MultivaluedHashMap<>();
        formParams.put(HeartbeatAdapter.FROM_PLACE_NAME, Collections.singletonList(FROM_PLACE));
        formParams.put(HeartbeatAdapter.TO_PLACE_NAME, Collections.singletonList(TO_PLACE));
        node = mock(EmissaryNode.class);
        when(node.isValid()).thenReturn(true);
        when(node.getPeerConfigurator()).thenReturn(ConfigUtil.getConfigInfo("peer-TESTING.cfg"));
        dp = new DirectoryPlace("peer-TESTING.cfg", TO_PLACE, node);
        Namespace.bind(TO_PLACE, dp);
        Namespace.bind(FROM_PLACE, dp);
    }

    @Override
    @AfterEach
    public void tearDown() {
        Namespace.unbind(TO_PLACE);
        Namespace.unbind(FROM_PLACE);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n", "\t"})
    void badParams(String badValue) {
        // setup
        formParams.put(HeartbeatAdapter.FROM_PLACE_NAME, Collections.singletonList(badValue));
        formParams.put(HeartbeatAdapter.TO_PLACE_NAME, Collections.singletonList(badValue));

        // test
        try (final Response response = target(HEARTBEAT_ACTION).request().post(Entity.form(formParams))) {
            // verify
            final int status = response.getStatus();
            assertEquals(500, status);
            final String result = response.readEntity(String.class);
            assertTrue(result.startsWith("Heartbeat failed"));
        }
    }

    @Test
    void heartbeat() {
        // test
        try (final Response response = target(HEARTBEAT_ACTION).request().post(Entity.form(formParams))) {
            // verify
            final int status = response.getStatus();
            assertEquals(200, status);
            final String result = response.readEntity(String.class);
            assertEquals(dp.toString(), result);
        }
    }

    @Test
    void directoryNotRunning() {
        // setup
        Namespace.unbind(TO_PLACE);
        when(dp.isRunning()).thenReturn(true);
        Namespace.bind(TO_PLACE, dp);

        // test
        try (final Response response = target(HEARTBEAT_ACTION).request().post(Entity.form(formParams))) {
            // verify
            final int status = response.getStatus();
            assertEquals(200, status);
            final String result = response.readEntity(String.class);
            assertEquals(dp.toString(), result);
        }
    }

}
