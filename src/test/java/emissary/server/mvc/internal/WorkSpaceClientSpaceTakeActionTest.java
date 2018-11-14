package emissary.server.mvc.internal;

import static emissary.server.mvc.internal.WorkSpaceClientSpaceTakeAction.CLIENT_NAME;
import static emissary.server.mvc.internal.WorkSpaceClientSpaceTakeAction.SPACE_NAME;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.util.Arrays;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;

import emissary.core.Namespace;
import emissary.pickup.WorkBundle;
import emissary.pickup.WorkSpace;
import emissary.server.mvc.EndpointTestBase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class WorkSpaceClientSpaceTakeActionTest extends EndpointTestBase {
    @DataPoints
    public static String[] EMPTY_REQUEST_PARAMS = new String[] {"", null, " ", "\n", "\t"};
    private MultivaluedHashMap<String, String> formParams;
    private static final String PLACE_NAME = "INITIAL.FILE_PICK_UP_CLIENT.INPUT.http://localhost:9001/FilePickUpClient";
    private static final String WORKSPACE_BIND_KEY = "http://workSpaceCLientSpaceTakeActionTest:7001/WorkSpace";
    private static final String WORKSPACE_NAME = "WORKSPACE.WORK_SPACE.INPUT." + WORKSPACE_BIND_KEY;
    private static final String CLIENT_SPACE_TAKE_ACTION = "WorkSpaceClientSpaceTake.action";
    @SuppressWarnings("unused")
    private static final String FAILURE_RESULT = "<entryList />";

    @Before
    public void setup() throws Exception {
        formParams = new MultivaluedHashMap<>();
        formParams.put(CLIENT_NAME, Arrays.asList(PLACE_NAME));
        formParams.put(SPACE_NAME, Arrays.asList(WORKSPACE_NAME));
        WorkSpace ws = new WorkSpace();
        Namespace.bind(WORKSPACE_BIND_KEY, ws);
    }

    @After
    public void tearDown() {
        Namespace.unbind(WORKSPACE_BIND_KEY);
    }

    @Theory
    public void emptyParams(String badValue) {
        // setup
        formParams.replace(CLIENT_NAME, Arrays.asList(badValue));
        formParams.replace(SPACE_NAME, Arrays.asList(badValue));

        // test
        Response response = target(CLIENT_SPACE_TAKE_ACTION).request().post(Entity.form(formParams));

        // verify
        final int status = response.getStatus();
        assertThat(status, equalTo(500));
        final String result = response.readEntity(String.class);
        assertThat(result.startsWith("Bad params:"), equalTo(true));
    }

    @Test
    public void badWorkspaceKey() {
        // setup
        formParams.replace(SPACE_NAME, Arrays.asList("WONT.CHOP.THIS.http://localhost:7001/WorkSpace"));

        // test
        Response response = target(CLIENT_SPACE_TAKE_ACTION).request().post(Entity.form(formParams));

        // verify
        final int status = response.getStatus();
        assertThat(status, equalTo(500));
        final String result = response.readEntity(String.class);
        assertThat(result, equalTo("Not found: host:7001/WorkSpace"));
    }


    @Test
    public void nothingToTakeFromWorkSpace() {
        // TODO Investigate this case, seems like we shouldn't be returning empty WorkBundles in this case
        // test
        Response response = target(CLIENT_SPACE_TAKE_ACTION).request().post(Entity.form(formParams));

        // verify
        final int status = response.getStatus();
        assertThat(status, equalTo(200));
        final String result = response.readEntity(String.class);
        final WorkBundle resultWb = WorkBundle.buildWorkBundle(result);
        assertThat(resultWb, notNullValue());
    }

    @Test
    public void successfulTake() throws Exception {
        // setup
        WorkSpace spy = spy(new WorkSpace());
        WorkBundle wb = new WorkBundle();
        wb.setBundleId("1");
        wb.addFileName("file");
        doReturn(wb).when(spy).take(PLACE_NAME);
        Namespace.bind(WORKSPACE_BIND_KEY, spy);

        // test
        Response response = target(CLIENT_SPACE_TAKE_ACTION).request().post(Entity.form(formParams));
        final int status = response.getStatus();
        assertThat(status, equalTo(200));
        final String result = response.readEntity(String.class);
        final WorkBundle resultWb = WorkBundle.buildWorkBundle(result);
        assertThat(wb.getFileNameList(), equalTo(resultWb.getFileNameList()));
        assertThat(wb.getBundleId(), equalTo(resultWb.getBundleId()));
    }


}
