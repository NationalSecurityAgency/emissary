package emissary.server.mvc.internal;

import static emissary.server.mvc.internal.WorkSpaceClientSpaceTakeAction.CLIENT_NAME;
import static emissary.server.mvc.internal.WorkSpaceClientSpaceTakeAction.SPACE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.util.Collections;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;

import emissary.core.Namespace;
import emissary.pickup.WorkBundle;
import emissary.pickup.WorkSpace;
import emissary.server.mvc.EndpointTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

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
    @ValueSource(strings = {" ", "\n", "\t"})
    void emptyParams(String badValue) {
        // setup
        formParams.replace(CLIENT_NAME, Collections.singletonList(badValue));
        formParams.replace(SPACE_NAME, Collections.singletonList(badValue));

        // test
        Response response = target(CLIENT_SPACE_TAKE_ACTION).request().post(Entity.form(formParams));

        // verify
        final int status = response.getStatus();
        assertEquals(500, status);
        final String result = response.readEntity(String.class);
        assertTrue(result.startsWith("Bad params:"));
    }

    @Test
    void badWorkspaceKey() {
        // setup
        formParams.replace(SPACE_NAME, Collections.singletonList("WONT.CHOP.THIS.http://localhost:7001/WorkSpace"));

        // test
        Response response = target(CLIENT_SPACE_TAKE_ACTION).request().post(Entity.form(formParams));

        // verify
        final int status = response.getStatus();
        assertEquals(500, status);
        final String result = response.readEntity(String.class);
        assertEquals("There was an exception in the WorkSpaceClientSpaceTake", result);
    }


    @Test
    void nothingToTakeFromWorkSpace() {
        // TODO Investigate this case, seems like we shouldn't be returning empty WorkBundles in this case
        // test
        Response response = target(CLIENT_SPACE_TAKE_ACTION).request().post(Entity.form(formParams));

        // verify
        final int status = response.getStatus();
        assertEquals(200, status);
        final String result = response.readEntity(String.class);
        final WorkBundle resultWb = WorkBundle.buildWorkBundle(result);
        assertNotNull(resultWb);
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
        Response response = target(CLIENT_SPACE_TAKE_ACTION).request().post(Entity.form(formParams));
        final int status = response.getStatus();
        assertEquals(200, status);
        final String result = response.readEntity(String.class);
        final WorkBundle resultWb = WorkBundle.buildWorkBundle(result);
        assertNotNull(resultWb);
        assertEquals(resultWb.getFileNameList(), wb.getFileNameList());
        assertEquals(resultWb.getBundleId(), wb.getBundleId());
    }


}
