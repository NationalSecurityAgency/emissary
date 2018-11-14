package emissary.core;

import static org.junit.Assert.assertTrue;

import emissary.pickup.file.FilePickUpPlace;
import emissary.test.core.UnitTest;
import org.junit.Before;
import org.junit.Test;

public class FilePickUpPlaceHealthCheckTest extends UnitTest {

    @Before
    public void configureTestLoggers() {

    }

    @Test
    public void testWhenThereIsNoPickUpPlace() throws Exception {
        FilePickUpPlaceHealthCheck f = new FilePickUpPlaceHealthCheck(10, 1000);
        assertTrue("Health check should pass when no Client", f.execute().isHealthy());
    }

    @Test
    public void testWhenThereIsAPickUpPlace() throws Exception {
        FilePickUpPlace fpc = new FilePickUpPlace();
        FilePickUpPlaceHealthCheck f = new FilePickUpPlaceHealthCheck(10, 1000);
        assertTrue("Health check should pass when client is fresh", f.execute().isHealthy());
        fpc.shutDown();
    }
}
