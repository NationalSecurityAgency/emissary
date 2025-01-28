package emissary.admin;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.core.EmissaryException;
import emissary.core.EmissaryRuntimeException;
import emissary.core.Namespace;
import emissary.directory.DirectoryEntry;
import emissary.directory.DirectoryPlace;
import emissary.directory.EmissaryNode;
import emissary.directory.IDirectoryPlace;
import emissary.directory.KeyManipulator;
import emissary.pickup.PickUpPlace;
import emissary.place.CoordinationPlace;
import emissary.place.IServiceProviderPlace;
import emissary.server.EmissaryServer;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;

public class Startup {

    public static final int DIRECTORYSTART = 0;
    public static final int DIRECTORYADD = 1;
    public static final int DIRECTORYDELETE = 2;
    public static final String ACTIONADD = "-add";
    public static final String ACTIONDELETE = "-delete";
    public static final String ACTIONSTART = "-start";

    private static final String PARALLEL_PLACE_STARTUP_CONFIG = "PARALLEL_PLACE_STARTUP";
    @SuppressWarnings("NonFinalStaticField")
    static int directoryAction = DIRECTORYADD;

    // If we are an emissary node these will be present
    private final EmissaryNode node;

    // Our logger
    private static final Logger logger = LoggerFactory.getLogger(Startup.class);

    // The startup config object
    @Nullable
    protected Configurator hostsConfig = null;

    // Successfully started directories
    protected final Map<String, String> localDirectories = new ConcurrentHashMap<>();

    // Failed directories
    protected final Map<String, String> failedLocalDirectories = new ConcurrentHashMap<>();

    protected final Set<String> failedPlaces = ConcurrentHashMap.newKeySet();

    // Collection of the places as they finish coming up
    protected static final Map<String, String> places = new ConcurrentHashMap<>();

    // Collection of places that are being started
    protected final Set<String> placesToStart = ConcurrentHashMap.newKeySet();

    // sorted lists of the place types, grouped by hostname
    protected final Map<String, Set<String>> placeLists = new ConcurrentHashMap<>();
    protected final Map<String, Set<String>> pickupLists = new ConcurrentHashMap<>();

    // sets to keep track of possible invisible place startup
    protected static final Set<String> activeDirPlaces = new LinkedHashSet<>();
    protected static final Set<String> placeAlreadyStarted = new LinkedHashSet<>();

    // invisible place startups occurred in strict mode
    @SuppressWarnings("NonFinalStaticField")
    protected static boolean invisPlacesStartedInStrictMode = false;

    /**
     * n return the full DNS name and port without the protocol part
     */
    public static String placeHost(final String key) {
        return KeyManipulator.getServiceHost(key);
    }

    /**
     * return the type of place specified Key manipulator does not work on this, though it seems to if the key has dots in
     * the hostname like many do.
     */
    public static String placeName(final String key) {
        final int pos = key.lastIndexOf("/");
        if (pos != -1) {
            return key.substring(pos + 1);
        }
        return key;
    }

    /**
     * Set the action based on the command line argument
     */
    public static int setAction(final String optarg) {
        if (ACTIONADD.equalsIgnoreCase(optarg)) {
            return DIRECTORYADD;
        }

        if (ACTIONDELETE.equalsIgnoreCase(optarg)) {
            return DIRECTORYDELETE;
        }

        // default
        return DIRECTORYSTART;
    }

    private static String makeConfig(final String path, final String file) {
        if (file.startsWith("/") && new File(file).exists()) {
            return file;
        }
        return ConfigUtil.getConfigFile(path, file);
    }

