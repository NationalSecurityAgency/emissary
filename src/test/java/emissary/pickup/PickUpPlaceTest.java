package emissary.pickup;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import emissary.core.IMobileAgent;
import emissary.pickup.file.FilePickUpClient;
import emissary.pickup.file.FilePickUpPlace;
import emissary.server.EmissaryServer;
import emissary.test.core.UnitTest;
import org.junit.Test;

public class PickUpPlaceTest extends UnitTest {

    @Test
    public void testIsPickUpTrue() {
        assertTrue(PickUpPlace.implementsPickUpPlace(FilePickUpPlace.class));
    }

    @Test
    public void testIsPickupFalse() {
        assertFalse(PickUpPlace.implementsPickUpPlace(EmissaryServer.class));
    }

    @Test
    public void testIsPickupFalseForInterface() {
        assertFalse(PickUpPlace.implementsPickUpPlace(IMobileAgent.class));
    }

    @Test
    public void testIsPickupTrueForClient() {
        assertTrue(PickUpPlace.implementsPickUpPlace(FilePickUpClient.class));
    }

    @Test
    public void testIdPickupTrueForPickupSpace() {
        assertTrue(PickUpPlace.implementsPickUpPlace(PickUpSpace.class));
    }

}
