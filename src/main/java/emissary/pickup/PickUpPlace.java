package emissary.pickup;

import emissary.config.Configurator;
import emissary.core.DataObjectFactory;
import emissary.core.EmissaryException;
import emissary.core.Form;
import emissary.core.IBaseDataObject;
import emissary.core.IMobileAgent;
import emissary.core.NamespaceException;
import emissary.core.channels.InMemoryChannelFactory;
import emissary.log.MDCConstants;
import emissary.parser.ParserEOFException;
import emissary.parser.ParserException;
import emissary.parser.ParserFactory;
import emissary.parser.SessionParser;
import emissary.parser.SessionProducer;
import emissary.place.AgentsNotSupportedPlace;
import emissary.place.IServiceProviderPlace;
import emissary.place.ServiceProviderPlace;
import emissary.pool.AgentPool;
import emissary.spi.ObjectTracing;
import emissary.spi.ObjectTracingService;
import emissary.util.ClassComparator;
import emissary.util.TimeUtil;
import emissary.util.io.DiskSpaceListener;
import emissary.util.io.DiskSpaceMonitor;
import emissary.util.shell.Executrix;

import jakarta.annotation.Nullable;
import org.slf4j.MDC;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static emissary.core.constants.Parameters.FILE_DATE;
import static emissary.core.constants.Parameters.FILE_NAME;

/**
 * This class is the base class of those places that inject data into the system. This place knows a lot about
 * processing files of input, including files of sessions that must be identified and parsed using a ParserFactory. It
 * knows nothing about where the data comes from, though. The method of input, either a directory or set of directories
 * to monitor, a socket, a WorkSpace provider, or something else, comes from classes that extend this one.
 */
public abstract class PickUpPlace extends ServiceProviderPlace implements IPickUpPlace, AgentsNotSupportedPlace {

    // Any data picked up with less/more bytes than this will be
    // set to ERROR initially, can be overridden in config files
    // for pickup places.
    protected int minimumContentLength = 10;
    protected long maximumContentLength = 1048567;
    protected String oversizeArea = "OversizeData";

    // Directory store original data while processing
    protected String holdingArea;

    // Where to move data that has an error
    protected String errorArea;

    // Where to move data when done
    @Nullable
    protected String doneArea;

    // Our parser factory
    protected ParserFactory parserFactory = new ParserFactory();

    // True turns off data identification engine to just favor
    // the simple parsers
    protected boolean simpleMode = false;

    // Reference to global agent pool for out payloads
    protected AgentPool agentPool;

    // Initial forms for new data, read from config file
    protected List<String> initialFormValues = Collections.emptyList();

    // Metadata items that should always be copied to children
    protected Set<String> alwaysCopyMetadataVals = new HashSet<>();

    protected boolean useObjectTraceLogger = false;

    // Disk space monitoring (optional)
    @Nullable
    protected DiskSpaceMonitor diskSpaceMonitor = null;

    public PickUpPlace() throws IOException {
        super();
        configurePickUpPlace();
    }

    public PickUpPlace(InputStream configStream) throws IOException {
        super(configStream);
        configurePickUpPlace();
    }

    /**
     * Create a pick up place
     * 
     * @param configInfo the config location
     * @param placeLocation the place key
     * @throws IOException If there is some I/O problem.
     */
    public PickUpPlace(String configInfo, String placeLocation) throws IOException {
        this(configInfo, null, placeLocation);
    }


    /**
     * Create a pick up place
     * 
     * @param configInfo the config location
     * @param dir the key of the controlling directory
     * @param placeLoc the place key
     * @throws IOException If there is some I/O problem.
     */
    public PickUpPlace(String configInfo, @Nullable String dir, String placeLoc) throws IOException {
        super(configInfo, dir, placeLoc);
        configurePickUpPlace();
    }

    /**
     * Create a pick up place
     * 
     * @param configStream the config stream
     * @param dir the key of the controlling directory
     * @param placeLoc the place key
     * @throws IOException If there is some I/O problem.
     */
    public PickUpPlace(InputStream configStream, String dir, String placeLoc) throws IOException {
        super(configStream, dir, placeLoc);
        configurePickUpPlace();
    }

