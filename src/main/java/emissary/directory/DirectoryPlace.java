package emissary.directory;

import emissary.config.Configurator;
import emissary.core.EmissaryException;
import emissary.core.IBaseDataObject;
import emissary.core.Namespace;
import emissary.log.MDCConstants;
import emissary.place.ServiceProviderPlace;
import emissary.server.mvc.adapters.DirectoryAdapter;

import org.slf4j.MDC;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArraySet;
import javax.annotation.Nullable;

/**
 * The DirectoryPlace class is used to store information relating to Places/Services in the Emissary Agent-Based
 * architecture. When a Place comes up it calls the method addPlace passing in all the relevant information to store in
 * the Directory. Agents query the directory by calling the method nextKeys which requires a query String search
 * pattern.
 *
 * <p>
 * We try to support some network topographic constructions by providing a set of peer directories. Peers are monitored
 * and checked automatically by HeartbeatManager and the peer network is assumed to be fully connected. Peer directories
 * are a fairly-static list of peer directories read from a config file. At least one host must be listed in order to
 * bootstrap the network.
 *
 * <p>
 * Emissary directory instances are also observable with respect to Peer activities, and Place activities. Peer
 * observers will be called with a list of current members of the peer group (including this directory) whenever the
 * peer group loses or gains members. Place observers will be called with a key that matches the pattern supplied on
 * their subscription and an indication of whether it is a register or deregister or cost change.
 *
 */
public class DirectoryPlace extends ServiceProviderPlace implements IRemoteDirectory {

    /**
     * Map of DirectoryEntryList objects by data id. This map contains the actual advertisements seen by this directory and
     * available for MobilAgent/Place use via nextKeys
     */
    protected DirectoryEntryMap entryMap = new DirectoryEntryMap();

    /** Peer directories to this one */
    protected Set<DirectoryEntry> peerDirectories = new CopyOnWriteArraySet<>();

    /**
     * Statically configured peers. Remember them even when they shut down. A subset of peerDirectories
     */
    protected Set<String> staticPeers = new HashSet<>();

