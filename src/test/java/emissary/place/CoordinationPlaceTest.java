package emissary.place;

import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.core.MobileAgent;
import emissary.core.Namespace;
import emissary.core.ResourceWatcher;
import emissary.place.sample.CachePlace;
import emissary.place.sample.RefreshablePlace;
import emissary.place.sample.ToLowerPlace;
import emissary.place.sample.ToUpperPlace;
import emissary.test.core.junit5.UnitTest;
import emissary.util.io.ResourceReader;

import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CoordinationPlaceTest extends UnitTest {

    CoordinationPlace place;
    IServiceProviderPlace mockCoordPlace;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        InputStream configStream = new ResourceReader().getConfigDataAsStream(this);
        place = new CoordinationPlace(configStream);

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
        IBaseDataObject ibdo = DataObjectFactory.getInstance("testing this".getBytes(), "test_file", "text");
        place.process(ibdo);
        assertTrue(ibdo.getAllCurrentForms().contains("TESTCOORDINATE"));
    }

    @Test
    void processHeavyDuty() throws Exception {
        when(mockCoordPlace.agentProcessHeavyDuty(Mockito.any(IBaseDataObject.class))).thenReturn(
                Collections.singletonList(DataObjectFactory.getInstance("child testing this".getBytes(), "test_file", "text")));

        IBaseDataObject ibdo = DataObjectFactory.getInstance("testing this".getBytes(), "test_file", "text");
        List<IBaseDataObject> sprouts = place.processHeavyDuty(ibdo);
        assertTrue(CollectionUtils.isNotEmpty(sprouts) && sprouts.size() == 1);
        assertTrue(ibdo.getAllCurrentForms().contains("TESTCOORDINATE"));
    }

    @Test
    void testPlaceReferenceOrder() {
        place.configG.addEntry("APPEND_SERVICE_COORDINATION", RefreshablePlace.class.getName());
        place.configG.addEntry("DENY_SERVICE_COORDINATION", CachePlace.class.getName());

        place.configG.addEntry("SERVICE_COORDINATION", CachePlace.class.getName());
        place.configG.addEntry("SERVICE_COORDINATION", ToLowerPlace.class.getName());
        place.configG.addEntry("SERVICE_COORDINATION", ToUpperPlace.class.getName());
        place.configurePlace();

        assertEquals(3, place.placeRefs.size());
        assertEquals("emissary.place.sample.ToLowerPlace", place.placeRefs.get(0).getPlaceName());
        assertEquals("emissary.place.sample.ToUpperPlace", place.placeRefs.get(1).getPlaceName());
        assertEquals("emissary.place.sample.RefreshablePlace", place.placeRefs.get(2).getPlaceName());

        place.configG.removeEntry("APPEND_SERVICE_COORDINATION", RefreshablePlace.class.getName());
        place.configG.removeEntry("SERVICE_COORDINATION", ToUpperPlace.class.getName());
        place.configG.removeEntry("SERVICE_COORDINATION", ToLowerPlace.class.getName());
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
}