    /**
     * Create a pick up place
     * 
     * @param configStream the config stream
     * @param placeLoc the place key
     * @throws IOException If there is some I/O problem.
     */
    public PickUpPlace(InputStream configStream, String placeLoc) throws IOException {
        super(configStream, placeLoc);
        configurePickUpPlace();
    }

    /**
     * Create a pick up place
     *
     * @param configs config data
     * @throws IOException If there is some I/O problem.
     */
    public PickUpPlace(Configurator configs) throws IOException {
        super(configs);
        configurePickUpPlace();
    }


    /**
     * Configure the place specific items
     * <ul>
     * <li>MINIMUM_DATA_SIZE: min size in bytes of a file</li>
     * <li>MAXIMUM_DATA_SIZE: max size in bytes of a file, -1 for unlimited</li>
     * <li>OVERSIZE_DATA_HOLDING_AREA: where to put oversize data</li>
     * <li>HOLDING_AREA: where to put data while inprocess</li>
     * <li>ERROR_DATA: where to put things that have errors</li>
     * <li>DONE_DATA: where it goes when done</li>
     * <li>SIMPLE_MODE: boolean when true turns off DataIdentification engine</li>
     * <li>INITIAL_FORM: one or more forms for new payloads</li>
     * </ul>
     */
    protected void configurePickUpPlace() {
        minimumContentLength = configG.findIntEntry("MINIMUM_DATA_SIZE", minimumContentLength);
        maximumContentLength = configG.findSizeEntry("MAXIMUM_DATA_SIZE", maximumContentLength);
        oversizeArea = configG.findStringEntry("OVERSIZE_DATA_HOLDING_AREA", oversizeArea);

        simpleMode = configG.findBooleanEntry("SIMPLE_MODE", false);
        holdingArea = configG.findCanonicalFileNameEntry("HOLDING_AREA", null);
        doneArea = configG.findCanonicalFileNameEntry("DONE_DATA", doneArea);
        errorArea = configG.findCanonicalFileNameEntry("ERROR_DATA", "errorArea");

        if (doneArea != null && doneArea.equals("")) {
            doneArea = null;
            logger.info("Alert: Completed data will be deleted from the system due to DONE_AREA setting");
        }

        logger.debug("Pickup Canonical HOLD => {}, Pickup Canonical DONE => {}, Pickup Canonical ERROR => {}", holdingArea, doneArea, errorArea);

        initialFormValues = configG.findEntries("INITIAL_FORM");
        if (initialFormValues.isEmpty()) {
            initialFormValues.add(Form.UNKNOWN);
        }

        // Grab the default pool
        try {
            agentPool = AgentPool.lookup();
        } catch (NamespaceException e) {
            logger.warn("Cannot find agent pool!");
        }

        alwaysCopyMetadataVals = configG.findEntriesAsSet("ALWAYS_COPY_METADATA");

        // Whether or not to use the objectTrace logger
        useObjectTraceLogger = configG.findBooleanEntry("USE_OBJECT_TRACE_LOGGER", useObjectTraceLogger);

        // Configure disk space monitoring if enabled
        configureDiskSpaceMonitoring();
    }

