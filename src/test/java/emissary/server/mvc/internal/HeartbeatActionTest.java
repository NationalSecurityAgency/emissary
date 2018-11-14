package emissary.server.mvc.internal;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;

import emissary.core.Namespace;
import emissary.directory.DirectoryPlace;
import emissary.server.mvc.EndpointTestBase;
import emissary.server.mvc.adapters.HeartbeatAdapter;
import org.hamcrest.core.StringStartsWith;
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
    private static final String FROM_PLACE = "http://some-other-host:8001/DirectoryPlace";
    private static final String TO_PLACE = "http://localhost:8001/DirectoryPlace";
    private static final String HEARTBEAT_ACTION = "Heartbeat.action";
    private DirectoryPlace dp;

    @Before
    public void setup() throws Exception {
        formParams = new MultivaluedHashMap<>();
        formParams.put(HeartbeatAdapter.FROM_PLACE_NAME, Arrays.asList(FROM_PLACE));
        formParams.put(HeartbeatAdapter.TO_PLACE_NAME, Arrays.asList(TO_PLACE));
        dp = mock(DirectoryPlace.class);
        when(dp.isRunning()).thenReturn(true);
        when(dp.toString()).thenReturn(TO_PLACE);
        when(dp.getKey()).thenReturn("toPlaceKey");
        Namespace.bind(TO_PLACE, dp);
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
        assertThat(status, equalTo(500));
        final String result = response.readEntity(String.class);
        assertThat(result, StringStartsWith.startsWith("Heartbeat failed"));
    }

    @Test
    public void heartbeat() throws Exception {
        // test
        final Response response = target(HEARTBEAT_ACTION).request().post(Entity.form(formParams));

        // verify
        final int status = response.getStatus();
        assertThat(status, equalTo(200));
        final String result = response.readEntity(String.class);
        assertThat(result, equalTo(TO_PLACE));
    }

    @Test
    public void directoryNotRunning() throws Exception {
        // setup
        Namespace.unbind(TO_PLACE);
        when(dp.isRunning()).thenReturn(true);
        Namespace.bind(TO_PLACE, dp);

        // test
        final Response response = target(HEARTBEAT_ACTION).request().post(Entity.form(formParams));

        // verify
        final int status = response.getStatus();
        assertThat(status, equalTo(200));
        final String result = response.readEntity(String.class);
        assertThat(result, equalTo(TO_PLACE));
    }

}
