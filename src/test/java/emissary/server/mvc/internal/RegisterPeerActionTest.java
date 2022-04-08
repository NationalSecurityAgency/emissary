package emissary.server.mvc.internal;

import static emissary.server.mvc.adapters.DirectoryAdapter.DIRECTORY_NAME;
import static emissary.server.mvc.adapters.DirectoryAdapter.TARGET_DIRECTORY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;

import emissary.config.ConfigUtil;
import emissary.core.Namespace;
import emissary.directory.DirectoryPlace;
import emissary.directory.EmissaryNode;
import emissary.server.mvc.EndpointTestBase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class RegisterPeerActionTest extends EndpointTestBase {
    @DataPoints
    public static String[] EMPTY_REQUEST_PARAMS = new String[] {"", null, " ", "\n", "\t"};
    private MultivaluedHashMap<String, String> formParams;
    private static final String PEER_KEY_GOOD = "EMISSARY_DIRECTORY_SERVICES.DIRECTORY.STUDY.http://remoteHost:8888/DirectoryPlace";
    private static final String PEER_KEY_BAD = "EMISSARY_DIRECTORY_SERVICES.DIRECTORY.STUDY.http://otherRemoteHost:8888/DirectoryPlace";
    private static final String DIRNAME = "EMISSARY_DIRECTORY_SERVICES.DIRECTORY.STUDY.http://localhost:9999/DirectoryPlace$5050";
    private static final String REGISTER_PEER_ACTION = "RegisterPeer.action";
    private static final String SUCCESS_RESULT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
            + "<directory location=\"EMISSARY_DIRECTORY_SERVICES.DIRECTORY.STUDY.http://localhost:9999/DirectoryPlace\">\r\n"
            + "  <entryList dataid=\"EMISSARY_DIRECTORY_SERVICES::STUDY\">\r\n" + "    <entry>\r\n"
            + "      <key>EMISSARY_DIRECTORY_SERVICES.DIRECTORY.STUDY.http://localhost:9999/DirectoryPlace</key>\r\n" + "      <description />\r\n"
            + "      <cost>50</cost>\r\n" + "      <quality>50</quality>\r\n" + "      <expense>5050</expense>\r\n" + "    </entry>\r\n"
            + "  </entryList>\r\n" + "</directory>\r\n";
    private EmissaryNode node;

    @Before
    public void setup() throws IOException {
        formParams = new MultivaluedHashMap<>();
        formParams.put(DIRECTORY_NAME, Arrays.asList(PEER_KEY_GOOD));
        formParams.put(TARGET_DIRECTORY, Arrays.asList(DIRNAME));

        node = mock(EmissaryNode.class);
        when(node.isValid()).thenReturn(true);
        when(node.getPeerConfigurator()).thenReturn(ConfigUtil.getConfigInfo("peer-TESTING.cfg"));
        // make a new directory place and register it in the Namespace @ dirName
        DirectoryPlace directoryPlace = new DirectoryPlace("peer-TESTING.cfg", DIRNAME, node);
        Namespace.bind(DIRNAME, directoryPlace);
    }

    @Override
    @After
    public void tearDown() {
        Namespace.unbind(DIRNAME);
    }

    @Theory
    public void badParamValues(String badValue) {
        // setup
        formParams.replace(DIRECTORY_NAME, Arrays.asList(badValue));
        formParams.replace(TARGET_DIRECTORY, Arrays.asList(badValue));

        // test
        Response response = target(REGISTER_PEER_ACTION).request().post(Entity.form(formParams));

        // verify
        final int status = response.getStatus();
        assertEquals(500, status);
        final String result = response.readEntity(String.class);
        assertTrue(result.startsWith("Bad Params: "));
    }

    @Test
    public void registerPeerSuccessfully() {
        // test

        Response response = target(REGISTER_PEER_ACTION).request().post(Entity.form(formParams));

        // verify
        final int status = response.getStatus();
        assertEquals(200, status);
        final String result = response.readEntity(String.class);
        assertEquals(SUCCESS_RESULT, result);
    }

    @Test
    public void failUnknownPeerRegistration() {
        MultivaluedHashMap<String, String> newFormParams = new MultivaluedHashMap<>();
        newFormParams.put(DIRECTORY_NAME, Arrays.asList(PEER_KEY_BAD));
        newFormParams.put(TARGET_DIRECTORY, Arrays.asList(DIRNAME));

        // test
        Response response = target(REGISTER_PEER_ACTION).request().post(Entity.form(newFormParams));

        // verify
        final int status = response.getStatus();
        assertEquals(500, status);
        final String result = response.readEntity(String.class);
        assertTrue(result.startsWith("Registration failed"));
    }

    @Test
    public void missingBoundDirectoryPlace() {
        // setup
        Namespace.unbind(DIRNAME);

        // test
        Response response = target(REGISTER_PEER_ACTION).request().post(Entity.form(formParams));

        // verify
        final int status = response.getStatus();
        assertEquals(500, status);
        final String result = response.readEntity(String.class);
        assertEquals("Remote directory lookup failed for dirName: " + DIRNAME, result);
    }
}