    /**
     * Configures disk space monitoring if enabled in the configuration. When disk space exceeds the configured threshold,
     * {@link #onDiskSpaceExceeded(Path, double, long)} will be called. When disk space returns below the resume threshold,
     * {@link #onDiskSpaceRecovered(Path, double, long)} will be called.
     * <p>
     * Configuration parameters:
     * <ul>
     * <li>DISK_SPACE_MONITORING_ENABLED: Enable disk space monitoring (default: false)</li>
     * <li>DISK_SPACE_CHECK_INTERVAL_SECONDS: Check interval in seconds (default: 30)</li>
     * <li>DISK_SPACE_PAUSE_THRESHOLD_PERCENT: Pause when disk usage exceeds this percentage (0-100)</li>
     * <li>DISK_SPACE_RESUME_THRESHOLD_PERCENT: Resume when disk usage falls below this percentage (0-100)</li>
     * <li>DISK_SPACE_PAUSE_THRESHOLD_BYTES: Pause when free space falls below this byte count (alternative to percent)</li>
     * <li>DISK_SPACE_RESUME_THRESHOLD_BYTES: Resume when free space exceeds this byte count (alternative to percent)</li>
     * <li>DISK_SPACE_MONITORED_PATH: Path to monitor (default: OUTPUT_DATA or DONE_DATA)</li>
     * </ul>
     */
    protected void configureDiskSpaceMonitoring() {
        boolean enabled = configG.findBooleanEntry("DISK_SPACE_MONITORING_ENABLED", false);

        if (!enabled) {
            logger.debug("Disk space monitoring is disabled");
            return;
        }

        // Determine path to monitor - try configured path first, then OUTPUT_DATA, then DONE_DATA
        String monitoredPathStr = configG.findStringEntry("DISK_SPACE_MONITORED_PATH", null);
        if (monitoredPathStr == null) {
            monitoredPathStr = configG.findStringEntry("OUTPUT_DATA", null);
        }
        if (monitoredPathStr == null) {
            monitoredPathStr = doneArea;
        }

        if (monitoredPathStr == null) {
            logger.warn("No path configured for disk space monitoring (tried DISK_SPACE_MONITORED_PATH, OUTPUT_DATA, DONE_DATA)");
            return;
        }

        Path monitoredPath = Paths.get(monitoredPathStr);
        if (!Files.exists(monitoredPath)) {
            logger.warn("Monitored path {} does not exist, disk space monitoring disabled", monitoredPath);
            return;
        }

        long checkInterval = configG.findLongEntry("DISK_SPACE_CHECK_INTERVAL_SECONDS", 30L);

        // Get thresholds - percentage takes precedence over bytes
        // Use -1 as sentinel value to detect if not configured
        double pausePercent = configG.findDoubleEntry("DISK_SPACE_PAUSE_THRESHOLD_PERCENT", -1.0);
        double resumePercent = configG.findDoubleEntry("DISK_SPACE_RESUME_THRESHOLD_PERCENT", -1.0);
        long pauseBytes = configG.findSizeEntry("DISK_SPACE_PAUSE_THRESHOLD_BYTES", -1L);
        long resumeBytes = configG.findSizeEntry("DISK_SPACE_RESUME_THRESHOLD_BYTES", -1L);

        // Validate configuration
        boolean hasPercentConfig = pausePercent >= 0;
        boolean hasBytesConfig = pauseBytes >= 0;

        if (!hasPercentConfig && !hasBytesConfig) {
            logger.warn("No disk space thresholds configured, monitoring disabled");
            return;
        }

        if (hasPercentConfig && hasBytesConfig) {
            logger.warn("Both percentage and byte thresholds configured, using percentage");
        }

        try {
            DiskSpaceMonitor.Builder builder = new DiskSpaceMonitor.Builder(monitoredPath)
                    .checkInterval(checkInterval);

            if (hasPercentConfig) {
                builder.pauseThresholdPercent(pausePercent);
                if (resumePercent >= 0) {
                    builder.resumeThresholdPercent(resumePercent);
                } else {
                    // Set a sensible default: 5% lower than pause threshold to avoid oscillation
                    double defaultResumePercent = Math.max(0, pausePercent - 5.0);
                    logger.warn("DISK_SPACE_RESUME_THRESHOLD_PERCENT not set, using {}% (pause threshold - 5%)", defaultResumePercent);
                    builder.resumeThresholdPercent(defaultResumePercent);
                }
            } else {
                builder.pauseThresholdBytes(pauseBytes);
                if (resumeBytes >= 0) {
                    builder.resumeThresholdBytes(resumeBytes);
                } else {
                    // Set a sensible default: 50% more than pause threshold to allow recovery
                    long defaultResumeBytes = (long) (pauseBytes * 1.5);
                    logger.warn("DISK_SPACE_RESUME_THRESHOLD_BYTES not set, using {} bytes (pause threshold * 1.5)", defaultResumeBytes);
                    builder.resumeThresholdBytes(defaultResumeBytes);
                }
            }

            diskSpaceMonitor = builder.build();
            diskSpaceMonitor.addListener(new DiskSpaceListener() {
                @Override
                public void onDiskSpaceExceeded(Path path, double usedPercent, long freeBytes) {
                    logger.warn("Disk space threshold exceeded for {}: {}% used, {} bytes free",
                            path, String.format("%.2f", usedPercent), freeBytes);
                    PickUpPlace.this.onDiskSpaceExceeded(path, usedPercent, freeBytes);
                }

                @Override
                public void onDiskSpaceRecovered(Path path, double usedPercent, long freeBytes) {
                    logger.info("Disk space recovered for {}: {}% used, {} bytes free",
                            path, String.format("%.2f", usedPercent), freeBytes);
                    PickUpPlace.this.onDiskSpaceRecovered(path, usedPercent, freeBytes);
                }
            });

            logger.info("Disk space monitoring configured for {} with check interval {} seconds",
                    monitoredPath, checkInterval);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid disk space monitoring configuration: {}", e.getMessage());
        }
    }

