package emissary.server.api;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import javax.ws.rs.core.Response;

import emissary.client.response.AgentsResponseEntity;
import emissary.core.Namespace;
import emissary.directory.EmissaryNode;
import emissary.pool.AgentPool;
import emissary.pool.MobileAgentFactory;
import emissary.server.EmissaryServer;
import emissary.server.mvc.EndpointTestBase;
import org.hamcrest.collection.IsEmptyCollection;
import org.hamcrest.collection.IsIterableContainingInOrder;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class AgentsTest extends EndpointTestBase {

    private static final String[] EXPECTED_AGENTS = {"MobileAgent-00: Idle", "MobileAgent-01: Idle", "MobileAgent-02: Idle", "MobileAgent-03: Idle",
            "MobileAgent-04: Idle", "MobileAgent-05: Idle", "MobileAgent-06: Idle", "MobileAgent-07: PHASE.FORM.Place", "MobileAgent-08: Idle",
            "MobileAgent-09: Idle"};

    @Before
    public void setup() {
        EmissaryServer mockServer = mock(EmissaryServer.class);
        EmissaryNode node = spy(new EmissaryNode());
        doReturn("localhost").when(node).getNodeName();
        doReturn(8001).when(node).getNodePort();
        AgentPool pool = new AgentPool(new MobileAgentFactory("emissary.core.HDMobileAgent"), 10);
        when(mockServer.getNode()).thenReturn(node);
        Namespace.bind("EmissaryServer", mockServer);
        Namespace.bind("MobileAgent-07", "PHASE.FORM.Place");
        Namespace.bind("AgentPool", pool);
    }

    @After
    public void cleanup() {
        Namespace.unbind("EmissaryServer");
        Namespace.unbind("AgentPool");
    }


    @Ignore
    // TODO: stop mocking and run a server since we made it so easy
    @Test
    public void agents() throws Exception {
        // test
        Response response = target("agents").request().get();

        // verify
        assertThat(response.getStatus(), equalTo(200));
        AgentsResponseEntity entity = response.readEntity(AgentsResponseEntity.class);
        assertThat(entity.getErrors(), IsEmptyCollection.empty());
        assertThat(entity.getCluster(), IsEmptyCollection.empty());
        assertThat(entity.getLocal().getHost(), equalTo("localhost:8001"));
        assertThat(entity.getLocal().getAgents(), IsIterableContainingInOrder.contains(EXPECTED_AGENTS));
    }

    @Ignore
    @Test
    public void clusterAgents() throws Exception {
        // TODO Look at putting this into an integration test with two real EmissaryServers stood up
    }

}
