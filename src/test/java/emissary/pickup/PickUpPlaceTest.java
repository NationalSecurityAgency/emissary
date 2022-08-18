package emissary.pickup;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import emissary.core.IMobileAgent;
import emissary.pickup.file.FilePickUpClient;
import emissary.pickup.file.FilePickUpPlace;
import emissary.server.EmissaryServer;
import emissary.test.core.UnitTest;
import org.junit.jupiter.api.Test;

class PickUpPlaceTest extends UnitTest {

    @Test
    void testIsPickUpTrue() {
        assertTrue(PickUpPlace.implementsPickUpPlace(FilePickUpPlace.class));
    }

    @Test
    void testIsPickupFalse() {
        assertFalse(PickUpPlace.implementsPickUpPlace(EmissaryServer.class));
    }

    @Test
    void testIsPickupFalseForInterface() {
        assertFalse(PickUpPlace.implementsPickUpPlace(IMobileAgent.class));
    }

    @Test
    void testIsPickupTrueForClient() {
        assertTrue(PickUpPlace.implementsPickUpPlace(FilePickUpClient.class));
    }

    @Test
    void testIdPickupTrueForPickupSpace() {
        assertTrue(PickUpPlace.implementsPickUpPlace(PickUpSpace.class));
    }

}