    /**
     * Called when disk space threshold is exceeded. Subclasses should override this to take appropriate action, such as
     * pausing pickup operations.
     *
     * @param path the filesystem path being monitored
     * @param usedPercent the current disk usage as a percentage (0-100)
     * @param freeBytes the current number of free bytes available
     */
    protected void onDiskSpaceExceeded(Path path, double usedPercent, long freeBytes) {
        // Default implementation does nothing - subclasses should override
        logger.debug("Disk space exceeded callback - subclass should override to take action");
    }

    /**
     * Called when disk space has recovered below the resume threshold. Subclasses should override this to resume operations
     * if they were paused.
     *
     * @param path the filesystem path being monitored
     * @param usedPercent the current disk usage as a percentage (0-100)
     * @param freeBytes the current number of free bytes available
     */
    protected void onDiskSpaceRecovered(Path path, double usedPercent, long freeBytes) {
        // Default implementation does nothing - subclasses should override
        logger.debug("Disk space recovered callback - subclass should override to take action");
    }

    /**
     * Starts disk space monitoring if configured. Should be called when the pickup place is ready to start operations.
     */
    protected void startDiskSpaceMonitoring() {
        if (diskSpaceMonitor != null) {
            diskSpaceMonitor.start();
            logger.info("Disk space monitoring started");
        }
    }

    /**
     * Stops disk space monitoring if configured. Should be called during shutdown.
     */
    protected void stopDiskSpaceMonitoring() {
        if (diskSpaceMonitor != null) {
            logger.debug("Stopping disk space monitor");
            diskSpaceMonitor.stop();
        }
    }

    /**
     * Return the value of the inprocess area, usually a directory path
     * 
     * @return holdingArea string
     */
    @Override
    public String getInProcessArea() {
        return holdingArea;
    }

    /**
     * Return the value of the error area, usually a directory path
     * 
     * @return errorArea string
     */
    @Override
    public String getErrorArea() {
        return errorArea;
    }

    /**
     * Return the value of the done area, usually a directory path
     * 
     * @return doneArea string
     */
    @Override
    public String getDoneArea() {
        return doneArea;
    }

    /**
     * Return the maximum content size for a file that can be handled by this place
     * 
     * @return maximumContentLength string
     */
    @Override
    public long getMaximumContentLength() {
        return maximumContentLength;
    }

    /**
     * Return the minumum content size for a file that can be handled by this place
     * 
     * @return minimumContentLength string
     */
    @Override
    public int getMinimumContentLength() {
        return minimumContentLength;
    }


    /**
     * Return the value of the oversize area, usually a directory path
     * 
     * @return path to the oversize area
     */
    @Override
    public String getOversizeArea() {
        return oversizeArea;
    }

    /**
     * Add metadata as the data objects are created Can be overridden to customize behavior
     * 
     * @param d the nascent data object
     * @param f the file it came from
     */
    protected void dataObjectCreated(IBaseDataObject d, File f) {
        d.putParameter(FILE_DATE, TimeUtil.getDateAsISO8601(f.lastModified()));
        d.putParameter(FILE_NAME, f.getName());
    }

    /**
     * Call back from a data server or queue server when a new file is ready to process. This method is called for raw
     * files, not work bundles, so the simpleMode determination is made by this Place configuration.
     * 
     * @param f file to process
     * @return true if it worked
     * @throws IOException If there is some I/O problem.
     */
    public boolean processDataFile(File f) throws IOException, EmissaryException {
        boolean isOversize = false;
        if (maximumContentLength != -1 && f.length() > maximumContentLength) {
            logger.warn("Sorry, This file is too large ({} < {}): {}", f.length(), maximumContentLength, f.getPath());
            isOversize = true;
            // Let it continue on knowing it is too big
            // as we may need a record of the file
        }
        String fixedName = fixFileName(f.getName());
        return processDataFile(f, fixedName, isOversize, simpleMode, getDoneArea());
    }

