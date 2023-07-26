package emissary.core;

import emissary.admin.PlaceStarter;
import emissary.directory.DirectoryEntry;
import emissary.place.IServiceProviderPlace;
import emissary.place.ServiceProviderPlace;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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
    void testDenyList() throws Exception {
        byte[] configDeniedData = ("PLACE_NAME = \"DelayPlace\"\n" + "SERVICE_NAME = \"DELAY\"\n"
                + "SERVICE_TYPE = \"STUDY\"\n" + "SERVICE_DESCRIPTION = \"delay stuff\"\n" + "SERVICE_COST = 99\n"
                + "SERVICE_QUALITY = 50\n" + "SERVICE_PROXY = \"*\"\n" + "SERVICE_PROXY_DENY = \"FINI\"\n").getBytes();
        InputStream config = new ByteArrayInputStream(configDeniedData);
        IServiceProviderPlace place = new PlaceTest(config);
        HDMobileAgent agent = new MobAg2();

        // test accepted
        IBaseDataObject d1 = DataObjectFactory.getInstance();
        d1.setCurrentForm("THECF");
        d1.appendTransformHistory("S.GARBAGE.ANALYZE.http://localhost:8005/GarbagePlace$1234");
        agent.getNextKey(place, d1);

        // test denied
        IBaseDataObject d2 = DataObjectFactory.getInstance();
        d2.setCurrentForm("FINI");
        d2.appendTransformHistory("A.FOO1.ANALYZE.http://localhost:8005/GarbagePlace$1234");
        agent.getNextKey(place, d2);

        // test upstream in parallel tracking
        byte[] configDeniedData2 = ("PLACE_NAME = \"FilePickUpClient\"\n" + "SERVICE_NAME = \"DELAY\"\n"
                + "SERVICE_TYPE = \"STUDY\"\n" + "SERVICE_DESCRIPTION = \"delay stuff\"\n" + "SERVICE_COST = 99\n"
                + "SERVICE_QUALITY = 50\n" + "SERVICE_PROXY = \"*\"\n" + "SERVICE_PROXY_DENY = \"THECF\"\n").getBytes();
        InputStream config2 = new ByteArrayInputStream(configDeniedData2);
        PlaceStarter.createPlace("http://localhost:8005/FilePickUpClient", config2,
                "emissary.pickup.file.FilePickUpClientTest$MyFilePickUpClient", null);
        IBaseDataObject d3 = DataObjectFactory.getInstance();
        d3.setCurrentForm("THECF");
        d3.appendTransformHistory("B.FOO1.ANALYZE.http://localhost:8005/GarbagePlace$1234");
        agent.getNextKey(place, d3);

        // verify
        assertEquals(1, agent.visitedPlaces.size(), "FOO2 and FOO3 should not have been added");
        assertTrue(agent.visitedPlaces.contains("FOO"), "Only FOO should have been added");

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
        private boolean hit = false;

        @Override
        protected DirectoryEntry nextKeyFromDirectory(final String dataID, final IServiceProviderPlace place, final DirectoryEntry lastEntry,
                final IBaseDataObject payloadArg) {
            if (lastEntry.getDataType().equalsIgnoreCase("S")) {
                DirectoryEntry d = new DirectoryEntry("A.FOO.ANALYZE.http://localhost:8001/PlaceTest$9950[1]");
                d.isLocal();
                return d;
            } else if (lastEntry.getDataType().equalsIgnoreCase("A")) {
                DirectoryEntry d = new DirectoryEntry("B.FOO2.ANALYZE.http://localhost:8001/PlaceTest$9950[1]");
                d.isLocal();
                return d;
            } else if (lastEntry.getDataType().equalsIgnoreCase("B")) {
                DirectoryEntry d = new DirectoryEntry("C.FOO.ANALYZE.http://localhost:8001/PlaceTest$9950[1]");
                d.isLocal();
                return d;
            } else if (!hit && lastEntry.getDataType().equalsIgnoreCase("THECF")) {
                DirectoryEntry d = new DirectoryEntry("D.FOO3.ANALYZE.http://localhost:8005/FilePickUpClient");
                d.isLocal();
                hit = true;
                return d;
            } else {
                return null;
            }
        }
    }

    private static final class PlaceTest extends ServiceProviderPlace {
        public PlaceTest(InputStream config) throws IOException {
            super(config);
        }
    }
}
