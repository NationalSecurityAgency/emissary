package emissary.admin;

import emissary.config.ServiceConfigGuide;
import emissary.core.EmissaryRuntimeException;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
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

import jakarta.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class StartupIT extends UnitTest {
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
        Set<String> pickups = startup.pickupLists.get("");
        Set<String> places = startup.placeLists.get("");
        assertIterableEquals(Arrays.asList(location + "/" + "FilePickUpPlace", location + "/" + "FilePickUpClient"), pickups);
        assertIterableEquals(Arrays.asList(location + "/" + "CoordinationPlace", location + "/" + "DelayPlace", location + "/" + "DevNullPlace"),
                places);
    }

    @Test
    void testSortPlacesMissingClassEntryStrictMode() throws IOException {
        EmissaryNode node = new EmissaryNode();
        node.setStrictStartupMode(true);
        String location = "http://" + node.getNodeName() + ":" + node.getNodePort();
        String missing = location + "/NoSuchInventoryEntryPlace";

        Startup startup = new Startup(node.getNodeConfigurator(), node);
        startup.sortPlaces(Arrays.asList(location + "/" + DelayPlace.class.getSimpleName(), missing));

        assertIterableEquals(List.of(location + "/DelayPlace"), startup.placeLists.get(""));
        assertTrue(startup.failedPlaces.contains(missing));
    }

    @Test
    void testSortPlacesMissingClassEntryNonStrictMode() throws IOException {
        EmissaryNode node = new EmissaryNode();
        node.setStrictStartupMode(false);
        String location = "http://" + node.getNodeName() + ":" + node.getNodePort();
        String missing = location + "/NoSuchInventoryEntryPlace";

        Startup startup = new Startup(node.getNodeConfigurator(), node);
        startup.sortPlaces(List.of(missing));

        // falls back to the default class and is still sorted for startup
        assertIterableEquals(List.of(missing), startup.placeLists.get(""));
        assertTrue(startup.failedPlaces.isEmpty());
    }

    @Test
    void testLocalDirectorySetupMissingClassEntryStrictMode() {
        EmissaryNode node = new EmissaryNode();
        node.setStrictStartupMode(true);
        String missing = "http://" + node.getNodeName() + ":" + node.getNodePort() + "/NoSuchInventoryEntryPlace";

        ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry("LOCAL_DIRECTORY", missing);
        Startup startup = new Startup(config, node);

        assertFalse(startup.localDirectorySetup(startup.localDirectories));
        assertTrue(startup.failedPlaces.contains(missing));
        assertTrue(startup.failedLocalDirectories.containsValue(missing));
        assertTrue(startup.localDirectories.isEmpty());
        // no DevNullPlace stand-in was constructed at the directory location
        assertThrows(NamespaceException.class, () -> Namespace.lookup(missing));
    }

    @Test
    void testLocalDirectorySetupMissingClassEntryNonStrictMode() {
        EmissaryNode node = new EmissaryNode();
        node.setStrictStartupMode(false);
        String missing = "http://" + node.getNodeName() + ":" + node.getNodePort() + "/NoSuchInventoryEntryPlace";

        ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry("LOCAL_DIRECTORY", missing);
        Startup startup = new Startup(config, node);

        try {
            // non-strict mode keeps the old behavior: fall back to the default class and carry on
            assertTrue(startup.localDirectorySetup(startup.localDirectories));
            assertTrue(startup.failedPlaces.isEmpty());
            // the default class has no directory constructor, so the failed directory is recorded for placeSetup to skip
            assertTrue(startup.failedLocalDirectories.containsValue(missing));
        } finally {
            for (String s : Namespace.keySet()) {
                Namespace.unbind(s);
            }
        }
    }

    @Test
    void testGetClassStringMissingEntry() {
        String missing = "http://localhost:8001/NoSuchInventoryEntryPlace";
        assertEquals(PlaceStarter.defaultClassName, PlaceStarter.getClassString(missing));
        assertEquals(PlaceStarter.defaultClassName, PlaceStarter.getClassString(missing, false));
        assertThrows(EmissaryRuntimeException.class, () -> PlaceStarter.getClassString(missing, true));
    }

    @Test
    void testInvisPlaceStart() throws IOException {
        // setup node, startup, and DirectoryPlace
        EmissaryNode node = new EmissaryNode();
        String location = "http://" + node.getNodeName() + ":" + node.getNodePort();
        dirStartUp();

        // test if place is already started before startup
        Startup.activeDirPlaces.add(location + "/PlaceAlreadyStartedTest");
        Startup.placeAlreadyStarted.add(location + "/PlaceAlreadyStartedTest");
        assertTrue(Startup.verifyNoInvisiblePlacesStarted());
        Startup.activeDirPlaces.clear();
        Startup.placeAlreadyStarted.clear();

        // test if place is started up normally and is active dir
        Startup.places.put(location, DevNullPlace.class.getSimpleName());
        Startup.activeDirPlaces.add(DevNullPlace.class.getSimpleName());
        assertTrue(Startup.verifyNoInvisiblePlacesStarted());
        Startup.activeDirPlaces.clear();
        Startup.places.remove(location, DevNullPlace.class.getSimpleName());

        // test unannounced place with active dir
        Startup.activeDirPlaces.add(location + "/PlaceStartUnannouncedTest");
        assertFalse(Startup.verifyNoInvisiblePlacesStarted());
        Startup.activeDirPlaces.clear();

        // teardown
        dirTeardown();
    }

    @Test
    void verifyNoInvisiblePlacesStartedHandlesNullLocalPlace() {
        try (MockedStatic<DirectoryPlace> dirPlace = Mockito.mockStatic(DirectoryPlace.class)) {

            DirectoryEntry entry = mock(DirectoryEntry.class);
            when(entry.getLocalPlace()).thenReturn(null);

            List<DirectoryEntry> dirEntries = new ArrayList<>();
            dirEntries.add(0, entry);

            IDirectoryPlace directoryPlace = mock(IDirectoryPlace.class);
            when(directoryPlace.getEntries()).thenReturn(dirEntries);

            dirPlace.when(DirectoryPlace::lookup).thenReturn(directoryPlace);

            assertTrue(Startup.verifyNoInvisiblePlacesStarted());
        }
    }

    @Nullable
    private DirectoryPlace server = null;
    @Nullable
    private DirectoryPlace client = null;

    public void dirStartUp() throws IOException {
        // server directory
        InputStream serverConfigStream = new ResourceReader().getConfigDataAsStream(this);
        String serverLocation = "http://localhost:8001/TestServerDirectoryPlace";
        this.server = spy(new DirectoryPlace(serverConfigStream, serverLocation, new EmissaryNode()));
        serverConfigStream.close();
        Namespace.bind(serverLocation, this.server);

        // client directory
        InputStream clientConfigStream = new ResourceReader().getConfigDataAsStream(this);
        String clientLocation = "http://localhost:8001/DirectoryPlace";
        this.client = spy(new DirectoryPlace(clientConfigStream, this.server.getDirectoryEntry().getKey(), clientLocation, new EmissaryNode()));
        clientConfigStream.close();
        Namespace.bind(clientLocation, this.client);
    }

    public void dirTeardown() {
        this.server.shutDown();
        this.server = null;
        this.client.shutDown();
        this.client = null;
        for (String s : Namespace.keySet()) {
            Namespace.unbind(s);
        }
    }
}