    /**
     * Call back from a data server or queue server when a new file is ready to process
     * 
     * @param theFile file to process
     * @param fixedName the good short name of the file
     * @param isOversize true if the content is too big by configuration
     * @param simpleMode true if no session parsing is desired
     * @param outputRoot the done area
     * @return true if it worked
     * @throws IOException If there is some I/O problem.
     */
    public boolean processDataFile(File theFile, String fixedName, boolean isOversize, boolean simpleMode, String outputRoot) throws IOException,
            EmissaryException {
        boolean success = true;
        logger.debug("Starting processDataFile in PickUpPlace for {}", theFile);

        ObjectTracingService.emitLifecycleEvent(null, fixedName, ObjectTracing.Stage.PICK_UP, useObjectTraceLogger);

        // Handle oversize data quickly without reading the file
        if (isOversize) {
            handleOversizePayload(theFile, fixedName, simpleMode);
        }

        // Handle it without session parsing if simple mode is on
        else if (simpleMode) {
            handleSimplePayload(theFile, fixedName);
        }

        // Parse sessions out of the file
        else {
            try {
                logger.debug("Starting processSessions on {}", theFile);
                processSessions(theFile, fixedName);
                logger.debug("Finished with processSessions on {}", theFile);
            } catch (ParserException ex) {
                logger.error("Cannot parse {}", theFile.getName(), ex);
                success = false;
            }
        }

        if (success) {
            handleFileSuccess(theFile, outputRoot);
        } else {
            handleFileError(theFile);
        }

        logger.debug("Ending processDataFile {} {} {}", theFile, success ? "success" : "failure", simpleMode ? "simple" : "");
        return success;
    }

    /**
     * Handle oversize payload item
     * 
     * @param theFile the file with the oversize data
     * @param fixedName name to use for the object
     * @param simpleMode simple flag from the input
     * @return true
     */
    protected boolean handleOversizePayload(File theFile, String fixedName, boolean simpleMode) throws EmissaryException {
        // Send it away, blocks until an agent is ready
        IBaseDataObject dataObject =
                DataObjectFactory.getInstance(new Object[] {("The file is oversize at " + theFile.length() + " bytes").getBytes(), fixedName,
                        "OVERSIZE"});
        dataObject.setParameter("SIMPLE_MODE", Boolean.toString(simpleMode));
        dataObjectCreated(dataObject, theFile);
        logger.info("**Deploying an agent for oversized {} and object {} simple={}", fixedName, dataObject.getInternalId(),
                simpleMode ? "simple" : "");
        assignToPooledAgent(dataObject, -1L);
        return true;
    }

    /**
     * Action to handle a simple mode File
     * 
     * @param theFile the file that contains the data
     * @param fixedName name to use for the dataObject
     * @return true if the file is processed successfully
     */
    protected boolean handleSimplePayload(File theFile, String fixedName) throws EmissaryException {
        byte[] theContent = Executrix.readDataFromFile(theFile.getAbsolutePath());
        return processDataObject(theContent, fixedName, theFile, true);
    }

    /**
     * Action to move th file to the done area when successfully processed
     * 
     * @param theFile the file that was processed
     * @return true if the file was renamed
     */
    protected boolean renameFileToDoneArea(File theFile) {
        return renameFileToDoneArea(theFile, getDoneArea());
    }

    /**
     * Action to move th file to the done area when successfully processed using the specified outut area
     * 
     * @param theFile the file that was processed
     * @param outputRoot a specified output root
     * @return true if the file was renamed
     */
    protected boolean renameFileToDoneArea(File theFile, @Nullable String outputRoot) {
        String base = theFile.getPath();
        boolean renamed = false;
        if (holdingArea != null) {
            base = base.substring(holdingArea.length());
        }

        if (outputRoot != null) {
            File dest = new File(outputRoot + "/" + base);
            dest.getParentFile().mkdirs();
            renamed = theFile.renameTo(dest);
            if (renamed) {
                logger.info("{} processed and moved to done area {}", theFile.getName(), outputRoot);
            } else {
                logger.warn("{} processed but could not be moved to done area as {}", theFile.getName(), dest);
            }
        }
        return renamed;
    }

