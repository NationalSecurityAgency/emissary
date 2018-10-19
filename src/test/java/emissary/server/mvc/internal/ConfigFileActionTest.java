package emissary.server.mvc.internal;

import static emissary.server.mvc.internal.ConfigFileAction.CONFIG_PARAM;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

import javax.ws.rs.core.Response;

import emissary.server.mvc.EndpointTestBase;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class ConfigFileActionTest extends EndpointTestBase {
    @DataPoints
    public static String[] EMPTY_REQUEST_PARAMS = new String[] {"", null, " ", "\n", "\t"};
    private static String CONFIG_ACTION = "/ConfigFile.action";

    @Theory
    public void testCreatePlaceBadParams(String badParam) {
        Response response = target(CONFIG_ACTION).queryParam(CONFIG_PARAM, badParam).request().get();
        assertThat(500, equalTo(response.getStatus()));
        String body = response.readEntity(String.class);
        assertThat(body, equalTo(CreatePlaceAction.EMPTY_PARAM_MSG));
    }

    @Test
    @Ignore
    // TODO Fix this test
    public void testConfigFileConfigDir() throws Exception {
        // TODO figure out how/what ends up in the emissary/config dir
        Response response = target(CONFIG_ACTION).queryParam(CONFIG_PARAM, "testConfigFile").request().get();
        assertThat(200, equalTo(response.getStatus()));
        String body = response.readEntity(String.class);
        assertThat(body.startsWith("<?xml version="), is(true));
    }

    @Test
    public void testConfigFileClasspathCorrect() throws Exception {
        Response response = target(CONFIG_ACTION).queryParam(CONFIG_PARAM, "emissary.place.sample.ToUpperPlace.cfg").request().get();
        assertThat(200, equalTo(response.getStatus()));
        String body = response.readEntity(String.class);
        assertThat(body.startsWith("PLACE_NAME"), is(true));
    }

    @Test
    public void testConfigFileClasspathIncorrect() throws Exception {
        Response response = target(CONFIG_ACTION).queryParam(CONFIG_PARAM, "emissary.place.sample.ToUpperPlace").request().get();
        assertThat(500, equalTo(response.getStatus()));
        String body = response.readEntity(String.class);
        assertThat(body.startsWith(ConfigFileAction.CONFIG_NOT_FOUND), is(true));
    }

}
