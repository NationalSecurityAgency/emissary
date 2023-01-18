package emissary.server.mvc.internal;

import emissary.core.Namespace;
import emissary.pickup.WorkSpace;
import emissary.server.mvc.EndpointTestBase;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;

import static emissary.server.mvc.adapters.WorkSpaceAdapter.WORK_BUNDLE_ID;
import static emissary.server.mvc.adapters.WorkSpaceAdapter.WORK_BUNDLE_STATUS;
import static emissary.server.mvc.internal.WorkSpaceClientSpaceTakeAction.CLIENT_NAME;
import static emissary.server.mvc.internal.WorkSpaceClientSpaceTakeAction.SPACE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class WorkBundleCompletedActionTest extends EndpointTestBase {

    private MultivaluedHashMap<String, String> formParams;
    private static final String CLIENT_KEY = "INITIAL.FILE_PICK_UP_CLIENT.INPUT.http://localhost:9001/FilePickUpClient";
    private static final String WORKSPACE_BIND_KEY = "http://workBundleCompletedActionTest:7001/WorkSpace";
    private static final String WORKSPACE_NAME = "WORKSPACE.WORK_SPACE.INPUT." + WORKSPACE_BIND_KEY;
    private static final String WORK_BUNDLE_COMPLETED_ACTION = "WorkBundleCompleted.action";
    @SuppressWarnings("unused")
    private static final String FAILURE_RESULT = "<entryList />";

    @BeforeEach
    public void setup() throws Exception {
        formParams = new MultivaluedHashMap<>();
        formParams.put(CLIENT_NAME, Collections.singletonList(CLIENT_KEY));
        formParams.put(SPACE_NAME, Collections.singletonList(WORKSPACE_NAME));
        formParams.put(WORK_BUNDLE_ID, Collections.singletonList("1"));
        formParams.put(WORK_BUNDLE_STATUS, Collections.singletonList("true"));
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
        formParams.replace(WORK_BUNDLE_ID, Collections.singletonList(badValue));
        formParams.replace(WORK_BUNDLE_STATUS, Collections.singletonList(badValue));

        // test
        try (final Response response = target(WORK_BUNDLE_COMPLETED_ACTION).request().post(Entity.form(formParams))) {
            // verify
            final int status = response.getStatus();
            assertEquals(500, status);
            final String result = response.readEntity(String.class);
            assertTrue(result.startsWith("Bad params:"));
        }
    }

    @Test
    void badWorkSpaceKey() {
        // setup
        formParams.replace(SPACE_NAME, Collections.singletonList("ThisShouldCauseAnException"));

        // test
        try (final Response response = target(WORK_BUNDLE_COMPLETED_ACTION).request().post(Entity.form(formParams))) {
            // verify
            final int status = response.getStatus();
            assertEquals(500, status);
            final String result = response.readEntity(String.class);
            assertTrue(result.startsWith("Bad params:"));
        }
    }

    @Test
    void missingWorkSpaceKey() {
        // setup
        formParams.replace(SPACE_NAME, Collections.singletonList(WORKSPACE_NAME + "ThisWillMiss"));

        // test
        try (final Response response = target(WORK_BUNDLE_COMPLETED_ACTION).request().post(Entity.form(formParams))) {
            // verify
            final int status = response.getStatus();
            assertEquals(500, status);
            final String result = response.readEntity(String.class);
            assertEquals(
                    "There was a problem while processing the WorkBundle: Not found: http://workBundleCompletedActionTest:7001/WorkSpaceThisWillMiss",
                    result);
        }
    }

    @Test
    void badPickupClientKey() {
        // setup
        formParams.replace(CLIENT_NAME, Collections.singletonList("ThisIsBad"));

        // test
        try (final Response response = target(WORK_BUNDLE_COMPLETED_ACTION).request().post(Entity.form(formParams))) {
            // verify
            final int status = response.getStatus();
            assertEquals(500, status);
            final String result = response.readEntity(String.class);
            assertTrue(result.startsWith("Bad params:"));
        }
    }

    @Test
    void itemNotPresentInPending() {
        // test
        try (final Response response = target(WORK_BUNDLE_COMPLETED_ACTION).request().post(Entity.form(formParams))) {
            // verify
            final int status = response.getStatus();
            assertEquals(500, status);
            final String result = response.readEntity(String.class);
            assertTrue(result.startsWith(""));
        }
    }

    @Test
    void successfulSubmission() throws Exception {
        // TODO Add a better test for the WorkSpace that validates the workCompleted method
        // setup
        Namespace.unbind(WORKSPACE_BIND_KEY);
        WorkSpace spyWs = spy(new WorkSpace());
        doReturn(true).when(spyWs).workCompleted("http://localhost:9001/FilePickUpClient", "1", true);
        Namespace.bind(WORKSPACE_BIND_KEY, spyWs);

        // test
        try (final Response response = target(WORK_BUNDLE_COMPLETED_ACTION).request().post(Entity.form(formParams))) {
            // verify
            final int status = response.getStatus();
            assertEquals(200, status);
            final String result = response.readEntity(String.class);
            assertEquals("Work Bundle Completed", result);
        }
    }
}
