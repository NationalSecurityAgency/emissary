package emissary.server.mvc.internal;

import static emissary.server.mvc.adapters.DirectoryAdapter.ADD_KEY;
import static emissary.server.mvc.adapters.DirectoryAdapter.ADD_PROPAGATION_FLAG;
import static emissary.server.mvc.adapters.DirectoryAdapter.FAILED_DIRECTORY_NAME;
import static emissary.server.mvc.adapters.DirectoryAdapter.TARGET_DIRECTORY;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.util.Arrays;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;

import com.google.common.collect.Sets;
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
public class FailDirectoryActionTest extends EndpointTestBase {
    @DataPoints
    public static String[] EMPTY_REQUEST_PARAMS = new String[] {"", null, " ", "\n", "\t"};
    private MultivaluedHashMap<String, String> formParams;
    private static final String TARGET_DIR = "http://failDirectoryActionTest:1234/DirectoryPlace";
    private static final String FAIL_DIR = "EMISSARY_DIRECTORY_SERVICES.DIRECTORY.STUDY.http://failDirectoryRemotePlace:7001/DirectoryPlace";
    private static final String FAIL_DIRECTORY_ACTION = "FailDirectory.action";
    private static final ResourceReader rr = new ResourceReader();
    private static DirectoryPlace directory;

    @Before
    public void setup() throws Exception {
        formParams = new MultivaluedHashMap<>();
        formParams.put(TARGET_DIRECTORY, Arrays.asList(TARGET_DIR));
        formParams.put(FAILED_DIRECTORY_NAME, Arrays.asList(FAIL_DIR));
        directory = new DirectoryPlace(rr.getConfigDataAsStream(DirectoryPlace.class), TARGET_DIR, new TestEmissaryNode());
        // TODO directory isn't setup to recognize FAIL_DIR as a peer, can't get propogating test to pass
        directory.addPeerDirectories(Sets.newHashSet(FAIL_DIR), true);
        Namespace.bind(TARGET_DIR, directory);
    }

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
        final Response response = target(FAIL_DIRECTORY_ACTION).request().post(Entity.form(formParams));

        // verify
        final int status = response.getStatus();
        assertThat(status, equalTo(500));
        final String result = response.readEntity(String.class);
        assertThat(result.startsWith("Bad params:"), equalTo(true));
    }

    @Test
    public void failDirectoryNonPropagating() {
        // test
        final Response response = target(FAIL_DIRECTORY_ACTION).request().post(Entity.form(formParams));

        // verify
        final int status = response.getStatus();
        assertThat(status, equalTo(200));
        final String result = response.readEntity(String.class);
        assertThat(result, equalTo("Modified 0 entries from EMISSARY_DIRECTORY_SERVICES.DIRECTORY.STUDY." + TARGET_DIR
                + "$5050[1] due to failure of remote " + FAIL_DIR));
    }

    @Test
    public void failDirectoryPropagating() {
        // setup
        // TODO This needs to be investigated to try and get the DirectoryPlace setup correctly recognize peer
        // spy until we can figure out proper DirectoryPlace conf
        DirectoryPlace spy = spy(directory);
        doReturn(1).when(spy).irdFailRemoteDirectory(FAIL_DIR, true);
        Namespace.unbind(TARGET_DIR);
        Namespace.bind(TARGET_DIR, spy);
        formParams.put(ADD_PROPAGATION_FLAG, Arrays.asList("true"));

        // test
        final Response response = target(FAIL_DIRECTORY_ACTION).request().post(Entity.form(formParams));

        // verify
        final int status = response.getStatus();
        assertThat(status, equalTo(200));
        final String result = response.readEntity(String.class);

        assertThat(result, equalTo("Modified 1 entries from EMISSARY_DIRECTORY_SERVICES.DIRECTORY.STUDY." + TARGET_DIR
                + "$5050[1] due to failure of remote " + FAIL_DIR));


    }

    @Test
    public void badDirectoryPlaceLookup() {
        // setup
        formParams.replace(TARGET_DIRECTORY, Arrays.asList("WontFindThis"));

        // test
        final Response response = target(FAIL_DIRECTORY_ACTION).request().post(Entity.form(formParams));

        // verify
        final int status = response.getStatus();
        assertThat(status, equalTo(500));
        final String result = response.readEntity(String.class);
        assertThat(result, equalTo("No local directory found using name WontFindThis"));
    }


    // TODO can we clean this up and just use an EmissaryNode?
    private static final class TestEmissaryNode extends EmissaryNode {
        public TestEmissaryNode() {
            super();
            this.nodeName = "TestNode";
            this.nodePort = 2345;
        }
    }
}
