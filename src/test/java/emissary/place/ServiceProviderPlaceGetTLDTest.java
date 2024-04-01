package emissary.place;

import emissary.core.DataObjectFactory;
import emissary.core.Family;
import emissary.core.IBaseDataObject;
import emissary.core.IMobileAgent;
import emissary.pool.AgentPool;
import emissary.pool.MobileAgentFactory;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServiceProviderPlaceGetTLDTest extends UnitTest {

    @BeforeEach
    public void setupLoggers() {


    }

    @Test
    void testGettingTLDThroughPlace() throws Exception {

        // Set up place, pool, agent
        IServiceProviderPlace place = new PlaceTest();
        AgentPool pool = new AgentPool(new MobileAgentFactory());
        IMobileAgent agent = pool.borrowAgent();

        // Build family tree
        IBaseDataObject payload = DataObjectFactory.getInstance("test".getBytes(), "test.1", "PARENT");
        payload.putParameter("PARENT_INFO", "this is a secret held by the parent");
        IBaseDataObject child = DataObjectFactory.getInstance("test child".getBytes(), "test.1" + Family.SEP + "1", "CHILD");
        List<IBaseDataObject> payloadList = new ArrayList<>();
        payloadList.add(child);
        payloadList.add(payload);

        // Hand payload to agent and wait for agent to be idle
        agent.arrive(payloadList, place, 0, new ArrayList<>());
        while (agent.isInUse()) {
            Thread.sleep(10);
        }

        assertEquals(payload.getStringParameter("PARENT_INFO").toUpperCase(Locale.ROOT),
                child.getStringParameter("CHILD_INFO"),
                "Child should have obtained access to parent metadata");

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
                d.putParameter("CHILD_INFO", p.getStringParameter("PARENT_INFO").toUpperCase(Locale.ROOT));
            } else {
                System.err.println("COuld not get parent " + p);
            }
        }
    }
}
