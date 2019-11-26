package emissary.directory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArraySet;

import emissary.client.EmissaryResponse;
import emissary.config.Configurator;
import emissary.core.EmissaryException;
import emissary.core.IBaseDataObject;
import emissary.core.Namespace;
import emissary.log.MDCConstants;
import emissary.place.ServiceProviderPlace;
import emissary.server.mvc.adapters.DirectoryAdapter;
import org.apache.http.HttpStatus;
import org.slf4j.MDC;

/**
 * The DirectoryPlace class is used to store information relating to Places/Services in the Emissary Agent-Based
 * architecture. When a Place comes up it calls the method addPlace passing in all of the relevant information to store
 * in the Directory. Agents query the directory by calling the method nextKeys which requires a query String search
 * pattern.
 *
 * <p>
 * We try to support some network topographic constructions by providing a set of peer directories and a set of child
 * directories. Child directory elements are proxied through this host/place when advertised to peers. Peers are
 * monitored and checked automatically by HeartbeatManager and the peer network is assumed to be fully connected. Parent
 * directories act as relay points (in the JXTA sense of the word) for their childrenwhile peer directories are like
 * JXTA Peers. The JXTA RendezVous service is provided by the, currently fairly static, list of peer directories
 * initially read from a config file. At least one host must be listed in order to bootstrap the network.
 *
 * <p>
 * Emissary directory instances are also observable with respect to Peer activities, and Place activities. Peer
 * observers will be called with a list of current members of the peer group (including this directory) whenever the
 * peer group loses or gains members. Place observers will be called with a key that matches the pattern supplied on
 * thier subscription and an indication of whether it is a register or deregister or cost change.
 *
 * <p>
 * For resiliency of the resulting topology, more than one statically configured rendezvous host should be present in
 * each peer group. The relay directory must have visibility with all of the machines in the peer subgroup it relays
 * for. We only allow one relay point for a subgroup with this version. That should eventually change to allow multple
 * connections through the relay point if the physical network will support it.
 */
public class DirectoryPlace extends ServiceProviderPlace implements IDirectoryPlace, IRemoteDirectory {
    // My parent connects into the next higher level peer group
    DirectoryEntry theParent = null;

    /**
     * Map of DirectoryEntryList objects by data id. This map contains the actual advertisements seen by this directory and
     * available for MobilAgent/Place use via nextKeys
     */
    protected DirectoryEntryMap entryMap = new DirectoryEntryMap();

    /** Peer directories to this one */
    protected Set<DirectoryEntry> peerDirectories = new CopyOnWriteArraySet<DirectoryEntry>();

    /**
     * Statically configured peers. Remember them even when they shutdown. A subset of peerDirectories
     */
    protected Set<String> staticPeers = new HashSet<String>();

    /**
     * List of known peers in the child network, just the keys
     */
    protected Set<DirectoryEntry> childDirectories = new CopyOnWriteArraySet<DirectoryEntry>();

    /**
     * Child directories to this one, map of dataId to list of entry list. for the network being relayed
     */
    protected DirectoryEntryMap childEntries = new DirectoryEntryMap();

    /** Heartbeat manager for checking up on remote child directories */
    protected HeartbeatManager heartbeat;

    /** Manage observers */
    protected DirectoryObserverManager observerManager;

    /** True if this directory is a rendezvous peer */
    protected boolean rdvPeer = false;

    /** True is this directory is shutdown */
    protected boolean shutdownInitiated = false;

    /** True if this directory is running */
    protected boolean running = false;

    /** Emissary node configuration for network topology */
    protected EmissaryNode emissaryNode = null;

    /** True if a JSAcceptFacet should be configured in */
    protected boolean useJSAcceptFacet = true;

    /** True if an ItineraryFacet should be configured in */
    protected boolean useItineraryFacet = false;

    /**
     * Window of slop between asking for a zone and purging "stale" entries from the entry map. Since there is a window of
     * time when the remote directory might be spewing out addPlace calls while we are asking for the zone transfer we can't
     * just remove all entries once we get the zone, demarshall it and decide (finally) that it's ready to put into our map.
     * We have to allow things somewhat recent to stay around also. This time window looks back from the beginning of the
     * zone transfer request to provide some leniency.
     */
    protected long zoneSlopWindowMillis = 30000; // 30 sec

    /**
     * Create a new empty directory using this location and no parent
     *
     * @param placeLoc string key to register this directory
     * @throws IOException when configuration fails
     */
    @Deprecated
    // need to pass in EmissaryNode
    public DirectoryPlace(final String placeLoc) throws IOException {
        super(placeLoc);
        this.emissaryNode = new EmissaryNode();
        setupDirectory(null);
    }

    /**
     * Create a new empty directory using this location and no parent
     *
     * @param placeLoc string key to register this directory
     * @param node EmissaryNode for this directory place
     * 
     * @throws IOException when configuration fails
     */
    public DirectoryPlace(final String placeLoc, EmissaryNode node) throws IOException {
        super(placeLoc);
        this.emissaryNode = node;
        setupDirectory(null);
    }

    /**
     * Create a new empty directory as specified by the config info no parent.
     *
     * @param configInfo our config file to read
     * @param placeLoc string key to register this directory
     * @throws IOException when configuration fails
     */
    @Deprecated
    // need to pass in EmissaryNode
    public DirectoryPlace(final String configInfo, final String placeLoc) throws IOException {
        this(configInfo, null, placeLoc);
    }

    /**
     * Create a new child directory as specified by the config info with a parent for relaying through.
     *
     * @param configInfo our config file to read
     * @param parentDir string key of the parent directory
     * @param placeLoc string key to register this directory
     * @throws IOException when configuration fails
     */
    @Deprecated
    // need to pass in EmissaryNode
    public DirectoryPlace(final String configInfo, final String parentDir, final String placeLoc) throws IOException {
        super(configInfo, parentDir, placeLoc);
        this.emissaryNode = new EmissaryNode();
        setupDirectory(parentDir);
    }

    /**
     * Create a new directory as specified by the config info with a parent for relaying through.
     *
     * @param configStream config info
     * @param parentDir the parent directory or null if none
     * @param placeLoc key for this place
     * @throws IOException when configuration fails
     */
    @Deprecated
    // need to pass in EmissaryNode
    public DirectoryPlace(final InputStream configStream, final String parentDir, final String placeLoc) throws IOException {
        super(configStream, parentDir, placeLoc);
        this.emissaryNode = new EmissaryNode();
        setupDirectory(parentDir);
    }

    /**
     * Create a new directory as specified by the config info with a parent for relaying through.
     *
     * @param configStream config info
     * @param parentDir the parent directory or null if none
     * @param placeLoc key for this place
     * @param node node configuration details or null for defaults
     * @throws IOException when configuration fails
     */
    public DirectoryPlace(final InputStream configStream, final String parentDir, final String placeLoc, final EmissaryNode node) throws IOException {
        super(configStream, parentDir, placeLoc);
        this.emissaryNode = node;
        setupDirectory(parentDir);
    }

    /**
     * Create a new child directory as specified by the config info
     *
     * @param configInfo our config file to read
     * @param placeLoc string key to register this directory
     * @param node node configuration details or null for defaults
     * @throws IOException when configuration fails
     */
    public DirectoryPlace(final String configInfo, final String placeLoc, final EmissaryNode node) throws IOException {
        super(configInfo, placeLoc);
        this.emissaryNode = node;
        setupDirectory(null);
    }

    /**
     * Create a new directory as specified by the config info
     *
     * @param configStream config info
     * @param placeLoc key for this place
     * @param node node configuration details or null for defaults
     * @throws IOException when configuration fails
     */
    public DirectoryPlace(final InputStream configStream, final String placeLoc, final EmissaryNode node) throws IOException {
        super(configStream, placeLoc);
        this.emissaryNode = node;
        setupDirectory(null);
    }

    /**
     * Shared code for all the constructors to take advantage of in initializing directory services Configuration items read
     * here are
     * <ul>
     * <li>HEARTBEAT_DELAY_SECONDS, default is 30</li>
     * <li>HEARTBEAT_INTERVAL_SECONDS, default is 30</li>
     * <li>USE_JS_ACCEPT_FACET, default is true</li>
     * <li>USE_ITINERARY_FACET, default is false</li>
     * <li>HEARTBEAT_FAILURE_THRESHOLD, set transient failure count, default owned by HeartbeatManager</li>
     * <li>HEARTBEAT_PERMANENT_FAILURE_THRESHOLD, set permanent failure count, default owned by HeartbeatManager</li>
     * </ul>
     *
     * @param parentDir the parent directory or null if none
     */
    private void setupDirectory(final String parentDir) {
        if (this.emissaryNode.isValid() && !this.emissaryNode.isStandalone()) {
            // Start a heart beat manager with initial and interval seconds
            final int initialSeconds = configG.findIntEntry("HEARTBEAT_DELAY_SECONDS", 30);
            final int intervalSeconds = configG.findIntEntry("HEARTBEAT_INTERVAL_SECONDS", 30);

            this.heartbeat = new HeartbeatManager(myKey, initialSeconds, intervalSeconds);

            final int heartbeatFailureThreshold = configG.findIntEntry("HEARTBEAT_FAILURE_THRESHOLD", -1);
            if (heartbeatFailureThreshold > 0) {
                this.heartbeat.setFailThreshold(heartbeatFailureThreshold);
            }

            final int heartbeatPermanentFailure = configG.findIntEntry("HEARTBEAT_PERMANENT_FAILURE_THRESHOLD", -1);
            if (heartbeatPermanentFailure > 0) {
                this.heartbeat.setPermanentFailThreshold(heartbeatPermanentFailure);
            }
        }

        // Read some config items
        this.useJSAcceptFacet = configG.findBooleanEntry("USE_JS_ACCEPT_FACET", this.useJSAcceptFacet);
        this.useItineraryFacet = configG.findBooleanEntry("USE_ITINERARY_FACET", this.useItineraryFacet);

        // Set up deferred stuff from ServiceProviderPlace
        // for directories only we are our own localDirPlace
        // and the key is our own key
        localDirPlace = this;
        dirPlace = myKey;

        // Start an observer manager
        this.observerManager = new DirectoryObserverManager(myKey);

        // Add any configured itinerary faces in a facet manager
        // and add the itinerary facet to this object
        if (this.useItineraryFacet) {
            logger.debug("Turning on itinerary facet");
            addFacet(new ItineraryFacet(configG));
        }

        // Add a Javascript acceptance execution facet
        // Allows service providers to build an accept() function
        // and provide it in javascript that will restrict
        // things that would normally be routed to their service
        if (this.useJSAcceptFacet) {
            logger.debug("Turning on JSAccept facet");
            addFacet(new JSAcceptFacet());
        }

        // Configure the relay/parent
        if (parentDir != null) {
            logger.debug("Specified the parent directory " + parentDir + " in the place constructor rather than as a RELAY_HOST.");
            setParent(parentDir);
        }

        // Configure my initial rendezvous peers
        configureNetworkTopology();

        // Add an entry representing myself into the
        // local entry map. This allows observers to
        // work for this case, and allows Jetty instances
        // with just a DirectoryPlace and some bunches
        // of other non-Place code to function well and trigger
        // the peer discovery mechanism when they zone transfer
        // this entry
        final List<String> list = new ArrayList<String>();
        list.add(keys.get(0));
        addPlaces(list);
        this.running = true;
    }

