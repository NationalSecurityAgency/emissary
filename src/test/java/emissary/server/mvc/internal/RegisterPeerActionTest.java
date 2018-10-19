package emissary.server.mvc.internal;

import static emissary.server.mvc.adapters.DirectoryAdapter.DIRECTORY_NAME;
import static emissary.server.mvc.adapters.DirectoryAdapter.TARGET_DIRECTORY;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.util.Arrays;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;

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
    private static final String PEER_KEY = "EMISSARY_DIRECTORY_SERVICES.DIRECTORY_REGISTERPEERTEST.STUDY.http://junkme:9001/DirectoryPlace";
    private static final String DIRNAME = "http://junkme:10001/DirectoryPlace";
    private static final String REGISTER_PEER_ACTION = "RegisterPeer.action";
    private static final String SUCCESS_RESULT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
            + "<directory location=\"EMISSARY_DIRECTORY_SERVICES.DIRECTORY.STUDY.http://junkme:10001/DirectoryPlace\">\r\n"
            + "  <entryList dataid=\"EMISSARY_DIRECTORY_SERVICES::STUDY\">\r\n" + "    <entry>\r\n"
            + "      <key>EMISSARY_DIRECTORY_SERVICES.DIRECTORY.STUDY.http://junkme:10001/DirectoryPlace</key>\r\n" + "      <description />\r\n"
            + "      <cost>50</cost>\r\n" + "      <quality>50</quality>\r\n" + "      <expense>5050</expense>\r\n" + "    </entry>\r\n"
            + "  </entryList>\r\n" + "</directory>\r\n";

    @Before
    public void setup() throws IOException {
        formParams = new MultivaluedHashMap<>();
        formParams.put(DIRECTORY_NAME, Arrays.asList(PEER_KEY));
        formParams.put(TARGET_DIRECTORY, Arrays.asList(DIRNAME));
        // make a new directory place and register it in the Namespace @ dirName
        DirectoryPlace directoryPlace = new DirectoryPlace(DIRNAME, new TestEmissaryNode());
        // TODO Probably an integration test but need to verify actually pulling all the directories from a peer, can't
        // do in standalone mode
        // directoryPlace.addPlaces(Arrays.asList("INITIAL.FILE_PICKUP_CLIENT.INPUT.http://junkme:8001/FilePickUpClient"));
        Namespace.bind(DIRNAME, directoryPlace);
    }

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
        assertThat(status, equalTo(500));
        final String result = response.readEntity(String.class);
        assertThat(result.startsWith("Bad Params: "), equalTo(true));

    }

    @Test
    public void registerPeerSuccessfully() {
        // test
        Response response = target(REGISTER_PEER_ACTION).request().post(Entity.form(formParams));

        // verify
        final int status = response.getStatus();
        assertThat(status, equalTo(200));
        final String result = response.readEntity(String.class);
        assertThat(result, equalTo(SUCCESS_RESULT));
    }

    @Test
    public void missingBoundDirectoryPlace() {
        // setup
        Namespace.unbind(DIRNAME);

        // test
        Response response = target(REGISTER_PEER_ACTION).request().post(Entity.form(formParams));

        // verify
        final int status = response.getStatus();
        assertThat(status, equalTo(500));
        final String result = response.readEntity(String.class);
        assertThat(result, equalTo("Remote directory lookup failed for dirName: " + DIRNAME));
    }

    static class TestEmissaryNode extends EmissaryNode {
        // public TestEmissaryNode() {
        // nodeNameIsDefault = true;
        // }
        //
        @Override
        public int getNodePort() {
            return 10001;
        }

        @Override
        public String getNodeName() {
            return "junkme";
        }

        //
        // @Override
        // public String getNodeType() {
        // return "trs80";
        // }

        @Override
        public boolean isStandalone() {
            return false;
        }
    }

}
