package emissary.place;

import emissary.config.ConfigEntry;
import emissary.config.Configurator;
import emissary.core.Family;
import emissary.core.Form;
import emissary.core.HDMobileAgent;
import emissary.core.IBaseDataObject;
import emissary.core.MobileAgent;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.core.ResourceException;
import emissary.core.ResourceWatcher;
import emissary.directory.DirectoryServiceProviderPlace;
import emissary.kff.KffDataObjectHandler;
import emissary.log.MDCConstants;
import emissary.parser.SessionParser;
import emissary.util.JMXUtil;

import com.codahale.metrics.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

import static emissary.core.constants.Configurations.PLACE_RESOURCE_LIMIT_MILLIS;

/**
 * Concrete instances of ServiceProviderPlace can be created by the emissary.admin.PlaceStarter and registered with the
 * emissary.directory.IDirectoryPlace to make their respective services available and a specified cost and quality
 * throughout the system.
 */
public abstract class ServiceProviderPlace extends DirectoryServiceProviderPlace implements IServiceProviderPlace, ServiceProviderPlaceMBean {

    /**
     * Container for all configuration parameters read from the configuration file for this place. The net result is that
     * many name value pairs are loaded from one or more files. See ServiceConfigGuide for details.
     *
     * @see emissary.config.ServiceConfigGuide
     */
    @Nullable
    protected Configurator configG;

    /**
     * Static context logger
     */
    protected static final Logger slogger = LoggerFactory.getLogger(ServiceProviderPlace.class);

    /**
     * Set up handler for rehashing
     */
    @Nullable
    protected KffDataObjectHandler kff = null;

    /**
     * These are used to track process vs processHD implementations to know whether one can proxy for the other one
     */
    protected boolean processMethodImplemented = false;
    protected boolean heavyDutyMethodImplemented = false;

    /**
     * Create a place and register it in the local directory. The default config must contain at least one SERVICE_KEY
     * element used to know where that is and how to name it. If the old style config with SERVICE_PROXY etc is used then
     * the PlaceName becomes the runtime class name of the instance without the package.
     */
    public ServiceProviderPlace() throws IOException {
        super();
        String placeLocation = this.getClass().getSimpleName();
        configG = loadConfigurator(placeLocation);
        setupPlace(null, placeLocation);
    }

    /**
     * Create a place and register it at the location specified. The location key contains the name of the place which is
     * used to configure it. The local directory instance is found in the local Namespace.
     *
     * @param thePlaceLocation string name of our location
     */
    public ServiceProviderPlace(String thePlaceLocation) throws IOException {
        super();
        configG = loadConfigurator(thePlaceLocation);
        setupPlace(null, thePlaceLocation);
    }

    /**
     * Create the place the normal way
     *
     * @param configFile file name for config data
     * @param theDir string name of our directory
     * @param thePlaceLocation string name of our location
     */
    protected ServiceProviderPlace(String configFile, @Nullable String theDir, String thePlaceLocation) throws IOException {
        super();
        configG = loadConfigurator(configFile, thePlaceLocation);
        setupPlace(theDir, thePlaceLocation);
    }

    /**
     * Construct with config data from a stream
     *
     * @param configStream stream of config data
     * @param theDir string name of our directory
     * @param thePlaceLocation string name of our location
     */
    protected ServiceProviderPlace(InputStream configStream, @Nullable String theDir, String thePlaceLocation) throws IOException {
        super();
        configG = loadConfigurator(configStream, thePlaceLocation);
        setupPlace(theDir, thePlaceLocation);
    }

    /**
     * Construct with config data from a stream on the local directory
     *
     * @param configStream stream of config data
     */
    protected ServiceProviderPlace(InputStream configStream) throws IOException {
        super();
        String placeLocation = this.getClass().getSimpleName();
        configG = loadConfigurator(configStream, placeLocation);
        setupPlace(null, placeLocation);
    }

    /**
     * Create the place with no directory
     *
     * @param configFile string name of config data file
     * @param placeLocation string name of our location
     */
    protected ServiceProviderPlace(String configFile, String placeLocation) throws IOException {
        this(configFile, null, placeLocation);
    }