    /**
     * Get the endpoint file name for when the file is move to inProcess
     * 
     * @param theFile the file to be considered
     * @param eatPrefix optional prefix strip from the work bundle
     * @return null if no holdingArea, else the new File endpoint
     */
    @Nullable
    protected File getInProcessFileNameFor(File theFile, @Nullable String eatPrefix) {
        String base = theFile.getPath();
        if (eatPrefix != null) {
            base = base.substring(eatPrefix.length());
            logger.debug("Using base of {} due to eatPrefix of {} chopped from incoming {}", base, eatPrefix, theFile);
        }

        if (holdingArea != null) {
            return new File(holdingArea + "/" + base);
        }
        return null;
    }

    /**
     * Action to move the file to inProcess area when taking ownership
     * 
     * @param source the file to be renamed
     * @param dest where it should end up, or use the holdingArea if nil
     * @return true if renamed, false if not
     */
    protected boolean renameToInProcessAreaAs(File source, @Nullable File dest) {
        if (holdingArea == null && dest == null) {
            logger.warn("Holding area not configured, cannot rename {}", source);
            return false;
        }

        if (dest == null) {
            dest = getInProcessFileNameFor(source, null);
        } else {
            dest.getParentFile().mkdirs();
        }

        boolean renamed = source.renameTo(dest);
        if (renamed) {
            logger.debug("{} moved to inProcess area as {}", source.getName(), dest);
        } else {
            logger.warn("{} could not be moved to inProcess area {}", source.getName(), dest);
        }
        return renamed;
    }


    /**
     * Action to move the file to the error area due to failure to process
     * 
     * @param theFile the file to move
     * @return true if the rename was successful
     */
    protected boolean renameFileToErrorArea(File theFile) {
        boolean renamed = theFile.renameTo(new File(errorArea, theFile.getName()));
        if (renamed) {
            logger.warn("{} failed and is moved to the error area", theFile.getName());
        } else {
            logger.error("{} failed and could not be moved to the error area", theFile.getName());
        }
        return renamed;
    }

    /**
     * Action to delete the file from the holding area
     * 
     * @param theFile file to delete
     */
    protected void deleteFileFromHoldingArea(File theFile) {
        boolean deleted = theFile.delete();
        if (deleted) {
            logger.info("{} processed and deleted", theFile.getName());
        } else {
            logger.warn("{} processed but could not be deleted", theFile.getName());
        }
    }

    /**
     * File was successfully processed, take appropriate action
     * 
     * @param theFile the file that was processed
     */
    protected void handleFileSuccess(File theFile) {
        handleFileSuccess(theFile, getDoneArea());
    }

    /**
     * File was successfully processed, take appropriate action using specified done area
     * 
     * @param theFile the file that was processed
     * @param outputRoot the specified output done area
     */
    protected void handleFileSuccess(File theFile, @Nullable String outputRoot) {
        if (outputRoot != null) {
            logger.debug("Handling file success by moving to doneArea {}", theFile);
            renameFileToDoneArea(theFile, outputRoot);
        } else if (holdingArea != null) {
            logger.debug("Handling file success by deleting from holdingArea {}", theFile);
            deleteFileFromHoldingArea(theFile);
        } else {
            logger.debug("Neither Done nor Holding areas defined, leaving file as {}", theFile.getName());
        }
    }

    /**
     * File failed to process, take appropriate action
     * 
     * @param theFile the file that failed
     */
    protected void handleFileError(File theFile) {
        if (errorArea != null) {
            logger.debug("Handling file error case for {}", theFile);
            renameFileToErrorArea(theFile);
        } else {
            logger.warn("There is no error location defined and the file did not process. It is stuck at {}", theFile.getName());
        }
    }

    /**
     * Build a data object and handle the data bytes
     * 
     * @param theContent the data bytes
     * @param fixedName good short name for the data
     * @param theFile where it came from
     * @param simpleMode simple flag from the input
     * @return true if it works
     */
    protected boolean processDataObject(byte[] theContent, String fixedName, File theFile, boolean simpleMode) throws EmissaryException {
        IBaseDataObject d = DataObjectFactory.getInstance(new Object[] {theContent, fixedName});
        return processDataObject(d, fixedName, theFile, simpleMode);
    }

