package emissary.place;

import emissary.admin.PlaceStarter;
import emissary.core.EmissaryException;
import emissary.core.Form;
import emissary.core.IBaseDataObject;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.core.ResourceException;
import emissary.core.ResourceWatcher;
import emissary.core.TimedResource;
import emissary.directory.DirectoryEntry;
import emissary.directory.KeyManipulator;

import org.apache.commons.collections4.CollectionUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

import static emissary.core.constants.Configurations.OUTPUT_FORM;

/**
 * This place will coordinate service to several lower level service places. We have a list and will execute each place
 * in turn. This is good for service orchestration and there is an override point for derived classed to determine
 * whether to continue processing at each step. It is good for these types of places to register as "STUDY" places so
 * that current forms of the requested type will come here first instead of going to the individual places for ID, or as
 * "COORDINATE" if coordinating for TRANSFORM places, or as "VERIFY" if coordinating for IO places (OUTPUT, that is).
 *
 * We only coordinate among places in the local Namespace. If the place specified is not initially in the local
 * namespace we attempt to create it. If it cannot be created it is not used.
 */
public class CoordinationPlace extends ServiceProviderPlace {

    // The list of place keys we coordinate for
    protected List<String> placeKeys;

    // The list of place references we coordinate for
    protected List<IServiceProviderPlace> placeRefs;


    @Nullable
    protected String outputForm = null; // What we call it when we are finished
    protected boolean pushForm = true; // push or set on the form
    protected boolean updateTransformHistory = false;

    // set of coordination places that failed to be created/did not exist
    protected static final Set<String> failedCoordPlaceCreation = new LinkedHashSet<>();

    /**
     * Create the place using the supplied configuration and location
     * 
     * @param cfgInfo the configuration resource to use
     * @param dir the controlling directory
     * @param placeLoc binding information for this instance
     */
    public CoordinationPlace(String cfgInfo, String dir, String placeLoc) throws IOException {
        super(cfgInfo, dir, placeLoc);
        configurePlace();
    }

    /**
     * Create the place using the supplied configuration
     * 
     * @param cfgInfo the configuration resource to use
     */
    public CoordinationPlace(String cfgInfo) throws IOException {
        super(cfgInfo, "TestCoordinationPlace.foo.com:8003");
        configurePlace();
    }

    /**
     * Create the place using the supplied configuration
     * 
     * @param cfgInfo the configuration stream to use
     */
    public CoordinationPlace(InputStream cfgInfo) throws IOException {
        super(cfgInfo);
        configurePlace();
    }


    /**
     * Create the place taking all defaults for location and config
     */
    public CoordinationPlace() throws IOException {
        super();
        configurePlace();
    }

    /**
     * Set up the place specific information Config items read here are:
     * 
     * <ul>
     * <li>OUTPUT_FORM: default=null, output form for final step of coordination</li>
     * <li>PUSH_OUTPUT_FORM: default=true, calls pushOutputForm when true, setOutputForm otherwise</li>
     * <li>SERVICE_COORDINATION: place entries to use for this coordination place, an ordered list of places that must
     * already be constructed</li>
     * </ul>
     */
    protected void configurePlace() {
        outputForm = configG.findStringEntry(OUTPUT_FORM, null);
        pushForm = configG.findBooleanEntry("PUSH_OUTPUT_FORM", true);
        updateTransformHistory = configG.findBooleanEntry("UPDATE_TRANSFORM_HISTORY", false);

        placeKeys = configG.findEntries("SERVICE_COORDINATION");
        logger.debug("We got {} entries to coordinate", placeKeys.size());

        placeRefs = new ArrayList<>();
        for (String s : placeKeys) {
            try {
                // See if the place already exists
                Object ref = Namespace.lookup(s);
                if (ref instanceof IServiceProviderPlace) {
                    placeRefs.add((IServiceProviderPlace) ref);
                    logger.debug("Added reference for {}:{}", s, ref);
                } else {
                    logger.error("Referenced place {} is of the wrong type: {}", s, ref.getClass().getName());
                }
            } catch (NamespaceException ex) {
                // Try creating the place
                try {
                    String skey = KeyManipulator.getServiceHostUrl(keys.get(0)) + s;
                    logger.debug("No such place {}, creating as {}", s, skey, ex);
                    String sclz = PlaceStarter.getClassString(skey);
                    IServiceProviderPlace p = PlaceStarter.createPlace(skey, null, sclz, dirPlace);
                    if (p != null) {
                        placeRefs.add(p);
                        logger.debug("Place created: {}", p);
                    } else {
                        failedCoordPlaceCreation.add(s + " in " + configG.findStringEntry("PLACE_NAME"));
                        logger.error("Place does not exist and cannot be created: {}", s);
                    }
                } catch (RuntimeException e) {
                    failedCoordPlaceCreation.add(s + " in " + configG.findStringEntry("PLACE_NAME"));
                    logger.error("Place does not exist and cannot be created: {}", s, e);
                }
            }
        }
    }

