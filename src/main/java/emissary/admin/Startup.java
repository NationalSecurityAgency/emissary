package emissary.admin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import emissary.client.EmissaryResponse;
import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.core.EmissaryException;
import emissary.directory.EmissaryNode;
import emissary.directory.KeyManipulator;
import emissary.pickup.PickUpPlace;
import emissary.place.IServiceProviderPlace;
import emissary.server.mvc.adapters.HeartbeatAdapter;
import emissary.server.mvc.adapters.PlaceStarterAdapter;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Startup {

    public static final int DIRECTORYSTART = 0;
    public static final int DIRECTORYADD = 1;
    public static final int DIRECTORYDELETE = 2;
    static int directoryAction = DIRECTORYADD;

    // If we are an emissary node these will be present
    private EmissaryNode node;

    // Our logger
    private static final Logger logger = LoggerFactory.getLogger(Startup.class);

    // The startup config object
    protected Configurator hostsConfig = null;

    // Successfully started directories
    protected final Map<String, String> localDirectories = new ConcurrentHashMap<String, String>();

    // Failed directories
    protected final Map<String, String> failedLocalDirectories = new ConcurrentHashMap<String, String>();

    // Collection of the places as they finish coming up
    protected final Map<String, String> places = new ConcurrentHashMap<String, String>();

    // Collection of places that are being started
    protected final Set<String> placesToStart = ConcurrentHashMap.newKeySet();

    // sorted lists of the place types, grouped by hostname
    protected final Map<String, List<String>> placeLists = new ConcurrentHashMap<String, List<String>>();
    protected final Map<String, List<String>> pickupLists = new ConcurrentHashMap<String, List<String>>();

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
        if ("-add".equalsIgnoreCase(optarg)) {
            return DIRECTORYADD;
        }

        if ("-delete".equalsIgnoreCase(optarg)) {
            return DIRECTORYDELETE;
        }

        if ("-start".equalsIgnoreCase(optarg)) {
            return DIRECTORYSTART;
        }

        // default
        return DIRECTORYSTART;
    }

    private static String makeConfig(final String path, final String file) {
        if (file.startsWith("/") && new File(file).exists()) {
            return file;
        }
        return emissary.config.ConfigUtil.getConfigFile(path, file);
    }

    /**
     * The main entry point
     */
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
            directoryAction = setAction("-start");
            if (args[0].startsWith("/") || args[0].toUpperCase().startsWith("HTTP")) {
                startupConfigFile = args[0];
            } else {
                startupConfigFile = emissary.config.ConfigUtil.getConfigFile(args[0]);
            }
        } else if (args.length == 2) {
            directoryAction = setAction("-start");
            startupConfigFile = makeConfig(args[0], args[1]);
        } else {
            directoryAction = setAction(args[0]);
            startupConfigFile = makeConfig(args[1], args[2]);
        }

        final Startup start = new Startup(startupConfigFile);
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

    @Deprecated
    public Startup(final String startupConfigFile) throws IOException {
        // Read in startup config file specifying place/host setup
        this(new ServiceConfigGuide(startupConfigFile), new EmissaryNode());

    }

    public boolean bootstrap() {

        //
        // Setup the Local Directories in a hashtable
        //
        final boolean status = localDirectorySetup(directoryAction, this.localDirectories);

        if (!status) {
            logger.warn("Startup: local directory setup failed.");
            return false;
        }

        //
        // Setup the rest of the Places except no pickups
        //
        sortPlaces(this.hostsConfig.findEntries("PLACE"));

        logger.info("Ready to start {}  places and {} PickUp places.", hashListSize(this.placeLists), hashListSize(this.pickupLists));

        logger.info("Processing non-pickup places...");
        startMapOfPlaces(this.placeLists);

        //
        // Wait for all places to get started and registered
        //
        this.stopAndWaitForPlaceCreation();

        logger.info("Done with bootstrap phase");
        return true;
    }

    /**
     * Start all the pickup places and wait for them to finish
     */
    void startPickUpPlaces() {

        startMapOfPlaces(this.pickupLists);

        logger.info("Done starting pickup places, waiting for them...");

        //
        // Wait for all places to get started and registered
        //
        stopAndWaitForPlaceCreation();

    }

    void startMapOfPlaces(final Map<String, List<String>> m) {

        if (hashListSize(m) > 0) {
            for (final List<String> placeList : m.values()) {
                final boolean status = placeSetup(directoryAction, this.localDirectories, this.places, placeList);

                if (!status) {
                    logger.warn("Startup: places setup failed!");
                    return;
                }
            }

        }

        logger.info("done with map of " + hashListSize(m) + " places");
    }

    /**
     * Count all entries in lists of a map
     */
    private int hashListSize(final Map<String, List<String>> m) {
        int total = 0;
        if (m != null) {
            for (final List<String> l : m.values()) {
                if (l != null) {
                    total += l.size();
                }
            }
        }
        return total;
    }

    protected boolean localDirectorySetup(final int directoryActionArg, final Map<String, String> localDirectoriesArg) {

        final List<String> hostParameters = this.hostsConfig.findEntries("LOCAL_DIRECTORY");

        final long start = System.currentTimeMillis();
        final Map<String, String> dirStarts = new HashMap<String, String>();
        for (final String thePlaceLocation : hostParameters) {

            final String host = placeHost(thePlaceLocation);

            if (KeyManipulator.isLocalTo(thePlaceLocation, "http://" + this.node.getNodeName() + ":" + this.node.getNodePort() + "/StartupEngine")) {
                logger.debug("Doing local startup for directory {} ", thePlaceLocation);
                final String thePlaceClassStr = PlaceStarter.getClassString(thePlaceLocation);
                final IServiceProviderPlace p = PlaceStarter.createPlace(thePlaceLocation, (InputStream) null, thePlaceClassStr, (String) null);
                if (p != null) {
                    dirStarts.put(host, thePlaceLocation);
                    localDirectoriesArg.put(host, p.toString());
                } else {
                    localDirectoriesArg.remove(thePlaceLocation);
                    logger.debug("Giving up on directory {}", thePlaceLocation);
                }
                continue;
            }

            if (dirStarts.get(host) != null) {
                logger.warn("Duplicate directory for host {} : previously {}, now {}, skipping it.", host, dirStarts.get(host), thePlaceLocation);
                continue;
            }

            if (directoryActionArg == DIRECTORYSTART) {

                startOneDirectory(thePlaceLocation);
                logger.info("Trying to start local directory {} on {}", thePlaceLocation, host);
                dirStarts.put(host, thePlaceLocation);

            } else if (directoryActionArg == DIRECTORYADD) {

                startOneDirectory(thePlaceLocation);
                logger.info("Trying to add local directory {} on {}", thePlaceLocation, host);
                dirStarts.put(host, thePlaceLocation);

            } else {
                logger.error("Have not reimlpemented this action ({})", directoryActionArg);
            }
        }

        // wait for them all to either completely start or fail
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
                // empty catch block
            }
        }

        logger.info("Directories all up in {}s", (System.currentTimeMillis() - start) / 1000.0);

        return true;
    }

    /**
     * Start one local directory with retry in a threaded fashion
     */
    private void startOneDirectory(final String thePlaceLocation) {

        final Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                final String thePlaceHost = placeHost(thePlaceLocation);

                final String thePlaceClassStr = PlaceStarter.getClassString(thePlaceLocation);
                if (thePlaceClassStr == null) {
                    logger.error("Unable to start {} : No class string", thePlaceLocation);
                    Startup.this.failedLocalDirectories.put(thePlaceHost, thePlaceLocation);
                    return;
                }

                String localDirectory = null;


                for (int tries = 0; tries < 4 && localDirectory == null; tries++) {
                    String key = null;

                    PlaceStarterAdapter psa = new PlaceStarterAdapter(thePlaceClassStr, thePlaceLocation, null);
                    EmissaryResponse response = psa.outboundCreatePlace();
                    // TODO consider putting this method on the EmissaryReponse
                    if (response.getStatus() == HttpStatus.SC_OK) {
                        key = response.getContentString();
                    }

                    if (key == null) {
                        logger.error("Could not create local directory at {}", thePlaceLocation);
                    } else {
                        localDirectory = key;
                    }

                    if (localDirectory == null || localDirectory.length() == 0) {
                        logger.error("ERROR: Directory startup failed on {}", thePlaceHost);
                        Startup.this.failedLocalDirectories.put(thePlaceHost, thePlaceLocation);
                        return;
                    }
                    logger.trace("Starting up {} on {}", localDirectory, thePlaceHost);
                    Startup.this.localDirectories.put(thePlaceHost, localDirectory);
                }
            }
        });

        t.start();
    }

    /**
     * Start all places on the list on a thread, return control immediately All places in hostParameters list must be for
     * the same host:port!
     */
    protected boolean placeSetup(final int directoryActionArg, final Map<String, String> localDirectoriesArg, final Map<String, String> placesArg,
            final List<String> hostParameters) {

        // Track how many places we are trying to start
        // globalPlacesStarted += hostParameters.size();
        this.placesToStart.addAll(hostParameters);

        final Thread t = new Thread(new Runnable() {
            @Override
            public void run() {

                final String thePlaceHost = placeHost(hostParameters.get(0));

                final String localDirectory = localDirectoriesArg.get(thePlaceHost);

                if (localDirectory == null) {
                    Startup.this.placesToStart.removeAll(hostParameters);
                    if (Startup.this.failedLocalDirectories.get(thePlaceHost) != null) {
                        logger.warn("Skipping {} due to previously failed directory", thePlaceHost);
                    } else {
                        logger.warn("Skipping {} : local Directory not found", thePlaceHost);
                    }
                    return;
                }

                if (directoryActionArg != DIRECTORYSTART && directoryActionArg != DIRECTORYADD) {
                    Startup.this.placesToStart.removeAll(hostParameters);
                    return;
                }

                logger.info("Using localDir={} to create {} places on {}", localDirectory, hostParameters.size(), thePlaceHost);

                // Start everything in hostParameters
                // (PLACE lines from cfg file for a given host
                for (int i = 0; i < hostParameters.size(); i++) {
                    final String thePlaceLocation = hostParameters.get(i);
                    placeName(thePlaceLocation);

                    // Get the class name and Class object for what we want to make
                    final String thePlaceClassString = PlaceStarter.getClassString(thePlaceLocation);
                    if (thePlaceClassString == null) {
                        logger.warn("Skipping {}, no class string", thePlaceLocation);
                        Startup.this.placesToStart.remove(thePlaceLocation);
                        continue;
                    }

                    logger.debug("Starting place {}", thePlaceLocation);
                    if (KeyManipulator.isLocalTo(thePlaceLocation,
                            String.format("http://{}:{}/StartupEngine", Startup.this.node.getNodeName(), Startup.this.node.getNodePort()))) {
                        if (directoryActionArg == DIRECTORYADD && emissary.core.Namespace.exists(thePlaceLocation)) {
                            logger.info("Local place already exists: {}", thePlaceLocation);
                            Startup.this.placesToStart.remove(thePlaceLocation);
                            continue;
                        }

                        logger.debug("Doing local startup on place {}", thePlaceLocation);
                        final String thePlaceClassStr = PlaceStarter.getClassString(thePlaceLocation);
                        final IServiceProviderPlace p =
                                PlaceStarter.createPlace(thePlaceLocation, (InputStream) null, thePlaceClassStr, localDirectory);
                        if (p != null) {
                            placesArg.put(thePlaceLocation, thePlaceLocation);
                        } else {
                            Startup.this.placesToStart.remove(thePlaceLocation);
                            logger.debug("Giving up on {}", thePlaceLocation);
                        }

                        continue;
                    }

                    if (directoryActionArg == DIRECTORYADD) {
                        final HeartbeatAdapter adapter = new HeartbeatAdapter();
                        adapter.outboundHeartbeat("ADMIN.START.STARTUP.http://localhost:8181/Startup", "*.*.*." + thePlaceLocation);
                        // Already exists do not replace
                        logger.info("Skipping {}, already exists", thePlaceLocation);
                        Startup.this.placesToStart.remove(thePlaceLocation);
                        continue;
                    }


                    String thePlaceFinal = null;

                    int tryCounter = 0;

                    // Create the instanceretries
                    while (tryCounter < 3 && thePlaceFinal == null) {

                        final PlaceStarterAdapter psa = new PlaceStarterAdapter(thePlaceClassString, thePlaceLocation, localDirectory);
                        final EmissaryResponse response = psa.outboundCreatePlace();
                        // TODO consider putting this on the EmissaryReponse object
                        if (response.getStatus() == HttpStatus.SC_OK) {
                            thePlaceFinal = response.getContentString();
                        }

                        if (thePlaceFinal == null) {
                            logger.warn("error with PlaceStarter.createPlace: Cannot instantiate {} try {} failed!", thePlaceLocation, tryCounter);
                            if (tryCounter++ > 2) {
                                // globalPlacesStarted --;
                                Startup.this.placesToStart.remove(thePlaceLocation);
                                break;
                            }

                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException junke) {
                                // empty catch block
                            }
                        } else {
                            placesArg.put(thePlaceLocation, thePlaceFinal);
                            logger.debug("Started place {}", thePlaceLocation);
                        }
                    }
                }
            }
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
        int numPlacesFound = 0;
        int numPlacesFoundPreviously = 0;

        logger.info("Waiting for {} places to start.", numPlacesExpected);
        do {
            if (this.placesToStart.size() != numPlacesExpected) {
                logger.info("NOW Waiting for {} places to start. (originally {} places)", this.placesToStart.size(), numPlacesExpected);
                numPlacesExpected = this.placesToStart.size();
            }

            numPlacesFound = this.places.size();

            if (numPlacesFound >= numPlacesExpected) {
                // normal termination of the loop
                logger.info("Woohoo! {} of {} places are up and running.", numPlacesFound, numPlacesExpected);
                break;
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                // empty catch block
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

                logger.info("{}{} of {} places are up and running.", leadString, numPlacesFound, numPlacesExpected);
            }

        } while (true); // break terminated loop
    }

    /**
     * sort all the PLACE entries into either a processing place or a pickup place
     */
    protected void sortPlaces(final List<String> allPlaces) {

        for (final String theLocation : allPlaces) {
            Startup.placeName(theLocation);
            final String className = PlaceStarter.getClassString(theLocation);
            if (className == null) {
                continue;
            }


            try {
                if (PickUpPlace.implementsPickUpPlace(Class.forName(className))) {
                    sortPickupOrPlace(theLocation, this.pickupLists);
                } else {
                    sortPickupOrPlace(theLocation, this.placeLists);
                }
            } catch (ClassNotFoundException e) {
                logger.error("Could not create place {}", className, e);
            }
        }
    }

    private void sortPickupOrPlace(String theLocation, Map<String, List<String>> placeList) {
        final String host = placeHost(theLocation);
        List<String> l = placeList.get(host);
        if (l == null) {
            l = new ArrayList<String>();
            placeList.put(host, l);
        }
        l.add(theLocation);
    }
}
