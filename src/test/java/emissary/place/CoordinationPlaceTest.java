package emissary.place;

import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.core.MobileAgent;
import emissary.core.Namespace;
import emissary.core.ResourceWatcher;
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
