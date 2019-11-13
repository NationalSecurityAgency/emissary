package emissary.place;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.codahale.metrics.Timer;
import emissary.config.ConfigEntry;
import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.EmissaryException;
import emissary.core.IBaseDataObject;
import emissary.core.MobileAgent;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.core.ResourceException;
import emissary.core.ResourceWatcher;
import emissary.directory.DirectoryEntry;
import emissary.directory.EmissaryNode;
import emissary.directory.IDirectoryPlace;
import emissary.directory.KeyManipulator;
import emissary.directory.WildcardEntry;
import emissary.kff.KffDataObjectHandler;
import emissary.log.MDCConstants;
import emissary.parser.SessionParser;
import emissary.server.mvc.adapters.DirectoryAdapter;
import emissary.util.JMXUtil;
import emissary.util.JavaCharSetLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Concrete instances of ServiceProviderPlace can be created by the emissary.admin.PlaceStarter and registered with the
 * emissary.directory.IDirectoryPlace to make their respective services available and a specified cost and quality
 * throughout the system.
 */
public abstract class ServiceProviderPlace extends emissary.core.AggregateObject implements emissary.place.IServiceProviderPlace,
        emissary.core.IAggregator, ServiceProviderPlaceMBean {

    /**
     * Container for all configuration parameters read from the configuration file for this place. The net result is that
     * many name value pairs are loaded from one or more files. See ServiceConfigGuide for details.
     *
     * @see emissary.config.ServiceConfigGuide
     */
    protected Configurator configG;

    /**
     * A <i><b>local</b></i> reference to the directory that this place resides in. Every JVM that contains 'places' must
     * have a local directory
     *
     * @see emissary.directory.DirectoryPlace
     */
    protected String dirPlace;
    protected IDirectoryPlace localDirPlace = null;

    /**
     * set of keys for this place read from configG. Each of the values defined by
     * SERVICE_PROXY.SERCVICE_TYPE.SERVICE_NAME.PLACE_LOCATION$EXPENSE from the config file or KEY values from the config
     * file.
     */
    protected List<String> keys = new ArrayList<String>();

    // Items that are going to be deprecated, but here now to
    // make the transition easier, for compatibility
    protected String myKey = null;
    protected int serviceCost = -1;
    protected int serviceQuality = -1;
    protected String placeName = null;

    /**
     * Text description of what the place does, usually from config file
     */
    protected String serviceDescription;

    /**
     * Static context logger
     */
    protected static final Logger slogger = LoggerFactory.getLogger(ServiceProviderPlace.class);

    /**
     * Dynamic context logger uses run-time classname as category
     */
    protected Logger logger;

    /**
     * Set up handler for rehashing
     */
    protected KffDataObjectHandler kff = null;

    private static final String DOT = ".";
    private static final String UNUSED_PROXY = "UNUSABLE-XyZZy";

    /**
     * These are used to track process vs processHD impelementations to know whether one can proxy for the other one
     */
    protected boolean processMethodImplemented = false;
    protected boolean processHDMethodImplemented = false;

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
    protected ServiceProviderPlace(String configFile, String theDir, String thePlaceLocation) throws IOException {
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
    protected ServiceProviderPlace(InputStream configStream, String theDir, String thePlaceLocation) throws IOException {
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
     * Load the configurator
     *
     * @param configStream the stream to use or null to auto configure
     */
    protected Configurator loadConfigurator(InputStream configStream, String placeLocation) throws IOException {
        // Read the configuration stream
        if (configStream != null) {
            // Use supplied stream
            return emissary.config.ConfigUtil.getConfigInfo(configStream);
        }
        return loadConfigurator(placeLocation);
    }

    /**
     * Load the configurator
     *
     * @param configFileName the file name to use or null to auto configure
     */
    protected Configurator loadConfigurator(String configFileName, String placeLocation) throws IOException {
        // Read the configuration stream
        if (configFileName != null) {
            // Use supplied stream
            return emissary.config.ConfigUtil.getConfigInfo(configFileName);
        }
        return loadConfigurator(placeLocation);
    }


    /**
     * Load the configurator, figuring out whence automatically
     */
    protected Configurator loadConfigurator(String placeLocation) throws IOException {
        if (placeLocation == null) {
            placeLocation = this.getClass().getSimpleName();
        }

        // Extract config data stream name from place location
        // and try finding config info with and without the
        // package name of this class (in that order)
        String myPackage = this.getClass().getPackage().getName();
        List<String> configLocs = new ArrayList<String>();
        // Dont use KeyManipulator for this, only works when hostname/fqdn has dots
        int pos = placeLocation.lastIndexOf("/");
        String serviceClass = (pos > -1 ? placeLocation.substring(pos + 1) : placeLocation);
        configLocs.add(myPackage + DOT + serviceClass + ConfigUtil.CONFIG_FILE_ENDING);
        configLocs.add(serviceClass + ConfigUtil.CONFIG_FILE_ENDING);
        return emissary.config.ConfigUtil.getConfigInfo(configLocs);
    }

    /**
     * Help the constructor get the place running
     *
     * @param theDir name of our directory
     */
    protected void setupPlace(String theDir, String placeLocation) throws IOException {

        // Customize the logger to the runtime class
        logger = LoggerFactory.getLogger(this.getClass());

        // The order of the following initialization calls
        // is touchy. NPE all over if you mess up here.

        // Initialize charset typing mechanism
        JavaCharSetLoader.initialize();

        // Set ServicePlace config items
        configureServicePlace(placeLocation);

        // Backwards compatibility setup items
        DirectoryEntry firstentry = new DirectoryEntry(keys.get(0));
        myKey = firstentry.getKey();
        serviceCost = firstentry.getCost();
        serviceQuality = firstentry.getQuality();
        placeName = firstentry.getServiceLocation();


        // configure directory references
        if (!(this instanceof emissary.directory.DirectoryPlace)) {
            localizeDirectory(theDir);
            logger.debug("Our localizedDirectory is " + dirPlace);
        } else {
            logger.debug("Not localizing directory since we are a directory");
        }

        // Set up kff if we need it
        if (this instanceof RehashingPlace || this instanceof MultiFileServerPlace) {
            initKff();
        }

        // Bind to the namespace before registering
        // our keys. This allows incoming traffic to find
        // us as soon as they see the keys
        for (String key : keys) {
            String bindKey = KeyManipulator.getServiceLocation(key);
            logger.debug("Binding myself into the namespace as " + bindKey);
            Namespace.bind(bindKey, this);
        }

        // Register with the directory
        // This pushes all our keys out to the directory which
        // sends them on in turn to peers, &c. in the p2p network
        register();

        // register MBean with JMX
        JMXUtil.registerMBean(this);

        // Verify and warn of incorrect process/processHeavyDuty implementation
        verifyProcessImplementationProvided();
    }

    /**
     * Get a local reference to the directpry.
     *
     * @param theDir key for the directory to use, if null will look up default name
     * @return true if it worked
     */
    private boolean localizeDirectory(String theDir) {
        // Get a local (non proxy) copy of the directory if possible!
        // Looking up both if nothing is provided
        if (theDir == null) {
            try {
                localDirPlace = emissary.directory.DirectoryPlace.lookup();
                dirPlace = localDirPlace.toString();
            } catch (EmissaryException ex) {
                if (emissary.server.EmissaryServer.exists() && !(this instanceof emissary.directory.DirectoryPlace)) {
                    logger.warn("Unable to find DirectoryPlace in local namespace", ex);
                    return false;
                }
            }
        } else {
            dirPlace = theDir;
            localDirPlace = null;
            try {
                String myUrl = KeyManipulator.getServiceHostURL(keys.get(0));
                String dirUrl = KeyManipulator.getServiceHostURL(dirPlace);
                if (dirUrl != null && dirUrl.equals(myUrl)) {
                    localDirPlace = (IDirectoryPlace) Namespace.lookup(KeyManipulator.getServiceLocation(theDir));
                } else {
                    logger.debug("Not localizing directory since dirPlace " + dirPlace + " is not equal to myUrl " + myUrl);
                }
            } catch (EmissaryException ex) {
                logger.error("Exception attempting to get local reference to directory", ex);
                return false;
            }
        }
        return true;
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
     * Set the logger to use, allows easier mocking among other things
     *
     * @param l the logger instance to use
     */
    public void setLogger(Logger l) {
        this.logger = l;
    }

    /**
     * Return an encapsulation of our key and cost structure Only good for the top key on the list
     *
     * @return a DirectoryEntry for this place
     */
    @Override
    public DirectoryEntry getDirectoryEntry() {
        return new DirectoryEntry(keys.get(0), serviceDescription, serviceCost, serviceQuality);
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

    /**
     * Configuration items read here are:
     *
     * <ul>
     * <li>PLACE_NAME: place name portion of key, required</li>
     * <li>SERVICE_NAME: service name portion of key, required</li>
     * <li>SERVICE_TYPE: service type portion of key, required</li>
     * <li>SERVICE_DESCRIPTION: description of place, required</li>
     * <li>SERVICE_COST: cost of service provided, required</li>
     * <li>SERVICE_QUALITY: quality of service provided, required</li>
     * <li>SERVICE_PROXY: list of service proxy types for key</li>
     * <li>SERVICE_KEY: full 4 part keys with expense</li>
     * </ul>
     *
     * @param placeLocation the specified placeLocation or a full four part key to register with
     */
    protected void configureServicePlace(String placeLocation) throws IOException {
        serviceDescription = configG.findStringEntry("SERVICE_DESCRIPTION");
        if (serviceDescription == null || serviceDescription.length() == 0) {
            serviceDescription = "Description not available";
        }

        if (placeLocation == null) {
            placeLocation = this.getClass().getSimpleName();
        }

        String locationPart = placeLocation;
        if (KeyManipulator.isKeyComplete(placeLocation)) {
            keys.add(placeLocation); // save as first in list
            locationPart = KeyManipulator.getServiceLocation(placeLocation);
        } else if (placeLocation.indexOf("://") == -1) {
            EmissaryNode node = new EmissaryNode();
            locationPart = "http://" + node.getNodeName() + ":" + node.getNodePort() + "/" + placeLocation;
        }


        // Build keys the old fashioned way from parts specified in the config
        String placeName = configG.findStringEntry("PLACE_NAME");
        String serviceName = configG.findStringEntry("SERVICE_NAME");
        String serviceType = configG.findStringEntry("SERVICE_TYPE");
        int serviceCost = configG.findIntEntry("SERVICE_COST", -1);
        int serviceQuality = configG.findIntEntry("SERVICE_QUALITY", -1);

        // Bah.
        if (placeName != null && placeName.length() == 0) {
            placeName = null;
        }
        if (serviceName != null && serviceName.length() == 0) {
            serviceName = null;
        }
        if (serviceType != null && serviceType.length() == 0) {
            serviceType = null;
        }

        if (placeName != null && serviceName != null && serviceType != null && serviceCost > -1 && serviceQuality > -1) {
            // pick up the proxies(save full 4-tuple keys!)
            for (String sp : configG.findEntries("SERVICE_PROXY")) {
                DirectoryEntry de = new DirectoryEntry(sp, serviceName, serviceType, locationPart, serviceDescription, serviceCost, serviceQuality);
                keys.add(de.getFullKey());
            }
        } else {
            // May be configured the new way, but warn if there is a mixture of
            // null and non-null items using the old-fashioned way. Perhaps the
            // user just missed one of them
            int nullCount = 0;
            if (placeName == null) {
                nullCount++;
            }
            if (serviceName == null) {
                nullCount++;
            }
            if (serviceType == null) {
                nullCount++;
            }
            if (serviceCost == -1) {
                nullCount++;
            }
            if (serviceQuality == -1) {
                nullCount++;
            }

            if (nullCount > 0 && nullCount < 5) {
                throw new IOException(
                        "Missing configuration items. Please check SERVICE_NAME, SERVICE_TYPE, PLACE_NAME, SERVICE_COST, SERVICE_QUALITY");

            }
        }

        // Now build any keys the new way
        for (String k : configG.findEntries("SERVICE_KEY")) {
            if (KeyManipulator.isKeyComplete(k)) {
                keys.add(k);
            } else {
                logger.warn("SERVICE_KEY '" + k + "' is missing parts' and cannot be used");
            }
        }

        // Make sure some keys were defined one way or the other
        if (keys.size() == 0) {
            throw new IOException("NO keys were defined. Please configure at least one "
                    + "SERVICE_KEY or SERVICE_NAME/SERVICE_TYPE/SERVICE_PROXY group");
        }
    }

    /**
     * Delegate nextKey to our directory
     *
     * @param dataID key to entryMap in directory, dataType::serviceType
     * @param lastEntry place agent visited last, this is not stateless
     * @return List of DirectoryEntry with next places to go
     */
    @Override
    public List<DirectoryEntry> nextKeys(final String dataID, final IBaseDataObject payload, final DirectoryEntry lastEntry) {
        if (localDirPlace != null) {
            return (localDirPlace.nextKeys(dataID, payload, lastEntry));
        }
        logger.error("No local directory in place " + keys.get(0) + " with dir=" + dirPlace);
        return null;
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
        } catch (Exception e) {
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

        logger.debug("Entering agentProcessHeavyDuty with " + payloadList.size() + " payload items");

        List<IBaseDataObject> list = new ArrayList<IBaseDataObject>();

        // For each incoming payload object
        for (IBaseDataObject dataObject : payloadList) {
            try {
                // Process the payload item
                List<IBaseDataObject> l = agentProcessHeavyDuty(dataObject);
                if (l.size() > 0) {
                    dataObject.setNumChildren(dataObject.getNumChildren() + l.size());
                }

                // Accumulate results in a list to return
                list.addAll(l);
            } catch (Exception e) {
                logger.error("Place.process exception", e);
                dataObject.addProcessingError("agentProcessHD(" + keys.get(0) + "): " + e);
                dataObject.replaceCurrentForm(emissary.core.Form.ERROR);
            }
        }

        // Some debug output
        if (logger.isDebugEnabled()) {
            for (IBaseDataObject d : list) {
                logger.debug("Returning child " + d.shortName() + " -> " + d.getAllCurrentForms());
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
            logger.error("Place.process threw: " + e, e);
            throw e;
        }
    }

    /**
     * Rehash the payload if this is a rehashing place
     *
     * @param payload the payload to evaluate and rehash
     */
    protected void rehash(IBaseDataObject payload) {
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
        if (processHDMethodImplemented) {
            List<IBaseDataObject> children = processHeavyDuty(payload);
            if (children != null && children.size() > 0) {
                logger.error("Sprouting is no longer supported, lost " + children.size() + " children");
            }
        } else {
            throw new RuntimeException("Neither process nor processHeavyDuty appears to be implemented");
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
            throw new RuntimeException("Neither process nor processHeavyDuty appears to be implemented");
        }
    }

    /**
     * This method must be called during setup of the place to ensure that one of the two implementations is provided by the
     * declaring class.
     */
    protected void verifyProcessImplementationProvided() {

        Class<?> c = this.getClass();
        while (!c.getName().equals(ServiceProviderPlace.class.getName())) {
            for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                String mname = m.getName();
                String rname = m.getReturnType().getName();
                Class<?>[] params = m.getParameterTypes();

                if (params.length == 1 && params[0].isAssignableFrom(IBaseDataObject.class)) {
                    if (mname.equals("process") && rname.equals("void")) {
                        processMethodImplemented = true;
                    } else if (mname.equals("processHeavyDuty") && rname.equals(List.class.getName())) {
                        processHDMethodImplemented = true;
                    }
                }
            }

            if (processHDMethodImplemented || processMethodImplemented) {
                logger.debug("Found enough process implementation at level " + c.getName());
                break;
            } else {
                c = c.getSuperclass();
            }
        }

        if (!processMethodImplemented && !processHDMethodImplemented && !(this instanceof AgentsNotSupportedPlace)) {
            logger.error("It appears that neither process nor processHeavyDuty is implemented. "
                    + "If that is incorrect you can directly set one of the corresponding "
                    + "boolean flags or override verifyProcessImplementationProvided or " + "implement AgentsNotSupported to turn this message off");
        }
    }

    /**
     * Convenience method a lot of places use. Removes all items from the current form stack that this place has proxies for
     *
     * @param d a data object whose current form will be expunged of my proxies
     * @return count of how many items removed
     */
    protected int nukeMyProxies(IBaseDataObject d) {
        List<String> nukem = new ArrayList<String>();
        int sz = d.currentFormSize();
        Set<String> serviceProxies = getProxies();

        if (serviceProxies.contains("*")) {
            d.replaceCurrentForm(null); // clear it out
            return sz;
        }

        // listem
        for (int i = 0; i < sz; i++) {
            String form = d.currentFormAt(i);
            if (serviceProxies.contains(form)) {
                nukem.add(form);
            } else {
                // cardem
                WildcardEntry wc = new WildcardEntry(form);
                if (!Collections.disjoint(wc.asSet(), serviceProxies)) {
                    nukem.add(form);
                }
            }
        }

        // nukem
        for (int i = 0; i < nukem.size(); i++) {
            String f = nukem.get(i);
            int pos = d.searchCurrentForm(f);
            if (pos != -1) {
                d.deleteCurrentFormAt(pos);
            }
        }

        return nukem.size();
    }

    /**
     * Return a set of the service proxies
     *
     * @return set of string values
     */
    @Override
    public Set<String> getProxies() {
        Set<String> s = new TreeSet<String>();
        for (String k : keys) {
            s.add(KeyManipulator.getDataType(k));
        }
        s.remove(UNUSED_PROXY);
        return s;
    }

    /**
     * Get the keys that this place instance is registered with
     */
    @Override
    public Set<String> getKeys() {
        Set<String> s = new TreeSet<String>();
        for (String k : keys) {
            if (!k.startsWith(UNUSED_PROXY)) {
                s.add(k);
            }
        }
        return s;
    }

    /**
     * Return the first service proxy on the list
     *
     * @return SERVICE_PROXY value from first key on list
     */
    @Override
    public String getPrimaryProxy() {
        String s = KeyManipulator.getDataType(keys.get(0));
        if (s.equals(UNUSED_PROXY)) {
            s = "";
        }
        return s;
    }


    /**
     * Key for string form
     */
    @Override
    public String toString() {
        return keys.get(0) + "[" + keys.size() + "]";
    }

    /**
     * Fulfill IServiceProviderPlace
     */
    @Override
    public String getKey() {
        return KeyManipulator.removeExpense(keys.get(0));
    }

    /**
     * Add a service proxy to a running place. Duplicates are ignored.
     *
     * @param serviceProxy the new proxy string to add
     */
    @Override
    public void addServiceProxy(String serviceProxy) {
        // Add new one to the top key in the list
        DirectoryEntry de = new DirectoryEntry(keys.get(0));
        boolean keyAdded = false;
        if (!de.getDataType().equals(serviceProxy)) {
            // Clear out the placeholder
            if (de.getDataType().equals(UNUSED_PROXY)) {
                keys.remove(0);
            }

            de.setDataType(serviceProxy);

            if (!keys.contains(de.getFullKey())) {
                keys.add(de.getFullKey());

                // Register the new proxy in the directory
                logger.debug("Registering new key " + de.getKey());
                register(de.getFullKey());
                keyAdded = true;
            }
        }

        if (!keyAdded) {
            logger.debug("Duplicate service proxy " + serviceProxy + " ignored");
        }
    }

    /**
     * Add another key to the place
     *
     * @param key the key to add
     */
    @Override
    public void addKey(String key) {
        if (KeyManipulator.isValid(key)) {
            DirectoryEntry de = new DirectoryEntry(keys.get(0));
            if (de.getDataType().equals(UNUSED_PROXY)) {
                keys.remove(0);
            }

            logger.debug("Adding and registering new key " + key);
            keys.add(key);
            register(key);
        } else {
            logger.warn("Invalid key cannot be added: " + key);
        }
    }

    /**
     * Register a single service proxy key
     *
     * @param key the new key to register
     */
    protected void register(String key) {
        logger.debug("Registering key " + key);
        // Cannot register if we have no directory
        // If we are the directory, its no problem though
        if (dirPlace == null) {
            if (!(this instanceof emissary.directory.IDirectoryPlace)) {
                logger.debug("Directory is null: cannot register anything. Illegal configuration.");
            }
            return;
        }

        // Register place and all proxies by building up a list
        // of our interests to send to the directory
        List<String> keylist = new ArrayList<String>();
        keylist.add(key);
        registerWithDirectory(keylist);
    }

    /**
     * Register our interest in all of our serviceProxies Sends only one message to the directory to cover all service
     * proxies (a scalability issue for large systems)
     */
    protected void register() {
        logger.debug("Registering: " + this);

        // Cannot register if we have no directory
        // If we are the directory, its no problem though
        if (dirPlace == null) {
            if (!(this instanceof emissary.directory.IDirectoryPlace)) {
                logger.debug("Directory is null: cannot register anything. Illegal configuration.");
            }
            return;
        }

        // Register place and all proxies by building up a list
        // of our current interests to send to the directory
        registerWithDirectory(new ArrayList<String>(keys));
    }

    /**
     * Register keys with the directory
     *
     * @param keylist the keys to register
     */
    protected void registerWithDirectory(List<String> keylist) {
        try {
            if (localDirPlace == null) {
                // This should never happen, we require a local
                // directory on every JVM...
                logger.error("Key registration requires a DirectoryPlace in every Emissary Node");
            } else {
                logger.debug("Registering my " + keylist.size() + " keys " + keylist);
                localDirPlace.addPlaces(keylist);
            }
        } catch (Exception e) {
            logger.warn("Register ERROR for keys " + keylist, e);
        }
    }


    /**
     * Deregister keys from the directory
     *
     * @param keys the keys to register
     */
    protected void deregisterFromDirectory(List<String> keys) {
        try {
            if (localDirPlace == null && dirPlace != null) {
                // This should never happen, we require a local
                // directory on every JVM...
                logger.debug("Deregistering my " + keys.size() + " proxies " + keys + " from remote dir " + dirPlace);
                DirectoryAdapter da = new DirectoryAdapter();
                da.outboundRemovePlaces(dirPlace, keys, false);
            } else if (localDirPlace != null) {
                logger.debug("Deregistering my " + keys.size() + " proxies " + keys);
                localDirPlace.removePlaces(keys);
            }
        } catch (Exception e) {
            logger.warn("Deregister ERROR keys=" + keys, e);
        }
    }


    /**
     * Remove a service proxy from the running place. Proxy strings not found registered will be ignored Will remove all
     * keys that match the supplied proxy
     *
     * @param serviceProxy the proxy string to remove
     */
    @Override
    public void removeServiceProxy(String serviceProxy) {
        // List of keys to deregister
        List<String> keylist = new ArrayList<String>();

        // nb. no enhanced for loop due to remove
        for (Iterator<String> i = keys.iterator(); i.hasNext();) {
            String k = i.next();
            String kproxy = KeyManipulator.getDataType(k);
            if (kproxy.equals(serviceProxy)) {
                keylist.add(KeyManipulator.removeExpense(k));
                i.remove();
            }
        }
        if (keylist.size() > 0) {
            deregisterFromDirectory(keylist);
        } else {
            logger.debug("Unknown service proxy " + serviceProxy + " ignored");
        }

        // Make sure we leave something on the keys list
        if (keys.size() == 0 && keylist.size() > 0) {
            DirectoryEntry de = new DirectoryEntry(keylist.get(0));
            de.setCost(serviceCost);
            de.setQuality(serviceQuality);
            de.setDataType(UNUSED_PROXY);
            keys.add(de.getFullKey());
        }
    }

    /**
     * Remove and deregister a key
     *
     * @param key the full key (with expense) to deregister
     */
    @Override
    public void removeKey(String key) {
        String keyWithOutExpense = KeyManipulator.removeExpense(key);

        if (keys.remove(key)) {
            List<String> keylist = new ArrayList<String>();
            keylist.add(keyWithOutExpense);
            deregisterFromDirectory(keylist);

            if (keys.size() == 0) {
                DirectoryEntry de = new DirectoryEntry(key);
                de.setDataType(UNUSED_PROXY);
                keys.add(de.getFullKey());
            }
        } else {
            logger.debug("Key specified for removal not found: " + key);
        }
    }

    /**
     * Stop all threads if any. Can be overridden by, deregister from directory, and remove from the namespace. derived
     * classes if needed
     */
    @Override
    public void shutDown() {
        logger.debug("Called shutDown()");

        // List of keys without expense
        List<String> keylist = new ArrayList<String>();
        for (String k : keys) {
            keylist.add(KeyManipulator.removeExpense(k));
        }

        // Remove from directory
        deregisterFromDirectory(keylist);

        // Unbind from namespace
        for (String key : keys) {
            String bindKey = KeyManipulator.getServiceLocation(key);
            Namespace.unbind(bindKey);
            logger.debug("Unbinding place with " + bindKey);
        }
    }

    /**
     * Return the place name
     *
     * @return string key of this place
     */
    @Override
    public String getPlaceName() {
        return KeyManipulator.getServiceClassname(keys.get(0));
    }

    /**
     * Get custom resource limitation in millis if specified
     *
     * @return -2 if not specified, or long millis if specified
     */
    @Override
    public long getResourceLimitMillis() {
        return configG.findLongEntry("PLACE_RESOURCE_LIMIT_MILLIS", -2L);
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

    /**
     * Convenience to access the Main runner from a subclass If more flexibility is desired, the subclass can instantiate
     * and use the Main class directly.
     *
     * @param placeClass the class to instantiate
     * @param args from command line
     */
    public static void mainRunner(Class<?> placeClass, String[] args) {
        Main main = new Main(placeClass.getName(), args);
        main.run();
    }

    /**
     * Convenience to access the Main runner from a subclass If more flexibility is desired, the subclass can instantiate
     * and use the Main class directly.
     *
     * @param placeClass the class name to instantiate
     * @param args from command line
     */
    public static void mainRunner(String placeClass, String[] args) {
        Main main = new Main(placeClass, args);
        main.run();
    }


    @Override
    public List<String> getRunningConfig() {
        List<String> runningConfigList = new ArrayList<String>();

        if (configG != null) {
            for (ConfigEntry c : configG.getEntries()) {
                runningConfigList.add("Key: " + c.getKey() + " Value: " + c.getValue());
            }
        }
        return runningConfigList;
    }

    @Override
    public String getPlaceStats() {
        ResourceWatcher resource = null;
        Timer placeStats = null;
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
        logger.info("Dumping Running Config for " + placeName + ":");
        logger.info("===============");
        logger.info(getRunningConfig().toString());
        logger.info("===============");
    }

    @Override
    public void dumpPlaceStats() {
        logger.info("Dumping Places Stats for " + placeName + ":");
        logger.info("===============");
        logger.info(getPlaceStats());
        logger.info("===============");
    }

    /**
     * Leverage our insider knowledge of how emissary MobileAgents work in conjunction with the namespace to reach around
     * the back door of the system and get the TLD for the current payload You should only call this if
     * payload.shortName().indexOf(Family.SEP) &gt; -1
     */
    protected IBaseDataObject getTLD() {
        try {
            MobileAgent agent = getAgent();

            if (agent instanceof emissary.core.HDMobileAgent) {
                Object payload = ((emissary.core.HDMobileAgent) agent).getPayloadForTransport();
                if (payload instanceof List) {
                    List<?> familyTree = (List<?>) payload;
                    for (Object familyMember : familyTree) {
                        if (familyMember instanceof IBaseDataObject) {
                            IBaseDataObject member = (IBaseDataObject) familyMember;
                            if (member.shortName().indexOf(emissary.core.Family.SEP) == -1) {
                                return member;
                            }
                        } else {
                            logger.debug("Family members are not the right class - " + familyMember.getClass().getName());
                        }
                    }
                } else {
                    logger.debug("Agent is transporting unknown data type - " + payload.getClass().getName());
                }
            } else {
                logger.debug("System is operating with " + agent.getClass().getName() + " so there is no hope of getting the family tree");
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
