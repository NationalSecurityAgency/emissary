package emissary.admin;

import emissary.core.Namespace;
import emissary.directory.DirectoryEntry;
import emissary.directory.DirectoryPlace;
import emissary.directory.EmissaryNode;
import emissary.directory.IDirectoryPlace;
import emissary.pickup.file.FilePickUpClient;
import emissary.pickup.file.FilePickUpPlace;
import emissary.place.CoordinationPlace;
import emissary.place.sample.DelayPlace;
import emissary.place.sample.DevNullPlace;
import emissary.test.core.junit5.UnitTest;
import emissary.util.io.ResourceReader;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class StartupTest extends UnitTest {
    @Test
    void testSortPlaces() throws IOException {
        List<String> somePlaces = new ArrayList<>();
        EmissaryNode node = new EmissaryNode();
        String location = "http://" + node.getNodeName() + ":" + node.getNodePort();
        somePlaces.add(location + "/" + CoordinationPlace.class.getSimpleName());
        somePlaces.add(location + "/" + DelayPlace.class.getSimpleName());
        somePlaces.add(location + "/" + DevNullPlace.class.getSimpleName());
        somePlaces.add(location + "/" + FilePickUpPlace.class.getSimpleName());
        somePlaces.add(location + "/" + FilePickUpClient.class.getSimpleName());


        Startup startup = new Startup(node.getNodeConfigurator(), node);
        startup.sortPlaces(somePlaces);
        // TODO: figure out why the key isn't http://localhost:8001. Seems
        // this way when running too, so get("") works. This test is just testing the
        // PickUp stuff is pulled out separately
        List<String> pickups = startup.pickupLists.get("");
        List<String> places = startup.placeLists.get("");
        assertIterableEquals(Arrays.asList(location + "/" + "FilePickUpPlace", location + "/" + "FilePickUpClient"), pickups);
        assertIterableEquals(Arrays.asList(location + "/" + "CoordinationPlace", location + "/" + "DelayPlace", location + "/" + "DevNullPlace"),
                places);
    }

    @Test
    void testInvisPlaceStart() throws IOException {
        // setup node, startup, and DirectoryPlace
        EmissaryNode node = new EmissaryNode();
        Startup startup = new Startup(node.getNodeConfigurator(), node);
        String location = "http://" + node.getNodeName() + ":" + node.getNodePort();
        dirStartUp();

        // test if place is already started before startup
        startup.activeDirPlaces.add(location + "/PlaceAlreadyStartedTest");
        startup.placeAlreadyStarted.add(location + "/PlaceAlreadyStartedTest");
        assertTrue(startup.verifyNoInvisiblePlacesStarted());
        startup.activeDirPlaces.clear();
        startup.placeAlreadyStarted.clear();

        // test if place is started up normally and is active dir
        startup.places.put(location, DevNullPlace.class.getSimpleName());
        startup.activeDirPlaces.add(DevNullPlace.class.getSimpleName());
        assertTrue(startup.verifyNoInvisiblePlacesStarted());
        startup.activeDirPlaces.clear();
        startup.places.remove(location, DevNullPlace.class.getSimpleName());

        // test unannounced place with active dir
        startup.activeDirPlaces.add(location + "/PlaceStartUnannouncedTest");
        assertFalse(startup.verifyNoInvisiblePlacesStarted());
        startup.activeDirPlaces.clear();

        // teardown
        dirTeardown();
    }

    @Test
    void verifyNoInvisiblePlacesStartedHandlesNullLocalPlace() throws IOException {
        // setup node, startup, and DirectoryPlace
        EmissaryNode node = new EmissaryNode();
        Startup startup = new Startup(node.getNodeConfigurator(), node);

        try (MockedStatic<DirectoryPlace> dirPlace = Mockito.mockStatic(DirectoryPlace.class)) {

            DirectoryEntry entry = mock(DirectoryEntry.class);
            when(entry.getLocalPlace()).thenReturn(null);

            List<DirectoryEntry> dirEntries = new ArrayList<>();
            dirEntries.add(0, entry);

            IDirectoryPlace directoryPlace = mock(IDirectoryPlace.class);
            when(directoryPlace.getEntries()).thenReturn(dirEntries);

            dirPlace.when(DirectoryPlace::lookup).thenReturn(directoryPlace);

            assertTrue(startup.verifyNoInvisiblePlacesStarted());
        }
    }

    private DirectoryPlace master = null;
    private DirectoryPlace client = null;

    public void dirStartUp() throws IOException {
        // master directory
        InputStream configStream = new ResourceReader().getConfigDataAsStream(this);

        String masterloc = "http://localhost:8001/TestMasterDirectoryPlace";
        this.master = spy(new DirectoryPlace(configStream, masterloc, new EmissaryNode()));
        configStream.close();
        Namespace.bind(masterloc, this.master);

        // non-master directory
        configStream = new ResourceReader().getConfigDataAsStream(this);
        String clientloc = "http://localhost:8001/DirectoryPlace";
        this.client = spy(new DirectoryPlace(configStream, this.master.getDirectoryEntry().getKey(), clientloc, new EmissaryNode()));
        configStream.close();
        Namespace.bind(clientloc, this.client);
    }

    public void dirTeardown() {
        this.master.shutDown();
        this.master = null;
        this.client.shutDown();
        this.client = null;
        for (String s : Namespace.keySet()) {
            Namespace.unbind(s);
        }
    }
}
