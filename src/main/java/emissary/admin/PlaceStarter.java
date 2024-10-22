package emissary.admin;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.EmissaryException;
import emissary.core.EmissaryRuntimeException;
import emissary.core.Factory;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.directory.DirectoryEntry;
import emissary.directory.DirectoryPlace;
import emissary.directory.EmissaryNode;
import emissary.directory.IDirectoryPlace;
import emissary.directory.KeyManipulator;
import emissary.place.IServiceProviderPlace;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Static methods to start places in the system.
 */
@SuppressWarnings("SystemExitOutsideMain")
public class PlaceStarter {
    private static final Logger logger = LoggerFactory.getLogger(PlaceStarter.class);

    @Nullable
    private static Configurator classConf = null;

    protected static final String defaultClassName = "emissary.place.sample.DevNullPlace";

    static {
        try {
            classConf = ConfigUtil.getClassNameInventory();
        } catch (IOException | EmissaryException iox) {
            logger.error("Missing ClassNameInventory.cfg: all places will become " + defaultClassName
                    + " which is probably not what you want. Config is now " + System.getProperty(ConfigUtil.CONFIG_DIR_PROPERTY), iox);
            System.exit(1);
        }
    }

    /**
     * Create a place using File based config
     *
     * @param theLocation key for the new place
     * @param theClassStr string name of the class to instantiate
     * @param directory the string directory name to register in
     * @return the place that was found or created, or null if it can't be done
     */
    public static IServiceProviderPlace createPlace(final String theLocation, final String theClassStr, final String directory) {
        // generate constructor args
        final String theConfigFile = theClassStr + ConfigUtil.CONFIG_FILE_ENDING;

        final Object[] constructorArgs = {theConfigFile, directory, theLocation};

        return createPlace(theLocation, constructorArgs, theClassStr);
    }

    /**
     * Create a place using Stream based config
     *
     * @param theLocation key for the new place
     * @param theConfigStream stream configuration for the place
     * @param theClassStr string name of the class to instantiate
     * @param directory the string directory name to register in
     * @return the place that was found or created, or null if it can't be done
     */
    public static IServiceProviderPlace createPlace(final String theLocation, final InputStream theConfigStream, final String theClassStr,
            final String directory) {
        // generate constructor args
        final Object[] constructorArgs = {theConfigStream, directory, theLocation};
        return createPlace(theLocation, constructorArgs, theClassStr);
    }

    /**
     * Create a place using Stream based config
     *
     * @param theLocation key for the new place
     * @param theConfigStream stream configuration for the place
     * @param theClassStr string name of the class to instantiate
     * @param directory the string directory name to register in
     * @param node the emissary node
     * @return the place that was found or created, or null if it can't be done
     */
    public static IServiceProviderPlace createPlace(final String theLocation, final InputStream theConfigStream, final String theClassStr,
            final String directory, final EmissaryNode node) {
        // generate constructor args
        final Object[] constructorArgs = {theConfigStream, directory, theLocation, node};
        return createPlace(theLocation, constructorArgs, theClassStr);
    }


    /**
     * Create a place using generic Object[] constructor args for maximum flexibility for finding any existing constructor.
     * Will check to see if the place already exists first and return the existing instance from the Namespace if it does.
     *
     * @param theLocation key for the new place
     * @param constructorArgs array of args to pass to the place constructor
     * @param theClassStr string name of the class to instantiate
     * @return the place that was found or created, or null if it can't be done
     * @deprecated use {@link #createPlace(String, List, String)}
     */
    @Nullable
    @Deprecated
    @SuppressWarnings("AvoidObjectArrays")
    public static IServiceProviderPlace createPlace(final String theLocation, final Object[] constructorArgs, @Nullable final String theClassStr) {
        return createPlace(theLocation, Arrays.asList(constructorArgs), theClassStr);
    }