    /**
     * Find an optional peer config stream or file and initialize tracking of the rendezvous and relay peers found there. We
     * don't actually contact any of the remote directories here so we can get the heck out of the constructor code and get
     * this place registered in the namespace quick! so other directories can find us in a timely fashion.
     */
    private void configureNetworkTopology() {
        if (!this.emissaryNode.isValid()) {
            if (this.emissaryNode.isStandalone()) {
                logger.debug("Running as a standalone emissary node");
            } else {
                logger.debug("Not configured as an emissary node");
            }
            return;
        }

        logger.debug("Emissary node info: " + this.emissaryNode);

        try {
            // Peer network configuration is from peer.cfg
            final Configurator peerConfig = this.emissaryNode.getPeerConfigurator();
            final Set<String> peers = peerConfig.findEntriesAsSet("RENDEZVOUS_PEER");
            this.staticPeers.addAll(peers);
            addPeerDirectories(peers, true);

            logger.debug("Configured " + this.peerDirectories.size() + " rendezvous peers from " + peers.size() + " config entries.");
            logger.debug("This directory is " + (this.rdvPeer ? "" : "NOT (yet) ") + "a rendezvous peer.");

            final String relayKey = peerConfig.findStringEntry("RELAY_HOST");
            if (relayKey != null) {
                if (isLocal(relayKey)) {
                    logger.debug("Cannot be my own parent, " + "ignoring RELAY_HOST=" + relayKey);
                } else if (this.theParent != null) {
                    if (!relayKey.equals(this.theParent.getKey())) {
                        logger.warn("Woah there! Parent already set via constructor to " + this.theParent.getKey() + " ignoring configured value "
                                + relayKey);
                    }
                } else {
                    setParent(relayKey);
                }
            }
        } catch (IOException iox) {
            logger.debug("There is no peer.cfg data available");
        }
    }

    /**
     * Attach this directory instance to the parent directory for relaying
     *
     * @param dir the string key of the parent directory
     */
    protected void setParent(final String dir) {
        if (dir != null) {
            logger.debug("This directory will relay through " + dir);
            this.theParent = new DirectoryEntry(dir);

            // Set up a heartbeat to monitor the parent
            if (!this.emissaryNode.isStandalone()) {
                this.heartbeat.addRemoteDirectory(dir, HeartbeatManager.NO_CONTACT);
            }

        }
    }

    /**
     * Used to notify parent that we are the child and do a zone tranfer Should be used when the parent is initially set and
     * whenever the parent comes back into an online status.
     */
    protected void resetParentStatus() {
        if (this.theParent != null && !this.emissaryNode.isStandalone()) {
            final String parentKey = this.theParent.getKey();
            logger.debug("(Re)registering with  myParent key=" + parentKey);
            final DirectoryAdapter da = new DirectoryAdapter();
            final EmissaryResponse response = da.outboundAddChildDirectory(parentKey, myKey);
            // TODO consider adding this to EmissaryResponse object
            boolean status = (response.getStatus() == HttpStatus.SC_OK);

            // Follow logic to irdAddChildDirectory on receiving end
            if (!status) {
                logger.warn("Unable to notify parent of relay status");
                this.heartbeat.setHealthStatus(parentKey, HeartbeatManager.NO_CONTACT, "Failed child registration");
            } else {
                // If and only if we registered as a child of the parent
                // do a zone transfer from the parent
                logger.debug("Registration with parent " + parentKey + " worked, do a zone transfer now");
                loadParentEntries();
            }
        } else {
            logger.info("The parent is null, nothing to do.");
        }
    }

    /**
     * Determine if the key is local to this directory
     *
     * @param key the key to query for
     * @return true iff the key host and port are the same (jvm locality test)
     */
    private boolean isLocal(final String key) {
        return KeyManipulator.isLocalTo(key, myKey);
    }

    /**
     * Determine if the entry is local to this directory
     *
     * @param entry the entry to query for
     * @return true iff the entry key host and port are the same (jvm locality test)
     */
    private boolean isLocal(final DirectoryEntry entry) {
        return isLocal(entry.getKey());
    }

    /**
     * Return true if the entry is via my parent
     *
     * @param entry the entry to test
     */
    private boolean isOnMyRelay(final DirectoryEntry entry) {
        return isOnMyRelay(entry.getKey());
    }

    /**
     * Return true if the entry key is via my parent
     *
     * @param key the key to test
     */
    private boolean isOnMyRelay(final String key) {
        if ((this.theParent != null) && KeyManipulator.isLocalTo(this.theParent.getKey(), key)) {
            return true;
        }
        return false;
    }

    /**
     * Return true if the entry key is one of my children
     */
    private boolean isMyChild(final DirectoryEntry entry) {
        return isMyChild(entry.getKey());
    }

