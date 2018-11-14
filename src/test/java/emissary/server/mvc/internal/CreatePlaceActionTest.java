package emissary.server.mvc.internal;

import static emissary.server.mvc.adapters.PlaceStarterAdapter.CP_CLASS_NAME;
import static emissary.server.mvc.adapters.PlaceStarterAdapter.CP_DIRECTORY;
import static emissary.server.mvc.adapters.PlaceStarterAdapter.CP_LOCATION;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

import java.util.Arrays;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;

import emissary.server.mvc.EndpointTestBase;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class CreatePlaceActionTest extends EndpointTestBase {
    @DataPoints
    public static String[] EMPTY_REQUEST_PARAMS = new String[] {"", null, " ", "\n", "\t"};
    private final String CREATE_PLACE_ACTION = "/CreatePlace.action";
    private final String LOCATION = "http://localhost:8001/ToUpperPlace";
    private final String CLASS_STRING = "emissary.place.sample.ToUpperPlace";
    private final String DIRECTORY = "EMISSARY_DIRECTORY_SERVICES.DIRECTORY.STUDY.http://localhost:8001/DirectoryPlace$5050[1]";
    private final String SUCCESSFUL_RESPONSE = "LOWER_CASE.TO_UPPER.TRANSFORM.http://localhost:8001/ToUpperPlace$5010[1]";
    private MultivaluedHashMap<String, String> formParams;

    @Before
    public void setup() {
        formParams = new MultivaluedHashMap<>();
        formParams.put(CP_DIRECTORY, Arrays.asList(DIRECTORY));
        formParams.put(CP_LOCATION, Arrays.asList(LOCATION));
        formParams.put(CP_CLASS_NAME, Arrays.asList(CLASS_STRING));
    }

    @Theory
    public void testCreatePlaceBadParams(String badParam) {
        // This causes 500 errors because we are posting with empty required parameters
        // setup
        formParams = new MultivaluedHashMap<>();
        formParams.put(CP_DIRECTORY, Arrays.asList(badParam));
        formParams.put(CP_LOCATION, Arrays.asList(badParam));
        formParams.put(CP_CLASS_NAME, Arrays.asList(badParam));

        // test
        Response response = target(CREATE_PLACE_ACTION).request().post(Entity.form(formParams));

        // verify
        final int status = response.getStatus();
        assertThat(status, equalTo(500));
        final String result = response.readEntity(String.class);
        assertThat(result, equalTo(CreatePlaceAction.EMPTY_PARAM_MSG));
    }

    @Test
    public void testCreatePlace() {
        // test
        Response response = target(CREATE_PLACE_ACTION).request().post(Entity.form(formParams));

        // verify
        final int status = response.getStatus();
        assertThat(status, equalTo(200));
        final String result = response.readEntity(String.class);
        assertThat(result, equalTo(SUCCESSFUL_RESPONSE));
    }

    @Test
    public void testCreatePlaceBadDirectory() {
        // setup
        String badDirectory = "This is a bad DIRECTORY";
        formParams.replace(CP_DIRECTORY, Arrays.asList(badDirectory));

        // test
        Response response = target(CREATE_PLACE_ACTION).request().post(Entity.form(formParams));

        // verify
        final int status = response.getStatus();
        assertThat(status, equalTo(200));
        final String result = response.readEntity(String.class);
        // TODO Look at this case, should we be successful?
        assertThat(result, equalTo(SUCCESSFUL_RESPONSE));
    }

    @Test
    public void testCreatePlaceBadLocation() {
        // setup
        String badLocation = "badLocation";
        formParams.replace(CP_LOCATION, Arrays.asList(badLocation));

        // test
        Response response = target(CREATE_PLACE_ACTION).request().post(Entity.form(formParams));

        // verify
        final int status = response.getStatus();
        assertThat(status, equalTo(500));
        final String result = response.readEntity(String.class);
        assertThat(result.startsWith(CreatePlaceAction.PLACE_STARTER_ERR), is(true));
    }

    @Test
    public void testCreatePlaceBadClassString() {
        // setup
        String badClass = "this.should.Fail";
        formParams.replace(CP_CLASS_NAME, Arrays.asList(badClass));

        // test
        Response response = target(CREATE_PLACE_ACTION).request().post(Entity.form(formParams));

        // verify
        final int status = response.getStatus();
        assertThat(status, equalTo(500));
        final String result = response.readEntity(String.class);
        assertThat(result.startsWith(CreatePlaceAction.PLACE_STARTER_ERR), is(true));
    }

}