    /**
     * Create a place using generic List constructor args for maximum flexibility for finding any existing constructor. Will
     * check to see if the place already exists first and return the existing instance from the Namespace if it does.
     *
     * @param theLocation key for the new place
     * @param constructorArgs list of args to pass to the place constructor
     * @param theClassStr string name of the class to instantiate
     * @return the place that was found or created, or null if it can't be done
     */
    @Nullable
    public static IServiceProviderPlace createPlace(final String theLocation, final List<Object> constructorArgs,
            @Nullable final String theClassStr) {
        logger.debug("Ready to createPlace {} as {}", theLocation, theClassStr);

        final long t1 = System.currentTimeMillis();

        // check the input arguments
        // TODO we should add a check to validate theLocation
        final IServiceProviderPlace place = alreadyExists(theLocation);
        if (place != null) {
            // a place already exists at this location, can't create another!
            logger.warn("{} already exists!", theLocation);
            return place;
        }

        // error, must have the class string known...
        if (theClassStr == null) {
            logger.warn("classStr check failed for {}", theLocation);
            return null;
        }

        final String bindKey = KeyManipulator.removeExpense(theLocation);
        final IServiceProviderPlace thePlace;
        try {
            thePlace = (IServiceProviderPlace) Factory.create(theClassStr, constructorArgs, bindKey);
        } catch (Throwable te) {
            // error creating place
            logger.error("cannot create {}", theLocation, te);
            shutdownFailedPlace(bindKey, null);
            return null; // couldn't start the place.
        }

        final long t2 = System.currentTimeMillis();

        if (logger.isDebugEnabled()) {
            logger.debug("Started {} in {}s", theLocation, (t2 - t1) / 1000.0);
        }
        return thePlace;
    }

    public static void shutdownFailedPlace(final String loc, @Nullable final IServiceProviderPlace place) {
        try {
            logger.warn("shutting down the failed place: {}", loc);
            if (place != null) {
                place.shutDown();
            } else {
                // Force keys to be deregistered if we can
                deregisterPlace(loc);
            }
            Namespace.unbind(loc);
        } catch (Throwable tt) {
            logger.error("whoa there pardner... {}", loc, tt);
        }
    }

    public static void deregisterPlace(final String loc) {
        try {
            final IDirectoryPlace localDir = DirectoryPlace.lookup();
            final List<DirectoryEntry> entries = localDir.getMatchingEntries("*." + loc);
            if (entries != null && !entries.isEmpty()) {
                final List<String> keys = new ArrayList<>();
                for (final DirectoryEntry entry : entries) {
                    keys.add(entry.getKey());
                }
                logger.info("Forcing removal of {} keys due to failed {}", keys.size(), loc);
                localDir.removePlaces(keys);
            } else {
                logger.debug("Failed {} did not have any directory keys registered", loc);
            }
        } catch (EmissaryException ee) {
            logger.debug("NO local directory, cannot force key dereg for {}", loc);
        }
    }

    // ////////////////////////////////////////////////////////////
    /**
     * method to check if the place already exists.
     */
    // ////////////////////////////////////////////////////////////
    @Nullable
    public static IServiceProviderPlace alreadyExists(final String theLocation) {
        final String thePlaceHost = Startup.placeHost(theLocation);
        // TODO should we add a check for index of? Can cause an exception if // isn't present
        final String luStr = theLocation.substring(theLocation.indexOf("//"));
        try {
            final IServiceProviderPlace thePlace = (IServiceProviderPlace) Namespace.lookup(luStr);
            logger.debug("{} already running on {}", theLocation, thePlaceHost);
            return thePlace;
        } catch (NamespaceException nse) {
            // expected when the place doesn't exist
        } catch (Throwable t) {
            // empty catch block
        }
        return null;
    }

    public static String getClassString(final String theLocation) {
        return getClassString(theLocation, false);
    }

    public static String getClassString(final String theLocation, boolean isStrictMode) {
        final String thePlaceName = Startup.placeName(theLocation);
        if (StringUtils.isBlank(thePlaceName)) {
            logger.error("Illegal location specified {}, has no place name", theLocation);
        }
        final List<String> classStringList = classConf.findEntries(thePlaceName);
        if (classStringList.isEmpty()) {
            logger.error("Need a CLASS config entry for {} check entry in emissary.admin.ClassNameInventory.cfg, using default "
                    + "{} which is probably not what you want.", thePlaceName, defaultClassName);
            return defaultClassName;
        } else if (classStringList.size() > 1) {
            if (isStrictMode) {
                throw new EmissaryRuntimeException("Multiple entries for " + thePlaceName + ", found " + classStringList);
            }
            logger.warn("Multiple entries for {}, found {}", thePlaceName, classStringList);
        }
        return classStringList.get(0);
    }

    /** This class is not meant to be instantiated. */
    private PlaceStarter() {}
}