    /**
     * Evaluate whether to continue processing. Classes can override this method to provide any additional logic during
     * coordination
     */
    protected boolean shouldContinue(IBaseDataObject d, IServiceProviderPlace p) {
        logger.debug("Continuing with currentForm {} to place {}", d.currentForm(), p);
        return true;
    }

    /**
     * Return whether to continue traversing the list of coordinated places when an error occurs in one of them
     *
     * @param p place that is currently processing the ibdo
     * @param errorOccurred true if an error occurred
     *
     * @return false if processing should not continue
     */
    protected boolean shouldContinue(IServiceProviderPlace p, boolean errorOccurred) {
        if (!continueOnError() && errorOccurred) {
            logger.info("Error terminating coordination step at {}", p);
            return false;
        }
        return true;
    }

    /**
     * Evaluate whether to skip processing. This will allow the coordination place to continue to the next configured place.
     * Note that shouldContinue method takes precedence. Use one or the other and be cautious when using both Classes can
     * override this method to provide any additional logic during coordination
     */
    protected boolean shouldSkip(IBaseDataObject d, IServiceProviderPlace p) {
        logger.debug("Skipping with currentForm {} to place {}", d.currentForm(), p);
        return false;
    }

    /**
     * Classes can override this method to do any last things to the data object before closing out the job
     */
    protected void cleanUpHook(IBaseDataObject d) {
        logger.debug("In base cleanUpHook for coordination {}", d.currentForm());
    }

    /**
     * Classes can override this method to do anything to the list of sprouted data objects from this coordination
     */
    protected void sproutHook(List<IBaseDataObject> sproutCollection, IBaseDataObject d) {
        logger.debug("In base sproutHook with {}", sproutCollection.size());
    }

    private void sproutHook(List<IBaseDataObject> sproutCollection, IBaseDataObject d, boolean hd) {
        // Allow derived classes a shot at the sprouts
        if (hd) {
            sproutHook(sproutCollection, d);
        }
    }

    /**
     * Consume a data object and coordinate its processing
     *
     * @param d the payload to process
     * @param hd true if doing heavy-duty processing
     * @return the list of sprouted data objects
     */
    protected List<IBaseDataObject> coordinate(IBaseDataObject d, boolean hd) {
        List<IBaseDataObject> sproutCollection = new ArrayList<>();

        boolean errorOccurred = false;

        // Iterate over the configured places
        for (IServiceProviderPlace p : placeRefs) {
            // Let derived classed decide to quit or continue this loop
            if (!shouldContinue(d, p)) {
                break;
            } else if (shouldSkip(d, p)) {
                continue;
            }

            updateTransformHistory(d, p);

            // Collect attachments for hd processing
            List<IBaseDataObject> sprouts = null;

            // Like an agent would do it
            try (TimedResource tr = resourceWatcherStart(p)) {
                assert tr != null; // to silence an unused resource warning

                if (hd) {
                    // Do the normal HD processing
                    sprouts = p.agentProcessHeavyDuty(d);
                } else {
                    // Do the normal Non-HD processing
                    p.agentProcessCall(d);
                }
                errorOccurred = d.currentForm().equals(Form.ERROR);
            } catch (Exception ex) {
                errorOccurred = handlePlaceException(p, hd, ex);
            } finally {
                if (Thread.interrupted()) {
                    logger.warn("Place {} was interrupted during execution.", p);
                }
            }

            if (!shouldContinue(p, errorOccurred)) {
                break;
            }

            // Track any new attachments
            if (CollectionUtils.isNotEmpty(sprouts)) {
                sproutCollection.addAll(sprouts);
            }
        }

        applyForm(d, errorOccurred);

        sproutHook(sproutCollection, d, hd);

        // Allow derived classes a shot to clean up the parent
        cleanUpHook(d);

        return sproutCollection;
    }

