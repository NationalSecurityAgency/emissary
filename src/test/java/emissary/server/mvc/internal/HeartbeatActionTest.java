package emissary.server.mvc.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import emissary.core.Namespace;
import emissary.directory.DirectoryPlace;
import emissary.directory.EmissaryNode;
import emissary.server.mvc.EndpointTestBase;
import emissary.server.mvc.adapters.HeartbeatAdapter;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class HeartbeatActionTest extends EndpointTestBase {
    @DataPoints
    public static String[] EMPTY_REQUEST_PARAMS = new String[] {"", null, " ", "\n", "\t"};
    private MultivaluedHashMap<String, String> formParams;
    private static final String FROM_PLACE = "EMISSARY_DIRECTORY_SERVICES.DIRECTORY.STUDY.http://remoteHost:8888/DirectoryPlace";
    private static final String TO_PLACE = "EMISSARY_DIRECTORY_SERVICES.DIRECTORY.STUDY.http://localhost:9999/DirectoryPlace";
    private static final String HEARTBEAT_ACTION = "Heartbeat.action";
    private DirectoryPlace dp;
    private EmissaryNode node;

    @Before
    public void setup() throws Exception {
        formParams = new MultivaluedHashMap<>();
        formParams.put(HeartbeatAdapter.FROM_PLACE_NAME, Arrays.asList(FROM_PLACE));
        formParams.put(HeartbeatAdapter.TO_PLACE_NAME, Arrays.asList(TO_PLACE));
        node = mock(EmissaryNode.class);
        when(node.isValid()).thenReturn(true);
        when(node.getPeerConfigurator()).thenReturn(ConfigUtil.getConfigInfo("peer-TESTING.cfg"));
        dp = new DirectoryPlace("peer-TESTING.cfg", TO_PLACE, node);
        Namespace.bind(TO_PLACE, dp);
        Namespace.bind(FROM_PLACE, dp);
    }

    @After
    public void tearDown() {
        Namespace.unbind(TO_PLACE);
        Namespace.unbind(FROM_PLACE);
    }

    @Theory
    public void badParams(String badValue) {
        // setup
        formParams.put(HeartbeatAdapter.FROM_PLACE_NAME, Arrays.asList(badValue));
        formParams.put(HeartbeatAdapter.TO_PLACE_NAME, Arrays.asList(badValue));

        // test
        final Response response = target(HEARTBEAT_ACTION).request().post(Entity.form(formParams));

        // verify
        final int status = response.getStatus();
        assertEquals(500, status);
        final String result = response.readEntity(String.class);
        assertTrue(result.startsWith("Heartbeat failed"));
    }

    @Test
    public void heartbeat() {
        // test
        final Response response = target(HEARTBEAT_ACTION).request().post(Entity.form(formParams));

        // verify
        final int status = response.getStatus();
        assertEquals(200, status);
        final String result = response.readEntity(String.class);
        assertEquals(dp.toString(), result);
    }

    @Test
    public void directoryNotRunning() {
        // setup
        Namespace.unbind(TO_PLACE);
        when(dp.isRunning()).thenReturn(true);
        Namespace.bind(TO_PLACE, dp);

        // test
        final Response response = target(HEARTBEAT_ACTION).request().post(Entity.form(formParams));

        // verify
        final int status = response.getStatus();
        assertEquals(200, status);
        final String result = response.readEntity(String.class);
        assertEquals(dp.toString(), result);
    }

}