    /**
     * Create the place with no directory
     *
     * @param configStream stream of config data
     * @param placeLocation string name of our location
     */
    protected ServiceProviderPlace(InputStream configStream, String placeLocation) throws IOException {
        this(configStream, null, placeLocation);
    }

    @Override
    protected Configurator getConfigurator() {
        return this.configG;
    }

    /**
     * Help the constructor get the place running
     *
     * @param theDir name of our directory
     */
    @Override
    protected void setupPlace(@Nullable String theDir, String placeLocation) throws IOException {
        super.setupPlace(theDir, placeLocation);

        // Set up kff if we need it
        if (this instanceof RehashingPlace || this instanceof MultiFileServerPlace) {
            initKff();
        }

        // register MBean with JMX
        JMXUtil.registerMBean(this);

        // Verify and warn of incorrect process/processHeavyDuty implementation
        verifyProcessImplementationProvided();
    }

    /**
     * Initialize the Kff Handler with our policy settings
     */
    protected synchronized void initKff() {
        kff =
                new KffDataObjectHandler(KffDataObjectHandler.TRUNCATE_KNOWN_DATA, KffDataObjectHandler.SET_FORM_WHEN_KNOWN,
                        KffDataObjectHandler.SET_FILE_TYPE);
    }

    /**
     * The ServiceProviderPlace facade for visiting agents
     *
     * @param payload dataobject from a MobileAgent
     */
    @Override
    public void agentProcessCall(IBaseDataObject payload) throws ResourceException {
        try {
            process(payload);
            rehash(payload);
        } catch (ResourceException r) {
            throw r;
        } catch (RuntimeException e) {
            logger.error("Place.process exception", e);
        }
    }

    /**
     * "HD" agent calls this method when visiting the place. If you use emissary.core.MobileAgent this method is never
     * called. Should be overridden by concrete places that wish to process bulk data in a different manner than one payload
     * at a time.
     *
     * @param payloadList list of IBaseDataObject from an HDMobileAgent
     * @return list of IBaseDataObject "sprouts"
     */
    @Override
    public List<IBaseDataObject> agentProcessHeavyDuty(List<IBaseDataObject> payloadList) throws Exception {

        logger.debug("Entering agentProcessHeavyDuty with {} payload items", payloadList.size());

        List<IBaseDataObject> list = new ArrayList<>();

        // For each incoming payload object
        for (IBaseDataObject dataObject : payloadList) {
            try {
                // Process the payload item
                List<IBaseDataObject> l = agentProcessHeavyDuty(dataObject);
                if (!l.isEmpty()) {
                    dataObject.setNumChildren(dataObject.getNumChildren() + l.size());
                }

                // Accumulate results in a list to return
                list.addAll(l);
            } catch (Exception e) {
                logger.error("Place.process exception", e);
                dataObject.addProcessingError("agentProcessHD(" + keys.get(0) + "): " + e);
                dataObject.replaceCurrentForm(Form.ERROR);
            }
        }

        // Some debug output
        if (logger.isDebugEnabled()) {
            for (IBaseDataObject d : list) {
                logger.debug("Returning child {} -> {}", d.shortName(), d.getAllCurrentForms());
            }
        }

        return list;
    }

    /**
     * "HD" method called only by HDMobileAgent for a single incoming payload
     *
     * @param payload data object to process
     * @return List of IBaseDataObject "sprouts"
     */
    @Override
    public List<IBaseDataObject> agentProcessHeavyDuty(IBaseDataObject payload) throws Exception {
        MDC.put(MDCConstants.SHORT_NAME, payload.shortName());
        MDC.put(MDCConstants.SERVICE_LOCATION, this.getKey());
        try {
            List<IBaseDataObject> l = processHeavyDuty(payload);
            rehash(payload);
            return l;
        } catch (Exception e) {
            logger.error("Place.process threw:", e);
            throw e;
        }
    }

    /**
     * Rehash the payload if this is a rehashing place
     *
     * @param payload the payload to evaluate and rehash
     */
    protected void rehash(@Nullable IBaseDataObject payload) {
        // Recompute hash if marker interface is enabled
        if (this instanceof RehashingPlace && kff != null && payload != null) {
            kff.hash(payload);
            payload.setParameter(SessionParser.ORIG_DOC_SIZE_KEY, "" + payload.dataLength());
        }
    }