    /** Heartbeat manager for checking up on remote directories */
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
    protected EmissaryNode emissaryNode;

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
     * @deprecated use {@link #DirectoryPlace(String, EmissaryNode)}
     */
    @Deprecated
    // need to pass in EmissaryNode
    public DirectoryPlace(final String placeLoc) throws IOException {
        super(placeLoc);
        this.emissaryNode = new EmissaryNode();
        setupDirectory();
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
        setupDirectory();
    }

    /**
     * Create a new empty directory as specified by the config info no parent.
     *
     * @param configInfo our config file to read
     * @param placeLoc string key to register this directory
     * @throws IOException when configuration fails
     * @deprecated use {@link #DirectoryPlace(String, String, EmissaryNode)}
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
     * @deprecated use {@link #DirectoryPlace(String, String, EmissaryNode)}
     */
    @Deprecated
    // need to pass in EmissaryNode
    public DirectoryPlace(final String configInfo, final String parentDir, final String placeLoc) throws IOException {
        super(configInfo, parentDir, placeLoc);
        this.emissaryNode = new EmissaryNode();
        setupDirectory();
    }

    /**
     * Create a new directory as specified by the config info with a parent for relaying through.
     *
     * @param configStream config info
     * @param parentDir the parent directory or null if none
     * @param placeLoc key for this place
     * @throws IOException when configuration fails
     * @deprecated use {@link #DirectoryPlace(InputStream, String, String, EmissaryNode)}
     */
    @Deprecated
    // need to pass in EmissaryNode
    // is actually/accidentally the one used by Startup/PlaceStarter.createPlace and probably shouldn't be?
    public DirectoryPlace(final InputStream configStream, final String parentDir, final String placeLoc) throws IOException {
        super(configStream, parentDir, placeLoc);
        this.emissaryNode = new EmissaryNode();
        setupDirectory();
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
        setupDirectory();
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
        setupDirectory();
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
        setupDirectory();
    }

    /**
     * Shared code for all the constructors to take advantage of in initializing directory services Configuration items read
     * here are
     * <ul>
     * <li>HEARTBEAT_DELAY_SECONDS, default is 30</li>
     * <li>HEARTBEAT_INTERVAL_SECONDS, default is 30</li>
     * <li>HEARTBEAT_FAILURE_THRESHOLD, set transient failure count, default owned by HeartbeatManager</li>
     * <li>HEARTBEAT_PERMANENT_FAILURE_THRESHOLD, set permanent failure count, default owned by HeartbeatManager</li>
     * </ul>
     */
    private void setupDirectory() {
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

        // Set up deferred stuff from ServiceProviderPlace
        // for directories only we are our own localDirPlace
        // and the key is our own key
        localDirPlace = this;
        dirPlace = myKey;

        // Start an observer manager
        this.observerManager = new DirectoryObserverManager(myKey);

        // Configure my initial rendezvous peers
        configureNetworkTopology();

        // Add an entry representing myself into the
        // local entry map. This allows observers to
        // work for this case, and allows Jetty instances
        // with just a DirectoryPlace and some bunches
        // of other non-Place code to function well and trigger
        // the peer discovery mechanism when they zone transfer
        // this entry
        final List<String> list = new ArrayList<>();
        list.add(keys.get(0));
        addPlaces(list);
        this.running = true;
    }

    /**
     * Find an optional peer config stream or file and initialize tracking of the peers found there.
     * <p>
     * We don't actually contact any of the remote directories here, so we can get the heck out of the constructor code and
     * get this place registered in the namespace quick! so other directories can find us in a timely fashion.
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

        logger.debug("Emissary node info: {}", this.emissaryNode);

        try {
            // Peer network configuration is from peer.cfg
            final Configurator peerConfig = this.emissaryNode.getPeerConfigurator();
            final Set<String> peers = peerConfig.findEntriesAsSet("RENDEZVOUS_PEER");
            this.staticPeers.addAll(peers);
            addPeerDirectories(peers, true);

            logger.debug("Configured {} rendezvous peers from {} config entries.", this.peerDirectories.size(), peers.size());
            logger.debug("This directory is {}a rendezvous peer.", (this.rdvPeer ? "" : "NOT (yet) "));
        } catch (IOException iox) {
            logger.debug("There is no peer.cfg data available");
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
     * Add a Set of peer directory to this one
     *
     * @param keys set of string key for peer directories
     */
    @Override
    public void irdAddPeerDirectories(@Nullable final Set<String> keys) {
        // Validate contract
        if ((keys == null) || keys.isEmpty()) {
            logger.warn("Ignoring irdAddPeerDirectories called with null or no keys");
            return;
        }

        // Validate remote parameters
        for (final String key : keys) {
            if (!KeyManipulator.isValid(key)) {
                logger.warn("Ignoring irdAddPeerDirectories called with {} keys, invalid key {}", keys.size(), key);
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

            if (!isStaticPeer(key)) {
                logger.warn("Unknown peer requesting to be added: {}", key);
                continue;
            }

            if (!isKnownPeer(key)) {
                this.peerDirectories.add(new DirectoryEntry(key));
                logger.debug("Added peer directory {}", key);

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
                logger.debug("We already knew about peer {}", key);
                if (!this.heartbeat.isAlive(key)) {
                    logger.debug("Forcing peer {} alive due to arriving registration", key);
                    this.heartbeat.setHealthStatus(key, HeartbeatManager.IS_ALIVE, "Received peer registration");
                    loadPeerEntries(key);
                }
            }
        }

        // Notify all observers
        if (changeMade) {
            this.observerManager.peerUpdate(new HashSet<>(this.peerDirectories));
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

        logger.debug("Doing zone transfer with peer {}", peerKey);
        // TODO See DirectoryPlace for spy example which needs to be addressed
        final DirectoryEntryMap newEntries = loadRemoteEntries(peerKey, this.entryMap);
        if ((newEntries == null) || newEntries.isEmpty()) {
            logger.debug("We got nothing back from the peer zone xfer");
            return;
        }

        // We just did this guy remove his stuff
        newEntries.removeAllOnDirectory(peerKey);

        // Remove local stuff
        newEntries.removeAllOnDirectory(myKey);

        // Make note of any possible new peer directory
        // We should only be seeing peers here
        final Set<String> newPeers = new HashSet<>();
        for (final DirectoryEntry newEntry : newEntries.allEntries()) {
            if (!isLocal(newEntry)) {
                final String possiblePeer = KeyManipulator.getDefaultDirectoryKey(newEntry.getKey());
                if (!isKnownPeer(possiblePeer) && !newPeers.contains(possiblePeer)) {
                    logger.debug("Discovered new peer {} from {} during zt with {}", possiblePeer, newEntry.getKey(), peerKey);
                    newPeers.add(possiblePeer);
                }
            }
        }
        if (!newPeers.isEmpty()) {
            logger.debug("Adding {} new peers from zt with {}", newPeers.size(), peerKey);
            addPeerDirectories(newPeers, false);
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
    private DirectoryEntryMap loadRemoteEntries(final String key, @Nullable final DirectoryEntryMap loadMap) {

        if (this.emissaryNode.isStandalone()) {
            logger.debug("Cannot load remote entries in standalone nodes");
            return null;
        }

        if (!isStaticPeer(key)) {
            logger.debug("Ignoring non-configured peer {}", key);
            return null;
        }

        // Track how long the zone transfer takes and use that
        // info along with the slop window to help determine if
        // there are stale entries and what they might be.

        final long startZone = System.currentTimeMillis();
        DirectoryEntryMap map = null;
        try {
            // Also registers as a peer with them
            // TODO should we need to get the current EmissaryClient to ensure parameters are set correctly
            final DirectoryAdapter da = new DirectoryAdapter();
            map = da.outboundRegisterPeer(key, myKey);

            if (logger.isDebugEnabled()) {
                logger.debug("Retrieved {} entries in zone transfer from {} in {} millis", map.entryCount(), key,
                        (System.currentTimeMillis() - startZone));
            }

            // No entries mean we got the remote message,
            // and they just don't have any places registered yet
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
                logger.debug("Skipping load of {} new entries from {} returning list to caller", map.entryCount(), key);
            }
        } catch (Exception ex) {
            if (logger.isDebugEnabled()) {
                logger.debug("Unable to zone transfer with {}", key, ex);
            } else {
                logger.info("Unable to zone transfer with {}", key);
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
     * but only if the cost is the same. Otherwise, we leave it so that a cost-change event can propagete from later code
     * but still avoid triggering a place removed event.
     *
     * @param loadMap the map we are removing from
     * @param key the key of the directory whose entries might be stale
     * @param checkpoint the time window to determine possible staleness
     * @param newEntries the new map arriving
     * @param performNotification only use observerManager if true
     * @return list of entries that were removed
     */
    private List<DirectoryEntry> removeStaleEntries(final DirectoryEntryMap loadMap, final String key, final long checkpoint,
            @Nullable final DirectoryEntryMap newEntries, final boolean performNotification) {

        final List<DirectoryEntry> staleEntries = new ArrayList<>();

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
                        logger.debug("Marking stale entry {}", d.getKey());
                        staleEntries.add(d);
                    } else if (matches.size() == 1) {
                        // remove from newEntries if exact dup
                        final DirectoryEntry me = matches.get(0);
                        if (me.getFullKey().equals(d.getFullKey())) {
                            logger.debug("Removing duplcate key from incoming map {}", me.getKey());
                            newEntries.removeEntry(me.getKey());
                        }
                    }
                } else {
                    // must be stale if no newEntries
                    logger.debug("Marking stale entry (no new entries){}", d.getKey());
                    staleEntries.add(d);
                }
            }
        }

        // Remove and notify
        if (!staleEntries.isEmpty()) {
            for (final DirectoryEntry stale : staleEntries) {
                logger.debug("Removing stale entry {}", stale.getKey());
                loadMap.removeEntry(stale.getKey());
            }

            if (performNotification) {
                logger.debug("Notifying observers of {} stale entry removals", staleEntries.size());
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
    private void cleanLoadNotifyEntries(final DirectoryEntryMap map, @Nullable final DirectoryEntryMap loadMap, @Nullable final String purgeKey,
            final int costBump) {
        // Remove local entries from the new map
        // We already know about our local stuff.
        if (purgeKey != null) {
            final List<DirectoryEntry> removed = map.removeAllOnDirectory(purgeKey);
            logger.debug("Clean/load removed {} entries based on {} remaining = {}", removed.size(), purgeKey, map.entryCount());
        }

        // Add remote overhead to remaining
        if (costBump > 0) {
            map.addCostToMatching("*.*.*.*", costBump);
            logger.debug("Clean/load did cost-bump of {} on {} entries", costBump, map.entryCount());
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
                logger.debug("Loading {} new entries", newCount);
                loadMap.addEntries(newEntries);
                this.observerManager.placeAdd(newEntries.allEntryKeys());
            } else {
                logger.debug("Nothing truly new from {} entries", map.entryCount());
            }

            // .. and cost change entries
            if (cceCount > 0) {
                logger.debug("Loading {} better cost entries", cceCount);
                loadMap.addEntries(costChangeEntries);
                this.observerManager.placeCostChange(costChangeEntries.allEntryKeys());
            } else {
                logger.debug("No cost change entries from {} entries", map.entryCount());
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
            logger.debug("Clean/load got a null loadMap so skipping the load for {} entries", map.entryCount());
        }
    }


    /**
     * Get a list of the keys of all the peer directories known here
     *
     * @return set of string names of peer directory keys
     */
    @Override
    public Set<String> getPeerDirectories() {
        final Set<String> l = new TreeSet<>();
        for (final DirectoryEntry sde : this.peerDirectories) {
            l.add(sde.getKey());
        }
        return l;
    }

    /**
     * Add a list of entries to the directory Entries are kept in a Hash by "datatype::serviceType" Each entry is a List of
     * sorted DirectoryEntries sorted order on cost and then quality, held in a DirectoryEntryList object
     *
     * @param entryList the new entries to add
     */
    protected void addEntries(final List<DirectoryEntry> entryList) {
        logger.debug("Adding {} new entries", entryList.size());

        // add them
        this.entryMap.addEntries(entryList);

        // notify all observers
        this.observerManager.placeAddEntries(entryList);

        final Set<String> peerSet = new HashSet<>();
        for (final DirectoryEntry newEntry : entryList) {
            // Make a note of any possible new peer directory
            if (!isLocal(newEntry)) {
                final String peerKey = KeyManipulator.getDefaultDirectoryKey(newEntry.getKey());
                if (!isKnownPeer(peerKey) && !peerSet.contains(peerKey)) {
                    logger.debug("Discovered new peer {} from  addEntries {}", peerKey, newEntry.getKey());
                    peerSet.add(peerKey);
                } else {
                    logger.debug("No new peer implications to {} from {}", peerKey, newEntry.getKey());
                }
            }
        }

        if (!peerSet.isEmpty()) {
            logger.debug("Adding {} newly discovered peer entries", peerSet.size());
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
        logger.debug("Adding single new entry {}", newEntry.getKey());
        final List<DirectoryEntry> entryList = new ArrayList<>();
        entryList.add(newEntry);
        addEntries(entryList);
    }

    /**
     * Determine if key represents a configured peer
     */
    public boolean isStaticPeer(final String key) {
        return this.staticPeers.contains(key);
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
            this.observerManager.peerUpdate(new HashSet<>(this.peerDirectories));

            // Remove the entries if any remain
            removePlaces(Collections.singletonList(KeyManipulator.getHostMatchKey(expeer.getKey())));
        }

        return expeer;
    }

    /**
     * Remove directory. Called from the heartbeat manager and from the EmissaryClient
     *
     * @param key string key of failed directory
     * @param permanent true if from a normal shutdown rather than a transient error
     * @return count of how many places were removed locally
     */
    @Override
    public int irdFailDirectory(final String key, final boolean permanent) {

        if (this.emissaryNode.isStandalone()) {
            logger.debug("Cannot fail remotes in standalone nodes");
            return 0;
        }

        // Validate remote input
        if (!KeyManipulator.isValid(key)) {
            logger.warn("Ignoring, called with invalid key {}", key);
            return 0;
        }

        if (this.shutdownInitiated) {
            logger.debug("Remote {} reported as failed, in shutdown", key);
            return 0;
        }

        // Reports of my demise are premature...
        if (isLocal(key)) {
            logger.warn(
                    "Someone reported me as failed, but I appear to be still running. Refusing to remove my own entries and propagate this filthy lie.");
            return 0;
        }

        final String dirKey = KeyManipulator.getDefaultDirectoryKey(key);
        final String hmKey = KeyManipulator.getHostMatchKey(key);
        int count = 0;

        logger.debug("irdFailDirectory {} {} permanent", key, (permanent ? "is" : "is not"));

        // Modify local entries for the failed remote directory
        // Permanent failure removes entries on failed directory.
        // Transient failure adjusts weight of entries on failed directory.
        if (permanent) {
            logger.debug("Permanent failure of remote {}", key);
            count += removePlaces(Collections.singletonList(hmKey));
        } else {
            // Change the weight of the paths for all places matching the
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

            // Remove from peer list
            if (isKnownPeer(dirKey)) {
                if (!isStaticPeer(dirKey)) {
                    logger.debug("Removing non-static peer {}", dirKey);
                    removePeer(dirKey);
                } else {
                    logger.debug("Static peer {} is deregistered but monitoring continues", dirKey);
                }
            } else {
                logger.warn("Directory {} failed but it isn't a peer??", dirKey);
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
            logger.error("Problem talking to directory {} to fail {}", directory.getKey(), failKey, ex);
        }
    }

    /**
     * Established or re-established contact with a remote directory. Check for presence on peer and initiate zone transfer
     * if needed.
     *
     * @param key the key of the directory we contacted
     */
    void contactedRemoteDirectory(final String key) {
        MDC.put(MDCConstants.SERVICE_LOCATION, KeyManipulator.getServiceLocation(myKey));
        logger.debug("Established contact with {}", key);

        if (isStaticPeer(key) && isKnownPeer(key)) {
            loadPeerEntries(key);
        } else {
            logger.warn("Contact established with {} but it is not a peer", key);
        }
        MDC.remove(MDCConstants.SERVICE_LOCATION);
    }

    /**
     * Register a place with all of its complete keys
     *
     * @param keys list of complete keys with expense
     */
    @Override
    public void addPlaces(@Nullable final List<String> keys) {
        // Validate contract
        if ((keys == null) || keys.isEmpty() || (keys.get(0) == null)) {
            logger.error("addPlaces skipping place with no keys");
            return;
        }

        // Build a list of DirectoryEntry out of these
        final List<DirectoryEntry> del = new ArrayList<>();
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
    public void irdAddPlaces(@Nullable final List<DirectoryEntry> entryList, final boolean propagating) {

        if ((entryList == null) || entryList.isEmpty()) {
            logger.debug("irdAddPlaces called with null or empty entryList!");
            return;
        }

        // Validate remote input
        for (final DirectoryEntry d : entryList) {
            if (d == null || !d.isValid()) {
                logger.warn("Ignoring irdAddPlaces called with {} DirectoryEntry objects due to invalid key in {}", entryList.size(), d);
                return;
            }
        }

        // These keys better all be from the same emissary node
        // We should check that they are and throw if not
        final String place = entryList.get(0).getKey(); // !!
        final boolean isLocal = isLocal(place);

        if (logger.isDebugEnabled()) {
            logger.debug("Starting irdAddPlaces with {} entries for {} place  - place={}, myKey={}", entryList.size(),
                    (isLocal ? "local" : "non-local"), place, myKey);
        }

        // make a defensive deep copy of the incoming list, so we
        // can safely proxy and adjust cost as needed
        final List<DirectoryEntry> entries = new ArrayList<>();
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

        logger.debug("Doing addEntries for {} new entries", entries.size());
        addEntries(entries);

        // Notify peers if entries are being added locally
        if (isLocal && !this.peerDirectories.isEmpty()) {
            // This may fail if the peer is not up yet. That is normal.
            for (final DirectoryEntry peer : this.peerDirectories) {
                if (this.heartbeat.isAlive(peer.getKey())) {
                    registerWith(peer, entries, false);
                } else if (logger.isDebugEnabled()) {
                    logger.debug("Not registering {} with peer {}, not alive right now", entries.size(), peer.getKey());
                }
            }
        }
    }

    /**
     * Private helper to register directories. This method handles multiple directory entries, each can have separate key,
     * description, cost, and quality
     *
     * @param dir the place entry to register
     * @param entryList the new entries
     * @param propagating true if propagating back down from higher level directory
     */
    protected void registerWith(final DirectoryEntry dir, final List<DirectoryEntry> entryList, final boolean propagating) {
        if (logger.isDebugEnabled()) {
            logger.debug("registerWith({},{},{})", dir.getKey(), entryList, propagating);
        }

        try {
            new DirectoryAdapter().outboundAddPlaces(dir.getKey(), entryList, propagating);
            logger.debug("registration succeeded");
        } catch (Exception ex) {
            logger.warn("DirectoryPlace.registerWith: Problem talking to directory {} to add {} entries", dir.getKey(), entryList.size(), ex);
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
        logger.debug("nextKey called with dataID='{}', and lastPlace={}", dataID, (lastPlace == null ? "null" : lastPlace.getFullKey()));

        List<DirectoryEntry> entries = nextKeys(dataID, payload, lastPlace, this.entryMap);
        if (logger.isDebugEnabled() && (entries != null) && !entries.isEmpty()) {
            logger.debug("nextKey produced {} entries from main map {}", entries.size(), entries);
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
    protected List<DirectoryEntry> nextKeys(final String dataID, final IBaseDataObject payload, @Nullable final DirectoryEntry lastPlace,
            final DirectoryEntryMap entries) {
        // Find the entry list for the type being requested
        final DirectoryEntryList currentList = getWildcardedEntryList(dataID, entries);

        // Nothing for the dataID or any wildcarded versions, we are done
        if ((currentList == null) || currentList.isEmpty()) {
            logger.debug("nextKey - nothing found here for {}", dataID);
            return Collections.emptyList();
        }

        // remove denied entries
        currentList.removeIf(de -> de.getLocalPlace() != null && de.getLocalPlace().isDenied(payload.currentForm()));

        if (currentList.isEmpty()) {
            logger.debug("nextKeys - no non-DENIED entries found here for {}", dataID);
            return Collections.emptyList();
        }
        // The list we are building for return to the caller
        final List<DirectoryEntry> keyList = new ArrayList<>();

        // The dataID this time is different from the last place
        // visited, so we can just choose from the list of the lowest
        // expense places and get on with it
        DirectoryEntry trialEntry = currentList.getEntry(0);
        if (lastPlace == null || (!lastPlace.getDataID().equals(dataID) && !trialEntry.getServiceLocation().equals(lastPlace.getServiceLocation()))) {
            logger.debug("doing first in list for {}", trialEntry);
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
                    logger.debug("nextKey skip lower cost {}", trialEntry.getFullKey());
                    continue;
                }

                // If relaying, we want to be hopping closer to the target
                if ((te == le) && (trialEntry.getExpense() >= lastPlace.getExpense())
                        && !trialEntry.getServiceHostURL().equals(lastPlace.getServiceHostURL())) {
                    logger.debug("nextKey skip equal cost {}", trialEntry.getFullKey());
                    continue;
                }

                // If equal or lower cost, no point in using the entry
                if ((trialEntry.getExpense() <= lastPlace.getExpense()) && trialEntry.getServiceHostURL().equals(lastPlace.getServiceHostURL())) {
                    logger.debug("nextKey skip lower cost not relaying {}", trialEntry.getFullKey());
                    continue;
                }

                // Entry is more expense and different service
                logger.debug("nextKey - doing next in list");
                keyList.add(currentList.pickOneOf(trialEntry.getExpense()));
                break;
            }

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
        return WildcardEntry.getWildcardedEntry(dataID, entries);
    }

    /**
     * Payloads that need to traverse the relay gateway can visit here to be forwarded on to the correct destination
     * <p>
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
            logger.debug("Doing routing on '{}'", d.shortName());
            handleRouting(d);
        }
    }

    /**
     * Handle the routing for a payload
     *
     * @param d the visiting payload
     */
    protected void handleRouting(final IBaseDataObject d) {
        // The source entry we are interested in is the one that got us
        // here. The "lastPlaceVisited" should be my own key, so we want
        // the one before that.
        DirectoryEntry sourceEntry = d.getPenultimatePlaceVisited();

        // Source entry still null?
        if (sourceEntry == null) {
            logger.debug("Payload had no source entry and no places visited. " + "Using my own directory key, which is probably wrong.");
            sourceEntry = this.getDirectoryEntry();
        }

        // Last place visited shows the key that cause the payload
        // to arrive at this place since it is logged into the history
        // just before calling this method. The dataId on this entry
        // is the entry that finally was selected for use, so we reuse
        // it here in the proper entry map.
        final DirectoryEntry thisEntry = d.getLastPlaceVisited();
        final String dataId = thisEntry.getDataID();

        if (logger.isDebugEnabled()) {
            logger.debug("Relay payload '{}' arrived with form {} coming from {} arrival entry {} arrival dataID={}", d.shortName(), d.currentForm(),
                    sourceEntry.getKey(), thisEntry.getKey(), dataId);
        }

        // Where we want to go from here
        List<DirectoryEntry> destination = nextKeys(dataId, d, sourceEntry);

        if (logger.isDebugEnabled()) {
            logger.debug("Selected {} entries {} from incoming {} and data id {} current form={}", destination.size(), destination,
                    sourceEntry.getKey(), dataId, d.currentForm());
        }

        // Replace the current form with the full key version of same
        d.popCurrentForm();
        for (final DirectoryEntry destEntry : destination) {
            d.pushCurrentForm(destEntry.getKey());
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Leaving relay gateway with current form {}", d.getAllCurrentForms());
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
     * Make directory contents entry keys available for display and transfer
     *
     * @return Set of String in the DataID format DATATYPE::SERVICETYPE
     */
    @Override
    public Set<String> getEntryKeys() {
        return new TreeSet<>(this.entryMap.keySet());
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
    public int irdRemovePlaces(@Nullable final List<String> keys, final boolean propagating) {
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
                logger.warn("Ignoring irdRemovePlaces called with {} keys due to invalid key {}", keys.size(), key);
                return 0;
            }
        }

        // Note we don't just pull the dataId from the map
        // because we anticipate the incoming keys will be
        // wildcarded as places go away rather than individual
        // service proxy keys
        final List<DirectoryEntry> matches = new ArrayList<>();
        for (final String key : keys) {
            final List<DirectoryEntry> m = this.entryMap.removeAllMatching(key);
            matches.addAll(m);
        }

        final int count = matches.size();
        if (logger.isDebugEnabled()) {
            logger.debug("Found {} entries for removal matching {} keys={}", count, keys.size(), keys);
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

        // Notify peers if local entries are being removed
        if (!this.peerDirectories.isEmpty() && (localCount > 0)) {
            // This may fail if the peer is not up. That is normal.
            for (final DirectoryEntry peer : this.peerDirectories) {
                if (this.heartbeat.isAlive(peer.getKey())) {
                    logger.debug("Deregistering {} keys from peer {}", keys.size(), peer);
                    deregisterFrom(peer, keys, false);
                }
            }
        }

        final List<String> localKeys = new ArrayList<>();
        for (final DirectoryEntry match : matches) {
            final String key = match.getKey();
            if (isLocal(key)) {
                logger.debug("Removed {} putting in local bucket", key);
                localKeys.add(key);
            }
        }

        return localKeys.size();
    }

    /**
     * Private helper to deregister entry from directories
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
     * Shutdown this place and deregister and notify any peers and observers that this directory is closing
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
                logger.debug("Sending fail msg to peer {}", peer);
                sendFailMessage(peer, myKey, true);
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

        unbindFromNamespace();
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
        logger.debug("We now have {} observers registered", this.observerManager.getObserverCount());
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
        logger.debug("We now have {} observers registered", this.observerManager.getObserverCount());
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
        if (nsval instanceof IDirectoryPlace) {
            return (IDirectoryPlace) nsval;
        }

        throw new EmissaryException("Bad directory place lookup found " + nsval);
    }

    /**
     * Get the sync status of a remote directory as seen from this directory. Note that this method only can return true for
     * things that the HeartbeatManager is tracking, i.e. peer directories of this instance.
     *
     * @param key the key of the remote directory
     * @return true if remote is reported as being up, false otherwise
     */
    @Override
    public boolean isRemoteDirectoryAvailable(final String key) {
        return (this.heartbeat != null) && this.heartbeat.isHealthy(key);
    }

    /**
     * Force a heartbeat with a particular directory represented by key does not necessarily need to be one that the
     * HeartbeatManager is already tracking and calling this method will not add it permanently to any list to be tracked.
     * This is a one time event and can be used at the caller's discretion. Note however,that if the key is not a peer of
     * this directory, a warning will be issued here when the success or failure action is taken by the heartbeat manager.
     * It can be ignored in this case. Note also, that a true return from this method merely means that the remote directory
     * responded to the heartbeat method, not that the remote directory is in sync yet with this one.
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
}
