package emissary.admin;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import emissary.directory.EmissaryNode;
import emissary.pickup.file.FilePickUpClient;
import emissary.pickup.file.FilePickUpPlace;
import emissary.place.CoordinationPlace;
import emissary.place.sample.DelayPlace;
import emissary.place.sample.DevNullPlace;
import emissary.test.core.UnitTest;
import org.junit.jupiter.api.Test;

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

}
