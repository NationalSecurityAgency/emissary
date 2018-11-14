package emissary.place;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import emissary.core.DataObjectFactory;
import emissary.core.Family;
import emissary.core.IBaseDataObject;
import emissary.core.IMobileAgent;
import emissary.directory.DirectoryEntry;
import emissary.pool.AgentPool;
import emissary.pool.MobileAgentFactory;
import emissary.test.core.UnitTest;
import org.junit.Before;
import org.junit.Test;

public class ServiceProviderPlaceGetTLDTest extends UnitTest {

    @Before
    public void setupLoggers() {


    }

    @Test
    public void testGettingTLDThroughPlace() throws Exception {

        // Set up place, pool, agent
        IServiceProviderPlace place = new PlaceTest();
        AgentPool pool = new AgentPool(new MobileAgentFactory());
        IMobileAgent agent = pool.borrowAgent();

        // Build family tree
        IBaseDataObject payload = DataObjectFactory.getInstance("test".getBytes(), "test.1", "PARENT");
        payload.putParameter("PARENT_INFO", "this is a secret held by the parent");
        IBaseDataObject child = DataObjectFactory.getInstance("test child".getBytes(), "test.1" + Family.SEP + "1", "CHILD");
        List<IBaseDataObject> payloadList = new ArrayList<IBaseDataObject>();
        payloadList.add(child);
        payloadList.add(payload);

        // Hand payload to agent and wait for agent to be idle
        agent.arrive(payloadList, place, 0, new ArrayList<DirectoryEntry>());
        while (agent.isInUse()) {
            Thread.sleep(10);
        }

        assertEquals("Child should have obtained access to parent metadata", payload.getStringParameter("PARENT_INFO").toUpperCase(),
                child.getStringParameter("CHILD_INFO"));

        place.shutDown();
        agent.killAgentAsync();
        pool.close();
    }

    private static final class PlaceTest extends ServiceProviderPlace {

        public PlaceTest() throws IOException {
            super();
        }

        @Override
        public void process(IBaseDataObject d) {
            IBaseDataObject p = getTLD();
            if (p != null && p.hasParameter("PARENT_INFO")) {
                d.putParameter("CHILD_INFO", p.getStringParameter("PARENT_INFO").toUpperCase());
            } else {
                System.err.println("COuld not get parent " + p);
            }
        }
    }
}
