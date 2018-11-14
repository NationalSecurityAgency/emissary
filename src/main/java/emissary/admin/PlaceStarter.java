package emissary.admin;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.EmissaryException;
import emissary.core.Factory;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.directory.DirectoryEntry;
import emissary.directory.DirectoryPlace;
import emissary.directory.IDirectoryPlace;
import emissary.directory.KeyManipulator;
import emissary.place.IServiceProviderPlace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Static methods to start places in the system.
 */
public class PlaceStarter {
    private static final Logger logger = LoggerFactory.getLogger(PlaceStarter.class);

    private static Configurator classConf = null;

    protected static final String defaultClassName = "emissary.place.sample.DevNullPlace";

    static {
        try {
            classConf = ConfigUtil.getMasterClassNames();
        } catch (IOException | EmissaryException iox) {
            logger.error("Missing MasterClassNames.cfg: all places will become " + defaultClassName
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
     * Create a place using generic Object[] constructor args for maximum flexibility for finding any existing constructor.
     * Will check to see if the place already exists first and return the existing instance from the Namespace if it does.
     *
     * @param theLocation key for the new place
     * @param constructorArgs array of args to pass to the place constructor
     * @param theClassStr string name of the class to instantiate
     * @return the place that was found or created, or null if it can't be done
     */
    public static IServiceProviderPlace createPlace(final String theLocation, final Object[] constructorArgs, final String theClassStr) {
        logger.debug("Ready to createPlace " + theLocation + " as " + theClassStr);

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
            logger.warn("classStr check failed for " + theLocation);
            return null;
        }

        final String bindKey = KeyManipulator.removeExpense(theLocation);
        final IServiceProviderPlace thePlace;
        try {
            thePlace = (IServiceProviderPlace) Factory.create(theClassStr, constructorArgs, bindKey);
        } catch (Throwable te) {
            // error creating place
            logger.error("cannot create " + theLocation, te);
            shutdownFailedPlace(bindKey, null);
            return null; // couldn't start the place.
        }

        final long t2 = System.currentTimeMillis();

        logger.debug("Started " + theLocation + " in " + (t2 - t1) / 1000.0 + "s");
        return thePlace;
    }

    public static void shutdownFailedPlace(final String loc, final IServiceProviderPlace place) {

        try {
            logger.warn("shutting down the failed place: " + loc);
            if (place != null) {
                place.shutDown();
            } else {
                // Force keys to be deregistered if we can
                try {
                    final IDirectoryPlace localDir = DirectoryPlace.lookup();
                    final List<DirectoryEntry> entries = localDir.getMatchingEntries("*." + loc);
                    if (entries != null && entries.size() > 0) {
                        final List<String> keys = new ArrayList<String>();
                        for (final DirectoryEntry entry : entries) {
                            keys.add(entry.getKey());
                        }
                        logger.info("Forcing removal of " + keys.size() + " keys due to failed " + loc);
                        localDir.removePlaces(keys);
                    } else {
                        logger.debug("Failed " + loc + " did not have any directory keys registered");
                    }
                } catch (EmissaryException ee) {
                    logger.debug("NO local directory, cannot force key dereg for " + loc);
                }
            }
            Namespace.unbind(loc);
        } catch (Throwable tt) {
            logger.error("whoa there pardner... " + loc, tt);
        }
    }


    // ////////////////////////////////////////////////////////////
    /**
     * method to check if the place already exists.
     */
    // ////////////////////////////////////////////////////////////
    public static IServiceProviderPlace alreadyExists(final String theLocation) {
        final String thePlaceHost = Startup.placeHost(theLocation);
        // TODO should we add a check for index of? Can cause an exception if // isn't present
        final String luStr = theLocation.substring(theLocation.indexOf("//"));
        try {
            final IServiceProviderPlace thePlace = (IServiceProviderPlace) Namespace.lookup(luStr);
            logger.debug(theLocation + " already running on " + thePlaceHost);
            return thePlace;
        } catch (NamespaceException nse) {
            // expected when the place doesn't exist
        } catch (Throwable t) {
            // empty catch block
        }
        return null;
    }

    public static String getClassString(final String theLocation) {
        final String thePlaceName = Startup.placeName(theLocation);
        if (thePlaceName == null || thePlaceName.length() == 0) {
            logger.error("Illegal location specified " + theLocation + ", has no place name");
        }
        final List<String> classStringList = classConf.findEntries(thePlaceName);
        if (classStringList.size() < 1) {
            logger.error("Need a CLASS config entry for " + thePlaceName + " check entry in emissary.admin.MasterClassNames.cfg, using default "
                    + defaultClassName + " which is probably not what you want.");
            return defaultClassName;
        }
        final String out = classStringList.get(0);
        return out;
    }

    /** This class is not meant to be instantiated. */
    private PlaceStarter() {}
}