    /**
     * The main entry point
     */
    @SuppressWarnings("SystemOut")
    public static void main(final String[] args) throws IOException, EmissaryException {


        //
        // Evaluate arguments to the static main
        //
        // Need config path and startup config file on command line
        if (args.length < 1 || args.length > 3) {
            System.err.println("Usage: java emissary.admin.Startup " + "[-start|-add|-delete] [config_path] config_file");
            return;
        }

        final String startupConfigFile;
        if (args.length == 1) {
            directoryAction = setAction(ACTIONSTART);
            if (args[0].startsWith("/") || args[0].toUpperCase(Locale.getDefault()).startsWith("HTTP")) {
                startupConfigFile = args[0];
            } else {
                startupConfigFile = ConfigUtil.getConfigFile(args[0]);
            }
        } else if (args.length == 2) {
            directoryAction = setAction(ACTIONSTART);
            startupConfigFile = makeConfig(args[0], args[1]);
        } else {
            directoryAction = setAction(args[0]);
            startupConfigFile = makeConfig(args[1], args[2]);
        }

        final Startup start = new Startup(startupConfigFile, new EmissaryNode());
        start.start();

        logger.info("The system is up and running fine. All ahead Warp-7.");
    }


    /**
     * Start the system
     */
    public void start() throws EmissaryException {
        final boolean bootStatus = bootstrap();

        if (!bootStatus) {
            throw new EmissaryException("Unable to bootstrap the system");
        }

        // bootstrap now only starts the processing places (this allows
        // derived classes to hold off starting the pickup places until
        // they've completed their own set-up). So we have to startup
        // the pickup places here.
        startPickUpPlaces();

        if (!verifyNoInvisiblePlacesStarted() && node.isStrictStartupMode()) {
            invisPlacesStartedInStrictMode = true;
        }
    }


    // $AUTO: Constructors.

    /**
     * Class constructor loads the config file
     */
    public Startup(final String startupConfigFile, EmissaryNode node) throws IOException {
        // Read in startup config file specifying place/host setup
        this(new ServiceConfigGuide(startupConfigFile), node);
    }

    public Startup(final InputStream startupConfigStream, EmissaryNode node) throws IOException {
        this(new ServiceConfigGuide(startupConfigStream), node);
    }

    public Startup(final Configurator config, EmissaryNode node) {
        this.hostsConfig = config;
        this.node = node;
    }

    public boolean bootstrap() {

        //
        // Setup the Local Directories in a hashtable
        //
        final boolean status = localDirectorySetup(this.localDirectories);

        if (!status) {
            logger.warn("Startup: local directory setup failed.");
            return false;
        }

        //
        // Set up the rest of the Places except no pickups
        //
        sortPlaces(this.hostsConfig.findEntries("PLACE"));

        logger.info("Ready to start {} place(s) and {} PickUp place(s).", hashListSize(this.placeLists), hashListSize(this.pickupLists));

        logger.info("Processing non-pickup places...");
        startMapOfPlaces(this.placeLists);

        //
        // Wait for all places to get started and registered
        //
        this.stopAndWaitForPlaceCreation();

        logger.debug("Done with bootstrap phase");
        return true;
    }

    /**
     * Start all the pickup places and wait for them to finish
     */
    void startPickUpPlaces() {

        startMapOfPlaces(this.pickupLists);

        logger.info("Processing pickup places...");

        //
        // Wait for all places to get started and registered
        //
        stopAndWaitForPlaceCreation();

    }

    void startMapOfPlaces(final Map<String, Set<String>> m) {

        if (hashListSize(m) > 0) {
            for (final Set<String> placeList : m.values()) {
                final boolean status = placeSetup(directoryAction, this.localDirectories, places, placeList);

                if (!status) {
                    logger.warn("Startup: places setup failed!");
                    return;
                }
            }

        }

        logger.debug("done with map of {} places", hashListSize(m));
    }

    /**
     * Count all entries in lists of a map
     */
    private static int hashListSize(@Nullable final Map<String, Set<String>> m) {
        int total = 0;
        if (m != null) {
            for (final Set<String> l : m.values()) {
                if (l != null) {
                    total += l.size();
                }
            }
        }
        return total;
    }

