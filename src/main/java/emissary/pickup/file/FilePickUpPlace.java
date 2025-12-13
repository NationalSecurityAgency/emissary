package emissary.pickup.file;

import emissary.pickup.IPickUp;
import emissary.pickup.PickUpPlace;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Monitor one or more directories and pickup files for the system
 */
public class FilePickUpPlace extends PickUpPlace implements IPickUp {

    // How often to check directories in millis
    protected int pollingInterval = 30000;

    // List of FileDataServer objects
    protected List<FileDataServer> theDataServer = new ArrayList<>();

    // How many files the FileDataServer should group
    protected static final int DEFAULT_BUNDLE_SIZE = 20;
    protected int bundleSize;

    // Input directories to poll
    protected String[] inputDataDirs;

    /**
     * Create using default configuration
     */
    public FilePickUpPlace() throws IOException {
        super();
        configurePlace();
        startDataServer();
    }

    /**
     * Create, configure, and register
     */
    public FilePickUpPlace(String configInfo, String dir, String placeLoc) throws IOException {

        super(configInfo, dir, placeLoc);
        configurePlace();
        startDataServer();
    }

    /**
     * Configure this place
     * <ul>
     * <li>POLLING_INTERVAL: how long to sleep between directory polls</li>
     * <li>BUNDLE_SIZE: how many files to group in a bundle</li>
     * <li>INPUT_DATA: one or more directories to pull files from</li>
     * </ul>
     */
    protected void configurePlace() {
        pollingInterval = configG.findIntEntry("POLLING_INTERVAL", pollingInterval);
        bundleSize = configG.findIntEntry("BUNDLE_SIZE", DEFAULT_BUNDLE_SIZE);
        List<String> params = configG.findEntries("INPUT_DATA");
        inputDataDirs = params.toArray(new String[0]);
    }

    /**
     * Shutdown the directory monitoring threads and close resources
     */
    @Override
    public void shutDown() {
        stopDiskSpaceMonitoring();
        for (FileDataServer fileDataServer : theDataServer) {
            logger.info("*** Stopping FilePickUpPlace ");
            fileDataServer.shutdown();
        }
        super.shutDown();
    }

    /**
     * Pause the DataServers
     */
    @Override
    public void pause() {
        for (FileDataServer i : theDataServer) {
            logger.info("*** Pausing {} for {}", i.getClass().getName(), getClass().getName());
            i.pause();
        }
    }

    /**
     * Unpause the DataServers
     */
    @Override
    public void unpause() {
        for (FileDataServer i : theDataServer) {
            logger.info("*** Unpausing {} for {}", i.getClass().getName(), getClass().getName());
            i.unpause();
        }
    }


    /**
     * Check the status of the DataServers
     *
     * @return true if any data server is paused, false otherwise
     */
    @Override
    public boolean isPaused() {
        for (FileDataServer i : theDataServer) {
            if (i.isPaused()) {
                return true;
            }
        }
        return false;
    }

    /**
     * For each input directory start a new server thread.
     */
    @SuppressWarnings("ThreadPriorityCheck")
    public void startDataServer() {
        for (int i = 0; i < inputDataDirs.length; i++) {
            FileDataServer fds = new FileDataServer(inputDataDirs[i], this, pollingInterval);

            // Tell it how many files to pick up at a time
            fds.setBundleSize(bundleSize);

            // Set priority below agent processing
            fds.setPriority(Thread.NORM_PRIORITY - 1);

            // It's a thread so use the start method
            fds.start();

            // Add it to our list
            theDataServer.add(fds);
        }

        // Start disk space monitoring after data servers are running
        startDiskSpaceMonitoring();
    }

    /**
     * Called when disk space threshold is exceeded. Pauses all data servers to stop processing new files.
     *
     * @param path the filesystem path being monitored
     * @param usedPercent the current disk usage as a percentage (0-100)
     * @param freeBytes the current number of free bytes available
     */
    @Override
    protected void onDiskSpaceExceeded(Path path, double usedPercent, long freeBytes) {
        logger.warn("Disk space threshold exceeded - pausing pickup");
        pause();
    }

    /**
     * Called when disk space has recovered below the resume threshold. Resumes all data servers.
     *
     * @param path the filesystem path being monitored
     * @param usedPercent the current disk usage as a percentage (0-100)
     * @param freeBytes the current number of free bytes available
     */
    @Override
    protected void onDiskSpaceRecovered(Path path, double usedPercent, long freeBytes) {
        logger.info("Disk space recovered - resuming pickup");
        unpause();
    }

}
