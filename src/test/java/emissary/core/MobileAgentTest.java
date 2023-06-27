package emissary.core;

import emissary.admin.PlaceStarter;
import emissary.directory.DirectoryEntry;
import emissary.place.IServiceProviderPlace;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void testTypeLookup() {
        // invalid types
        assertEquals(0, MobileAgent.typeLookup(null));
        assertEquals(0, MobileAgent.typeLookup(""));
        assertEquals(0, MobileAgent.typeLookup("UNDEFINED"));
        assertEquals(0, MobileAgent.typeLookup("JUNK"));

        // valid types
        assertEquals(0, MobileAgent.typeLookup("STUDY"));
        assertEquals(1, MobileAgent.typeLookup("ID"));
        assertEquals(2, MobileAgent.typeLookup("COORDINATE"));
        assertEquals(3, MobileAgent.typeLookup("PRETRANSFORM"));
        assertEquals(4, MobileAgent.typeLookup("TRANSFORM"));
        assertEquals(5, MobileAgent.typeLookup("POSTTRANSFORM"));
        assertEquals(6, MobileAgent.typeLookup("ANALYZE"));
        assertEquals(7, MobileAgent.typeLookup("VERIFY"));
        assertEquals(8, MobileAgent.typeLookup("IO"));
        assertEquals(9, MobileAgent.typeLookup("REVIEW"));
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

    @Test
    void testDisallowedList() throws Exception {
        // setup
        HDMobileAgent agent = new MobAg2();
        HDMobileAgentTest.SimplePlace place = new HDMobileAgentTest.SimplePlace("emissary.place.sample.DisallowPlace.cfg");
        d.appendTransformHistory("S.GARBAGE.ANALYZE.http://localhost:8005/GarbagePlace$1234");
        agent.getNextKey(place, d);
        d.appendTransformHistory("A.FOO1.ANALYZE.http://localhost:8005/FooPlace$1234");
        agent.getNextKey(place, d);
        d.appendTransformHistory("B.FOO2.ANALYZE.http://localhost:8005/FooPlace$1234");
        agent.getNextKey(place, d);

        // verify
        assertEquals(2, agent.visitedPlaces.size(), "FOO2 should not have been added");
        assertTrue(agent.visitedPlaces.containsAll(Arrays.asList("FOO1", "FOO3")), "FOO1 and FOO3 should have both been added");
        agent.killAgent();
        place.shutDown();
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

    static final class MobAg2 extends HDMobileAgent {
        @Override
        protected DirectoryEntry nextKeyFromDirectory(final String dataID, final IServiceProviderPlace place, final DirectoryEntry lastEntry,
                final IBaseDataObject payloadArg) {
            if (lastEntry.getDataType().equalsIgnoreCase("S")) {
                return new DirectoryEntry("A.FOO1.ANALYZE.http://localhost:8005/FooPlace$1234");
            } else if (lastEntry.getDataType().equalsIgnoreCase("A")) {
                return new DirectoryEntry("B.FOO2.ANALYZE.http://localhost:8005/FooPlace$1234");
            } else if (lastEntry.getDataType().equalsIgnoreCase("B")) {
                return new DirectoryEntry("C.FOO3.ANALYZE.http://localhost:8005/FooPlace$1234");
            } else {
                return null;
            }
        }
    }
}
