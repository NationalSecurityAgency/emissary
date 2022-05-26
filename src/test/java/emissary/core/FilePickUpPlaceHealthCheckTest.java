package emissary.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import emissary.pickup.file.FilePickUpPlace;
import emissary.test.core.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FilePickUpPlaceHealthCheckTest extends UnitTest {

    @BeforeEach
    public void configureTestLoggers() {

    }

    @Test
    void testWhenThereIsNoPickUpPlace() {
        FilePickUpPlaceHealthCheck f = new FilePickUpPlaceHealthCheck(10, 1000);
        assertTrue(f.execute().isHealthy(), "Health check should pass when no Client");
    }

    @Test
    void testWhenThereIsAPickUpPlace() throws Exception {
        FilePickUpPlace fpc = new FilePickUpPlace();
        FilePickUpPlaceHealthCheck f = new FilePickUpPlaceHealthCheck(10, 1000);
        assertTrue(f.execute().isHealthy(), "Health check should pass when client is fresh");
        fpc.shutDown();
    }
}