    /**
     * Return true if the entry key is one of my children
     */
    private boolean isMyChild(final String key) {
        final String dkey = KeyManipulator.getDefaultDirectoryKey(key);
        for (final DirectoryEntry d : this.childDirectories) {
            // String compare the default wildcarded keys
            if (d.getKey().equals(dkey)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Add a Set of peer directory to this one
     *
     * @param keys set of string key for peer directories
     */
    @Override
    public void irdAddPeerDirectories(final Set<String> keys) {
        // Validate contract
        if ((keys == null) || keys.isEmpty()) {
            logger.warn("Ignoring irdAddPeerDirectories called with null or no keys");
            return;
        }

        // Validate remote parameters
        for (final String key : keys) {
            if (!KeyManipulator.isValid(key)) {
                logger.warn("Ignoring irdAddPeerDirectories called with " + keys.size() + " keys, invalid key " + key);
                return;
            }
        }
        addPeerDirectories(keys, false);
    }

    /**
     * Add a Set of peer directory to this one
     *
     * @param keys set of string key for peer directories
     * @param initPhase true if during place initialization
     */
    // TODO Look at DirectoryPlaceTest at the cases where spied methods are used
    public void addPeerDirectories(final Set<String> keys, final boolean initPhase) {

        if (this.shutdownInitiated) {
            logger.error("Shutdown has been initiated. Cannot add peer directories in this state.");
            return;
        }


        boolean changeMade = false;

        for (final String key : keys) {
            if (isLocal(key)) {
                // I am listed as a rendezvous for someone
                this.rdvPeer = true;
                continue;
            }

            if (this.emissaryNode.isStandalone()) {
                logger.debug("Not adding peers in standalone nodes");
                continue;
            }


            if (!isKnownPeer(key)) {
                this.peerDirectories.add(new DirectoryEntry(key));
                logger.debug("Added peer directory " + key);

                // Setup heartbeat to new peer directory
                if (initPhase) {
                    // not contacted yet
                    this.heartbeat.addRemoteDirectory(key, HeartbeatManager.NO_CONTACT);
                } else {
                    // already contacted
                    this.heartbeat.addRemoteDirectory(key, HeartbeatManager.IS_ALIVE);

                    // Initial transfer of remote directory info here
                    // It may not be up yet, so be resilient
                    loadPeerEntries(key);
                }


                changeMade = true;
            } else {
                logger.debug("We already knew about peer " + key);
                if (!this.heartbeat.isAlive(key)) {
                    logger.debug("Forcing peer " + key + " alive due to arriving registration");
                    this.heartbeat.setHealthStatus(key, HeartbeatManager.IS_ALIVE, "Received peer registration");
                    loadPeerEntries(key);
                }
            }
        }

        // Notify all observers
        if (changeMade) {
            this.observerManager.peerUpdate(new HashSet<DirectoryEntry>(this.peerDirectories));
        }
    }

    /**
     * Retrieve and load (zone transfer) all the entries from the specified peer directory. Zone transfers do not trigger
     * observables like addPlaces does
     *
     * @param peerKey the key of the peer directory
     */
    protected void loadPeerEntries(final String peerKey) {

        if (this.emissaryNode.isStandalone()) {
            logger.debug("Cannot load peer entries in standalone nodes");
            return;
        }

        logger.debug("Doing zone transfer with peer " + peerKey);
        // TODO See DirectoryPlace for spy example which needs to be addressed
        final DirectoryEntryMap newEntries = loadRemoteEntries(peerKey, this.entryMap, true);
        if ((newEntries == null) || newEntries.isEmpty()) {
            logger.debug("We got nothing back from the peer zone xfer");
            return;
        }

        // Set up proxy paths for children
        final List<DirectoryEntry> proxies = asProxy(newEntries.allEntries());

        // Send the list of proxified keys to each child
        for (final DirectoryEntry child : this.childDirectories) {
            if (this.heartbeat.isAlive(child.getKey())) {
                registerWith(child, proxies, true);
            }
        }

        // We just did this guy remove his stuff
        newEntries.removeAllOnDirectory(peerKey);

        // Remove local stuff
        newEntries.removeAllOnDirectory(myKey);

        // Make note of ay possible new peer directory
        // We should only be seeing peers here
        final Set<String> newPeers = new HashSet<String>();
        for (final DirectoryEntry newEntry : newEntries.allEntries()) {
            if (!isLocal(newEntry) && !isOnMyRelay(newEntry) && !isMyChild(newEntry)) {
                final String possiblePeer = KeyManipulator.getDefaultDirectoryKey(newEntry.getKey());
                if (!isKnownPeer(possiblePeer) && !newPeers.contains(possiblePeer)) {
                    logger.debug("Discovered new peer " + possiblePeer + " from " + newEntry.getKey() + " during zt with " + peerKey);
                    newPeers.add(possiblePeer);
                }
            }
        }
        if (!newPeers.isEmpty()) {
            logger.debug("Adding " + newPeers.size() + " new peers from zt with " + peerKey);
            addPeerDirectories(newPeers, false);
        }
    }

    /**
     * Retrieve and load (zone transfer) all the entries frm my parent Zone transfers do not trigger observables like
     * addPlaces does
     */
    protected void loadParentEntries() {
        if (this.theParent != null) {
            logger.debug("Doing zone transfer with parent " + this.theParent.getKey());
            final DirectoryEntryMap newEntries = loadRemoteEntries(this.theParent.getKey(), this.entryMap, false);
            if (logger.isDebugEnabled() && newEntries != null) {
                logger.debug("Load from parent found " + newEntries.entryCount() + " new entries");
                for (final DirectoryEntry d : newEntries.allEntries()) {
                    logger.debug(" >> " + d.getFullKey());
                }
            } else if (newEntries == null) {
                logger.debug("Got back <null> from loadRemoteEntries " + "on parent " + this.theParent.getKey());
            }
        }
    }

    /**
     * Retrieve and load (zone transfer) all the entries from the specified child directory. Notify parent, peers and other
     * child directories if necessary. Zone transfers do not trigger observables like addPlaces does
     *
     * @param childKey the key of the child directory
     */
    protected void loadChildEntries(final String childKey) {
        if (this.emissaryNode.isStandalone()) {
            logger.debug("Cannot load child entries in standalone nodes");
            return;
        }

        logger.debug("Doing zone transfer with child " + childKey);

        // Get the entries returned, dont let loadRemote add them to a map
        // It can take a while so remember the time when we start
        // loadRemoteEntries normally does this but we are using
        // a null loadmap in this instance so it must be done here
        final long startZone = System.currentTimeMillis();
        final DirectoryEntryMap newChildEntries = loadRemoteEntries(childKey, null, false);
        if (newChildEntries != null) {
            logger.debug("Got back " + newChildEntries.entryCount() + " entries from child load on " + childKey);

            // Remove and notify of any stale entries in child map and
            // entry map
            if (logger.isDebugEnabled()) {
                logger.debug("Removing possibly stale entries from " + childKey + " the zone xfer took " + (System.currentTimeMillis() - startZone)
                        + " millis");
            }
            removeChildEntries(childKey, startZone - this.zoneSlopWindowMillis, newChildEntries);

            // Remove local entries from the new map
            // We already know about our local stuff.
            logger.debug("Clean and cost-bumping " + newChildEntries.entryCount() + " new child entries");
            cleanLoadNotifyEntries(newChildEntries, null, myKey, REMOTE_COST_OVERHEAD);

            // Proxify and add them to the maps
            logger.debug("Adding remaining " + newChildEntries.entryCount() + " new child entries via addChildEntries");

            final List<DirectoryEntry> proxies = addChildEntries(newChildEntries);

            // Notify parent
            if (this.theParent != null && this.heartbeat.isAlive(this.theParent.getKey())) {
                registerWith(this.theParent, proxies, false);
            }

            // Notify all alive peers
            // This may fail if the peer is not up yet. That is normal.
            for (final DirectoryEntry peer : this.peerDirectories) {
                if (this.heartbeat.isAlive(peer.getKey())) {
                    registerWith(peer, proxies, false);
                }
            }

            // Send it to each alive child except the one they came from
            if (this.childDirectories.size() > 1) {
                for (final DirectoryEntry child : this.childDirectories) {
                    if (!KeyManipulator.isLocalTo(child.getKey(), childKey) && this.heartbeat.isAlive(child.getKey())) {
                        registerWith(child, proxies, true);
                    }
                }
            }
        } else {
            logger.debug("Got back <null> from child load on " + childKey);
        }
    }


    /**
     * Retrieve and load (zone transfer) all the entries from specified remote directory into the specified map. Remove any
     * stale entries from the destination map if one is specified and merge in the new entries. Zone transfers do not
     * trigger observables like addPlaces does
     *
     * @param key key of the remote directory to transfer from
     * @param loadMap the map to load into or null for no load. Observers are notified if loadMap is not null
     * @return the new entries
     */
    private DirectoryEntryMap loadRemoteEntries(final String key, final DirectoryEntryMap loadMap, final boolean isPeer) {

        if (this.emissaryNode.isStandalone()) {
            logger.debug("Cannot load remote entries in standalone nodes");
            return null;
        }

        // Track how long the zone transfer takes and use that
        // info along with the slop window to help determine if
        // there are stale entries and what they might be.

        final long startZone = System.currentTimeMillis();
        DirectoryEntryMap map = null;
        try {
            if (isPeer) {
                // Also registers as a peer with them
                // TODO should we need to get the current EmissaryClient to ensure parameters are set correctly
                final DirectoryAdapter da = new DirectoryAdapter();
                map = da.outboundRegisterPeer(key, myKey);
            } else {
                // Just do the transfer
                // TODO should we need to get the current EmissaryClient to ensure parameters are set correctly
                final DirectoryAdapter da = new DirectoryAdapter();
                map = da.outboundZoneTransfer(key, myKey);
            }

            if (logger.isDebugEnabled()) {
                logger.debug("Retrieved " + map.entryCount() + " entries in zone transfer from " + key + " in "
                        + (System.currentTimeMillis() - startZone) + " millis");
            }

            // No entries means we got the remote message
            // and they just dont have any places registered yet
            if (map.isEmpty()) {
                return map;
            }

            if (loadMap != null) {

                // Remove and notify of any stale entries in loadMap
                removeStaleEntries(loadMap, key, startZone - this.zoneSlopWindowMillis, map, true);

                // Remove any duplicate entries from map
                // so that they don't get double notified to observers
                // do the load and notify all observers
                cleanLoadNotifyEntries(map, loadMap, myKey, REMOTE_COST_OVERHEAD);
            } else {
                logger.debug("Skipping load of " + map.entryCount() + " new entries from " + key + " returning list to caller");
            }
        } catch (Exception ex) {
            if (logger.isDebugEnabled()) {
                logger.debug("Unable to zone transfer with " + key, ex);
            } else {
                logger.info("Unable to zone transfer with " + key);
            }
            // Failure condition. Trigger state change in heartbeat manager
            this.heartbeat.setHealthStatus(key, HeartbeatManager.NO_CONTACT, "Remote directory failed zone transfer");
        }

        return map;
    }

    /**
     * Remove stale entries from the specified map and notify any observers Nothing older than checkpoint time can be
     * considered stale and nothing that is on the incming newEntries list can be considered stale since we would just be
     * adding it back again. Duplicates (non-stale entries) are removed from the newEntries map to avoid further confusion
     * but only if the cost is the same. Otherwise we leave it so that a cost-change event can propagete from later code but
     * still avoid triggering a place removed event.
     *
     * @param loadMap the map we are removing from
     * @param key the key of the directory whose entries might be stale
     * @param checkpoint the time window to determine possible staleness
     * @param newEntries the new map arriving
     * @param performNotification only use observerManager if true
     * @return list of entries that were removed
     */
    private List<DirectoryEntry> removeStaleEntries(final DirectoryEntryMap loadMap, final String key, final long checkpoint,
            final DirectoryEntryMap newEntries, final boolean performNotification) {

        final List<DirectoryEntry> staleEntries = new ArrayList<DirectoryEntry>();

        // Nothing newer than the checkpoint time can be stale
        // Nothing that is in teh loadMap but also duplicated in
        // the newEntries map can be stale either. This helps eliminate
        // the problem of removing it just so we can add it back.
        // This uses a mark and sweep to prevent concurrent mod exceptions
        for (final DirectoryEntry d : loadMap.collectAllMatching(key)) {
            // is it old enough to be possibly stale
            if (d.getAge() < checkpoint) {
                // is it missing from the new list
                if (newEntries != null) {
                    final List<DirectoryEntry> matches = newEntries.collectAllMatching(d.getKey());
                    if (matches.isEmpty()) {
                        logger.debug("Marking stale entry " + d.getKey());
                        staleEntries.add(d);
                    } else if (matches.size() == 1) {
                        // remove from newEntries if exact dup
                        final DirectoryEntry me = matches.get(0);
                        if (me.getFullKey().equals(d.getFullKey())) {
                            logger.debug("Removing duplcate key from incoming map " + me.getKey());
                            newEntries.removeEntry(me.getKey());
                        }
                    }
                } else {
                    // must be stale if no newEntries
                    logger.debug("Marking stale entry (no new entries)" + d.getKey());
                    staleEntries.add(d);
                }
            }
        }

        // Remove and notify
        if (!staleEntries.isEmpty()) {
            for (final DirectoryEntry stale : staleEntries) {
                logger.debug("Removing stale entry " + stale.getKey());
                loadMap.removeEntry(stale.getKey());
            }

            if (performNotification) {
                logger.debug("Notifying observers of " + staleEntries.size() + " stale entry removals");
                this.observerManager.placeRemoveEntries(staleEntries);
            }
        } else {
            logger.debug("There were no stale entries to remove");
        }

        return staleEntries;
    }

    /**
     * Grok the details of a new entry list and figure out which observers need to be notified. Remove any entries that are
     * not going to end up being added anyway.
     *
     * @param map the new entries to understand
     * @param loadMap the map the entries will be loaded into
     * @param purgeKey remove any keys matching
     * @param costBump add cost to incoming
     */
    private void cleanLoadNotifyEntries(final DirectoryEntryMap map, final DirectoryEntryMap loadMap, final String purgeKey, final int costBump) {
        // Remove local entries from the new map
        // We already know about our local stuff.
        if (purgeKey != null) {
            final List<DirectoryEntry> removed = map.removeAllOnDirectory(purgeKey);
            logger.debug("Clean/load removed " + removed.size() + " entries based on " + purgeKey + " remaining = " + map.entryCount());
        }

        // Add remote overhead to remaining
        if (costBump > 0) {
            map.addCostToMatching("*.*.*.*", costBump);
            logger.debug("Clean/load did cost-bump of " + costBump + " on " + map.entryCount() + " entries");
        }

        if (loadMap != null) {
            final DirectoryEntryMap newEntries = new DirectoryEntryMap();
            final DirectoryEntryMap costChangeEntries = new DirectoryEntryMap();
            for (final DirectoryEntry e : map.allEntries()) {
                final List<DirectoryEntry> matches = loadMap.collectAllMatching(e.getKey());
                if (matches.isEmpty()) {
                    newEntries.addEntry(e);
                } else if ((matches.size() == 1) && e.isBetterThan(matches.get(0))) {
                    costChangeEntries.addEntry(e);
                }
            }

            // Merge remaining truly new entries and notify observers
            final int newCount = newEntries.entryCount();
            final int cceCount = costChangeEntries.entryCount();
            if (newCount > 0) {
                logger.debug("Loading " + newCount + " new entries");
                loadMap.addEntries(newEntries);
                this.observerManager.placeAdd(newEntries.allEntryKeys());
            } else {
                logger.debug("Nothing truly new from " + map.entryCount() + " entries");
            }

            // .. and cost change entries
            if (cceCount > 0) {
                logger.debug("Loading " + cceCount + " better cost entries");
                loadMap.addEntries(costChangeEntries);
                this.observerManager.placeCostChange(costChangeEntries.allEntryKeys());
            } else {
                logger.debug("No cost change entries from " + map.entryCount() + " entries");
            }

            // Now let the map that gets returned have just the new
            // and cost changed entries, no already known stuff
            if ((newCount + cceCount) < map.entryCount()) {
                map.clear();
                map.addEntries(costChangeEntries);
                map.addEntries(newEntries);
                map.sort();
            }
        } else {
            logger.debug("Clean/load got a null loadMap so skipping the load for " + map.entryCount() + " entries");
        }
    }


    /**
     * Get a list of the keys of all the peer directories known here
     *
     * @return set of string names of peer directory keys
     */
    @Override
    public Set<String> getPeerDirectories() {
        final Set<String> l = new TreeSet<String>();
        for (final DirectoryEntry sde : this.peerDirectories) {
            l.add(sde.getKey());
        }
        return l;
    }

    /**
     * Return key to our relay (parent) directory or null if none
     *
     * @return string key of parent directory or null if none
     */
    @Override
    public String getRelayDirectory() {
        return this.theParent != null ? this.theParent.getKey() : null;
    }

    /**
     * Return the set of keys for children directories.
     *
     * @return set of keys
     */
    @Override
    public Set<String> getRegisteredChildren() {
        final Set<String> ret = new HashSet<String>();
        for (final DirectoryEntry d : this.childDirectories) {
            ret.add(d.getKey());
        }
        return ret;
    }

    /**
     * Add a child directory to this one
     *
     * @param key string key for child directory
     */
    @Override
    public void irdAddChildDirectory(final String key) {
        if (this.emissaryNode.isStandalone()) {
            logger.debug("Cannot add child in standalone nodes");
            return;
        }

        logger.debug("irdAddChildDirectory " + key);

        // Validate remote input
        if (!KeyManipulator.isValid(key)) {
            logger.warn("Ignoring irdAddChildDirectory called with invalid key " + key);
            return;
        }

        final String dkey = KeyManipulator.getDefaultDirectoryKey(key);

        if (myKey.equals(dkey)) {
            logger.error("Cannot be own child: " + key);
            return;
        }

        if (isOnMyRelay(dkey)) {
            logger.error("Cannot parent my own relay connection " + key + "!");
            return;
        }

        logger.debug("Adding child directory " + dkey);
        this.childDirectories.add(new DirectoryEntry(dkey));

        // Setup heartbeat to new child directory
        this.heartbeat.addRemoteDirectory(dkey, HeartbeatManager.IS_ALIVE);

        // notify relay observers
        this.observerManager.childAdded(new DirectoryEntry(dkey));

        // Child may be in constructor so this might fail. But if it
        // is a reconnect, this is the only thing that will get
        // us all the entries
        loadChildEntries(dkey);
    }

    /**
     * Add entries to the child and regular entry maps This is a key we will proxy for in the entry map but need to perform
     * nextKeys type lookup in the child map to effect the relaying function.
     *
     * @param newEntries the new entries to add
     * @return the list of proxy entries that are created
     */
    protected List<DirectoryEntry> addChildEntries(final DirectoryEntryMap newEntries) {
        return addChildEntries(newEntries.allEntries());
    }

    /**
     * Add entries to the child and regular entry maps This is a key we will proxy for in the entry map but need to perform
     * nextKeys type lookup in the child map to effect the relaying function.
     *
     * @param newEntries the new entries to add (non-proxified)
     * @return the list of proxy entries that are created
     */
    protected List<DirectoryEntry> addChildEntries(final List<DirectoryEntry> newEntries) {
        final List<DirectoryEntry> proxies = asProxy(newEntries);
        this.childEntries.addEntries(newEntries);
        addEntries(proxies);
        return proxies;
    }

    /**
     * Add an entry to the child and regular entry maps This is a key we will proxy for in the entry map but need to perform
     * nextKeys type lookup in the child map to effect the relaying function.
     *
     * @param newEntry the new entry to add
     * @return the proxy entry that is created
     */
    protected DirectoryEntry addChildEntry(final DirectoryEntry newEntry) {
        logger.debug("Adding child entry " + newEntry.getKey());

        // Add it to the child entries map
        this.childEntries.addEntry(newEntry);

        // Set up the proxy and overhead the cost
        final DirectoryEntry proxyEntry = new DirectoryEntry(newEntry);
        proxyEntry.proxyFor(myKey);
        logger.debug("Created proxy entry in addChildEntry with key " + proxyEntry.getKey());

        // Add it to the regular map now that it is proxied
        addEntry(proxyEntry);
        return proxyEntry;
    }

    /**
     * Add a list of entries to the directory Entries are kept in a Hash by "datatype::serviceType" Each entry is a List of
     * sorted DirectoryEntries sorted order on cost and then quality, held in a DirectoryEntryList object
     *
     * @param entryList the new entries to add
     */
    protected void addEntries(final List<DirectoryEntry> entryList) {
        logger.debug("Adding " + entryList.size() + " new entries");

        // add them
        this.entryMap.addEntries(entryList);

        // notify all observers
        this.observerManager.placeAddEntries(entryList);

        final Set<String> peerSet = new HashSet<String>();
        for (final DirectoryEntry newEntry : entryList) {
            // Make a note of any possible new peer directory
            // We should only be seeing keys for peers and relays
            if (!isLocal(newEntry) && !isOnMyRelay(newEntry) && !isMyChild(newEntry)) {
                final String peerKey = KeyManipulator.getDefaultDirectoryKey(newEntry.getKey());
                if (!isKnownPeer(peerKey) && !peerSet.contains(peerKey)) {
                    logger.debug("Discovered new peer " + peerKey + " from  addEntries " + newEntry.getKey());
                    peerSet.add(peerKey);
                } else {
                    logger.debug("No new peer implications to " + peerKey + " from " + newEntry.getKey());
                }
            }
        }

        if (!peerSet.isEmpty()) {
            logger.debug("Adding " + peerSet.size() + " newly discovered peer entries");
            addPeerDirectories(peerSet, false);
        }
    }

    /**
     * Add an entry to the directory Entries are kept in a Hash by "datatype::serviceType" Each entry is a List of sorted
     * DirectoryEntries sorted order on cost and then quality, held in a DirectoryEntryList object
     *
     * @param newEntry the new entry to add
     */
    protected void addEntry(final DirectoryEntry newEntry) {
        logger.debug("Adding single new entry " + newEntry.getKey());
        final List<DirectoryEntry> entryList = new ArrayList<DirectoryEntry>();
        entryList.add(newEntry);
        addEntries(entryList);
    }

    /**
     * Determine if key represents a known peer
     */
    private boolean isKnownPeer(final String key) {
        for (final DirectoryEntry sde : this.peerDirectories) {
            if (KeyManipulator.isLocalTo(sde.getKey(), key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determine if key represents a known child
     */
    private boolean isKnownChild(final String key) {
        for (final DirectoryEntry sde : this.childDirectories) {
            if (KeyManipulator.isLocalTo(sde.getKey(), key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Remove a peer from the peer list
     *
     * @param key the peer to remove
     */
    private DirectoryEntry removePeer(final String key) {
        if (this.emissaryNode.isStandalone()) {
            logger.debug("Cannot remove peers from standalone nodes");
            return null;
        }

        DirectoryEntry expeer = null;

        // Find it
        for (final DirectoryEntry sde : this.peerDirectories) {
            if (KeyManipulator.isLocalTo(sde.getKey(), key)) {
                // nb. COW Set does not support iterator.remove
                expeer = sde;
                break;
            }
        }

        // Nuke it
        if (expeer != null) {
            // Remove from COW set
            this.peerDirectories.remove(expeer);

            // Remove from heartbeat manager
            this.heartbeat.removeRemoteDirectory(expeer.getKey());

            // Notify all observers, but don't give them
            // access to our own Set object
            this.observerManager.peerUpdate(new HashSet<DirectoryEntry>(this.peerDirectories));

            // Remove the entries if any remain
            removePlaces(Arrays.asList(new String[] {KeyManipulator.getHostMatchKey(expeer.getKey())}));
        }

        return expeer;
    }

    /**
     * Remove a child from the child list
     *
     * @param key the child to remove
     */
    private DirectoryEntry removeChild(final String key) {
        if (this.emissaryNode.isStandalone()) {
            logger.debug("Cannot remove child from standalone nodes");
            return null;
        }

        DirectoryEntry exchild = null;

        // Find it
        for (final DirectoryEntry sde : this.childDirectories) {
            if (KeyManipulator.isLocalTo(sde.getKey(), key)) {
                // nb. COWSet does not support iterator.remove
                exchild = sde;
                break;
            }
        }

        // Nuke it
        if (exchild != null) {
            // Remove from cow set
            this.childDirectories.remove(exchild);

            // Remove from heartbeat manager
            this.heartbeat.removeRemoteDirectory(exchild.getKey());

            // Remove child entries from relay map and
            // corresponding proxy entries from main map
            removeChildEntries(KeyManipulator.getHostMatchKey(exchild.getKey()), Long.MAX_VALUE, null);

            // Notify observers
            this.observerManager.childRemoved(exchild);
        }
        return exchild;
    }

    /**
     * Remove all entries from child list matching patterns and entries corresponding in mainmap
     *
     * @param patterns list of string key patterns to remove
     * @param checkpoint only remove entries older than this
     * @return count of entries removed from entry map
     */
    private int removeChildEntries(final List<String> patterns, final long checkpoint) {
        int count = 0;
        for (final String pat : patterns) {
            count += removeChildEntries(pat, checkpoint, null);
        }
        return count;
    }

    /**
     * Remove child entry keys matching pattern and their corresponding entries in the entryMap Entries older than
     * checkpoint that do not appear in newEntries (if not null) are stale and must be removed.
     *
     * @param pattern the key pattern to match in the child map
     * @param checkpoint only remove entries older than this
     * @param newEntries map of newly arriving entries to allow freshness determination or null if no new entries (i.e. zone
     *        transfer) triggered this action
     * @return count of entries removed from entry map
     */
    private int removeChildEntries(final String pattern, final long checkpoint, final DirectoryEntryMap newEntries) {

        // Remove child keys from relay map
        final List<DirectoryEntry> removed = removeStaleEntries(this.childEntries, pattern, checkpoint, newEntries, false);

        if (logger.isDebugEnabled()) {
            logger.debug("Removed " + removed.size() + " entries from " + "relay mapping for pattern " + pattern + " using checkpoint " + checkpoint
                    + " and " + (newEntries == null ? 0 : newEntries.size()) + " new entries");
        }

        int removeCount = 0;

        // Remove them from entryMap but must check
        // to see that nothing else is the same proxy
        for (final DirectoryEntry d : removed) {
            // Search main map by proxy key
            final String searchKey = KeyManipulator.removeExpense(KeyManipulator.makeProxyKey(d.getKey(), myKey, d.getExpense()));

            final List<DirectoryEntry> possibles = this.entryMap.collectAllMatching(searchKey);

            logger.debug("Found " + possibles.size() + " possible entries in our map to check from key " + searchKey);

            final String dataId = KeyManipulator.getDataID(d.getKey());

            // Check childEntries for the data Id in question
            final DirectoryEntryList remaining = this.childEntries.get(dataId);

            final List<String> removedProxies = new ArrayList<String>();

            for (final DirectoryEntry rm : possibles) {
                // If too cheap to be remote, it isn't remote
                if (rm.getExpense() < REMOTE_EXPENSE_OVERHEAD) {
                    logger.debug("Not removing " + rm.getKey() + " expense too low to be proxy of child " + pattern);
                } else if ((remaining != null) && !remaining.isEmpty()) {
                    // If more things proxy for this, don't remove
                    logger.debug("Not removing " + rm.getKey() + " since " + remaining.size()
                            + " entries are still left in childEntries map with dataId " + dataId);
                } else {
                    // Else go ahead and remove it and notify peers/parent
                    logger.debug("Marking proxy entry " + rm.getKey() + " for removal");
                    removedProxies.add(rm.getKey());
                }
            }

            if (!removedProxies.isEmpty()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Calling removePlaces to trigger removal and propagation on " + removedProxies.size() + " keys " + removedProxies);
                }

                removePlaces(removedProxies);
                removeCount = removedProxies.size();
            }

        }
        return removeCount;
    }

    /**
     * Remove places for a failed remote machine. Called from the heartbeat manager and from the EmissaryClient
     *
     * @param key string key of failed directory
     * @param permanent true if from a normal shutdown rather than a transient error
     * @return count of how many places were removed locally
     */
    @Override
    public int irdFailRemoteDirectory(final String key, final boolean permanent) {

        if (this.emissaryNode.isStandalone()) {
            logger.debug("Cannot fail remotes in standalone nodes");
            return 0;
        }

        // Validate remote input
        if (!KeyManipulator.isValid(key)) {
            logger.warn("Ignoring irdFailRemoteDirectory called with invalid key " + key);
            return 0;
        }

        if (this.shutdownInitiated) {
            logger.debug("Remote " + key + " reported as failed, in shutdown");
            return 0;
        }

        // Reports of my demise are premature...
        if (isLocal(key)) {
            logger.warn("Someone reported me as failed, but I appear to " + "be still running. Refusing to remove my own entries and "
                    + "propagate this filthy lie.");
            return 0;
        }

        final String dirKey = KeyManipulator.getDefaultDirectoryKey(key);
        final String hmKey = KeyManipulator.getHostMatchKey(key);
        int count = 0;

        logger.debug("irdFailRemoteDirectory " + key + (permanent ? "is" : "is not") + " permanent");

        // Modify local entries for the failed remote directory
        // Permanent failure removes entries on failed directory.
        // Transient failure adjusts weight of entries on failed directory.
        if (permanent) {
            // Permanent! Remove entries for the failed directory
            // and propagate to parent and children
            if (isMyChild(key)) {
                logger.debug("Permanent failure of child " + key);
                count += removeChildEntries(hmKey, Long.MAX_VALUE, null);
            } else {
                logger.debug("Permanent failure of remote " + key);
                count += removePlaces(Arrays.asList(new String[] {hmKey}));
            }
        } else {
            // Change the weight of the paths for all places matchng the
            // failed directory. This has the effect of causing them
            // not to be chosen as much.
            final List<DirectoryEntry> list = this.entryMap.collectAllMatching(hmKey);
            for (final DirectoryEntry e : list) {
                e.addPathWeight(-20);
            }
            this.observerManager.placeCostChangeEntries(list);
        }

        // Handle permanent removal of remote directory
        if (permanent) {
            // Notify my heartbeat manager so that a normal deregistration
            // followed by a restart will trigger a state transition even
            // if under the timer check time
            this.heartbeat.setHealthStatus(key, HeartbeatManager.NO_CONTACT, "Permanent deregistration");

            // Remove from peer list, child list or parent
            if (isKnownPeer(dirKey)) {
                if (!this.staticPeers.contains(dirKey)) {
                    logger.debug("Removing non-static peer " + dirKey);
                    removePeer(dirKey);
                } else {
                    logger.debug("Static peer " + dirKey + " is deregistered but monitoring continues");
                }
            } else if (isKnownChild(dirKey)) {
                logger.info("Child directory failed, detaching");
                removeChild(dirKey);
            } else if (this.theParent != null && KeyManipulator.isLocalTo(dirKey, this.theParent.getKey())) {
                logger.info("Parent directory failed, running in detached mode");
                this.heartbeat.setHealthStatus(this.theParent.getKey(), HeartbeatManager.NO_CONTACT, "Parent directory failed or shutdown");
            } else {
                logger.warn("Diretory " + dirKey + " failed but it isn't a peer, child,or parent??");
            }
        }

        return count;
    }

    /**
     * Send directory failure message to another directory
     *
     * @param directory the place to send the message
     * @param failKey the key of the one that failed
     * @param permanent true if this is from normal deregistrtion
     */
    protected void sendFailMessage(final DirectoryEntry directory, final String failKey, final boolean permanent) {

        if (this.emissaryNode.isStandalone()) {
            logger.debug("No remote failure messages generated in standalone node");
            return;
        }

        try {
            new DirectoryAdapter().outboundFailDirectory(directory.getKey(), failKey, permanent);
        } catch (Exception ex) {
            logger.error("DirectoryPlace.irdFailRemoteDirectory: " + "Problem talking to directory " + directory.getKey() + " to fail " + failKey,
                    ex);
        }
    }

    /**
     * Established or re-established contact with a remote directory. Check for presence on peer or relay and initiate zone
     * transfer if needed.
     *
     * @param key the key of the directory we contacted
     */
    void contactedRemoteDirectory(final String key) {
        MDC.put(MDCConstants.SERVICE_LOCATION, KeyManipulator.getServiceLocation(myKey));
        logger.debug("Established contact with " + key);

        if (isKnownPeer(key)) {
            loadPeerEntries(key);
        } else if (isOnMyRelay(key)) {
            resetParentStatus();
        } else if (isMyChild(key)) {
            loadChildEntries(key);
        } else {
            logger.warn("Contact established with " + key + " but it is not a peer, child or parent");
        }
        MDC.remove(MDCConstants.SERVICE_LOCATION);
    }

    /**
     * Register a place with all of its complete keys
     *
     * @param keys list of complete keys with expense
     */
    @Override
    public void addPlaces(final List<String> keys) {
        // Validate contract
        if ((keys == null) || keys.isEmpty() || (keys.get(0) == null)) {
            logger.error("addPlaces skipping place with no keys");
            return;
        }

        // Build a list of DirectoryEntry out of these
        final List<DirectoryEntry> del = new ArrayList<DirectoryEntry>();
        for (final String key : keys) {
            final DirectoryEntry d = new DirectoryEntry(key);
            del.add(d);
        }

        irdAddPlaces(del, false);
    }

    /**
     * Register a list of entries. This signature only meant to be called from within EmissaryClient code. Each entry will
     * have a separate key, cost and quality but should all be local to each other.
     *
     * @param entryList list of directoryEntry to add
     * @param propagating true if going back down the directory chain
     */
    @Override
    public void irdAddPlaces(final List<DirectoryEntry> entryList, final boolean propagating) {

        if ((entryList == null) || entryList.isEmpty()) {
            logger.debug("irdAddPlaces called with null or empty entryList!");
            return;
        }

        // Validate remote input
        for (final DirectoryEntry d : entryList) {
            if (d == null || !d.isValid()) {
                logger.warn("Ignoring irdAddPlaces called with " + entryList.size() + " DirectoryEntry objects due to invalid key in " + d);
                return;
            }
        }

        // These keys better all be from the same emissary node
        // We should check that they are and throw if not
        final String place = entryList.get(0).getKey(); // !!
        final boolean isLocal = isLocal(place);
        final boolean isFromChild = isMyChild(place);
        final boolean isFromParent = isOnMyRelay(place);

        if (logger.isDebugEnabled()) {
            logger.debug("Starting irdAddPlaces with " + entryList.size() + " entries for " + (isLocal ? "local" : "non-local") + " place "
                    + (isFromChild ? "from" : "not from") + " my child network, " + (isFromParent ? "from" : "not from") + " my parent relay"
                    + " - place=" + place + ", myKey=" + myKey);
        }

        // make a defensive deep copy of the incoming list so we
        // can safely proxy and adjust cost as needed
        final List<DirectoryEntry> entries = new ArrayList<DirectoryEntry>();
        for (final DirectoryEntry d : entryList) {
            entries.add(new DirectoryEntry(d, DirectoryEntry.PRESERVE_TIME));
        }

        // Let each directory add this non-local component to the cost
        // based on the place locality. This should be enough to
        // dwarf any cost variants among truly local places
        if (!isLocal) {
            for (final DirectoryEntry d : entries) {
                d.addCost(REMOTE_COST_OVERHEAD);
            }
        }

        // Get a proxy list of the bumped-cost entries
        final List<DirectoryEntry> proxies = asProxy(entries);

        // If the new place is coming from one of my children,
        // fix the key to force peer traffic through here.
        // Store the real keys in the child map and the fixed key
        // in the entry map.
        if (isFromChild) {
            // Add the entries to the relay map, the proxy entry to
            // the real map and get the proxy entry for notifying
            // all interested parties. BUT, do not recompute isLocal
            // or any of the others. The proxy key always looks local,
            // that's just the idea of it.
            logger.debug("Doing addEntries (child) for " + entries.size() + " new entries/proxies");
            addEntries(proxies);
            this.childEntries.addEntries(entries);
        } else {
            logger.debug("Doing addEntries (non-child) for " + entries.size() + " new entries");
            addEntries(entries);
        }

        // Inform parent directory if we have one and we didn't
        // get it from a higher level directory
        if (!isFromParent && this.theParent != null) {
            if (this.heartbeat.isAlive(this.theParent.getKey())) {
                logger.debug("Registering " + entries.size() + " with my parent/relay");
                registerWith(this.theParent, (isFromChild ? proxies : entries), false);
            } else {
                logger.debug("Not registering " + entries.size() + " with my parent/relay, not alive");
            }
        }

        // Notify peers if entries are being added locally or from
        // a child directory.
        // Since the parent tells all my peers, I don't need to if it
        // is coming from the relay parent.
        if ((isLocal || isFromChild) && !this.peerDirectories.isEmpty()) {
            // This may fail if the peer is not up yet. That is normal.
            for (final DirectoryEntry peer : this.peerDirectories) {
                if (this.heartbeat.isAlive(peer.getKey())) {
                    registerWith(peer, (isFromChild ? proxies : entries), false);
                } else {
                    logger.debug("Not registering " + entries.size() + " with peer " + peer.getKey() + ", not alive right now");
                }
            }
        }

        // If we have children, propagate it to them unless it came from there
        // Set up the proxy path for the children
        if (!this.childDirectories.isEmpty()) {
            // Send it to each child except the one it came from (if isFromChild)
            for (final DirectoryEntry child : this.childDirectories) {
                if (this.heartbeat.isAlive(child.getKey())) {
                    if (!KeyManipulator.isLocalTo(child.getKey(), entries.get(0).getKey())) {
                        registerWith(child, proxies, true);
                    }
                } else {
                    logger.debug("Not registering " + entries.size() + " with child " + child.getKey() + ", not alive right now");
                }

            }
        }
    }

    /**
     * Copy and proxify the supplied list of DirectoryEntry into proxies through this place
     *
     * @param list the list to copy as proxy
     * @return copy of the list proxied through here
     */
    private List<DirectoryEntry> asProxy(final List<DirectoryEntry> list) {
        logger.debug("asProxy(" + list.size() + " entries)");
        final List<DirectoryEntry> proxies = new ArrayList<DirectoryEntry>(list.size());
        for (final DirectoryEntry d : list) {
            final DirectoryEntry proxy = new DirectoryEntry(d, DirectoryEntry.PRESERVE_TIME);
            proxy.proxyFor(myKey);
            logger.debug("Created proxy entry " + proxy.getFullKey());
            proxies.add(proxy);
        }
        logger.debug("returning list of " + proxies.size() + " proxy entries");
        return proxies;
    }

    /**
     * Private helper to register with parent and children directories
     *
     * @param dir the place entry to register
     * @param entry the entry to register
     * @param propagating true if propagating back down from higher level directory
     */
    protected void registerWith(final DirectoryEntry dir, final DirectoryEntry entry, final boolean propagating) {
        if (logger.isDebugEnabled()) {
            logger.debug("registerWith(" + dir.getKey() + "," + entry.getKey() + "," + propagating + ")");
        }
        registerWith(dir, Arrays.asList(new DirectoryEntry[] {entry}), propagating);
    }

    /**
     * Private helper to register with parent and children directories. This method handles multiple directory entries, each
     * can have separate key, description, cost, and quality
     *
     * @param dir the place entry to register
     * @param entryList the new entries
     * @param propagating true if propagating back down from higher level directory
     */
    protected void registerWith(final DirectoryEntry dir, final List<DirectoryEntry> entryList, final boolean propagating) {
        if (logger.isDebugEnabled()) {
            logger.debug("registerWith(" + dir.getKey() + "," + entryList + "," + propagating + ")");
        }

        try {
            new DirectoryAdapter().outboundAddPlaces(dir.getKey(), entryList, propagating);
            logger.debug("registration succeeded");
        } catch (Exception ex) {
            logger.warn(
                    "DirectoryPlace.registerWith: " + "Problem talking to directory " + dir.getKey() + " to add " + entryList.size() + " entries",
                    ex);
        }
    }

    /**
     * Called by mobile agent to get a destination for a payload
     *
     * @param dataID key to entryMap, dataType::serviceType, e.g. UNKNOWN::ID
     * @param payload the payload being evaluated
     * @param lastPlace place agent visited last, this is not stateless
     * @return List of DirectoryEntry with next place to go or empty list if none
     */
    @Override
    public List<DirectoryEntry> nextKeys(final String dataID, final IBaseDataObject payload, final DirectoryEntry lastPlace) {
        // Normal lookup in public entry map
        logger.debug("nextKey called with dataID='" + dataID + "', and lastPlace=" + (lastPlace == null ? "null" : lastPlace.getFullKey()));

        List<DirectoryEntry> entries = nextKeys(dataID, payload, lastPlace, this.entryMap);
        if ((entries != null) && !entries.isEmpty()) {
            if (logger.isDebugEnabled()) {
                logger.debug("nextKey produced " + entries.size() + " entries from main map " + entries);
            }

            // Obviously we are asking this question because the
            // agent is local to this directory. Less obvious, if the
            // single answer is some proxy key of this very directory
            // there's no reason to have the agent leave only to visit
            // the process(IBaseDataObject) just so it can be looked up
            // in the child entry map. We can do that now and just skip
            // the extra step since we are already on the machine that
            // has visibility with the network of child machines
            // represented in the child entry map
            if ((entries.size() == 1) && entries.get(0).getServiceLocation().equals(KeyManipulator.getServiceLocation(myKey))) {
                final List<DirectoryEntry> centries = nextKeys(dataID, payload, lastPlace, this.childEntries);
                if (logger.isDebugEnabled()) {
                    logger.debug("nextKey reevaluation for proxy keys found " + (centries != null ? centries.size() : -1)
                            + " entries from child map " + centries);
                }
                // If we found something return that instead
                if ((centries != null) && !centries.isEmpty()) {
                    entries = centries;
                } else {
                    logger.error("Configuration in main map points to proxy key " + entries.get(0).getFullKey()
                            + " but child map check produced nothing.");
                }
            }
        }
        return entries;
    }

    /**
     * Get tne next logical entry based on current dataid and last place visited
     *
     * @param dataID key to entryMap, dataType::serviceType, e.g. UNKNOWN::ID
     * @param payload the payload being routed
     * @param lastPlace place agent visited last, this is not stateless
     * @param entries map of DirectoryEntry stored in this directory
     * @return List of DirectoryEntry with next place to go or empty list if none
     */
    protected List<DirectoryEntry> nextKeys(final String dataID, final IBaseDataObject payload, final DirectoryEntry lastPlace,
            final DirectoryEntryMap entries) {
        // Find the entry list for the type being requested
        final DirectoryEntryList currentList = getWildcardedEntryList(dataID, entries);

        // Nothing for the dataID or any wildcarded versions, we are done
        if ((currentList == null) || currentList.isEmpty()) {
            logger.debug("nextKey - nothing found here for " + dataID);
            return Collections.emptyList();
        }

        // The list we are building for return to the caller
        final List<DirectoryEntry> keyList = new ArrayList<DirectoryEntry>();

        // The dataID this time is different from the last place
        // visited, so we can just choose from the list of lowest
        // expense places and get on with it
        DirectoryEntry trialEntry = currentList.getEntry(0);
        if (lastPlace == null || (!lastPlace.getDataID().equals(dataID) && !trialEntry.getServiceLocation().equals(lastPlace.getServiceLocation()))) {
            logger.debug("doing first in list for " + trialEntry);
            keyList.add(currentList.pickOneOf(trialEntry.getExpense()));
        } else {
            // Trying a particular "dataType::serviceType" pair again
            for (int i = 0; i < currentList.size(); i++) {
                trialEntry = currentList.getEntry(i);

                // Skip entry if less/same expensive. Includes the obvious
                // test, plus evaluation of whether we would choose a
                // particular non-local place if it was here. If we wouldn't
                // choose it if it was here, we certainly aren't willing
                // to move to get it.
                final int te = trialEntry.getExpense() % REMOTE_EXPENSE_OVERHEAD;
                final int le = lastPlace.getExpense() % REMOTE_EXPENSE_OVERHEAD;

                // Always skip service cheaper than what we already did
                if (te < le) {
                    logger.debug("nextKey skip lower cost " + trialEntry.getFullKey());
                    continue;
                }

                // If relaying, we want to be hopping closer to the target
                if ((te == le) && (trialEntry.getExpense() >= lastPlace.getExpense())
                        && !trialEntry.getServiceHostURL().equals(lastPlace.getServiceHostURL())) {
                    logger.debug("nextKey skip equal cost " + trialEntry.getFullKey());
                    continue;
                }

                // If equal or lower cost and not relaying, no point in using the entry
                if ((trialEntry.getExpense() <= lastPlace.getExpense()) && trialEntry.getServiceHostURL().equals(lastPlace.getServiceHostURL())) {
                    logger.debug("nextKey skip lower cost not relaying " + trialEntry.getFullKey());
                    continue;
                }

                // Entry is more expense and different service
                logger.debug("nextKey - doing next in list");
                keyList.add(currentList.pickOneOf(trialEntry.getExpense()));
                break;
            }

        }

        // See if any configured Itinerary Facets want to comment on the
        // selection that we have made the old fashioned way
        try {
            final ItineraryFacet facet = ItineraryFacet.of(this);
            if (facet != null) {
                facet.thinkOn(dataID, payload, keyList, entries);
            }
        } catch (Exception ex) {
            logger.error("Cannot use itinerary facet for " + dataID, ex);
        }

        // See if any configured JSAccept Facets want to comment on
        // this selection and possibly remove itinerary steps
        try {
            final JSAcceptFacet facet = JSAcceptFacet.of(this);
            if (facet != null) {
                final int beforeCount = keyList.size();
                facet.accept(payload, keyList);
                final int afterCount = keyList.size();
                if (afterCount != beforeCount) {
                    logger.debug("JSAcceptFacet removed " + (beforeCount - afterCount) + " itinerary steps");
                }
            }
        } catch (Exception ex) {
            logger.error("Cannot use JSAccept facet for " + dataID, ex);
        }

        return keyList;
    }

    /**
     * Get the possibly wildcarded DirectoryEntryList for the dataId
     *
     * @param dataID the type of data being queried
     * @param entries the entry map to use
     * @return DirectoryEntryList or null if none
     */
    protected DirectoryEntryList getWildcardedEntryList(final String dataID, final DirectoryEntryMap entries) {
        // Ids of the form FOO-BAR(ASCII)-BAZ will be wildcarded as:
        // FOO-BAR(ASCII)-BAZ
        // FOO-BAR(*)-BAZ
        // FOO-BAR(*)-*
        // FOO-*
        // See WildcardEntry for a more thorough example
        final DirectoryEntryList found = WildcardEntry.getWildcardedEntry(dataID, entries);
        return found;
    }

    /**
     * Payloads that need to traverse the relay gateway can visit here to be forwarded on to the correct destination
     *
     * The payload will have the simple current form that caused this relay point to be selected replace with the full
     * four-tupled key of the place matching the request on the proper side of this relay point.
     *
     * @param d the payload to be inspected
     */
    @Override
    public void process(final IBaseDataObject d) {
        if (d.currentForm().equals(this.myKey)) {
            logger.debug("Probe routing has been removed");
        } else {
            logger.debug("Doing relay routing on '" + d.shortName() + "'");
            handleRelayRouting(d);
        }
    }

    /**
     * Provide a mechanism to traverse this directory that is acting as a relay point. The payload might want to traverse
     * from the child up into the current or peer network, or it might be coming from a peer and want to traverse into the
     * child network. This is determined by looking at the history to determine where the payload came from.
     *
     * @param d the visiting payload
     */
    protected void handleRelayRouting(final IBaseDataObject d) {
        // The source entry we are interested in is the one that got us
        // here. The "lastPlaceVisited" should be my own key, so we want
        // the one before that.
        DirectoryEntry sourceEntry = d.getPenultimatePlaceVisited();

        // Source entry still null?
        if (sourceEntry == null) {
            logger.debug("Payload had no source entry and no places visited. " + "Using my own directory key, which is probably wrong.");
            sourceEntry = this.getDirectoryEntry();
        }

        final boolean isChild = isMyChild(sourceEntry);

        // Last place visited shows the key that cause the payload
        // to arrive at this place since it is logged into the history
        // just before calling this method. The dataId on this entry
        // is the entry that finally was selected for use, so we reuse
        // it here in the proper entry map.
        final DirectoryEntry thisEntry = d.getLastPlaceVisited();
        final String dataId = thisEntry.getDataID();

        if (logger.isDebugEnabled()) {
            logger.debug("Relay payload '" + d.shortName() + "' arrived" + " with form " + d.currentForm() + " coming from " + sourceEntry.getKey()
                    + " arrival entry " + thisEntry.getKey() + " arrival dataID=" + dataId + ", isFromChild=" + isChild);
        }

        // Where we want to go from here
        List<DirectoryEntry> destination = null;

        // If it is from a child, perform nextKeys on the entryMap
        if (isChild) {
            // By leaving off the entryMap third param here we
            // will handle the child to child routing
            destination = nextKeys(dataId, d, sourceEntry);
        } else {
            // from a peer/parent, perform nextKeys on the childDirectory map
            destination = nextKeys(dataId, d, sourceEntry, this.childEntries);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Relay selected " + destination.size() + " entries " + destination + " from incoming " + sourceEntry.getKey()
                    + " and data id " + dataId + " current form=" + d.currentForm());
        }

        // Replace the current form with the full key version of same
        d.popCurrentForm();
        for (final DirectoryEntry destEntry : destination) {
            d.pushCurrentForm(destEntry.getKey());
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Leaving relay gateway with current form " + d.getAllCurrentForms());
        }
    }

    /**
     * Make directory contents available for debug or display and analysis
     *
     * @return List of DirectoryEntry (copies)
     */
    @Override
    public List<DirectoryEntry> getEntries() {
        final List<DirectoryEntry> entries = this.entryMap.allEntries();
        return DirectoryEntryList.deepCopy(entries, true);
    }

    /**
     * Get list of DirectoryEntry that match the key pattern
     *
     * @param pattern a key pattern to match
     * @return List of DirectoryEntry (copies)
     */
    @Override
    public List<DirectoryEntry> getMatchingEntries(final String pattern) {
        final List<DirectoryEntry> entries = this.entryMap.collectAllMatching(pattern);
        return DirectoryEntryList.deepCopy(entries, true);
    }

    /**
     * Make relay contents available for debug or display and analysis
     *
     * @return List of relay DirectoryEntries (copies)
     */
    @Override
    public List<DirectoryEntry> getRelayEntries() {
        final List<DirectoryEntry> entries = this.childEntries.allEntries();
        return DirectoryEntryList.deepCopy(entries, true);
    }

    /**
     * Get list of relay DirectoryEntry that match the key pattern
     *
     * @param pattern a key pattern to match
     * @return List of DirectoryEntry (copies)
     */
    @Override
    public List<DirectoryEntry> getMatchingRelayEntries(final String pattern) {
        final List<DirectoryEntry> entries = this.childEntries.collectAllMatching(pattern);
        return DirectoryEntryList.deepCopy(entries, true);
    }

    /**
     * Make directory contents entry keys available for display and transfer
     *
     * @return Set of String in the DataID format DATATYPE::SERVICETYPE
     */
    @Override
    public Set<String> getEntryKeys() {
        return new TreeSet<String>(this.entryMap.keySet());
    }

    /**
     * Get the requested directory entry
     *
     * @param dataId the key to the entry Map set of DirectoryEntryList objects
     * @return a DirectoryEntryList object for the key or null if none
     */
    @Override
    public DirectoryEntryList getEntryList(final String dataId) {
        final DirectoryEntryList value = this.entryMap.get(dataId);
        return new DirectoryEntryList(value, DirectoryEntryList.DEEP_COPY, DirectoryEntryList.PRESERVE_TIME);
    }

    /**
     * Get the requested directory entry list excluding all entries that actually proxy for a place local to the requester
     * From IRemoteDirectory interface
     *
     * @param dataId the key to the entry Map set of DirectoryEntryList objects
     * @param requester key of the place requesting the list
     * @return a DirectoryEntryList object for the key or null if none
     */
    @Override
    public DirectoryEntryList irdGetEntryListExcl(final String dataId, final String requester) {
        // this is a deep copy, not attached to the directory internal map
        final DirectoryEntryList d = getEntryList(dataId);
        logger.debug("EntryList for " + dataId + " starts with " + d.size());
        if (requester != null && isMyChild(requester)) {
            // NB: cannot remove through iterator on COWArrayList, cannot
            // remove through enhanced for loop
            for (int i = d.size() - 1; i >= 0; i--) {
                final DirectoryEntry e = d.get(i);
                // Construct what the child entry would be on the requester
                final DirectoryEntry possible = new DirectoryEntry(e, DirectoryEntry.PRESERVE_TIME);
                possible.setServiceLocation(KeyManipulator.getServiceLocation(requester));
                final DirectoryEntryList centries = this.childEntries.get(possible.getDataID());
                // Remove if there is only one and it is this child
                if ((centries != null) && (centries.size() == 1) && KeyManipulator.isLocalTo(centries.get(0).getKey(), requester)) {
                    logger.debug("removing " + e.getKey() + " since " + centries.get(0).getKey() + " is on the requester " + requester);
                    d.remove(i);
                }
            }
        }
        logger.debug("EntryList for " + dataId + " ends with " + d.size());
        return d;
    }

    /**
     * Deregister places removing all keys for the specified places.
     *
     * @param keys four-tuple key for the place
     * @return count of how many keys removed
     */
    @Override
    public int removePlaces(final List<String> keys) {
        return irdRemovePlaces(keys, false);
    }

    /**
     * Deregister places removing all keys for the specified places. Should only be called externally from EmissaryClient
     *
     * @see #removePlaces(List)
     * @param keys four-tuple key for the place
     * @param propagating true if going down the line
     * @return count of how many keys removed
     */
    @Override
    public int irdRemovePlaces(final List<String> keys, final boolean propagating) {
        if (this.emissaryNode.isStandalone()) {
            logger.debug("Cannot remove remote places in standalone nodes");
            return 0;
        }

        if ((keys == null) || keys.isEmpty()) {
            logger.warn("Ignoring null or empty key list for irdRemovePlaces");
            return 0;
        }

        // Validate remote input
        for (final String key : keys) {
            if (!KeyManipulator.isValid(key)) {
                logger.warn("Ignoring irdRemovePlaces called with " + keys.size() + " keys due to invalid key " + key);
                return 0;
            }
        }

        // If these entries are coming from a child
        // we need to not be in this method as
        // removeChildEntries will call here once the
        // appropriate changes are made to the child map
        // and the proxies computed.
        // So divide the keys up into child and non-child
        final List<String> childKeys = new ArrayList<String>();
        for (final String k : keys) {
            if (isMyChild(k)) {
                childKeys.add(k);
            }
        }

        // Process the child entries (will recursively come back
        // to this method at most one times
        if (!childKeys.isEmpty()) {
            logger.debug("Sending " + childKeys.size() + " child entries " + " for removal");
            removeChildEntries(childKeys, Long.MAX_VALUE);// nothing is stale by time
        }

        // Bail out of here if there are no non-child keys to
        // process. If there is a mix, then the matching
        // routine will handle below will handle just pulling
        // matches from the main entryMap
        if (childKeys.size() == keys.size()) {
            logger.debug("All the entries to be removed were from the child");
            return 0;
        }

        // Note we don't just pull the dataId from the map
        // because we anticipate the incoming keys will be
        // wildcarded as places go away rather than individual
        // service proxy keys
        final List<DirectoryEntry> matches = new ArrayList<DirectoryEntry>();
        for (final String key : keys) {
            final List<DirectoryEntry> m = this.entryMap.removeAllMatching(key);
            matches.addAll(m);
        }

        final int count = matches.size();
        if (logger.isDebugEnabled()) {
            logger.debug("Found " + count + " entries for removal matching " + keys.size() + " keys=" + keys);
        }

        // Nothing do to, nothing to propagate
        if (count == 0) {
            return 0;
        }

        int localCount = 0;

        // Count the locals and hit the observers
        for (final DirectoryEntry e : matches) {
            if (isLocal(e.getKey())) {
                localCount++;
            }
            // Notify observers of entry removal
            this.observerManager.placeRemove(e.getFullKey());
        }

        // Inform parent directory if we have one and we didn't
        // get it from a higher level directory
        if (!propagating && this.theParent != null && this.heartbeat.isAlive(this.theParent.getKey())) {
            logger.debug("Deregistering " + keys.size() + " keys from parent");
            deregisterFrom(this.theParent, keys, false);
        }

        // Notify peers if local entries are being removed
        if (!this.peerDirectories.isEmpty() && (localCount > 0)) {
            // This may fail if the peer is not up. That is normal.
            for (final DirectoryEntry peer : this.peerDirectories) {
                if (this.heartbeat.isAlive(peer.getKey())) {
                    logger.debug("Deregistering " + keys.size() + " keys from peer " + peer);
                    deregisterFrom(peer, keys, false);
                }
            }
        }

        // If some keys are non-local (i.e. peer or parent) we
        // need to remove proxies to the keys rather than actual keys
        // Divide them into two buckets, local and remote
        List<String> remoteKeys = new ArrayList<String>();
        final List<String> localKeys = new ArrayList<String>();
        for (final DirectoryEntry match : matches) {
            final String key = match.getKey();
            if (isLocal(key)) {
                logger.debug("Removed " + key + " putting in local bucket");
                localKeys.add(key);
            } else {
                logger.debug("Removed " + key + " putting in remote bucket");
                remoteKeys.add(key);
            }
        }

        // Turn the remote keys into proxies
        remoteKeys = generateDeregisterableProxies(remoteKeys);

        // If we have children, propagate it to them
        // Remove proxy from children
        for (final DirectoryEntry child : this.childDirectories) {
            if (this.heartbeat.isAlive(child.getKey())) {
                if (!localKeys.isEmpty()) {
                    logger.debug("Deregistering " + localKeys.size() + " local keys from child " + child.getKey());
                    deregisterFrom(child, localKeys, true);
                }
                if (!remoteKeys.isEmpty()) {
                    logger.debug("Deregistering " + remoteKeys.size() + " proxy keys " + "from cild " + child.getKey());
                    deregisterFrom(child, remoteKeys, true);
                }
            }
        }

        return remoteKeys.size() + localKeys.size();
    }

    /**
     * Deregister the proxies of the passed in keys if nothing remaining acts as a proxy for the same thing
     *
     * @param keys the list of keys the place can handle (SERVICE_PROXY)
     * @return list of string keys that are deregisterable
     */
    protected List<String> generateDeregisterableProxies(final List<String> keys) {
        // Generate proxy keys
        final List<String> proxyKeys = new ArrayList<String>();
        for (final String k : keys) {
            // See if anything else we have proxies for the same key
            // We need DATATYPE:*:SERVICETYPE:localhost:port/DirectoryPlace
            final String wcproxyKey = KeyManipulator.makeProxyKey(k, myKey, -1);
            final int proxyMatchCount = this.entryMap.countAllMatching(wcproxyKey);

            // This one has already been removed from entryMap, any more?
            if (proxyMatchCount == 0) {
                logger.debug("Good, nothing left for " + wcproxyKey + " so we can generate the proxy key for removal");
                proxyKeys.add(KeyManipulator.makeProxyKey(k, myKey, -1));
            } else {
                logger.debug("We stil have " + proxyMatchCount + " keys for " + wcproxyKey + " so not deregistering from child");
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Generated " + proxyKeys.size() + " removeable proxy keys " + proxyKeys);
        }

        return proxyKeys;
    }

    /**
     * Private helper to deregister entry from parent and children directories
     *
     * @param dir the remote directory to deregister from
     * @param keys the list of keys the place can handle (SERVICE_PROXY)
     * @param propagating true if propagating across levels
     */
    protected void deregisterFrom(final DirectoryEntry dir, final List<String> keys, final boolean propagating) {
        try {
            // Follow the logic to irdRemovePlaces on the remote side
            new DirectoryAdapter().outboundRemovePlaces(dir.getKey(), keys, propagating);
        } catch (Exception ex) {
            logger.error("DirectoryPlace.deregisterFrom: " + "Problem talking to directory " + dir.getKey() + " to deregister keys", ex);
        }
    }

    /**
     * Shutdown this place and deregister and notify any peers, relays and observers that this directory is closing
     */
    @Override
    public void shutDown() {
        if (this.shutdownInitiated) {
            return;
        }
        this.shutdownInitiated = true;
        this.running = false;

        logger.debug("Initiating directory shutdown");

        if (this.heartbeat != null) {
            this.heartbeat.shutDown();
        }

        if (!this.emissaryNode.isStandalone()) {

            // Notify peers of my demise
            for (final DirectoryEntry peer : this.peerDirectories) {
                logger.debug("Sending fail msg to peer " + peer);
                sendFailMessage(peer, myKey, true);
            }

            // Notify relay parent of my demise
            if (this.theParent != null) {
                logger.debug("Sending fail msg to parent " + this.theParent.getKey());
                sendFailMessage(this.theParent, myKey, true);
            }

            // Notify my children of my demise
            for (final DirectoryEntry child : this.childDirectories) {
                logger.debug("Sending fail msg to child " + child.getKey());
                sendFailMessage(child, myKey, true);
            }
        }

        // Remove all entries and notify all observers
        final List<DirectoryEntry> matches = this.entryMap.collectAllMatching("*.*.*.*");
        for (final DirectoryEntry e : matches) {
            this.observerManager.placeRemove(e.getFullKey());
        }

        // Nuke em
        this.entryMap.clear();

        // Remove peers and Notify all observers that we are leaving the group
        this.peerDirectories.clear();
        this.observerManager.peerUpdate(this.peerDirectories);

        logger.info("Done shutting down DirectoryPlace");

    }

    /**
     * Add an observer for one of the observable activities in the directory The runtime class of the observer determines
     * what is being observed
     *
     * @param observer the new DirectoryObserver to add
     */
    @Override
    public void addObserver(final DirectoryObserver observer) {
        this.observerManager.addObserver(observer);
        logger.debug("We now have " + this.observerManager.getObserverCount() + " observers registered");
    }

    /**
     * Remove an observer previously registered with this directory
     *
     * @param observer the object to remove
     * @return true if it was found on the list
     */
    @Override
    public boolean deleteObserver(final DirectoryObserver observer) {
        final boolean removed = this.observerManager.deleteObserver(observer);
        logger.debug("We now have " + this.observerManager.getObserverCount() + " observers registered");
        return removed;
    }

    /**
     * Pull the local directory from the namespace and return it. This does not work in some test scenarios where we have
     * multiple non-local directories in a single JVM.
     *
     * @return the local directory instance
     * @throws EmissaryException when directory does not exist in namespace
     */
    public static IDirectoryPlace lookup() throws EmissaryException {
        final String name = "DirectoryPlace";

        final Object nsval = Namespace.lookup(name);
        if (nsval != null && nsval instanceof IDirectoryPlace) {
            return (IDirectoryPlace) nsval;
        }

        throw new EmissaryException("Bad directory place lookup found " + nsval);
    }

    /**
     * Get the sync status of a remote directory as seen from this directory. Note that this method only can return true for
     * things that the HeartbeatManager is tracking, i.e. parent, child or peer directories of this instance.
     *
     * @param key the key of the remote directory
     * @return true if remote is reported as being up, false otherwise
     */
    @Override
    public boolean isRemoteDirectoryAvailable(final String key) {
        return (this.heartbeat != null) && this.heartbeat.isHealthy(key);
    }

    /**
     * Force a heartbeat with a particular directory Directory represented by key does not necessarily need to be one that
     * the HeartbeatManager is already tracking and calling this method will not add it permanently to any list to be
     * tracked. This is a one time event and can be used at the callers discretion. Note however,that if the key is not a
     * peer, child or parent of this directory, a warning will be issued here when the success or failure action is taken by
     * the heartbeat manager. It can be ignored in this case. Note also, that a true return from this method merely means
     * that the remote directory responded to the heartbeat method, not that the remote directory is in sync yet with this
     * one.
     *
     * @see #isRemoteDirectoryAvailable(String)
     * @param key the key of the remote directory
     * @return true if remote is up, false otherwise
     */
    @Override
    public boolean heartbeatRemoteDirectory(final String key) {
        return (this.heartbeat != null) && this.heartbeat.heartbeat(key);
    }

    /**
     * Indicate if directory is running
     *
     * @return true if running
     */
    @Override
    public boolean isRunning() {
        return this.running;
    }

    /**
     * Indicate whether shutdown has been initiated
     *
     * @return true if shutdown initiated
     */
    @Override
    public boolean isShutdownInitiated() {
        return this.shutdownInitiated;
    }

    /**
     * Add a Routing acceptor function for the specified directory entry
     *
     * @param de the directory entry to associate this function with
     * @param script the javascript accept function
     * @param isDefault is a default rule for SERVICE_NAME
     * @throws Exception if script does not compile
     * @return true if the function is added
     */
    @Override
    public boolean addRoutingFunction(final DirectoryEntry de, final String script, final boolean isDefault) throws Exception {
        boolean added = false;
        try {
            final JSAcceptFacet facet = JSAcceptFacet.of(this);
            if (facet != null) {
                added = facet.add(de, script, isDefault);
            }
        } catch (Exception ignore) {
            // empty catch block
        }

        return added;
    }

    /**
     * Remove a routing acceptor function for the specified directory entry. If it had been read in from the config subsytem
     * or the jar resources and it is still there, removing it will only be temporary as it will be reread from the same
     * source the next time it is needed. In order to remove the effect of such a function you must add or replace the
     * function in the configuration subsystem with a function definition that always returns true.
     *
     * @param de the directory entry's function to remove
     * @param isDefault if true remove the default routing rule
     * @return true if a function was removed
     */
    @Override
    public boolean removeRoutingFunction(final DirectoryEntry de, final boolean isDefault) {
        boolean removed = false;
        try {
            final JSAcceptFacet facet = JSAcceptFacet.of(this);
            if (facet != null) {
                removed = isDefault ? facet.removeDefault(de) : facet.remove(de);
            }
        } catch (Exception ignore) {
            // empty catch block
        }

        return removed;
    }
}
