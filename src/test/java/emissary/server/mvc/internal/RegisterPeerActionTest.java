package emissary.server.mvc.internal;

import emissary.config.ConfigUtil;
import emissary.core.Namespace;
import emissary.directory.DirectoryPlace;
import emissary.directory.EmissaryNode;
import emissary.server.mvc.EndpointTestBase;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.Collections;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;

import static emissary.server.mvc.adapters.DirectoryAdapter.DIRECTORY_NAME;
import static emissary.server.mvc.adapters.DirectoryAdapter.TARGET_DIRECTORY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegisterPeerActionTest extends EndpointTestBase {

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

    @BeforeEach
    public void setup() throws IOException {
        formParams = new MultivaluedHashMap<>();
        formParams.put(DIRECTORY_NAME, Collections.singletonList(PEER_KEY_GOOD));
        formParams.put(TARGET_DIRECTORY, Collections.singletonList(DIRNAME));

        node = mock(EmissaryNode.class);
        when(node.isValid()).thenReturn(true);
        when(node.getPeerConfigurator()).thenReturn(ConfigUtil.getConfigInfo("peer-TESTING.cfg"));
        // make a new directory place and register it in the Namespace @ dirName
        DirectoryPlace directoryPlace = new DirectoryPlace("peer-TESTING.cfg", DIRNAME, node);
        Namespace.bind(DIRNAME, directoryPlace);
    }

    @Override
    @AfterEach
    public void tearDown() {
        Namespace.unbind(DIRNAME);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n", "\t"})
    void badParamValues(String badValue) {
        // setup
        formParams.replace(DIRECTORY_NAME, Collections.singletonList(badValue));
        formParams.replace(TARGET_DIRECTORY, Collections.singletonList(badValue));

        // test
        try (Response response = target(REGISTER_PEER_ACTION).request().post(Entity.form(formParams))) {
            // verify
            final int status = response.getStatus();
            assertEquals(500, status);
            final String result = response.readEntity(String.class);
            assertTrue(result.startsWith("Bad Params: "));
        }
    }

    @Test
    void registerPeerSuccessfully() {
        // test
        try (Response response = target(REGISTER_PEER_ACTION).request().post(Entity.form(formParams))) {
            // verify
            final int status = response.getStatus();
            assertEquals(200, status);
            final String result = response.readEntity(String.class);
            assertEquals(SUCCESS_RESULT, result);
        }
    }

    @Test
    void failUnknownPeerRegistration() {
        MultivaluedHashMap<String, String> newFormParams = new MultivaluedHashMap<>();
        newFormParams.put(DIRECTORY_NAME, Collections.singletonList(PEER_KEY_BAD));
        newFormParams.put(TARGET_DIRECTORY, Collections.singletonList(DIRNAME));

        // test
        try (Response response = target(REGISTER_PEER_ACTION).request().post(Entity.form(newFormParams))) {
            // verify
            final int status = response.getStatus();
            assertEquals(500, status);
            final String result = response.readEntity(String.class);
            assertTrue(result.startsWith("Registration failed"));
        }
    }

    @Test
    void missingBoundDirectoryPlace() {
        // setup
        Namespace.unbind(DIRNAME);

        // test
        try (Response response = target(REGISTER_PEER_ACTION).request().post(Entity.form(formParams))) {
            // verify
            final int status = response.getStatus();
            assertEquals(500, status);
            final String result = response.readEntity(String.class);
            assertEquals("Remote directory lookup failed for dirName: " + DIRNAME, result);
        }
    }
}
