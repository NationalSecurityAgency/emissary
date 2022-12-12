package emissary.util;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.BaseDataObject;
import emissary.core.IBaseDataObject;
import emissary.place.ServiceProviderPlace;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlaceComparisonHelperTest extends UnitTest {

    private static final Class<NullPointerException> NPE = NullPointerException.class;

    private static final byte[] configurationBytes = new StringBuilder()
            .append("PLACE_NAME = \"TesterPlace\"")
            .append("SERVICE_NAME = \"TESTER\"")
            .append("SERVICE_TYPE = \"TRANSFORM\"")
            .append("SERVICE_DESCRIPTION = \"Place defined for testing\"")
            .append("SERVICE_COST = 50")
            .append("SERVICE_QUALITY = 50")
            .append("SERVICE_PROXY = \"UNKNOWN\"").toString().getBytes(StandardCharsets.UTF_8);

    @Test
    void testGetPlaceToCompareArguments() throws Exception {
        assertNull(PlaceComparisonHelper.getPlaceToCompare(null));

        final Configurator configurator1 = ConfigUtil.getConfigInfo(new ByteArrayInputStream(configurationBytes));
        assertNull(PlaceComparisonHelper.getPlaceToCompare(configurator1));
    }

    @Test
    void testCompareToPlaceArguments() throws IOException {
        final List<IBaseDataObject> newResults = new ArrayList<>();
        final IBaseDataObject ibdoForNewPlace = new BaseDataObject();
        final ServiceProviderPlace newPlace = new PlaceComparisonHelperTestPlace(new ByteArrayInputStream(configurationBytes));
        final String newMethodName = "newMethodName";
        final ServiceProviderPlace oldPlace = new PlaceComparisonHelperTestPlace(new ByteArrayInputStream(configurationBytes));
        final String oldMethodName = "oldMethodName";


        assertThrows(NPE, () -> PlaceComparisonHelper.compareToPlace(
                null, ibdoForNewPlace, newPlace, newMethodName, oldPlace, oldMethodName));
        assertThrows(NPE, () -> PlaceComparisonHelper.compareToPlace(
                newResults, null, newPlace, newMethodName, oldPlace, oldMethodName));
        assertThrows(NPE, () -> PlaceComparisonHelper.compareToPlace(
                newResults, ibdoForNewPlace, null, newMethodName, oldPlace, oldMethodName));
        assertThrows(NPE, () -> PlaceComparisonHelper.compareToPlace(
                newResults, ibdoForNewPlace, newPlace, null, oldPlace, oldMethodName));
        assertThrows(NPE, () -> PlaceComparisonHelper.compareToPlace(
                newResults, ibdoForNewPlace, newPlace, newMethodName, null, oldMethodName));
        assertThrows(NPE, () -> PlaceComparisonHelper.compareToPlace(
                newResults, ibdoForNewPlace, newPlace, newMethodName, oldPlace, null));
    }

    @Test
    void testCompareToPlace() throws Exception {
        final List<IBaseDataObject> newResults = new ArrayList<>();
        final IBaseDataObject ibdoForNewPlace = new BaseDataObject();
        final ServiceProviderPlace newPlace = new PlaceComparisonHelperTestPlace(new ByteArrayInputStream(configurationBytes));
        final String newMethodName = "processHeavyDuty";
        final ServiceProviderPlace oldPlace = new PlaceComparisonHelperTestPlace(new ByteArrayInputStream(configurationBytes));
        final String oldMethodName = "processHeavyDuty";

        assertNull(PlaceComparisonHelper.compareToPlace(
                newResults, ibdoForNewPlace, newPlace, newMethodName, oldPlace, oldMethodName));
    }

    @Test
    void testCheckDifferencesArguments() {
        final IBaseDataObject ibdoForOldPlace = new BaseDataObject();
        final IBaseDataObject ibdoForNewPlace = new BaseDataObject();
        final List<IBaseDataObject> oldResults = new ArrayList<>();
        final List<IBaseDataObject> newResults = new ArrayList<>();
        final String identifier = "identifier";

        assertThrows(NPE, () -> PlaceComparisonHelper.checkDifferences(
                null, ibdoForNewPlace, oldResults, newResults, identifier));
        assertThrows(NPE, () -> PlaceComparisonHelper.checkDifferences(
                ibdoForOldPlace, null, oldResults, newResults, identifier));
        assertThrows(NPE, () -> PlaceComparisonHelper.checkDifferences(
                ibdoForOldPlace, ibdoForNewPlace, null, newResults, identifier));
        assertThrows(NPE, () -> PlaceComparisonHelper.checkDifferences(
                ibdoForOldPlace, ibdoForNewPlace, oldResults, null, identifier));
        assertThrows(NPE, () -> PlaceComparisonHelper.checkDifferences(
                ibdoForOldPlace, ibdoForNewPlace, oldResults, newResults, null));
    }

    @Test
    void testCheckDifferences() {
        final IBaseDataObject ibdoForOldPlace = new BaseDataObject();
        final IBaseDataObject ibdoForNewPlace = new BaseDataObject();
        final IBaseDataObject ibdoForNewPlaceOneChange = new BaseDataObject();
        final IBaseDataObject ibdoForNewPlaceTwoChanges = new BaseDataObject();
        final List<IBaseDataObject> oldResults = new ArrayList<>();
        final List<IBaseDataObject> oldResultsTwoChanges = new ArrayList<>();
        final List<IBaseDataObject> newResults = new ArrayList<>();
        final List<IBaseDataObject> newResultsOneChange = new ArrayList<>();
        final List<IBaseDataObject> newResultsTwoChanges = new ArrayList<>();
        final String identifier = "identifier";

        ibdoForNewPlaceOneChange.setData(new byte[1]);
        newResultsOneChange.add(new BaseDataObject());

        ibdoForNewPlaceTwoChanges.setData(new byte[1]);
        ibdoForNewPlaceTwoChanges.addAlternateView("alternateView1", new byte[1]);
        newResultsTwoChanges.add(new BaseDataObject());
        newResultsTwoChanges.get(0).setData(new byte[2]);
        newResultsTwoChanges.get(0).addAlternateView("alternateView2", new byte[2]);
        oldResultsTwoChanges.add(new BaseDataObject());
        oldResultsTwoChanges.get(0).setData(new byte[3]);
        oldResultsTwoChanges.get(0).addAlternateView("alternateView3", new byte[3]);

        assertNull(PlaceComparisonHelper.checkDifferences(
                ibdoForOldPlace, ibdoForNewPlace, oldResults, newResults, identifier));
        assertNotNull(PlaceComparisonHelper.checkDifferences(
                ibdoForOldPlace, ibdoForNewPlaceOneChange, oldResults, newResults, identifier));
        assertNotNull(PlaceComparisonHelper.checkDifferences(
                ibdoForOldPlace, ibdoForNewPlace, oldResults, newResultsOneChange, identifier));
        assertNotNull(PlaceComparisonHelper.checkDifferences(
                ibdoForOldPlace, ibdoForNewPlaceOneChange, oldResults, newResultsOneChange, identifier));
        assertNotNull(PlaceComparisonHelper.checkDifferences(
                ibdoForOldPlace, ibdoForNewPlaceTwoChanges, oldResultsTwoChanges, newResultsTwoChanges, identifier));
    }
}
