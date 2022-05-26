package emissary.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import emissary.admin.PlaceStarter;
import emissary.directory.DirectoryEntry;
import emissary.place.IServiceProviderPlace;
import emissary.test.core.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MobileAgentTest extends UnitTest {
    private MobAg agent;
    private IServiceProviderPlace place;
    private IBaseDataObject d;

    @BeforeEach
    public void setup() {
        agent = new MobAg();
        place = PlaceStarter.createPlace("http://localhost:8006/ToUpperPlace", null, "emissary.place.sample.ToUpperPlace", null);
        d = DataObjectFactory.getInstance();
        d.setCurrentForm("THECF");
    }

    @AfterEach
    public void teardown() throws Exception {
        super.tearDown();
        agent.killAgent();
        place.shutDown();
    }

    @Test
    void testHistory() {
        d.appendTransformHistory("UNKNOWN.FOO.ID.http://localhost:8005/FooPlace$1234");
        agent.recordHistory(place, d);
    }


    @Test
    void testAddParrallelTrackingInfo() {
        // setup
        d.appendTransformHistory("UNKNOWN.GARBAGE.ANALYZE.http://localhost:8005/GarbagePlace$1234");
        agent.getNextKey(place, d);
        d.appendTransformHistory("UNKNOWN.FOO.ANALYZE.http://localhost:8005/FooPlace$1234");

        // test
        agent.getNextKey(place, d);

        // verify
        assertEquals(2, agent.visitedPlaces.size(), "FOO and FOOD should have both been added");
        assertTrue(agent.visitedPlaces.containsAll(Arrays.asList("FOO", "FOOD")), "FOO and FOOD should have both been added");
    }

    static final class MobAg extends HDMobileAgent {
        static final long serialVersionUID = 102211824991899593L;

        @Override
        public void recordHistory(final IServiceProviderPlace place, final IBaseDataObject payload) {
            final int sz = payload.transformHistory().size();
            super.recordHistory(place, payload);
            assertEquals(sz + 1, payload.transformHistory().size(), "One entry was not added to history");
            final String key = payload.getLastPlaceVisited().getFullKey();
            assertTrue(key.startsWith(payload.currentForm() + "."), "Current form is not on history element");
        }

        @Override
        protected DirectoryEntry nextKeyFromDirectory(final String dataID, final IServiceProviderPlace place, final DirectoryEntry lastEntry,
                final IBaseDataObject payloadArg) {
            if (lastEntry.getServiceName().equalsIgnoreCase("FOO")) {
                return new DirectoryEntry("UNKNOWN.FOO.ANALYZE.http://localhost:8005/FooPlace$1234");
            } else {
                return new DirectoryEntry("UNKNOWN.FOOD.ANALYZE.http://localhost:8005/FoodPlace$1234");
            }
        }
    }
}
