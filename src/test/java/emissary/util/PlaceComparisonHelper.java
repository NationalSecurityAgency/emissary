package emissary.util;

import emissary.config.Configurator;
import emissary.core.DiffCheckConfiguration;
import emissary.core.IBaseDataObject;
import emissary.core.IBaseDataObjectDiffHelper;
import emissary.core.IBaseDataObjectHelper;
import emissary.place.ServiceProviderPlace;

import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class to use during development of a major refactoring or replacement of a Place.
 */
public class PlaceComparisonHelper {

    private PlaceComparisonHelper() {}

    // Config key to use in Place configuration to define another place to compare
    public static final String CFG_PLACE_TO_COMPARE = "PLACE_TO_COMPARE";

    /**
     * Given a config which contains a 'PLACE_TO_COMPARE' directive, instantiate the referenced place with its usual
     * configuration
     * 
     * @param configG from an existing place which is scanned for PLACE_TO_COMPARE, which should reference a place that the
     *        developer wants to compare against
     * @return the instantiated place if configured
     * @throws ReflectiveOperationException if there is a problem.
     */
    @Nullable
    public static ServiceProviderPlace getPlaceToCompare(final Configurator configG) throws ReflectiveOperationException {
        if (configG == null) {
            return null;
        }

        // Find the config line, which could be null if not defined
        final String compareToPlaceCfg = StringUtils.trimToNull(configG.findStringEntry(CFG_PLACE_TO_COMPARE));
        if (StringUtils.isNotBlank(compareToPlaceCfg)) {
            final Class<? extends ServiceProviderPlace> clazz = Class
                    .forName(compareToPlaceCfg).asSubclass(ServiceProviderPlace.class);
            return clazz.getDeclaredConstructor(String.class)
                    .newInstance(compareToPlaceCfg + ".cfg");
        }

        return null;
    }

    /**
     * Used to compare a 'new' place with another, usually during development aimed at replacing the 'old' place.
     * 
     * @param actualChildren an empty list to add results into from running the actual (new) place
     * @param actualParentIbdo the actual BDO being passed in for comparison
     * @param actualPlace the new place we're comparing 'from'
     * @param actualMethodName to use when comparing (e.g. processHeavyDuty)
     * @param expectedPlace to compare against (the old place)
     * @param expectedMethodName to use when comparing (e.g. processHeavyDuty)
     * @param options {@link DiffCheckConfiguration} to configure diffing options
     * @return a readable string of differences to log or use elsewhere.
     * @throws ReflectiveOperationException if there is a problem.
     */
    @SuppressWarnings("unchecked")
    public static String compareToPlace(final List<IBaseDataObject> actualChildren, final IBaseDataObject actualParentIbdo,
            final ServiceProviderPlace actualPlace, final String actualMethodName, final ServiceProviderPlace expectedPlace,
            final String expectedMethodName, final DiffCheckConfiguration options) throws ReflectiveOperationException {

        Validate.notNull(actualChildren, "Required: actualChildren not null");
        Validate.notNull(actualParentIbdo, "Required: actualParentIbdo not null");
        Validate.notNull(actualPlace, "Required: actualPlace not null");
        Validate.notNull(actualMethodName, "actualMethodName: actualChildren not null");
        Validate.notNull(expectedPlace, "Required: expectedPlace not null");
        Validate.notNull(expectedMethodName, "Required: expectedMethodName not null");
        Validate.notNull(options, "Required: options not null");

        // Generate an identifier from the simple class names, e.g. Comparison[ColorPlace==ColourPlace]
        final String oldPlaceName = expectedPlace.getClass().getSimpleName();
        final String newPlaceName = actualPlace.getClass().getSimpleName();
        final String identifier = String.format("Comparison[%s==%s]", oldPlaceName, newPlaceName);

        // Get the method to run (e.g. processHeavyDuty)
        final Method oldProcess = expectedPlace.getClass().getDeclaredMethod(expectedMethodName, IBaseDataObject.class);
        final Method newProcess = actualPlace.getClass().getDeclaredMethod(actualMethodName, IBaseDataObject.class);

        // Clone the data before running old or new methods
        final IBaseDataObject ibdoForOldPlace = IBaseDataObjectHelper.clone(actualParentIbdo);

        // Actually run the places to get results
        final List<IBaseDataObject> oldResults = (List<IBaseDataObject>) oldProcess.invoke(expectedPlace, ibdoForOldPlace);
        actualChildren.addAll((List<IBaseDataObject>) newProcess.invoke(actualPlace, actualParentIbdo));

        // Now generate the 'diff' for the results
        return checkDifferences(ibdoForOldPlace, actualParentIbdo, oldResults, actualChildren, identifier, options);
    }

    /**
     * Given two BDOs and results from two processing place runs, compare them and log any differences.
     * 
     * @param expectedIbdo likely a cloned object of the 'new' place object
     * @param actualIbdo the 'main' BDO that was originally passed in from upstream
     * @param expectedChildren from the 'old' run with the 'expectedIbdo' object
     * @param actualChildren from the 'new' run with the 'actualIbdo' object
     * @param identifier to highlight any differences in logs
     * @param options {@link DiffCheckConfiguration} to configure diffing options
     * @return the string of differences, or null if there aren't any
     */
    @Nullable
    public static String checkDifferences(final IBaseDataObject expectedIbdo, final IBaseDataObject actualIbdo,
            final List<IBaseDataObject> expectedChildren, final List<IBaseDataObject> actualChildren, final String identifier,
            final DiffCheckConfiguration options) {
        Validate.notNull(expectedIbdo, "Required: expectedIbdo not null");
        Validate.notNull(actualIbdo, "Required: actualIbdo not null");
        Validate.notNull(expectedChildren, "Required: expectedChildren not null");
        Validate.notNull(actualChildren, "Required: actualChildren not null");
        Validate.notNull(identifier, "Required: identifier not null");
        Validate.notNull(options, "Required: options not null");

        final List<String> parentDifferences = new ArrayList<>();
        final List<String> childDifferences = new ArrayList<>();

        IBaseDataObjectDiffHelper.diff(expectedIbdo, actualIbdo, parentDifferences, options);
        IBaseDataObjectDiffHelper.diff(expectedChildren, actualChildren, identifier, childDifferences, options);

        if (!parentDifferences.isEmpty() || !childDifferences.isEmpty()) {
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parentDifferences.size(); i++) {
                if (i != 0) {
                    sb.append(StringUtils.LF);
                }
                sb.append(identifier).append(": Parent Diff: ");
                sb.append(parentDifferences.get(i));
            }
            if (!parentDifferences.isEmpty() && !childDifferences.isEmpty()) {
                sb.append(StringUtils.LF);
            }
            for (int i = 0; i < childDifferences.size(); i++) {
                if (i != 0) {
                    sb.append(StringUtils.LF);
                }
                sb.append(identifier).append(": Child Diff: ");
                sb.append(childDifferences.get(i));
            }

            return sb.toString();
        }

        return null;
    }
}
