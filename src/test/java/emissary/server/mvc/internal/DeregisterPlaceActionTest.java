package emissary.server.mvc.internal;

import emissary.core.Namespace;
import emissary.directory.DirectoryPlace;
import emissary.directory.EmissaryNode;
import emissary.server.mvc.EndpointTestBase;
import emissary.util.io.ResourceReader;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Collections;
import javax.annotation.Nullable;

import static emissary.server.mvc.adapters.DirectoryAdapter.ADD_KEY;
import static emissary.server.mvc.adapters.DirectoryAdapter.TARGET_DIRECTORY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeregisterPlaceActionTest extends EndpointTestBase {

    private MultivaluedHashMap<String, String> formParams;
    private static final String ADD_KEY_DIR = "UPPER_CASE.TO_LOWER.TRANSFORM.http://deregisterPlaceActionTest:8001/ToUpperPlace";
    private static final String TARGET_DIR = "http://deregisterPlaceActionTest:8001/DirectoryPlace";
    private static final String DEREGISTER_PLACE_ACTION = "DeregisterPlace.action";
    private static final ResourceReader rr = new ResourceReader();
    @Nullable
    @SuppressWarnings("NonFinalStaticField")
    private static DirectoryPlace directory;

    @BeforeEach
    public void setup() throws Exception {
        formParams = new MultivaluedHashMap<>();
        formParams.put(TARGET_DIRECTORY, Collections.singletonList(TARGET_DIR));
        formParams.put(ADD_KEY, Collections.singletonList(ADD_KEY_DIR));
        directory = new DirectoryPlace(rr.getConfigDataAsStream(DirectoryPlace.class), TARGET_DIR, new TestEmissaryNode());
        directory.addPlaces(Collections.singletonList(ADD_KEY_DIR));
        Namespace.bind(TARGET_DIR, directory);
    }

    @Override
    @AfterEach
    public void tearDown() {
        Namespace.unbind(TARGET_DIR);
        directory.shutDown();
        directory = null;
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void badParams(String badParam) {
        // setup
        formParams.replace(TARGET_DIRECTORY, Collections.singletonList(badParam));
        formParams.replace(ADD_KEY, Collections.singletonList(badParam));

        // test
        try (Response response = target(DEREGISTER_PLACE_ACTION).request().post(Entity.form(formParams))) {
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
        formParams.replace(TARGET_DIRECTORY, Collections.singletonList(paramsToSanitize));
        formParams.replace(ADD_KEY, Collections.singletonList(paramsToSanitize));

        // test
        try (Response response = target(DEREGISTER_PLACE_ACTION).request().post(Entity.form(formParams))) {
            // verify
            final int status = response.getStatus();
            assertEquals(500, status);
            final String result = response.readEntity(String.class);
            assertTrue(result.startsWith("No directory found"));
        }
    }

    @Test
    void badDirectoryParam() {
        // setup
        formParams.replace(TARGET_DIRECTORY, Collections.singletonList("CantFindThis"));

        // test
        try (Response response = target(DEREGISTER_PLACE_ACTION).request().post(Entity.form(formParams))) {
            // verify
            final int status = response.getStatus();
            assertEquals(500, status);
            final String result = response.readEntity(String.class);
            assertEquals("No directory found using name CantFindThis", result);
        }
    }

    @Test
    void removeSingleDirectory() {
        // test
        try (Response response = target(DEREGISTER_PLACE_ACTION).request().post(Entity.form(formParams))) {
            // verify
            final int status = response.getStatus();
            assertEquals(200, status);
            final String result = response.readEntity(String.class);
            assertEquals("Successfully removed 1 place(s) with keys: [" + ADD_KEY_DIR + "]", result);
        }
    }

    @Test
    void removeWithInvalidSecondKey() {
        // setup
        formParams.replace(ADD_KEY, Arrays.asList(ADD_KEY_DIR, "ThisOneWontHit"));
        // test
        try (Response response = target(DEREGISTER_PLACE_ACTION).request().post(Entity.form(formParams))) {
            // verify
            final int status = response.getStatus();
            assertEquals(200, status);
            final String result = response.readEntity(String.class);
            // Notice that ALL keys must be well formed or none of them get processed
            assertEquals("Successfully removed 0 place(s) with keys: [" + ADD_KEY_DIR + ", ThisOneWontHit]", result);
        }
    }

    @Test
    void removeWithMissingSecondKey() {
        // setup
        formParams.replace(ADD_KEY, Arrays.asList(ADD_KEY_DIR, ADD_KEY_DIR + "MissingMe"));
        // test
        try (Response response = target(DEREGISTER_PLACE_ACTION).request().post(Entity.form(formParams))) {
            // verify
            final int status = response.getStatus();
            assertEquals(200, status);
            final String result = response.readEntity(String.class);
            assertEquals("Successfully removed 1 place(s) with keys: [" + ADD_KEY_DIR + ", " + ADD_KEY_DIR + "MissingMe]", result);
        }
    }

    // TODO can we clean this up and just use an EmissaryNode?
    private static final class TestEmissaryNode extends EmissaryNode {
        public TestEmissaryNode() {
            super();
            this.nodeName = "TestNode";
            this.nodePort = 1234567;
        }

        @Override
        public boolean isStandalone() {
            return false;
        }
    }
}
