package emissary.server.mvc.internal;

import emissary.core.Namespace;
import emissary.pickup.WorkBundle;
import emissary.pickup.WorkSpace;
import emissary.server.mvc.EndpointTestBase;

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

import static emissary.server.mvc.internal.WorkSpaceClientSpaceTakeAction.CLIENT_NAME;
import static emissary.server.mvc.internal.WorkSpaceClientSpaceTakeAction.SPACE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class WorkSpaceClientSpaceTakeActionTest extends EndpointTestBase {

    private MultivaluedHashMap<String, String> formParams;
    private static final String PLACE_NAME = "INITIAL.FILE_PICK_UP_CLIENT.INPUT.http://localhost:9001/FilePickUpClient";
    private static final String WORKSPACE_BIND_KEY = "http://workSpaceCLientSpaceTakeActionTest:7001/WorkSpace";
    private static final String WORKSPACE_NAME = "WORKSPACE.WORK_SPACE.INPUT." + WORKSPACE_BIND_KEY;
    private static final String CLIENT_SPACE_TAKE_ACTION = "WorkSpaceClientSpaceTake.action";
    @SuppressWarnings("unused")
    private static final String FAILURE_RESULT = "<entryList />";

    @BeforeEach
    public void setup() throws Exception {
        formParams = new MultivaluedHashMap<>();
        formParams.put(CLIENT_NAME, Collections.singletonList(PLACE_NAME));
        formParams.put(SPACE_NAME, Collections.singletonList(WORKSPACE_NAME));
        WorkSpace ws = new WorkSpace();
        Namespace.bind(WORKSPACE_BIND_KEY, ws);
    }

    @Override
    @AfterEach
    public void tearDown() {
        Namespace.unbind(WORKSPACE_BIND_KEY);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void emptyParams(String badValue) {
        // setup
        formParams.replace(CLIENT_NAME, Collections.singletonList(badValue));
        formParams.replace(SPACE_NAME, Collections.singletonList(badValue));

        // test
        try (Response response = target(CLIENT_SPACE_TAKE_ACTION).request().post(Entity.form(formParams))) {
            // verify
            final int status = response.getStatus();
            assertEquals(500, status);
            final String result = response.readEntity(String.class);
            assertTrue(result.startsWith("Bad params:"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"test\n\r", "\n", "\r"})
    void cleanParams(String paramsToSanitize) {
        // setup
        formParams.replace(CLIENT_NAME, Collections.singletonList(paramsToSanitize));
        formParams.replace(SPACE_NAME, Collections.singletonList(paramsToSanitize));

        // test
        try (Response response = target(CLIENT_SPACE_TAKE_ACTION).request().post(Entity.form(formParams))) {
            // verify
            final int status = response.getStatus();
            assertEquals(500, status);
            final String result = response.readEntity(String.class);
            assertTrue(result.contains("HTTP ERROR 500 Request failed"));
            assertEquals("Server Error", response.getStatusInfo().getReasonPhrase());
        }
    }

    @Test
    void badWorkspaceKey() {
        // setup
        formParams.replace(SPACE_NAME, Collections.singletonList("WONT.CHOP.THIS.http://localhost:7001/WorkSpace"));

        // test
        try (Response response = target(CLIENT_SPACE_TAKE_ACTION).request().post(Entity.form(formParams))) {
            // verify
            final int status = response.getStatus();
            assertEquals(500, status);
            final String result = response.readEntity(String.class);
            assertEquals("There was an exception in the WorkSpaceClientSpaceTake", result);
        }
    }


    @Test
    void nothingToTakeFromWorkSpace() {
        // TODO Investigate this case, seems like we shouldn't be returning empty WorkBundles in this case
        // test
        try (Response response = target(CLIENT_SPACE_TAKE_ACTION).request().post(Entity.form(formParams))) {
            // verify
            final int status = response.getStatus();
            assertEquals(200, status);
            final String result = response.readEntity(String.class);
            final WorkBundle resultWb = WorkBundle.buildWorkBundle(result);
            assertNotNull(resultWb);
        }
    }

    @Test
    void successfulTake() throws Exception {
        // setup
        WorkSpace spy = spy(new WorkSpace());
        WorkBundle wb = new WorkBundle();
        wb.setBundleId("1");
        wb.addFileName("file");
        doReturn(wb).when(spy).take(PLACE_NAME);
        Namespace.bind(WORKSPACE_BIND_KEY, spy);

        // test
        try (Response response = target(CLIENT_SPACE_TAKE_ACTION).request().post(Entity.form(formParams))) {
            final int status = response.getStatus();
            assertEquals(200, status);
            final String result = response.readEntity(String.class);
            final WorkBundle resultWb = WorkBundle.buildWorkBundle(result);
            assertNotNull(resultWb);
            assertEquals(resultWb.getFileNameList(), wb.getFileNameList());
            assertEquals(resultWb.getBundleId(), wb.getBundleId());
        }
    }


}
