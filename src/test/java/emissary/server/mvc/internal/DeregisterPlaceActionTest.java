package emissary.server.mvc.internal;

import static emissary.server.mvc.adapters.DirectoryAdapter.ADD_KEY;
import static emissary.server.mvc.adapters.DirectoryAdapter.TARGET_DIRECTORY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;

import emissary.core.Namespace;
import emissary.directory.DirectoryPlace;
import emissary.directory.EmissaryNode;
import emissary.server.mvc.EndpointTestBase;
import emissary.util.io.ResourceReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class DeregisterPlaceActionTest extends EndpointTestBase {
    @DataPoints
    public static String[] EMPTY_REQUEST_PARAMS = new String[] {"", null, " ", "\n", "\t"};
    private MultivaluedHashMap<String, String> formParams;
    private static final String ADD_KEY_DIR = "UPPER_CASE.TO_LOWER.TRANSFORM.http://deregisterPlaceActionTest:8001/ToUpperPlace";
    private static final String TARGET_DIR = "http://deregisterPlaceActionTest:8001/DirectoryPlace";
    private static final String DEREGISTER_PLACE_ACTION = "DeregisterPlace.action";
    private static final ResourceReader rr = new ResourceReader();
    private static DirectoryPlace directory;

    @Before
    public void setup() throws Exception {
        formParams = new MultivaluedHashMap<>();
        formParams.put(TARGET_DIRECTORY, Arrays.asList(TARGET_DIR));
        formParams.put(ADD_KEY, Arrays.asList(ADD_KEY_DIR));
        directory = new DirectoryPlace(rr.getConfigDataAsStream(DirectoryPlace.class), TARGET_DIR, new TestEmissaryNode());
        directory.addPlaces(Arrays.asList(ADD_KEY_DIR));
        Namespace.bind(TARGET_DIR, directory);
    }

    @Override
    @After
    public void tearDown() {
        Namespace.unbind(TARGET_DIR);
        directory.shutDown();
        directory = null;
    }

    @Theory
    public void badParams(String badParam) {
        // setup
        formParams.replace(TARGET_DIRECTORY, Arrays.asList(badParam));
        formParams.replace(ADD_KEY, Arrays.asList(badParam));

        // test
        final Response response = target(DEREGISTER_PLACE_ACTION).request().post(Entity.form(formParams));

        // verify
        final int status = response.getStatus();
        assertEquals(500, status);
        final String result = response.readEntity(String.class);
        assertTrue(result.startsWith("Bad params:"));
    }

    @Test
    public void badDirectoryParam() {
        // setup
        formParams.replace(TARGET_DIRECTORY, Arrays.asList("CantFindThis"));

        // test
        final Response response = target(DEREGISTER_PLACE_ACTION).request().post(Entity.form(formParams));

        // verify
        final int status = response.getStatus();
        assertEquals(500, status);
        final String result = response.readEntity(String.class);
        assertEquals("No directory found using name CantFindThis", result);
    }

    @Test
    public void removeSingleDirectory() {
        // test
        final Response response = target(DEREGISTER_PLACE_ACTION).request().post(Entity.form(formParams));

        // verify
        final int status = response.getStatus();
        assertEquals(200, status);
        final String result = response.readEntity(String.class);
        assertEquals("Successfully removed 1 place(s) with keys: [" + ADD_KEY_DIR + "]", result);
    }

    @Test
    public void removeWithInvalidSecondKey() {
        // setup
        formParams.replace(ADD_KEY, Arrays.asList(ADD_KEY_DIR, "ThisOneWontHit"));
        // test
        final Response response = target(DEREGISTER_PLACE_ACTION).request().post(Entity.form(formParams));

        // verify
        final int status = response.getStatus();
        assertEquals(200, status);
        final String result = response.readEntity(String.class);
        // Notice that ALL keys must be well formed or none of them get processed
        assertEquals("Successfully removed 0 place(s) with keys: [" + ADD_KEY_DIR + ", ThisOneWontHit]", result);
    }

    @Test
    public void removeWithMissingSecondKey() {
        // setup
        formParams.replace(ADD_KEY, Arrays.asList(ADD_KEY_DIR, ADD_KEY_DIR + "MissingMe"));
        // test
        final Response response = target(DEREGISTER_PLACE_ACTION).request().post(Entity.form(formParams));

        // verify
        final int status = response.getStatus();
        assertEquals(200, status);
        final String result = response.readEntity(String.class);
        assertEquals("Successfully removed 1 place(s) with keys: [" + ADD_KEY_DIR + ", " + ADD_KEY_DIR + "MissingMe]", result);
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
