package emissary.pickup;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import emissary.core.IMobileAgent;
import emissary.pickup.file.FilePickUpClient;
import emissary.pickup.file.FilePickUpPlace;
import emissary.server.EmissaryServer;
import emissary.test.core.UnitTest;
import org.junit.Test;

public class PickUpPlaceTest extends UnitTest {

    @Test
    public void testIsPickUpTrue() {
        assertThat(PickUpPlace.implementsPickUpPlace(FilePickUpPlace.class), is(true));
    }

    @Test
    public void testIsPickupFalse() {
        assertThat(PickUpPlace.implementsPickUpPlace(EmissaryServer.class), is(false));
    }

    @Test
    public void testIsPickupFalseForInterface() {
        assertThat(PickUpPlace.implementsPickUpPlace(IMobileAgent.class), is(false));
    }

    @Test
    public void testIsPickupTrueForClient() {
        assertThat(PickUpPlace.implementsPickUpPlace(FilePickUpClient.class), is(true));
    }

    @Test
    public void testIdPickupTrueForPickupSpace() {
        assertThat(PickUpPlace.implementsPickUpPlace(PickUpSpace.class), is(true));
    }

}
