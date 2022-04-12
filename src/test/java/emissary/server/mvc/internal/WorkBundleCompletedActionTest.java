package emissary.server.mvc.internal;

import static emissary.server.mvc.adapters.WorkSpaceAdapter.WORK_BUNDLE_ID;
import static emissary.server.mvc.adapters.WorkSpaceAdapter.WORK_BUNDLE_STATUS;
import static emissary.server.mvc.internal.WorkSpaceClientSpaceTakeAction.CLIENT_NAME;
import static emissary.server.mvc.internal.WorkSpaceClientSpaceTakeAction.SPACE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.util.Arrays;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;

import emissary.core.Namespace;
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
public class WorkBundleCompletedActionTest extends EndpointTestBase {
    @DataPoints
    public static String[] EMPTY_REQUEST_PARAMS = new String[] {"", null, " ", "\n", "\t"};
    private MultivaluedHashMap<String, String> formParams;
    private static final String CLIENT_KEY = "INITIAL.FILE_PICK_UP_CLIENT.INPUT.http://localhost:9001/FilePickUpClient";
    private static final String WORKSPACE_BIND_KEY = "http://workBundleCompletedActionTest:7001/WorkSpace";
    private static final String WORKSPACE_NAME = "WORKSPACE.WORK_SPACE.INPUT." + WORKSPACE_BIND_KEY;
    private static final String WORK_BUNDLE_COMPLETED_ACTION = "WorkBundleCompleted.action";
    @SuppressWarnings("unused")
    private static final String FAILURE_RESULT = "<entryList />";

    @Before
    public void setup() throws Exception {
        formParams = new MultivaluedHashMap<>();
        formParams.put(CLIENT_NAME, Arrays.asList(CLIENT_KEY));
        formParams.put(SPACE_NAME, Arrays.asList(WORKSPACE_NAME));
        formParams.put(WORK_BUNDLE_ID, Arrays.asList("1"));
        formParams.put(WORK_BUNDLE_STATUS, Arrays.asList("true"));
        WorkSpace ws = new WorkSpace();
        Namespace.bind(WORKSPACE_BIND_KEY, ws);
    }

    @Override
    @After
    public void tearDown() {
        Namespace.unbind(WORKSPACE_BIND_KEY);
    }

    @Theory
    public void emptyParams(String badValue) {
        // setup
        formParams.replace(CLIENT_NAME, Arrays.asList(badValue));
        formParams.replace(SPACE_NAME, Arrays.asList(badValue));
        formParams.replace(WORK_BUNDLE_ID, Arrays.asList(badValue));
        formParams.replace(WORK_BUNDLE_STATUS, Arrays.asList(badValue));

        // test
        final Response response = target(WORK_BUNDLE_COMPLETED_ACTION).request().post(Entity.form(formParams));

        // verify
        final int status = response.getStatus();
        assertEquals(500, status);
        final String result = response.readEntity(String.class);
        assertTrue(result.startsWith("Bad params:"));
    }

    @Test
    public void badWorkSpaceKey() {
        // setup
        formParams.replace(SPACE_NAME, Arrays.asList("ThisShouldCauseAnException"));

        // test
        final Response response = target(WORK_BUNDLE_COMPLETED_ACTION).request().post(Entity.form(formParams));

        // verify
        final int status = response.getStatus();
        assertEquals(500, status);
        final String result = response.readEntity(String.class);
        assertTrue(result.startsWith("Bad params:"));
    }

    @Test
    public void missingWorkSpaceKey() {
        // setup
        formParams.replace(SPACE_NAME, Arrays.asList(WORKSPACE_NAME + "ThisWillMiss"));

        // test
        final Response response = target(WORK_BUNDLE_COMPLETED_ACTION).request().post(Entity.form(formParams));

        // verify
        final int status = response.getStatus();
        assertEquals(500, status);
        final String result = response.readEntity(String.class);
        assertEquals(
                "There was a problem while processing the WorkBundle: Not found: http://workBundleCompletedActionTest:7001/WorkSpaceThisWillMiss",
                result);
    }

    @Test
    public void badPickupClientKey() {
        // setup
        formParams.replace(CLIENT_NAME, Arrays.asList("ThisIsBad"));

        // test
        final Response response = target(WORK_BUNDLE_COMPLETED_ACTION).request().post(Entity.form(formParams));

        // verify
        final int status = response.getStatus();
        assertEquals(500, status);
        final String result = response.readEntity(String.class);
        assertTrue(result.startsWith("Bad params:"));
    }

    @Test
    public void itemNotPresentInPending() {
        // test
        final Response response = target(WORK_BUNDLE_COMPLETED_ACTION).request().post(Entity.form(formParams));

        // verify
        final int status = response.getStatus();
        assertEquals(500, status);
        final String result = response.readEntity(String.class);
        assertTrue(result.startsWith(""));
    }

    @Test
    public void successfulSubmission() throws Exception {
        // TODO Add a better test for the WorkSpace that validates the workCompleted method
        // setup
        Namespace.unbind(WORKSPACE_BIND_KEY);
        WorkSpace spyWs = spy(new WorkSpace());
        doReturn(true).when(spyWs).workCompleted("http://localhost:9001/FilePickUpClient", "1", true);
        Namespace.bind(WORKSPACE_BIND_KEY, spyWs);

        // test
        final Response response = target(WORK_BUNDLE_COMPLETED_ACTION).request().post(Entity.form(formParams));

        // verify
        final int status = response.getStatus();
        assertEquals(200, status);
        final String result = response.readEntity(String.class);
        assertEquals("Work Bundle Completed", result);
    }
}
