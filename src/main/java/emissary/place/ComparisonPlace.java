package emissary.place;

import emissary.core.DiffCheckConfiguration;
import emissary.core.IBaseDataObject;
import emissary.core.IBaseDataObjectHelper;
import emissary.core.ResourceException;
import emissary.util.PlaceComparisonHelper;

import org.apache.commons.lang3.Validate;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.List;

/**
 * This place takes two other places, gives each place a copy of the received IBDO and compares the output of the two
 * places.
 * <p>
 * NOTE: The configurations for the two places to be compared are used to construct the places internally; however, the
 * two places are never registered with the Emissary framework. Therefore, it is only the configuration for this class
 * that is registered with the Emissary framework. This means that PLACE_NAME, SERVICE_NAME, SERVICE_PROXY, etc. must
 * all be set correctly (and is most likely based on the two places being compared's configurations) in this place's
 * configuration in order to execute the two places to be compared correctly.
 * <p>
 * NOTE: the ibdo and attachments returned by Place A are the ones returned from this class.
 * <p>
 * The configuration file for this class must contain the five properties defined by the five constants in this class.
 */
public class ComparisonPlace extends ServiceProviderPlace {
    /**
     * The full pathname for the first class.
     */
    public static final String PLACE_A_CLASSNAME = "PLACE_A_CLASSNAME";
    /**
     * The full pathname for the configuration for the first class.
     */
    public static final String PLACE_A_CONFIGNAME = "PLACE_A_CONFIGNAME";
    /**
     * The full pathname for the second class.
     */
    public static final String PLACE_B_CLASSNAME = "PLACE_B_CLASSNAME";
    /**
     * The full pathname for the configuration for the second class.
     */
    public static final String PLACE_B_CONFIGNAME = "PLACE_B_CONFIGNAME";
    /**
     * An identifier to be added to the log message.
     */
    public static final String LOGGING_IDENTIFIER = "LOGGING_IDENTIFIER";

    private static final String CONFIG_ERROR_MSG = " must be defined in the configuration file!";
    private static final String PROCESS_PROCESSHD_MSG = "Mixed process/processHeavyDuty not allowed!";

    private final ServiceProviderPlace placeA;
    private final ServiceProviderPlace placeB;
    private final String loggingIdentifier;

    public ComparisonPlace(final String configFile, final String theDir, final String thePlaceLocation) throws IOException {
        super(configFile, theDir, thePlaceLocation);

        final String placeAClassName = configG.findStringEntry(PLACE_A_CLASSNAME);
        final String placeAConfigName = configG.findStringEntry(PLACE_A_CONFIGNAME);
        final String placeBClassName = configG.findStringEntry(PLACE_B_CLASSNAME);
        final String placeBConfigName = configG.findStringEntry(PLACE_B_CONFIGNAME);

        loggingIdentifier = configG.findStringEntry(LOGGING_IDENTIFIER);

        Validate.notNull(placeAClassName, PLACE_A_CLASSNAME + CONFIG_ERROR_MSG);
        Validate.notNull(placeAConfigName, PLACE_A_CONFIGNAME + CONFIG_ERROR_MSG);
        Validate.notNull(placeBClassName, PLACE_B_CLASSNAME + CONFIG_ERROR_MSG);
        Validate.notNull(placeBConfigName, PLACE_B_CONFIGNAME + CONFIG_ERROR_MSG);
        Validate.notNull(loggingIdentifier, LOGGING_IDENTIFIER + CONFIG_ERROR_MSG);

        placeA = createPlace(placeAClassName, placeAConfigName);
        placeB = createPlace(placeBClassName, placeBConfigName);

        Validate.isTrue(placeA.processMethodImplemented == placeB.processMethodImplemented, PROCESS_PROCESSHD_MSG);
    }

    @Override
    public List<IBaseDataObject> processHeavyDuty(final IBaseDataObject ibdoA) throws ResourceException {
        final IBaseDataObject ibdoB = IBaseDataObjectHelper.clone(ibdoA, true);
        final List<IBaseDataObject> attachmentsA;
        final List<IBaseDataObject> attachmentsB;

        if (placeA.processMethodImplemented) {
            placeA.process(ibdoA);
            placeB.process(ibdoB);

            attachmentsA = Collections.emptyList();
            attachmentsB = Collections.emptyList();
        } else {
            attachmentsA = placeA.processHeavyDuty(ibdoA);
            attachmentsB = placeB.processHeavyDuty(ibdoB);
        }

        final String differences = checkDifferences(ibdoA, ibdoB, attachmentsA, attachmentsB, loggingIdentifier);

        if (differences != null) {
            logDifferences(differences);
        }

        return attachmentsA;
    }

    /**
     * This method checks for the differences in the output between the two places and can be overridden for custom
     * behaviour.
     * 
     * @param ibdoA the IBDO from the first place.
     * @param ibdoB the IBDO from the second place.
     * @param attachmentsA the list of attachments from the first place.
     * @param attachmentsB the list of attachments from the second place.
     * @param loggingIdentifier the identifier to be added to the message to be logged.
     * @return the differences to be logger or null if there are no differences.
     */
    protected String checkDifferences(final IBaseDataObject ibdoA, final IBaseDataObject ibdoB, final List<IBaseDataObject> attachmentsA,
            final List<IBaseDataObject> attachmentsB, final String loggingIdentifier) {
        return PlaceComparisonHelper.checkDifferences(ibdoB, ibdoA, attachmentsB, attachmentsA, loggingIdentifier,
                DiffCheckConfiguration.onlyCheckData());
    }

    /**
     * This method logs the differences in the output between the two places and can be overriden for custom behaviour.
     * 
     * @param differences to be logged.
     */
    protected void logDifferences(final String differences) {
        logger.info(differences);
    }

    /**
     * This method creates a place given a class name and configuration file.
     * 
     * @param className of the place.
     * @param configName of the place.
     * @return the place
     * @throws IOException if anything goes wrong.
     */
    public static final ServiceProviderPlace createPlace(final String className, final String configName) throws IOException {
        try {
            final Class<?> placeAClass = Class.forName(className);
            final Constructor<?> placeAConstructor = placeAClass.getConstructor(String.class, String.class, String.class);

            return (ServiceProviderPlace) placeAConstructor.newInstance(null, null, configName);
        } catch (Exception e) {
            throw new IOException("Unable to create " + className, e);
        }
    }
}