    /**
     * Allow derived classes a shot to handle a place exception
     *
     * @param p place that was processing when the exception was thrown
     * @param hd true if doing heavy-duty processing
     * @param ex exception thrown by the place
     *
     * @return if an error occurred
     */
    protected boolean handlePlaceException(IServiceProviderPlace p, boolean hd, Exception ex) {
        logger.warn("agentProcess{} called from Coordinate problem", (hd ? "HeavyDuty" : "Call"), ex);
        return true;
    }

    private void updateTransformHistory(IBaseDataObject d, IServiceProviderPlace p) {
        if (updateTransformHistory) {
            DirectoryEntry de = p.getDirectoryEntry();
            de.setDataType(d.currentForm());
            // append to the transform history, with flag indicating that the visit was coordinated
            d.appendTransformHistory(de.getKey(), true);
        }
    }

    /**
     * How to handle applying the output form if an error occurred during processing
     *
     * @param d the ibdo to process
     * @param errorOccurred true if an error occurred
     */
    protected void applyForm(IBaseDataObject d, boolean errorOccurred) {
        if (!errorOccurred || shouldApplyOutputFormOnError()) {
            applyOutputForm(d);

            // Clean up my proxies
            nukeMyProxies(d);
        }
    }

    /**
     * If true, process the output form the same for the default and error condition
     *
     * @return boolean to allow processing of output for when an error occurs
     */
    protected boolean shouldApplyOutputFormOnError() {
        return false;
    }

    /**
     * Apply the output form according to configuration
     *
     * @param d the ibdo to process
     */
    protected void applyOutputForm(IBaseDataObject d) {
        if (outputForm != null) {
            if (pushForm) {
                d.pushCurrentForm(outputForm);
            } else {
                d.setCurrentForm(outputForm);
            }
        }
    }

    /**
     * If false, do not continue processing other places after an error occurs
     *
     * @return boolean to not continue processing after an error
     */
    protected boolean continueOnError() {
        return false;
    }

    protected TimedResource resourceWatcherStart(final IServiceProviderPlace place) {
        TimedResource tr = TimedResource.EMPTY;
        try {
            tr = ResourceWatcher.lookup().starting(getAgent(), place);
        } catch (EmissaryException ex) {
            logger.debug("No resource monitoring enabled");
        }
        return (tr == null) ? TimedResource.EMPTY : tr;
    }

    /**
     * Process point when not using HDMobileAgent
     * 
     * @param d the payload to process
     */
    @Override
    public void process(IBaseDataObject d) throws ResourceException {
        List<IBaseDataObject> l = coordinate(d, false);
        if (CollectionUtils.isNotEmpty(l)) {
            logger.error("Non-sprouted documents are being lost {}", l.size());
        }
    }

    /**
     * Process point for HDMobileAgent
     * 
     * @param d the payload to process
     * @return the list of sprouted data objects
     */
    @Override
    public List<IBaseDataObject> processHeavyDuty(IBaseDataObject d) throws ResourceException {
        return coordinate(d, true);
    }

    /**
     * Get method for the set of failed coordination places
     * 
     * @return the names of the failed coordination places
     */
    public static Set<String> getFailedCoordinationPlaces() {
        return failedCoordPlaceCreation;
    }
}
