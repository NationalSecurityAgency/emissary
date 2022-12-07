package emissary.util;

import emissary.core.BaseDataObject;
import emissary.core.IBaseDataObject;
import emissary.place.ServiceProviderPlace;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

class PlaceComparisonHelperTest {
    @Test
    void testCompareToPlaceArguments() throws IOException {
        final byte[] configurationBytes = new StringBuilder()
                .append("PLACE_NAME = \"TesterPlace\"")
                .append("SERVICE_NAME = \"TESTER\"")
                .append("SERVICE_TYPE = \"TRANSFORM\"")
                .append("SERVICE_DESCRIPTION = \"Place defined for testing\"")
                .append("SERVICE_COST = 50")
                .append("SERVICE_QUALITY = 50")
                .append("SERVICE_PROXY = \"UNKNOWN\"").toString().getBytes(StandardCharsets.UTF_8);
        final List<IBaseDataObject> newResults = new ArrayList<>();
        final IBaseDataObject ibdoForNewPlace = new BaseDataObject();
        final ServiceProviderPlace newPlace = new PlaceComparisonHelperTestPlace(new ByteArrayInputStream(configurationBytes));
        final String newMethodName = "newMethodName";
        final ServiceProviderPlace oldPlace = new PlaceComparisonHelperTestPlace(new ByteArrayInputStream(configurationBytes));
        final String oldMethodName = "oldMethodName";

        Assertions.assertThrows(NullPointerException.class, () -> PlaceComparisonHelper.compareToPlace(
                null, ibdoForNewPlace, newPlace, newMethodName, oldPlace, oldMethodName));
        Assertions.assertThrows(NullPointerException.class, () -> PlaceComparisonHelper.compareToPlace(
                newResults, null, newPlace, newMethodName, oldPlace, oldMethodName));
        Assertions.assertThrows(NullPointerException.class, () -> PlaceComparisonHelper.compareToPlace(
                newResults, ibdoForNewPlace, null, newMethodName, oldPlace, oldMethodName));
        Assertions.assertThrows(NullPointerException.class, () -> PlaceComparisonHelper.compareToPlace(
                newResults, ibdoForNewPlace, newPlace, null, oldPlace, oldMethodName));
        Assertions.assertThrows(NullPointerException.class, () -> PlaceComparisonHelper.compareToPlace(
                newResults, ibdoForNewPlace, newPlace, newMethodName, null, oldMethodName));
        Assertions.assertThrows(NullPointerException.class, () -> PlaceComparisonHelper.compareToPlace(
                newResults, ibdoForNewPlace, newPlace, newMethodName, oldPlace, null));
    }

    @Test
    void testCompareToPlace() throws Exception {
        final byte[] configurationBytes = new StringBuilder()
                .append("PLACE_NAME = \"TesterPlace\"")
                .append("SERVICE_NAME = \"TESTER\"")
                .append("SERVICE_TYPE = \"TRANSFORM\"")
                .append("SERVICE_DESCRIPTION = \"Place defined for testing\"")
                .append("SERVICE_COST = 50")
                .append("SERVICE_QUALITY = 50")
                .append("SERVICE_PROXY = \"UNKNOWN\"").toString().getBytes(StandardCharsets.UTF_8);
        final List<IBaseDataObject> newResults = new ArrayList<>();
        final IBaseDataObject ibdoForNewPlace = new BaseDataObject();
        final ServiceProviderPlace newPlace = new PlaceComparisonHelperTestPlace(new ByteArrayInputStream(configurationBytes));
        final String newMethodName = "processHeavyDuty";
        final ServiceProviderPlace oldPlace = new PlaceComparisonHelperTestPlace(new ByteArrayInputStream(configurationBytes));
        final String oldMethodName = "processHeavyDuty";

        Assertions.assertNull(PlaceComparisonHelper.compareToPlace(
                newResults, ibdoForNewPlace, newPlace, newMethodName, oldPlace, oldMethodName));
    }

    @Test
    void testCheckDifferencesArguments() {
        final IBaseDataObject ibdoForOldPlace = new BaseDataObject();
        final IBaseDataObject ibdoForNewPlace = new BaseDataObject();
        final List<IBaseDataObject> oldResults = new ArrayList<>();
        final List<IBaseDataObject> newResults = new ArrayList<>();
        final String identifier = "identifier";

        Assertions.assertThrows(NullPointerException.class, () -> PlaceComparisonHelper.checkDifferences(
                null, ibdoForNewPlace, oldResults, newResults, identifier));
        Assertions.assertThrows(NullPointerException.class, () -> PlaceComparisonHelper.checkDifferences(
                ibdoForOldPlace, null, oldResults, newResults, identifier));
        Assertions.assertThrows(NullPointerException.class, () -> PlaceComparisonHelper.checkDifferences(
                ibdoForOldPlace, ibdoForNewPlace, null, newResults, identifier));
        Assertions.assertThrows(NullPointerException.class, () -> PlaceComparisonHelper.checkDifferences(
                ibdoForOldPlace, ibdoForNewPlace, oldResults, null, identifier));
        Assertions.assertThrows(NullPointerException.class, () -> PlaceComparisonHelper.checkDifferences(
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

        Assertions.assertNull(PlaceComparisonHelper.checkDifferences(
                ibdoForOldPlace, ibdoForNewPlace, oldResults, newResults, identifier));
        Assertions.assertNotNull(PlaceComparisonHelper.checkDifferences(
                ibdoForOldPlace, ibdoForNewPlaceOneChange, oldResults, newResults, identifier));
        Assertions.assertNotNull(PlaceComparisonHelper.checkDifferences(
                ibdoForOldPlace, ibdoForNewPlace, oldResults, newResultsOneChange, identifier));
        Assertions.assertNotNull(PlaceComparisonHelper.checkDifferences(
                ibdoForOldPlace, ibdoForNewPlaceOneChange, oldResults, newResultsOneChange, identifier));
        Assertions.assertNotNull(PlaceComparisonHelper.checkDifferences(
                ibdoForOldPlace, ibdoForNewPlaceTwoChanges, oldResultsTwoChanges, newResultsTwoChanges, identifier));
    }
}
