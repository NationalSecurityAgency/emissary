package emissary.util;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.BaseDataObject;
import emissary.core.DiffCheckConfiguration;
import emissary.core.IBaseDataObject;
import emissary.place.ServiceProviderPlace;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlaceComparisonHelperTest extends UnitTest {

    private static final DiffCheckConfiguration DIFF_OPTIONS = DiffCheckConfiguration.onlyCheckData();

    private static final byte[] configurationBytes = new StringBuilder()
            .append("PLACE_NAME = \"TesterPlace\"")
            .append("SERVICE_NAME = \"TESTER\"")
            .append("SERVICE_TYPE = \"TRANSFORM\"")
            .append("SERVICE_DESCRIPTION = \"Place defined for testing\"")
            .append("SERVICE_COST = 50")
            .append("SERVICE_QUALITY = 50")
            .append("SERVICE_PROXY = \"UNKNOWN\"").toString().getBytes(StandardCharsets.UTF_8);

    private IBaseDataObject ibdoNewPlace;
    private IBaseDataObject ibdoOldPlace;
    private List<IBaseDataObject> resultsNewPlace;
    private List<IBaseDataObject> resultsOldPlace;
    private static final String identifier = "identifier";

    @BeforeEach
    void setup() {
        ibdoNewPlace = new BaseDataObject();
        ibdoOldPlace = new BaseDataObject();
        resultsNewPlace = new ArrayList<>();
        resultsOldPlace = new ArrayList<>();
    }

    @Test
    void testGetPlaceToCompareArguments() throws Exception {
        assertNull(PlaceComparisonHelper.getPlaceToCompare(null));

        final Configurator configurator1 = ConfigUtil.getConfigInfo(new ByteArrayInputStream(configurationBytes));
        assertNull(PlaceComparisonHelper.getPlaceToCompare(configurator1));
    }

    private static void checkThrowsNull(final Executable e) {
        assertThrows(NullPointerException.class, e);
    }

    @Test
    void testCompareToPlaceArguments() throws IOException {
        final ServiceProviderPlace newPlace = new TestMinimalServiceProviderPlace(new ByteArrayInputStream(configurationBytes));
        final String newMethodName = "newMethodName";
        final ServiceProviderPlace oldPlace = new TestMinimalServiceProviderPlace(new ByteArrayInputStream(configurationBytes));
        final String oldMethodName = "oldMethodName";
        checkThrowsNull(() -> PlaceComparisonHelper.compareToPlace(null, ibdoNewPlace,
                newPlace, newMethodName, oldPlace, oldMethodName, DIFF_OPTIONS));
        checkThrowsNull(() -> PlaceComparisonHelper.compareToPlace(resultsNewPlace, null,
                newPlace, newMethodName, oldPlace, oldMethodName, DIFF_OPTIONS));
        checkThrowsNull(() -> PlaceComparisonHelper.compareToPlace(resultsNewPlace, ibdoNewPlace,
                null, newMethodName, oldPlace, oldMethodName, DIFF_OPTIONS));
        checkThrowsNull(() -> PlaceComparisonHelper.compareToPlace(resultsNewPlace, ibdoNewPlace,
                newPlace, null, oldPlace, oldMethodName, DIFF_OPTIONS));
        checkThrowsNull(() -> PlaceComparisonHelper.compareToPlace(resultsNewPlace, ibdoNewPlace,
                newPlace, newMethodName, null, oldMethodName, DIFF_OPTIONS));
        checkThrowsNull(() -> PlaceComparisonHelper.compareToPlace(resultsNewPlace, ibdoNewPlace,
                newPlace, newMethodName, oldPlace, null, DIFF_OPTIONS));
        checkThrowsNull(() -> PlaceComparisonHelper.compareToPlace(resultsNewPlace, ibdoNewPlace,
                newPlace, newMethodName, oldPlace, oldMethodName, null));
    }

    @Test
    void testCompareToPlace() throws Exception {
        final ServiceProviderPlace newPlace = new TestMinimalServiceProviderPlace(new ByteArrayInputStream(configurationBytes));
        final String newMethodName = "processHeavyDuty";
        final ServiceProviderPlace oldPlace = new TestMinimalServiceProviderPlace(new ByteArrayInputStream(configurationBytes));
        final String oldMethodName = "processHeavyDuty";

        assertNull(PlaceComparisonHelper.compareToPlace(
                resultsNewPlace, ibdoNewPlace, newPlace, newMethodName, oldPlace, oldMethodName, DIFF_OPTIONS));
    }

    @Test
    void testCheckDifferencesArguments() {
        checkThrowsNull(() -> PlaceComparisonHelper.checkDifferences(
                null, ibdoNewPlace, resultsOldPlace, resultsNewPlace, identifier, DIFF_OPTIONS));
        checkThrowsNull(() -> PlaceComparisonHelper.checkDifferences(
                ibdoOldPlace, null, resultsOldPlace, resultsNewPlace, identifier, DIFF_OPTIONS));
        checkThrowsNull(() -> PlaceComparisonHelper.checkDifferences(
                ibdoOldPlace, ibdoNewPlace, null, resultsNewPlace, identifier, DIFF_OPTIONS));
        checkThrowsNull(() -> PlaceComparisonHelper.checkDifferences(
                ibdoOldPlace, ibdoNewPlace, resultsOldPlace, null, identifier, DIFF_OPTIONS));
        checkThrowsNull(() -> PlaceComparisonHelper.checkDifferences(
                ibdoOldPlace, ibdoNewPlace, resultsOldPlace, resultsNewPlace, null, DIFF_OPTIONS));
        checkThrowsNull(() -> PlaceComparisonHelper.checkDifferences(
                ibdoOldPlace, ibdoNewPlace, resultsOldPlace, resultsNewPlace, identifier, null));
    }

    @Test
    void testCheckDifferences() {
        final IBaseDataObject ibdoForNewPlaceOneChange = new BaseDataObject();
        final IBaseDataObject ibdoForNewPlaceTwoChanges = new BaseDataObject();
        final List<IBaseDataObject> oldResultsTwoChanges = new ArrayList<>();
        final List<IBaseDataObject> newResultsOneChange = new ArrayList<>();
        final List<IBaseDataObject> newResultsTwoChanges = new ArrayList<>();

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
                ibdoOldPlace, ibdoNewPlace, resultsOldPlace, resultsNewPlace, identifier, DIFF_OPTIONS));
        assertNotNull(PlaceComparisonHelper.checkDifferences(
                ibdoOldPlace, ibdoForNewPlaceOneChange, resultsOldPlace, resultsNewPlace, identifier, DIFF_OPTIONS));
        assertNotNull(PlaceComparisonHelper.checkDifferences(
                ibdoOldPlace, ibdoNewPlace, resultsOldPlace, newResultsOneChange, identifier, DIFF_OPTIONS));
        assertNotNull(PlaceComparisonHelper.checkDifferences(
                ibdoOldPlace, ibdoForNewPlaceOneChange, resultsOldPlace, newResultsOneChange, identifier, DIFF_OPTIONS));
        assertNotNull(PlaceComparisonHelper.checkDifferences(
                ibdoOldPlace, ibdoForNewPlaceTwoChanges, oldResultsTwoChanges, newResultsTwoChanges, identifier, DIFF_OPTIONS));
    }
}