    /**
     * Convenience method to process a single payload when there is no expecation of decomposing any new payload objects
     * from what was provided
     */
    @Override
    public void process(IBaseDataObject payload) throws ResourceException {
        if (heavyDutyMethodImplemented) {
            List<IBaseDataObject> children = processHeavyDuty(payload);
            if (children != null && !children.isEmpty()) {
                logger.error("Sprouting is no longer supported, lost {} children", children.size());
            }
        } else {
            throw new IllegalStateException("Neither process nor processHeavyDuty appears to be implemented");
        }
    }

    /**
     * Process a payload and return a list of new items decomosed from it (or an empty list) What happens here and what is
     * ultimately expected depends on the workflow stage of the place registration, and the type of job it is expected to
     * do.
     *
     * @param payload the BaseDataObject to process
     * @return list of BaseDataObject that represent the children (if any)
     */
    @Override
    public List<IBaseDataObject> processHeavyDuty(IBaseDataObject payload) throws ResourceException {
        if (processMethodImplemented) {
            process(payload);
            return Collections.emptyList();
        } else {
            throw new IllegalStateException("Neither process nor processHeavyDuty appears to be implemented");
        }
    }

    /**
     * This method must be called during setup of the place to ensure that one of the two implementations is provided by the
     * declaring class.
     */
    protected void verifyProcessImplementationProvided() {

        Class<?> c = this.getClass();
        while (!c.isAssignableFrom(ServiceProviderPlace.class)) {
            for (Method m : c.getDeclaredMethods()) {
                String mname = m.getName();
                String rname = m.getReturnType().getName();
                Class<?>[] params = m.getParameterTypes();

                if (params.length == 1 && params[0].isAssignableFrom(IBaseDataObject.class)) {
                    if (mname.equals("process") && rname.equals("void")) {
                        processMethodImplemented = true;
                    } else if (mname.equals("processHeavyDuty") && rname.equals(List.class.getName())) {
                        heavyDutyMethodImplemented = true;
                    }
                }
            }

            if (heavyDutyMethodImplemented || processMethodImplemented) {
                logger.debug("Found enough process implementation at level {}", c.getName());
                break;
            } else {
                c = c.getSuperclass();
            }
        }

        if (!processMethodImplemented && !heavyDutyMethodImplemented && !(this instanceof AgentsNotSupportedPlace)) {
            logger.error("It appears that neither process nor processHeavyDuty is implemented. "
                    + "If that is incorrect you can directly set one of the corresponding "
                    + "boolean flags or override verifyProcessImplementationProvided or "
                    + "implement AgentsNotSupported to turn this message off");
        }
    }

    /**
     * Get custom resource limitation in millis if specified
     *
     * @return -2 if not specified, or long millis if specified
     */
    @Override
    public long getResourceLimitMillis() {
        return configG.findLongEntry(PLACE_RESOURCE_LIMIT_MILLIS, -2L);
    }

    /**
     * Get the agent that is currently responsible for this thread
     *
     * @return the live instance of the mobile agent thread
     */
    @Override
    public MobileAgent getAgent() throws NamespaceException {
        return (MobileAgent) Namespace.lookup(Thread.currentThread().getName());
    }

    @Override
    public List<String> getRunningConfig() {
        List<String> runningConfigList = new ArrayList<>();

        if (configG != null) {
            for (ConfigEntry c : configG.getEntries()) {
                runningConfigList.add("Key: " + c.getKey() + " Value: " + c.getValue());
            }
        }
        return runningConfigList;
    }

    @Override
    public String getPlaceStats() {
        ResourceWatcher resource;
        Timer placeStats;
        String placeStatStr = "UNAVAILABLE";
        try {
            resource = ResourceWatcher.lookup();
            String statsKey = getPlaceName();
            placeStats = resource.getStat(statsKey);

            if (placeStats != null) {
                placeStatStr = placeStats.getSnapshot().toString();
            }
        } catch (NamespaceException ne) {
            logger.error("Exception occurred while trying to lookup resource", ne);
        }
        return placeStatStr;
    }

    @Override
    public void dumpRunningConfig() {
        logger.info("Dumping Running Config for {}:", placeName);
        logger.info("===============");
        logger.info("{}", getRunningConfig());
        logger.info("===============");
    }

