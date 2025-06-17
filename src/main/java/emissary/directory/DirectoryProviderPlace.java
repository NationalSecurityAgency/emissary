package emissary.directory;

import emissary.config.Configurator;
import emissary.core.EmissaryException;
import emissary.core.IBaseDataObject;
import emissary.core.Namespace;
import emissary.place.IServiceProviderPlace;
import emissary.server.EmissaryServer;
import emissary.server.mvc.adapters.DirectoryAdapter;

import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static emissary.core.constants.Configurations.PLACE_NAME;
import static emissary.core.constants.Configurations.SERVICE_COST;
import static emissary.core.constants.Configurations.SERVICE_DESCRIPTION;
import static emissary.core.constants.Configurations.SERVICE_KEY;
import static emissary.core.constants.Configurations.SERVICE_NAME;
import static emissary.core.constants.Configurations.SERVICE_PROXY;
import static emissary.core.constants.Configurations.SERVICE_PROXY_DENY;
import static emissary.core.constants.Configurations.SERVICE_QUALITY;
import static emissary.core.constants.Configurations.SERVICE_TYPE;

/**
 * DirectoryProviderPlace can be extended to enable registration for service discovery and access. This incorporates key
 * attributes such as cost, representing the data processing speed of the place, and quality, indicating how effectively
 * the place performs its function. Together, these attributes determine the total expense of the service and can be
 * leveraged for processing optimization.
 */
public abstract class DirectoryProviderPlace implements IServiceProviderPlace {

    /**
     * A <i><b>local</b></i> reference to the directory that this place resides in. Every JVM that contains 'places' must
     * have a local directory
     *
     * @see DirectoryPlace
     */
    @Nullable
    protected String dirPlace;
    @Nullable
    protected IDirectoryPlace localDirPlace = null;

    /**
     * set of keys for this place read from configG. Each of the values defined by
     * SERVICE_PROXY.SERCVICE_TYPE.SERVICE_NAME.PLACE_LOCATION$EXPENSE from the config file or KEY values from the config
     * file.
     */
    protected List<String> keys = new ArrayList<>();

    /**
     * List of denied places in SERVICE_PROXY_DENY
     */
    protected List<String> denyList = new ArrayList<>();

    // Items that are going to be deprecated, but here now to
    // make the transition easier, for compatibility
    @Nullable
    protected String myKey = null;
    protected int serviceCost = -1;
    protected int serviceQuality = -1;
    @Nullable
    protected String placeName = null;

    /**
     * Text description of what the place does, usually from config file
     */
    @Nullable
    protected String serviceDescription;

    /**
     * Dynamic context logger uses run-time classname as category
     */
    protected Logger logger;

    private static final String UNUSED_PROXY = "UNUSABLE-XyZZy";

    protected abstract Configurator getConfigurator();

    protected DirectoryProviderPlace() {
        super();
    }

    /**
     * Help the constructor get the place running
     *
     * @param theDir name of our directory
     */
    protected void setupPlace(@Nullable String theDir, String placeLocation, boolean register) throws IOException {

        // Customize the logger to the runtime class
        logger = LoggerFactory.getLogger(this.getClass());

        // The order of the following initialization calls
        // is touchy. NPE all over if you mess up here.

        if (register) {
            // Set ServicePlace config items
            configureServicePlace(placeLocation);
        }

        // Backwards compatibility setup items
        DirectoryEntry firstentry = new DirectoryEntry(keys.get(0));
        myKey = firstentry.getKey();
        serviceCost = firstentry.getCost();
        serviceQuality = firstentry.getQuality();
        placeName = firstentry.getServiceLocation();


        // configure directory references
        if (!(this instanceof DirectoryPlace)) {
            localizeDirectory(theDir);
            logger.debug("Our localizedDirectory is {}", dirPlace);
        } else {
            logger.debug("Not localizing directory since we are a directory");
        }
    }

    protected void setupPlacePostHook(boolean register) {

        // Bind to the namespace before registering
        // our keys. This allows incoming traffic to find
        // us as soon as they see the keys
        for (String key : keys) {
            String bindKey = KeyManipulator.getServiceLocation(key);
            logger.debug("Binding myself into the namespace as {}", bindKey);
            Namespace.bind(bindKey, this);
        }

        if (register) {
            // Register with the directory
            // This pushes all our keys out to the directory which
            // sends them on in turn to peers, &c. in the p2p network
            register();
        }
    }