    protected boolean localDirectorySetup(final Map<String, String> localDirectoriesArg) {

        final List<String> hostParameters = this.hostsConfig.findEntries("LOCAL_DIRECTORY");

        final long start = System.currentTimeMillis();
        final Map<String, String> dirStarts = new HashMap<>();
        EmissaryNode emissaryNode = EmissaryServer.getInstance().getNode();
        for (final String thePlaceLocation : hostParameters) {

            final String host = placeHost(thePlaceLocation);

            if (KeyManipulator.isLocalTo(thePlaceLocation, "http://" + this.node.getNodeName() + ":" + this.node.getNodePort() + "/StartupEngine")) {
                final String thePlaceClassStr = PlaceStarter.getClassString(thePlaceLocation);
                if (logger.isInfoEnabled()) {
                    logger.info("Doing local startup for directory {}({}) ", getLocationName(thePlaceLocation), thePlaceClassStr);
                }
                final IServiceProviderPlace p = PlaceStarter.createPlace(thePlaceLocation, null, thePlaceClassStr, null, emissaryNode);
                if (p != null) {
                    dirStarts.put(host, thePlaceLocation);
                    localDirectoriesArg.put(host, p.toString());
                } else {
                    localDirectoriesArg.remove(thePlaceLocation);
                    logger.warn("Giving up on directory {}", thePlaceLocation);
                }
            } else {
                logger.warn("Directory location is not local: {}", thePlaceLocation);
            }
        }

        // All local directories must be up before proceeding
        logger.debug("Waiting for all local directories to start, expecting {}", dirStarts.size());
        int prevCount = 0;
        while (localDirectoriesArg.size() + this.failedLocalDirectories.size() < dirStarts.size()) {
            final int newCount = localDirectoriesArg.size() + this.failedLocalDirectories.size();
            if (newCount > prevCount && newCount < dirStarts.size()) {
                logger.info("Completed {} of {} local directories", localDirectoriesArg.size(), dirStarts.size());
                prevCount = newCount;
            }

            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Directories all up in {}s", (System.currentTimeMillis() - start) / 1000.0);
        }

        return true;
    }

    /**
     * Start all places on the list on a thread, return control immediately. All places in hostParameters list must be for
     * the same host:port!
     */
    protected boolean placeSetup(final int directoryActionArg, final Map<String, String> localDirectoriesArg, final Map<String, String> placesArg,
            final Set<String> hostParameters) {

        // Track how many places we are trying to start
        this.placesToStart.addAll(hostParameters);

        final Thread t = new Thread(() -> {

            final String thePlaceHost = placeHost(hostParameters.stream().findFirst().get());

            final String localDirectory = localDirectoriesArg.get(thePlaceHost);

            if (localDirectory == null) {
                hostParameters.forEach(placesToStart::remove);
                if (failedLocalDirectories.get(thePlaceHost) != null) {
                    logger.warn("Skipping {} due to previously failed directory", thePlaceHost);
                } else {
                    logger.warn("Skipping {} due to local Directory not found", thePlaceHost);
                }
                return;
            }

            if (directoryActionArg != DIRECTORYSTART && directoryActionArg != DIRECTORYADD) {
                hostParameters.forEach(placesToStart::remove);
                return;
            }

            logger.debug("Using localDir={} to create {} places on {}", localDirectory, hostParameters.size(), thePlaceHost);

            // Create a stream of places that can be configured to start in parallel
            boolean parallelPlaceStartup = hostsConfig.findBooleanEntry(PARALLEL_PLACE_STARTUP_CONFIG, false);
            Stream<String> hostParametersStream = StreamSupport.stream(hostParameters.spliterator(), parallelPlaceStartup);
            logger.info("Using parallel place startup: {}", hostParametersStream.isParallel());

            // Start everything in hostParameters
            // (PLACE lines from cfg file for a given host
            hostParametersStream.forEach(thePlaceLocation -> {
                placeName(thePlaceLocation);

                // Get the class name and Class object for what we want to make
                final String thePlaceLocName = getLocationName(thePlaceLocation);
                final String thePlaceClassString = PlaceStarter.getClassString(thePlaceLocation);
                StringBuilder startupBuilder =
                        new StringBuilder("Doing local startup on ")
                                .append(thePlaceLocName)
                                .append("(")
                                .append(thePlaceClassString).append(")...");
                if (thePlaceClassString == null) {
                    startupBuilder.append("skipping, no class string!!");
                    placesToStart.remove(thePlaceLocation);
                    logger.warn(startupBuilder.toString());
                    return;
                }
                logger.debug("Starting place {}", thePlaceLocation);
                if (KeyManipulator.isLocalTo(thePlaceLocation, String.format("http://%s:%s/StartupEngine", node.getNodeName(), node.getNodePort()))) {
                    if (directoryActionArg == DIRECTORYADD && Namespace.exists(thePlaceLocation)) {
                        // logger.info("Local place already exists: {}", thePlaceLocation);
                        startupBuilder.append("local place already exists");
                        placesToStart.remove(thePlaceLocation);
                        // add place to placeAlreadyStarted list, so can be verified in verifyNoInvisibleStartPlaces
                        placeAlreadyStarted.add(thePlaceLocation.substring(thePlaceLocation.lastIndexOf("/") + 1));
                        logger.info(startupBuilder.toString());
                        return;
                    }

                    final IServiceProviderPlace p = PlaceStarter.createPlace(thePlaceLocation, null, thePlaceClassString, localDirectory);
                    if (p != null) {
                        placesArg.put(thePlaceLocation, thePlaceLocation);
                        startupBuilder.append("done!");
                        logger.info(startupBuilder.toString());
                    } else {
                        // logger.error("{} failed to start!", thePlaceLocation);
                        failedPlaces.add(thePlaceLocation);
                        placesToStart.remove(thePlaceLocation);
                        startupBuilder.append("FAILED!!");
                        logger.error(startupBuilder.toString());
                    }
                }
            });
        });
        t.start();
        return true;
    }