    @Override
    public void dumpPlaceStats() {
        logger.info("Dumping Places Stats for {}:", placeName);
        logger.info("===============");
        logger.info("{}", getPlaceStats());
        logger.info("===============");
    }

    /**
     * Leverage our insider knowledge of how emissary MobileAgents work in conjunction with the namespace to reach around
     * the back door of the system and get the TLD for the current payload You should only call this if
     * payload.shortName().indexOf(Family.SEP) &gt; -1
     */
    @Nullable
    protected IBaseDataObject getTld() {
        try {
            MobileAgent agent = getAgent();

            if (agent instanceof HDMobileAgent) {
                Object payload = ((HDMobileAgent) agent).getPayloadForTransport();
                if (payload instanceof List) {
                    List<?> familyTree = (List<?>) payload;
                    for (Object familyMember : familyTree) {
                        if (familyMember instanceof IBaseDataObject) {
                            IBaseDataObject member = (IBaseDataObject) familyMember;
                            if (!member.shortName().contains(Family.SEP)) {
                                return member;
                            }
                        } else {
                            logger.debug("Family members are not the right class - {}", familyMember.getClass().getName());
                        }
                    }
                } else {
                    logger.debug("Agent is transporting unknown data type - {}", payload.getClass().getName());
                }
            } else {
                logger.debug("System is operating with {} so there is no hope of getting the family tree", agent.getClass().getName());
            }
        } catch (Exception ex) {
            logger.debug("Could not get controlling agent", ex);
        }

        // Nothing found?
        return null;
    }

    /**
     * This method should return a list of all the parameters that a subclass Place will potentially modify (this includes
     * adding, removing, or changing the value of).
     *
     * Currently, this implementation in ServiceProviderPlace will return an empty List, but subclass implementers (i.e.
     * Place authors) are urged to override this method. Ultimately, this body of this method will be removed and it will
     * become abstract.
     *
     * @return a List&lt;String&gt; of all parameter names that a Place will add, remove, or change the value of
     */
    protected List<String> getParametersModified() {
        return Collections.emptyList();
    }

    /**
     * This method should return a list of all the alternate views that a subclass Place will potentially modify (this
     * includes adding, removing, or changing the value of).
     *
     * Currently, this implementation in ServiceProviderPlace will return an empty List, but subclass implementers (i.e.
     * Place authors) are urged to override this method. Ultimately, this body of this method will be removed and it will
     * become abstract.
     *
     * @return a List&lt;String&gt; of all alternate view names that a Place will add, remove, or change the value of
     */
    protected List<String> getAlternateViewsModified() {
        return Collections.emptyList();
    }

    /**
     * This method should return a list of all the output forms that a subclass Place will potentially output
     *
     * Currently, this implementation in ServiceProviderPlace will return an empty List, but subclass implementers (i.e.
     * Place authors) are urged to override this method. Ultimately, this body of this method will be removed and it will
     * become abstract.
     *
     * @return a List&lt;String&gt; of all output forms that a Place may output
     */
    protected List<String> getOutputForms() {
        return Collections.emptyList();
    }

    /**
     * This method should return a list of all the file types that a subclass Place will potentially output
     *
     * Currently, this implementation in ServiceProviderPlace will return an empty List, but subclass implementers (i.e.
     * Place authors) are urged to override this method. Ultimately, this body of this method will be removed and it will
     * become abstract.
     *
     * @return a List&lt;String&gt; of all file types that a Place may output
     */
    protected List<String> getFileTypes() {
        return Collections.emptyList();
    }

    /**
     * This method should return true if the Place may alter the IBaseDataObject data[] (i.e.PrimaryView) in any way, and
     * false otherwise
     *
     * @return true if the data[] member may be altered by the Place, false otherwise
     */
    protected boolean changesPrimaryView() {
        return false;
    }

    /**
     * This method should return true if the Place may create any "extracted" records, and false otherwise
     *
     * @return true if the Place may create "extracted" records, false otherwise
     */
    protected boolean createsExtractedRecords() {
        return false;
    }

    /**
     * This method should return true if the Place may sprout "child" IBaseDataObjects, and false otherwise
     *
     * @return true if the Place may sprout children, false otherwise
     */
    protected boolean sproutsChildren() {
        return false;
    }


}