    /**
     * Get a local reference to the directory.
     *
     * @param theDir key for the directory to use, if null will look up default name
     * @return true if it worked
     */
    private boolean localizeDirectory(@Nullable String theDir) {
        // Get a local (non proxy) copy of the directory if possible!
        // Looking up both if nothing is provided
        if (theDir == null) {
            try {
                localDirPlace = DirectoryPlace.lookup();
                dirPlace = localDirPlace.toString();
            } catch (EmissaryException ex) {
                if (EmissaryServer.exists() && !(this instanceof DirectoryPlace)) {
                    logger.warn("Unable to find DirectoryPlace in local namespace", ex);
                    return false;
                }
            }
        } else {
            dirPlace = theDir;
            localDirPlace = null;
            try {
                String myUrl = KeyManipulator.getServiceHostUrl(keys.get(0));
                String dirUrl = KeyManipulator.getServiceHostUrl(dirPlace);
                if (StringUtils.equals(dirUrl, myUrl)) {
                    localDirPlace = (IDirectoryPlace) Namespace.lookup(KeyManipulator.getServiceLocation(theDir));
                } else {
                    logger.debug("Not localizing directory since dirPlace {} is not equal to myUrl {}", dirPlace, myUrl);
                }
            } catch (EmissaryException ex) {
                logger.error("Exception attempting to get local reference to directory", ex);
                return false;
            }
        }
        return true;
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
    protected void configureServicePlace(@Nullable String placeLocation) throws IOException {
        Configurator configG = getConfigurator();
        serviceDescription = configG.findStringEntry(SERVICE_DESCRIPTION);
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
        } else if (!placeLocation.contains("://")) {
            EmissaryNode node = new EmissaryNode();
            locationPart = "http://" + node.getNodeName() + ":" + node.getNodePort() + "/" + placeLocation;
        }


        // Build keys the old fashioned way from parts specified in the config
        String placeName = configG.findStringEntry(PLACE_NAME);
        String serviceName = configG.findStringEntry(SERVICE_NAME);
        String serviceType = configG.findStringEntry(SERVICE_TYPE);
        int serviceCost = configG.findIntEntry(SERVICE_COST, -1);
        int serviceQuality = configG.findIntEntry(SERVICE_QUALITY, -1);

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
            for (String sp : configG.findEntries(SERVICE_PROXY)) {
                DirectoryEntry de = new DirectoryEntry(sp, serviceName, serviceType, locationPart, serviceDescription, serviceCost, serviceQuality);
                keys.add(de.getFullKey());
            }
            // pick up the denied proxies(save full 4-tuple keys!)
            for (String sp : configG.findEntries(SERVICE_PROXY_DENY)) {
                DirectoryEntry de = new DirectoryEntry(sp, serviceName, serviceType, locationPart, serviceDescription, serviceCost, serviceQuality);
                denyList.add(de.getDataType());
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
        for (String k : configG.findEntries(SERVICE_KEY)) {
            if (KeyManipulator.isKeyComplete(k)) {
                keys.add(k);
            } else {
                logger.warn("SERVICE_KEY '{}' is missing parts and cannot be used", k);
            }
        }

        // Make sure some keys were defined one way or the other
        if (keys.isEmpty()) {
            throw new IOException("NO keys were defined. Please configure at least one "
                    + "SERVICE_KEY or SERVICE_NAME/SERVICE_TYPE/SERVICE_PROXY group");
        }
    }

    /**
     * Delegate nextKey to our directory
     *
     * @param dataId key to entryMap in directory, dataType::serviceType
     * @param lastEntry place agent visited last, this is not stateless
     * @return List of DirectoryEntry with next places to go
     */
    @Override
    @Nullable
    public List<DirectoryEntry> nextKeys(final String dataId, final IBaseDataObject payload, final DirectoryEntry lastEntry) {
        if (localDirPlace != null) {
            return localDirPlace.nextKeys(dataId, payload, lastEntry);
        }
        logger.error("No local directory in place {} with dir={}", keys.get(0), dirPlace);
        return null;
    }

    /**
     * Convenience method a lot of places use. Removes all items from the current form stack that this place has proxies for
     *
     * @param d a data object whose current form will be expunged of my proxies
     * @return count of how many items removed
     */
    protected int nukeMyProxies(IBaseDataObject d) {
        List<String> nukem = new ArrayList<>();
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
        for (String f : nukem) {
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
        Set<String> s = new TreeSet<>();
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
        Set<String> s = new TreeSet<>();
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
                logger.debug("Registering new key {}", de.getKey());
                register(de.getFullKey());
                keyAdded = true;
            }
        }

        if (!keyAdded) {
            logger.debug("Duplicate service proxy {} ignored", serviceProxy);
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

            logger.debug("Adding and registering new key {}", key);
            keys.add(key);
            register(key);
        } else {
            logger.warn("Invalid key cannot be added: {}", key);
        }
    }

