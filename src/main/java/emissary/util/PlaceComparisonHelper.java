package emissary.util;

import emissary.config.Configurator;
import emissary.core.IBaseDataObject;
import emissary.core.IBaseDataObjectHelper;
import emissary.place.ServiceProviderPlace;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class to use during development of a major refactoring or replacement of a Place.
 */
public class PlaceComparisonHelper {

    private PlaceComparisonHelper() {}

    private static final String FMT_COMPARISON_IDENTIFIER = "Comparison[%s==%s]";
    // Config key to use in Place configuration to define another place to compare
    public static final String CFG_PLACE_TO_COMPARE = "PLACE_TO_COMPARE";

    /**
     * Given a config which contains a 'PLACE_TO_COMPARE' directive, instantiate the referenced place with its usual
     * configuration
     * 
     * @param configG from an existing place which is scanned for PLACE_TO_COMPARE, which should reference a place that the
     *        developer wants to compare against
     * @return the instantiated place if configured
     * @throws ClassNotFoundException if we can't find the other place
     * @throws SecurityException if we can't access the String constructor for the other place
     * @throws NoSuchMethodException if there isn't an accessible constructor for the other place
     * @throws InvocationTargetException if the underlying constructor throws an exception
     * @throws IllegalAccessException if the Constructor object is enforcing Java language access control and the underlying
     *         constructor is inaccessible.
     * @throws InstantiationException if the class that declares the underlying constructor represents an abstract class.
     */
    public static ServiceProviderPlace getPlaceToCompare(final Configurator configG) throws ClassNotFoundException,
            InstantiationException, IllegalAccessException, InvocationTargetException,
            NoSuchMethodException, SecurityException {
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
     * @param newResults an empty list to add results into from running the newPlace
     * @param ibdoForNewPlace the actual BDO being passed in for comparison
     * @param newPlace the new place we're comparing 'from'
     * @param newMethodName to use when comparing (e.g. processHeavyDuty)
     * @param oldPlace to compare against
     * @param oldMethodName to use when comparing (e.g. processHeavyDuty)
     * @return a readable string of differences to log or use elsewhere.
     * @throws SecurityException if we can't access the String constructor for the other place
     * @throws NoSuchMethodException if there isn't an accessible constructor for the other place
     * @throws InvocationTargetException if the underlying constructor throws an exception
     * @throws IllegalArgumentException if the number of actual and formal parameters differ; if an unwrapping conversion
     *         for primitive arguments fails; or if, after possible unwrapping, a parameter value cannot be converted to the
     *         corresponding formal parameter type by a method invocation conversion; if this constructor pertains to an
     *         enum class
     * @throws IllegalAccessException if the Constructor object is enforcing Java language access control and the underlying
     *         constructor is inaccessible.
     */
    @SuppressWarnings("unchecked")
    public static String compareToPlace(final List<IBaseDataObject> newResults, final IBaseDataObject ibdoForNewPlace,
            final ServiceProviderPlace newPlace,
            final String newMethodName, final ServiceProviderPlace oldPlace, final String oldMethodName) throws NoSuchMethodException,
            SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {

        Validate.notNull(newResults, "Required: newResults not null");
        Validate.notNull(ibdoForNewPlace, "Required: ibdoForNewPlace not null");
        Validate.notNull(newPlace, "Required: newPlace not null");
        Validate.notNull(newMethodName, "newMethodName: newResults not null");
        Validate.notNull(oldPlace, "Required: oldPlace not null");
        Validate.notNull(oldMethodName, "Required: oldMethodName not null");

        // Generate an identifier from the simple class names, e.g. Comparison[ColorPlace==ColourPlace]
        final String oldPlaceName = oldPlace.getClass().getSimpleName();
        final String newPlaceName = newPlace.getClass().getSimpleName();
        final String identifier = String.format(FMT_COMPARISON_IDENTIFIER, oldPlaceName, newPlaceName);

        // Get the method to run (e.g. processHeavyDuty)
        final Method oldProcess = oldPlace.getClass().getDeclaredMethod(oldMethodName, IBaseDataObject.class);
        final Method newProcess = newPlace.getClass().getDeclaredMethod(newMethodName, IBaseDataObject.class);

        // Clone the data before running old or new methods
        final IBaseDataObject ibdoForOldPlace = IBaseDataObjectHelper.clone(ibdoForNewPlace, true);

        // Actually run the places to get results
        final List<IBaseDataObject> oldResults = (List<IBaseDataObject>) oldProcess.invoke(oldPlace, ibdoForOldPlace);
        newResults.addAll((List<IBaseDataObject>) newProcess.invoke(newPlace, ibdoForNewPlace));

        // Now generate the 'diff' for the results
        return checkDifferences(ibdoForOldPlace, ibdoForNewPlace, oldResults, newResults, identifier);
    }

    /**
     * Given two BDOs and results from two processing place runs, compare them and log any differences.
     * 
     * @param ibdoForOldPlace likely a cloned object of the 'new' place object
     * @param ibdoForNewPlace the 'main' BDO that was originally passed in from upstream
     * @param oldResults from the 'old' run with the 'ibdoForOldPlace' object
     * @param newResults from the 'new' run with the 'ibdoForNewPlace' object
     * @param identifier to highlight any differences in logs
     * @return the string of differences, or null if there aren't any
     */
    public static String checkDifferences(final IBaseDataObject ibdoForOldPlace, final IBaseDataObject ibdoForNewPlace,
            final List<IBaseDataObject> oldResults, final List<IBaseDataObject> newResults, final String identifier) {
        Validate.notNull(ibdoForOldPlace, "Required: ibdoForOldPlace not null");
        Validate.notNull(ibdoForNewPlace, "Required: ibdoForNewPlace not null");
        Validate.notNull(oldResults, "Required: oldResults not null");
        Validate.notNull(newResults, "Required: newResults not null");
        Validate.notNull(identifier, "Required: identifier not null");

        // Options for diff
        final boolean checkData = true;
        final boolean checkTimestamp = false;
        final boolean checkInternalId = false;
        final boolean checkTransformHistory = false;
        final List<String> parentDifferences = new ArrayList<>();
        final List<String> childDifferences = new ArrayList<>();

        // Runnables aren't compared for the diff
        final List<Runnable> oldRunnables = DisposeHelper.get(ibdoForOldPlace);
        final List<Runnable> newRunnables = DisposeHelper.get(ibdoForNewPlace);

        try {
            ibdoForOldPlace.deleteParameter(DisposeHelper.KEY);
            ibdoForNewPlace.deleteParameter(DisposeHelper.KEY);
            IBaseDataObjectHelper.diff(ibdoForNewPlace, ibdoForOldPlace, parentDifferences,
                    checkData, checkTimestamp, checkInternalId, checkTransformHistory);
            IBaseDataObjectHelper.diff(newResults, oldResults, identifier, childDifferences,
                    checkData, checkTimestamp, checkInternalId, checkTransformHistory);
        } finally {
            // Make sure we put the runnables back on if an error occurs during the diff
            ibdoForOldPlace.setParameter(DisposeHelper.KEY, oldRunnables);
            ibdoForNewPlace.setParameter(DisposeHelper.KEY, newRunnables);
        }

        if (!parentDifferences.isEmpty() || !childDifferences.isEmpty()) {
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parentDifferences.size(); i++) {
                if (i != 0) {
                    sb.append(StringUtils.LF);
                }
                sb.append(identifier).append(": PDiff: ");
                sb.append(parentDifferences.get(i));
            }
            if (!parentDifferences.isEmpty() && !childDifferences.isEmpty()) {
                sb.append(StringUtils.LF);
            }
            for (int i = 0; i < childDifferences.size(); i++) {
                if (i != 0) {
                    sb.append(StringUtils.LF);
                }
                sb.append(identifier).append(": CDiff: ");
                sb.append(childDifferences.get(i));
            }

            return sb.toString();
        }

        return null;
    }
}
