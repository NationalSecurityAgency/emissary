package emissary.place;

import emissary.admin.PlaceStarter;
import emissary.config.ConfigUtil;
import emissary.core.DataObjectFactory;
import emissary.core.EmissaryException;
import emissary.core.IBaseDataObject;
import emissary.core.MobileAgent;
import emissary.core.Namespace;
import emissary.core.ResourceWatcher;
import emissary.directory.EmissaryNode;
import emissary.place.sample.DevNullPlace;
import emissary.server.EmissaryServer;
import emissary.test.core.junit5.UnitTest;

import jakarta.annotation.Nullable;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class CoordinationPlaceTest extends UnitTest {

    CoordinationPlace place;
    IServiceProviderPlace mockCoordPlace;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        place = new CoordinationPlace("emissary.place.CoordinationPlace.cfg");

        mockCoordPlace = mock(IServiceProviderPlace.class);
        place.placeRefs = Collections.singletonList(mockCoordPlace);

        Namespace.bind("ResourceWatcher", mock(ResourceWatcher.class));
        Namespace.bind(Thread.currentThread().getName(), mock(MobileAgent.class));
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        Namespace.unbind("ResourceWatcher");
        Namespace.unbind(Thread.currentThread().getName());
        CoordinationPlace.failedCoordPlaceCreation.clear();
        super.tearDown();
    }

    @Test
    void shouldContinue() {
        // test the default behavior
        assertTrue(place.shouldContinue(mock(IBaseDataObject.class), mock(IServiceProviderPlace.class)));
    }

    @Test
    void shouldSkip() {
        // test the default behavior
        assertFalse(place.shouldSkip(mock(IBaseDataObject.class), mock(IServiceProviderPlace.class)));
    }

    @Test
    void process() throws Exception {
        IBaseDataObject ibdo = DataObjectFactory.getInstance("testing this".getBytes(UTF_8), "test_file", "text");
        place.process(ibdo);
        assertTrue(ibdo.getAllCurrentForms().contains("TESTCOORDINATE"));
    }

    @Test
    void processHeavyDuty() throws Exception {
        when(mockCoordPlace.agentProcessHeavyDuty(Mockito.any(IBaseDataObject.class))).thenReturn(
                Collections.singletonList(DataObjectFactory.getInstance("child testing this".getBytes(UTF_8), "test_file", "text")));

        IBaseDataObject ibdo = DataObjectFactory.getInstance("testing this".getBytes(UTF_8), "test_file", "text");
        List<IBaseDataObject> sprouts = place.processHeavyDuty(ibdo);
        assertTrue(CollectionUtils.isNotEmpty(sprouts) && sprouts.size() == 1);
        assertTrue(ibdo.getAllCurrentForms().contains("TESTCOORDINATE"));
    }

    @Test
    void testPlaceReferenceOrder() throws IOException {
        place.configG = ConfigUtil.getConfigInfo(CoordinationPlaceTest.class);
        place.configurePlace();

        List<String> expectedRefs = List.of(
                "emissary.place.sample.CachePlace",
                "emissary.place.sample.ToLowerPlace",
                "emissary.place.sample.ToUpperPlace");

        List<String> actualRefs = place.placeRefs.stream()
                .map(IServiceProviderPlace::getPlaceName)
                .collect(Collectors.toList());

        assertEquals(expectedRefs, actualRefs);
    }

    @Test
    void testPlaceReferenceOrderWithFlavor() throws EmissaryException, IOException {
        String originalFlavor = System.getProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY);

        try {
            System.setProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY, String.join(",", originalFlavor, "FLAVORTEST"));
            ConfigUtil.initialize();

            place.configG = ConfigUtil.getConfigInfo(CoordinationPlaceTest.class);
            place.configurePlace();

            List<String> expectedRefs = List.of(
                    "emissary.place.sample.ToLowerPlace",
                    "emissary.place.sample.ToUpperPlace",
                    "emissary.place.sample.RefreshablePlace");

            List<String> actualRefs = place.placeRefs.stream()
                    .map(IServiceProviderPlace::getPlaceName)
                    .collect(Collectors.toList());

            assertEquals(expectedRefs, actualRefs);

        } finally {
            System.setProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY, originalFlavor);
            ConfigUtil.initialize();
        }
    }

    @Test
    void testFailedCoordinationPlace() {
        // add coordination place that does not exist
        place.configG.addEntry("SERVICE_COORDINATION", "fakePlace");
        place.configurePlace();

        // verify place was not started/created
        assertTrue(place.placeRefs.isEmpty());
        // verify coordination place failed, and thus was added to failedCoordPlaceCreation set
        assertEquals(1, CoordinationPlace.getFailedCoordinationPlaces().size());

        place.configG.removeEntry("SERVICE_COORDINATION", "fakePlace");
    }

    @Test
    void testStrictStartupModeUsesServerNode() {
        String originalStrict = System.getProperty(EmissaryNode.STRICT_STARTUP_MODE);
        try {
            System.setProperty(EmissaryNode.STRICT_STARTUP_MODE, "false");
            try (MockedStatic<EmissaryServer> ignored = mockServerNode(true)) {
                assertTrue(place.isStrictStartupMode());
            }

            // a stale system property must not override the node
            System.setProperty(EmissaryNode.STRICT_STARTUP_MODE, "true");
            try (MockedStatic<EmissaryServer> ignored = mockServerNode(false)) {
                assertFalse(place.isStrictStartupMode());
            }
        } finally {
            restoreStrictProperty(originalStrict);
        }
    }

    @Test
    void testStrictStartupModeWithoutServer() {
        String originalStrict = System.getProperty(EmissaryNode.STRICT_STARTUP_MODE);
        try (MockedStatic<EmissaryServer> server = Mockito.mockStatic(EmissaryServer.class)) {
            System.setProperty(EmissaryNode.STRICT_STARTUP_MODE, "true");
            server.when(EmissaryServer::isInitialized).thenReturn(false);
            assertFalse(place.isStrictStartupMode());
        } finally {
            restoreStrictProperty(originalStrict);
        }
    }

    @Test
    void testMissingInventoryEntryStrictNode() {
        try (MockedStatic<EmissaryServer> ignored = mockServerNode(true);
                MockedStatic<PlaceStarter> starter = mockCreatePlace()) {
            place.configG.addEntry("SERVICE_COORDINATION", MISSING_PLACE);
            place.configurePlace();

            // strict lookup fails before any place is created, so no DevNullPlace stands in for it
            starter.verify(() -> PlaceStarter.createPlace(endsWith("/" + MISSING_PLACE), nullable(InputStream.class), anyString(),
                    nullable(String.class)), never());
            assertTrue(place.placeRefs.isEmpty());
            assertEquals(1, CoordinationPlace.getFailedCoordinationPlaces().size());
        } finally {
            place.configG.removeEntry("SERVICE_COORDINATION", MISSING_PLACE);
        }
    }

    @Test
    void testMissingInventoryEntryNonStrictNodeIgnoresStaleProperty() {
        String originalStrict = System.getProperty(EmissaryNode.STRICT_STARTUP_MODE);
        try (MockedStatic<EmissaryServer> ignored = mockServerNode(false);
                MockedStatic<PlaceStarter> starter = mockCreatePlace()) {
            System.setProperty(EmissaryNode.STRICT_STARTUP_MODE, "true");
            place.configG.addEntry("SERVICE_COORDINATION", MISSING_PLACE);
            place.configurePlace();

            // non-strict node falls back to the default class
            starter.verify(() -> PlaceStarter.createPlace(endsWith("/" + MISSING_PLACE), nullable(InputStream.class),
                    eq(DevNullPlace.class.getName()), nullable(String.class)));
        } finally {
            place.configG.removeEntry("SERVICE_COORDINATION", MISSING_PLACE);
            restoreStrictProperty(originalStrict);
        }
    }

    private static final String MISSING_PLACE = "NoSuchInventoryEntryPlace";

    private static MockedStatic<EmissaryServer> mockServerNode(boolean strict) {
        EmissaryNode node = new EmissaryNode();
        node.setStrictStartupMode(strict);
        EmissaryServer server = mock(EmissaryServer.class);
        when(server.getNode()).thenReturn(node);
        MockedStatic<EmissaryServer> mocked = Mockito.mockStatic(EmissaryServer.class);
        mocked.when(EmissaryServer::isInitialized).thenReturn(true);
        mocked.when(EmissaryServer::getInstance).thenReturn(server);
        return mocked;
    }

    /** Use the real class lookup but don't actually create places */
    private static MockedStatic<PlaceStarter> mockCreatePlace() {
        MockedStatic<PlaceStarter> mocked = Mockito.mockStatic(PlaceStarter.class, Mockito.CALLS_REAL_METHODS);
        mocked.when(() -> PlaceStarter.createPlace(anyString(), nullable(InputStream.class), anyString(), nullable(String.class))).thenReturn(null);
        return mocked;
    }

    private static void restoreStrictProperty(@Nullable String original) {
        if (original == null) {
            System.clearProperty(EmissaryNode.STRICT_STARTUP_MODE);
        } else {
            System.setProperty(EmissaryNode.STRICT_STARTUP_MODE, original);
        }
    }
}