    /**
     * Register a single service proxy key
     *
     * @param key the new key to register
     */
    protected void register(String key) {
        logger.debug("Registering key {}", key);
        // Cannot register if we have no directory
        // If we are the directory, its no problem though
        if (dirPlace == null) {
            if (!(this instanceof IDirectoryPlace)) {
                logger.debug("Directory is null: cannot register anything. Illegal configuration.");
            }
            return;
        }

        // Register place and all proxies by building up a list
        // of our interests to send to the directory
        List<String> keylist = new ArrayList<>();
        keylist.add(key);
        registerWithDirectory(keylist);
    }

    /**
     * Register our interest in all of our serviceProxies Sends only one message to the directory to cover all service
     * proxies (a scalability issue for large systems)
     */
    protected void register() {
        logger.debug("Registering: {}", this);

        // Cannot register if we have no directory
        // If we are the directory, its no problem though
        if (dirPlace == null) {
            if (!(this instanceof IDirectoryPlace)) {
                logger.debug("Directory is null: cannot register anything. Illegal configuration.");
            }
            return;
        }

        // Register place and all proxies by building up a list
        // of our current interests to send to the directory
        registerWithDirectory(new ArrayList<>(keys));
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
                logger.debug("Registering my {} keys {}", keylist.size(), keylist);
                localDirPlace.addPlaces(keylist);
            }
        } catch (RuntimeException e) {
            logger.warn("Register ERROR for keys {}", keylist, e);
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
                logger.debug("Deregistering my {} proxies {} from remote dir {}", keys.size(), keys, dirPlace);
                DirectoryAdapter da = new DirectoryAdapter();
                da.outboundRemovePlaces(dirPlace, keys, false);
            } else if (localDirPlace != null) {
                logger.debug("Deregistering my {} proxies {}", keys.size(), keys);
                localDirPlace.removePlaces(keys);
            }
        } catch (RuntimeException e) {
            logger.warn("Deregister ERROR keys={}", keys, e);
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
        List<String> keylist = new ArrayList<>();

        // nb. no enhanced for loop due to remove
        for (Iterator<String> i = keys.iterator(); i.hasNext();) {
            String k = i.next();
            String kproxy = KeyManipulator.getDataType(k);
            if (kproxy.equals(serviceProxy)) {
                keylist.add(KeyManipulator.removeExpense(k));
                i.remove();
            }
        }
        if (!keylist.isEmpty()) {
            deregisterFromDirectory(keylist);
        } else {
            logger.debug("Unknown service proxy {} ignored", serviceProxy);
        }

        // Make sure we leave something on the keys list
        if (keys.isEmpty() && !keylist.isEmpty()) {
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
            List<String> keylist = new ArrayList<>();
            keylist.add(keyWithOutExpense);
            deregisterFromDirectory(keylist);

            if (keys.isEmpty()) {
                DirectoryEntry de = new DirectoryEntry(key);
                de.setDataType(UNUSED_PROXY);
                keys.add(de.getFullKey());
            }
        } else {
            logger.debug("Key specified for removal not found: {}", key);
        }
    }

    /**
     * Stop all threads if any. Can be overridden by, deregister from directory, and remove from the namespace. derived
     * classes if needed
     */
    @Override
    public void shutDown() {
        logger.debug("Called shutDown()");

        // Remove from directory a list of keys without expense
        deregisterFromDirectory(keys.stream().map(KeyManipulator::removeExpense).collect(Collectors.toList()));

        // Unbind from namespace
        unbindFromNamespace();
    }

    protected void unbindFromNamespace() {
        keys.stream().map(KeyManipulator::getServiceLocation).forEach(Namespace::unbind);
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

    @Override
    public boolean isDenied(String s) {
        return denyList.contains(s);
    }

}