    /**
     * Set up the dataobject and send it on the way
     * 
     * @param d the nascent data object
     * @param fixedName the short name of it
     * @param theFile where it came from
     * @param simpleMode simple flag from the input
     * @return true if it works
     */
    protected boolean processDataObject(IBaseDataObject d, String fixedName, File theFile, boolean simpleMode) throws EmissaryException {
        String currentForm = d.popCurrentForm();
        if (currentForm == null) {
            // Add our stuff to the form stack if none is set (e.g from DecomposedSession)
            for (int j = initialFormValues.size() - 1; j >= 0; j--) {
                d.pushCurrentForm(initialFormValues.get(j));
            }
        } else {
            d.pushCurrentForm(currentForm);
        }

        d.setParameter("SIMPLE_MODE", Boolean.toString(simpleMode));
        dataObjectCreated(d, theFile);
        logger.info("**Deploying an agent for {} and object {} forms={} simple={}", fixedName, d.getInternalId(), d.getAllCurrentForms(),
                simpleMode ? "simple" : "");

        assignToPooledAgent(d, -1L);
        return true;
    }

    /**
     * Parse out sessions and process data from a file
     * 
     * @param theFile file to process
     * @param fixedName the good short name of the file
     * @return count of sessions parsed
     * @throws IOException If there is some I/O problem.
     */
    public int processSessions(File theFile, String fixedName) throws IOException, ParserException {
        // We are going to prefer a RAF parser if one
        // is available so start by getting the file opened
        logger.debug("PickUpPlace: Starting on {}", theFile.getName());
        int sessionNum = 0;
        try (RandomAccessFile raf = new RandomAccessFile(theFile, "r")) {

            // Get the right type of session parser
            SessionParser sp = parserFactory.makeSessionParser(raf.getChannel());
            logger.debug("Using session parser from raf ident {}", sp.getClass().getName());

            // ... and a session producer to crank out the data objects...
            SessionProducer dof = new SessionProducer(sp);

            long fileStart = System.currentTimeMillis();
            long totalSize = 0;

            // For each session get a data object from the producer
            boolean isParserComplete = false;
            while (!isParserComplete) {
                long sessionStart = System.currentTimeMillis();
                try {
                    // Use filename-xx for default name
                    String sessionName = fixedName + "-" + (sessionNum + 1);

                    IBaseDataObject dataObject = dof.getNextSession(sessionName);
                    logger.debug("Pulled session {} from {} shortName={}", sessionName, theFile.getName(), dataObject.shortName());
                    sessionNum++;
                    long sessionEnd = System.currentTimeMillis();
                    totalSize += dataObject.data().length;
                    logger.info("sessionParseMetric:{},{},{},{},{},{}", sessionEnd - sessionStart, sp.getClass().getName(), theFile, sessionName,
                            sessionNum, dataObject.data().length);
                    processDataObject(dataObject, sessionName, theFile, false);
                } catch (ParserEOFException eof) {
                    // expected at end of file
                    isParserComplete = true;
                    long fileEnd = System.currentTimeMillis();
                    logger.info("fileParseMetric:{},{},{},{},{}", fileEnd - fileStart, sp.getClass().getName(), theFile, sessionNum, totalSize);
                } catch (EmissaryException ex) {
                    logger.error("Could not dispatch {}", theFile.getName(), ex);
                    throw new ParserException("Could not process" + theFile.getName(), ex);
                }
            }
        }

        logger.debug("Done processing {} sessions from {}", sessionNum, theFile.getName());
        return sessionNum;
    }

