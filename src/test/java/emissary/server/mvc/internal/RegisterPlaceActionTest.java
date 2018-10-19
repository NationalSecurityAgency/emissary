package emissary.server.mvc.internal;

import static emissary.server.mvc.adapters.DirectoryAdapter.TARGET_DIRECTORY;
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

//TODO This endpoint is actually a POST not a get. Refactor to test the POST case when refactoring the logic.
@Ignore
@RunWith(Theories.class)
public class RegisterPlaceActionTest extends EndpointTestBase {
    @DataPoints
    public static String[] EMPTY_REQUEST_PARAMS = new String[] {"", null, " ", "\n", "\t"};
    private final String REGISTER_PLACE_ACTION = "/RegisterPlace.action";

    @Theory
    public void testRegisterPlaceEmptyParams(String directory) {
        Response response = target(REGISTER_PLACE_ACTION).queryParam(TARGET_DIRECTORY, directory).request().get();
        assertThat(500, equalTo(response.getStatus()));
        String body = response.readEntity(String.class);
        assertThat(body, equalTo(CreatePlaceAction.EMPTY_PARAM_MSG));
    }

    // I expected this text to fail but it passes, need to look here.
    @Test
    @Ignore
    public void testRegisterPlaceBadDirectoryKey() {
        String directory = "thisIsABadKey";
        Response response = target(REGISTER_PLACE_ACTION).queryParam(TARGET_DIRECTORY, directory).request().get();
        assertThat(200, equalTo(response.getStatus()));
        String body = response.readEntity(String.class);
        assertThat(body, equalTo(RegisterPlaceAction.CALL_FAILURE));
    }

}
