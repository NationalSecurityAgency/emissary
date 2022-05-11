package emissary.server.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Response;

import emissary.client.response.PlacesResponseEntity;
import emissary.command.ServerCommand;
import emissary.core.EmissaryException;
import emissary.core.Namespace;
import emissary.directory.EmissaryNode;
import emissary.server.EmissaryServer;
import emissary.server.mvc.EndpointTestBase;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public class PlacesTest extends EndpointTestBase {
    private static final Set<String> EXPECTED_PLACES = new HashSet<>(Arrays.asList("pickupClient", "pickupPlace", "processingPlace"));
    private static final Set<String> UNBOUND_SERVER_ERR = new HashSet<>(
            Arrays.asList("Problem finding the emissary server or places in the namespace: Not found: EmissaryServer"));

    EmissaryServer server;

    @BeforeClass
    public static void setUpClass() {
        Namespace.clear();
    }

    @Before
    public void setup() throws InstantiationException, IllegalAccessException, ClassNotFoundException, EmissaryException {
        EmissaryNode node = new EmissaryNode();
        ServerCommand cmd = ServerCommand.parse(ServerCommand.class, "-m", "cluster");
        cmd.setupServer();
        server = new EmissaryServer(cmd, node);
    }

    @After
    public void cleanup() {
        Namespace.clear();
    }

    @Test
    public void places() throws Exception {
        Namespace.bind("EmissaryServer", server);
        Namespace.bind("PickupPlace", "pickupPlace");
        Namespace.bind("PickupClient", "pickupClient");
        Namespace.bind("ThisWontBeAdded", "miss");
        Namespace.bind("ProcessingPlace", "processingPlace");

        // test
        Response response = target("places").request().get();

        // verify
        assertEquals(200, response.getStatus());
        PlacesResponseEntity entity = response.readEntity(PlacesResponseEntity.class);
        assertEquals("localhost:8001", entity.getLocal().getHost());
        assertEquals(3, entity.getLocal().getPlaces().size());
        assertEquals(EXPECTED_PLACES, entity.getLocal().getPlaces());
        assertEquals(0, entity.getErrors().size());
        assertNull(entity.getCluster());

        // cleanup
        Namespace.unbind("EmissaryServer");
        Namespace.unbind("PickupPlace");
        Namespace.unbind("PickupClient");
        Namespace.unbind("ThisWontBeAdded");
        Namespace.unbind("ProcessingPlace");
    }

    @Test
    public void placesNoPlacesClientsBound() throws Exception {
        Namespace.bind("EmissaryServer", server);

        // test
        Response response = target("places").request().get();

        // verify
        assertEquals(200, response.getStatus());
        PlacesResponseEntity entity = response.readEntity(PlacesResponseEntity.class);
        assertEquals("localhost:8001", entity.getLocal().getHost());
        assertEquals(0, entity.getLocal().getPlaces().size());
        assertEquals(0, entity.getErrors().size());
        assertNull(entity.getCluster());
    }

    @Test
    public void noServerBound() throws Exception {
        // setup
        // Namespace.unbind("EmissaryServer");

        // test
        Response response = target("places").request().get();

        // verify
        assertEquals(200, response.getStatus());
        PlacesResponseEntity entity = response.readEntity(PlacesResponseEntity.class);
        assertNull(entity.getLocal());
        assertNull(entity.getCluster());
        assertEquals(1, entity.getErrors().size());
        assertEquals(UNBOUND_SERVER_ERR, entity.getErrors());

    }

    @Ignore
    @Test
    public void clusterPlaces() throws Exception {
        // TODO look at putting this in an integration tests with two real EmissaryServers
    }

}