    /**
     * Parse out sessions and process data from a byte array
     * 
     * @param data the bytes to process
     * @param fixedName the good short name of the file
     * @param theFile file object representing path data belongs to
     * @return count of sessions parsed
     */
    public int processSessions(byte[] data, String fixedName, File theFile) {
        // We are going to prefer a byte array parser since
        // the data is already in memory
        logger.debug("PickUpPlace: Starting on {}", theFile.getName());
        int sessionNum = 0;

        // Get the right type of session parser
        SessionParser sp = parserFactory.makeSessionParser(InMemoryChannelFactory.create(data).create());

        // .. and a session producer to crank out the data objects...
        SessionProducer dof = new SessionProducer(sp);

        // For each session get a data object from the producer
        boolean isParserComplete = false;
        while (!isParserComplete) {
            try {
                // Use filename-xx for default name
                String sessionName = fixedName + "-" + (sessionNum + 1);

                IBaseDataObject dataObject = dof.getNextSession(sessionName);
                sessionNum++;
                processDataObject(dataObject, sessionName, theFile, false);
            } catch (ParserEOFException eof) {
                // expected at end of file
                isParserComplete = true;
            } catch (EmissaryException ex) {
                logger.error("Could not dispatch {}", fixedName, ex);
            }
        }

        logger.debug("Done processing {} sessions from {}", sessionNum, theFile.getName());
        return sessionNum;
    }

    /**
     * Produce a legal tracking filename from the disk filename
     * 
     * @return fixed filename
     */
    @Nullable
    protected String fixFileName(@Nullable String v) {
        if (v == null) {
            return null;
        }
        String s = v.replace(' ', '_');
        s = s.replace('\t', '_');
        s = s.replace('\n', '_');
        s = s.replace('\r', '_');
        s = s.replace('\f', '_');
        s = s.replace(':', '_');

        if (s.startsWith(".")) {
            s = "_dot_" + s.substring(1);
        }

        return s;
    }

    /**
     * Retrieve and agent from the pool and assign the payload to it
     * 
     * @param payload the payload for the agent
     * @param timeoutMs maximum time in millis to wait for an agent from the pool. Set to -1 to wait forever. The specified
     *        time will not be strictly observed because the pool itself blocks for a configurable amount of time when
     *        requesting an agent. We will wait no more than the specified timeoutMs + the configured pool timeout value.
     * @throws EmissaryException when an agent cannot be obtained
     */
    public void assignToPooledAgent(IBaseDataObject payload, long timeoutMs) throws EmissaryException {
        assignToPooledAgent(payload, agentPool, this, timeoutMs);
    }

    /**
     * Retrieve and agent from the specified pool and assign the payload to it
     * 
     * @param payload the payload for the agent
     * @param agentPool the pool of agents
     * @param startingLocation the agent launch point
     * @param timeoutMs maximum time in millis to wait for an agent from the pool. Set to -1 to wait forever. The specified
     *        time will not be strictly observed because the pool itself blocks for a configurable amount of time when
     *        requesting an agent. We will wait no more than the specified timeoutMs + the configured pool timeout value.
     * @return mobile agent assigned to pool
     * @throws EmissaryException when an agent cannot be obtained
     */
    @SuppressWarnings("ThreadPriorityCheck")
    public static IMobileAgent assignToPooledAgent(IBaseDataObject payload, @Nullable AgentPool agentPool, IServiceProviderPlace startingLocation,
            long timeoutMs) throws EmissaryException {
        IMobileAgent agent = null;
        long startTime = System.currentTimeMillis();
        boolean warningGiven = false;
        int loopCount = 0;

        MDC.put(MDCConstants.SHORT_NAME, payload.shortName());
        try {

            if (agentPool == null) {
                agentPool = AgentPool.lookup();
            }

            do {
                loopCount++;
                try {
                    agent = agentPool.borrowAgent();
                } catch (Exception e) {
                    if (!warningGiven) {
                        slogger.debug("Cannot get agent from pool, trying again ", e);
                        warningGiven = true;
                    }
                }
            } while (agent == null && (timeoutMs < 0 || (startTime + timeoutMs) < System.currentTimeMillis()));

            if (agent == null) {
                throw new EmissaryException("No agent found for " + payload.shortName() + " after " + loopCount + " tries.");
            } else if (loopCount > 1) {
                slogger.info("Found agent after {} tries", loopCount);
            }

            agent.go(payload, startingLocation);
            Thread.yield();
        } finally {
            MDC.remove(MDCConstants.SHORT_NAME);
        }

        return agent;
    }

    public static boolean implementsPickUpPlace(Class<? extends Object> clazz) {
        return ClassComparator.isaImplementation(clazz, IPickUpPlace.class);
    }
}