    /**
     * Check to see if all the places have started and been registered in the directory. This doesn't account for
     * directories, just things started with a "PLACE" tag
     */
    protected void stopAndWaitForPlaceCreation() {
        int numPlacesExpected = this.placesToStart.size();
        int numPlacesFound;
        int numPlacesFoundPreviously = 0;

        logger.info("Waiting for {} places to start {}", placesToStart.size(),
                placesToStart.stream().map(s -> StringUtils.substringAfterLast(s, "/")).sorted().collect(Collectors.toList()));
        do {
            if (this.placesToStart.size() != numPlacesExpected) {
                logger.info("Now waiting for {} places to start. (originally {} places)", this.placesToStart.size(), numPlacesExpected);
                numPlacesExpected = this.placesToStart.size();
            }

            numPlacesFound = places.size();

            if (numPlacesFound >= numPlacesExpected) {
                boolean failedPlaceStartups = false;

                if (!this.failedPlaces.isEmpty()) {
                    failedPlaceStartups = true;
                    String failedPlaceList = String.join("; ", this.failedPlaces);
                    logger.warn("The following places have failed to start: {}", failedPlaceList);
                }
                if (!CoordinationPlace.getFailedCoordinationPlaces().isEmpty()) {
                    failedPlaceStartups = true;
                    String failedCoordPlaceList = String.join("; ", CoordinationPlace.getFailedCoordinationPlaces());
                    logger.warn("The following coordination places have failed to start: {}", failedCoordPlaceList);
                }

                // check if strict startup & places/coordination places failed, if yes, shut down server
                if (this.node.isStrictStartupMode() && failedPlaceStartups) {
                    logger.error("Server failed to start due to Strict mode being enabled.  To disable strict mode, " +
                            "run server start command without the --strict flag");
                    logger.error("Server shutting down");
                    System.exit(1);
                }

                // normal termination of the loop
                logger.debug("Woohoo! {} of {} places are up and running.", numPlacesFound, numPlacesExpected);
                break;
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (numPlacesFound != numPlacesFoundPreviously) {
                numPlacesFoundPreviously = numPlacesFound;

                final float percentageUp = (float) numPlacesFound / (float) numPlacesExpected;
                final String leadString;
                if (percentageUp < 0.20) {
                    leadString = "Hmmm... only ";
                } else if (percentageUp < 0.40) {
                    leadString = "Ok, now ";
                } else if (percentageUp < 0.60) {
                    leadString = "Making progress, ";
                } else if (percentageUp < 0.80) {
                    leadString = "Over half way there! ";
                } else if (percentageUp < 0.95) {
                    leadString = "Almost ready! ";
                } else if (numPlacesFound + 1 == numPlacesExpected) {
                    leadString = "One more to go... ";
                } else {
                    leadString = "Yeah! ";
                }

                logger.debug("{}{} of {} places are up and running.", leadString, numPlacesFound, numPlacesExpected);
            }

        } while (true); // break terminated loop
    }

    /**
     * sort all the PLACE entries into either a processing place or a pickup place
     */
    protected void sortPlaces(final List<String> placeList) {

        for (final String location : placeList) {
            final String className = PlaceStarter.getClassString(location);
            if (className == null) {
                continue;
            }

            try {
                sortPickupOrPlace(location, PickUpPlace.implementsPickUpPlace(Class.forName(className)) ? this.pickupLists : this.placeLists);
            } catch (ClassNotFoundException e) {
                logger.error("Could not create place {}", className, e);
            }
        }
    }

    private static void sortPickupOrPlace(String theLocation, Map<String, Set<String>> placeList) {
        final String host = placeHost(theLocation);
        Set<String> l = placeList.computeIfAbsent(host, k -> new LinkedHashSet<>());
        if (l.contains(theLocation)) {
            logger.warn("Sorting places found duplicate {}({}), skipping!", getLocationName(theLocation), PlaceStarter.getClassString(theLocation));
        } else {
            l.add(theLocation);
        }
    }

    protected static String getLocationName(String location) {
        return StringUtils.substringAfterLast(location, "/");
    }

    /**
     * Verifies the active directory places vs places started up. Log if any places are started without being announced in
     * start-up.
     *
     * @return true if no invisible places started, false if yes
     */
    public static boolean verifyNoInvisiblePlacesStarted() {
        try {
            IDirectoryPlace dirPlace = DirectoryPlace.lookup();
            List<DirectoryEntry> dirEntries = dirPlace.getEntries();
            for (DirectoryEntry entry : dirEntries) {
                // add place names of active places. getLocalPlace() returns null for any place that failed to start
                if (entry.getLocalPlace() != null) {
                    activeDirPlaces.add(entry.getLocalPlace().getPlaceName());
                }
            }

            // remove DirectoryPlace from activeDirPlaces. DirectoryPlace is started up automatically in order to
            // start all other places, so it isn't per se "announced", but it is known and logged
            activeDirPlaces.removeIf(dir -> dir.equalsIgnoreCase("DirectoryPlace"));
        } catch (EmissaryException e) {
            throw new EmissaryRuntimeException(e);
        }

        // compares place names in active dirs and active places, removes them from set if found
        for (String thePlaceLocation : places.values()) {
            activeDirPlaces.removeIf(dir -> dir.equalsIgnoreCase(placeName(thePlaceLocation)));
        }

        // places that are attempted to startup but are already up are added to separate list
        // this will only check if places are added to that list
        if (!placeAlreadyStarted.isEmpty()) {
            for (String thePlaceLocation : placeAlreadyStarted) {
                activeDirPlaces.removeIf(dir -> dir.equalsIgnoreCase(thePlaceLocation));
            }
        }

        // if any places are left in active dir keys, they are places not announced on startup
        if (!activeDirPlaces.isEmpty()) {
            logger.warn("{} place(s) started up without being announced! {}", activeDirPlaces.size(), activeDirPlaces);
            return false;
        }

        return true;
    }

    // get invisibly started places
    public static Set<String> getInvisPlaces() {
        return activeDirPlaces;
    }

    // get if invisible places are started while in strict mode
    public static boolean isInvisPlacesStartedInStrictMode() {
        return invisPlacesStartedInStrictMode;
    }
}
