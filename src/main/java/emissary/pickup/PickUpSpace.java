package emissary.pickup;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import emissary.server.mvc.adapters.WorkSpaceAdapter;

/**
 * Implementation of a pick up place that talks to a one or more WorkSpace instances for obtaining distributed work.
 */
public abstract class PickUpSpace extends emissary.pickup.PickUpPlace implements IPickUpSpace {
    // List of workspace instances to interact with
    protected List<String> openSpaceNames = new ArrayList<String>();

    // Map of how many consecutive take errors by workspace name
    protected Map<String, Integer> numConsecutiveTakeErrors = new HashMap<String, Integer>();

    // Comms adapter
    protected WorkSpaceAdapter tpa = new WorkSpaceAdapter();

    // Map of last bundle size by workspace name
    protected Map<String, Integer> lastBundleSize = new HashMap<String, Integer>();

    // Map of pending bundles to workspace name to facilitate replying
    protected Map<String, String> pendingBundles = new HashMap<String, String>();

    // Number of consecutive take errors that cause space to close
    protected int TAKE_ERROR_MAX = 10;

    /**
     * Create using default configuration
     */
    public PickUpSpace() throws IOException {
        super();
    }

    /**
     * Create one
     * 
     * @param configInfo path to config file
     * @param dir string key of the directory to register with
     * @param placeLocation string key of this place
     */
    public PickUpSpace(String configInfo, String dir, String placeLocation) throws IOException {
        super(configInfo, dir, placeLocation);
    }

    /**
     * Create one, figuring out the directory automatically
     * 
     * @param configInfo path to config file
     * @param placeLocation string key of this place
     */
    public PickUpSpace(String configInfo, String placeLocation) throws IOException {
        this(configInfo, null, placeLocation);
    }

    public PickUpSpace(InputStream configInfo) throws IOException {
        super(configInfo);
    }

    /**
     * Create one with stream config
     * 
     * @param configStream path to config file
     * @param theDir string key of the directory to register with
     * @param thePlaceLocation string key of this place
     */
    public PickUpSpace(InputStream configStream, String theDir, String thePlaceLocation) throws IOException {
        super(configStream, theDir, thePlaceLocation);
    }

    /**
     * Open a TreeSpace when told and start asking it for data
     * 
     * @param spaceName the remote name of the space to open
     */
    @Override
    public void openSpace(String spaceName) {
        if (openSpaceNames.contains(spaceName)) {
            logger.debug("Open spaces already includes " + spaceName);
        } else {
            openSpaceNames.add(spaceName);
            numConsecutiveTakeErrors.put(spaceName, 0);
            lastBundleSize.put(spaceName, 0);
            logger.debug("Added space " + spaceName + " (" + openSpaceNames.size() + ")");
        }
    }

    /**
     * Close down the named workspace
     */
    @Override
    public void closeSpace(String spaceName) {
        logger.info("Closing down connection to " + spaceName);
        openSpaceNames.remove(spaceName);
        lastBundleSize.remove(spaceName);
        numConsecutiveTakeErrors.remove(spaceName);
    }

    /**
     * Return name of the first space on the list or null if none
     */
    @Override
    public String getSpaceName() {
        if (openSpaceNames.size() > 0) {
            return openSpaceNames.get(0);
        } else {
            return null;
        }
    }

    /**
     * Return the names of all the spaces on the list
     */
    @Override
    public List<String> getSpaceNames() {
        return new ArrayList<String>(openSpaceNames);
    }

    /**
     * Return the count of how many spaces are on the list
     */
    @Override
    public int getSpaceCount() {
        return openSpaceNames.size();
    }

    /**
     * Take up to one item from eacho space that is active This can result in workspace instances being removed from the
     * list if we get a close message from one or if the threshold of consecutive errors is crossed
     * 
     * @return true if we got at least one
     */
    @Override
    public boolean take() {
        if (openSpaceNames.size() == 0) {
            logger.debug("Cannot perform 'take' when no spaces are available");
            return false;
        }

        // Keep track of space we may have to close
        List<String> closers = new ArrayList<String>();

        // We will take up to one bundle per workspace
        int countTaken = 0;
        for (String openSpaceName : openSpaceNames) {
            WorkBundle path = null;
            try {
                path = tpa.outboundWorkSpaceTake(openSpaceName, myKey);
            } catch (Exception ex) {
                logger.error("Failed to take work from " + openSpaceName, ex);
            }

            if (path == null) {
                // Error, record it, but might be transient
                logger.error("Got a null WorkBundle from " + openSpaceName);
                numConsecutiveTakeErrors.put(openSpaceName, numConsecutiveTakeErrors.get(openSpaceName) + 1);
            } else if (path.size() == 0) {
                // Close out message
                closers.add(openSpaceName);
            } else {
                logger.debug("Received bundle of " + path.size() + " from " + openSpaceName);
                lastBundleSize.put(openSpaceName, path.size());
                numConsecutiveTakeErrors.put(openSpaceName, 0);
                pendingBundles.put(path.getBundleId(), openSpaceName);
                if (!enque(path)) {
                    logger.error("Unable to enqueue bundle " + path.getBundleId() + " from " + openSpaceName + ", losing it.");
                }
                countTaken++;
            }
        }
        cleanupFailedSpaces(closers);
        return countTaken > 0;
    }

    /**
     * Clean up any spaces that have crosse the consecutive error message threshold and any that are specified in the
     * argument
     * 
     * @param forceClosers additional spaces to close
     */
    protected void cleanupFailedSpaces(List<String> forceClosers) {
        List<String> closers = new ArrayList<String>(forceClosers);
        for (String s : openSpaceNames) {
            if (getNumConsecutiveTakeErrors(s) > TAKE_ERROR_MAX) {
                logger.error("Closing down space " + s + " due to repeated errors");
                closers.add(s);
            }
        }

        for (String s : closers) {
            closeSpace(s);
        }

        if (closers.size() > 0) {
            logger.debug("Cleaned up " + closers.size() + " workspace instances, " + openSpaceNames.size() + " remaining");
        }
    }

    /**
     * Notify controlling space that a bundle is completed
     * 
     * @param bundleId the bundle that was completed
     * @param itWorked true if bundle processed normally
     */
    @Override
    public void bundleCompleted(String bundleId, boolean itWorked) {
        String openSpaceName = pendingBundles.get(bundleId);
        if (openSpaceName == null) {
            logger.debug("Space is gone before we could notify " + " bundle completion for " + bundleId);
        } else {
            pendingBundles.remove(bundleId);
            tpa.outboundBundleCompletion(openSpaceName, myKey, bundleId, itWorked);
        }
    }

    /**
     * Count consecutive times a WorkSpace.take() made an error
     */
    @Override
    public int getNumConsecutiveTakeErrors(String spaceName) {
        return numConsecutiveTakeErrors.get(spaceName);
    }

    /**
     * The size of the last WorkBundle successfully received
     */
    @Override
    public int getBundleSize(String spaceName) {
        return lastBundleSize.get(spaceName);
    }

    /**
     * Put a new WorkBundle on the queue
     * 
     * @param path the newly arrived WorkBundle object
     */
    @Override
    public abstract boolean enque(WorkBundle path);

    /**
     * Get the available size of the queue
     */
    public abstract int getQueSize();
}
