package emissary.pickup.file;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.Map;

import emissary.core.IBaseDataObject;
import emissary.pickup.IPickUp;
import emissary.pickup.IPickUpSpace;
import emissary.pickup.PickupQueue;
import emissary.pickup.QueServer;
import emissary.pickup.WorkBundle;
import emissary.pickup.WorkUnit;

/**
 * Pull bundles of file info from a WorkSpace and process as a normal FilePickUp. Monitors a queue rather than a
 * directory, but reads files from disk as specified in the received WorkBundle objects. Whether workBundles are
 * processed in simpleMode or not is controlled by the bundle settings not by the inherited configuration of this
 * client.
 */
public class FilePickUpClient extends emissary.pickup.PickUpSpace implements IPickUp {
    /**
     * These parameters determine the enqueing behavior. The desire is to minimize the number of remote calls from WorkSpace
     * or Distributor to an instance of this class with the getQueSize method, and at the same keep all of the places busy.
     * We do this by making the MAX_QUE_SIZE large enough to hold enough files to be processed in pollingInterval. BUT we
     * don't just make the MAX_QUE_SIZE huge because then we use too much memory. Some feeds put stuff on the Que in blocks.
     * If our que is a prime numbered size they cannot fill it completely, which will help prevent blocking maybe.
     */
    protected int pollingInterval = 500;
    protected int MAX_QUE_SIZE = 5;
    protected QueServer queServer;

    // work bundle currently being processed
    protected WorkBundle currentBundle = null;
    protected WorkUnit currentWorkUnit = null;

    // These allow the same config file to drive this
    // place on both windows and unix like systems where
    // the path need to change based on the OS. For example
    // we might have:
    // Unix: /sharedfilesystem/foo/bar/baz
    // Win: G:/foo/bar/baz
    protected String unixInRoot;
    protected String winInRoot;
    protected String unixOutRoot;
    protected String winOutRoot;

    protected MessageDigest digest = null;

    /**
     * Create using default configuration
     */
    public FilePickUpClient() throws IOException {
        super();
        configurePlace();
        configureQueueServer();
    }

    /**
     * Create, configure, and register
     */
    public FilePickUpClient(String configInfo, String dir, String placeLoc) throws IOException {
        super(configInfo, dir, placeLoc);
        configurePlace();
        configureQueueServer();
    }

    /**
     * Create, configure, and register
     */
    public FilePickUpClient(InputStream configInfo, String dir, String placeLoc) throws IOException {
        super(configInfo, dir, placeLoc);
        configurePlace();
        configureQueueServer();
    }

    public FilePickUpClient(InputStream configInfo) throws IOException {
        super(configInfo);
        configurePlace();
        configureQueueServer();
    }

    /**
     * Configure this place
     */
    protected void configurePlace() {
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (Exception ex) {
            logger.warn("Could not initialize message digest: ", ex);
        }

        pollingInterval = configG.findIntEntry("POLLING_INTERVAL", pollingInterval);
        MAX_QUE_SIZE = configG.findIntEntry("MAX_QUE_SIZE", MAX_QUE_SIZE);
        unixInRoot = configG.findStringEntry("UNIX_IN_ROOT", null);
        winInRoot = configG.findStringEntry("WIN_IN_ROOT", null);
        unixOutRoot = configG.findStringEntry("UNIX_OUT_ROOT", null);
        winOutRoot = configG.findStringEntry("WIN_OUT_ROOT", null);
    }

    protected void configureQueueServer() {
        queServer = new FileQueServer(this, new PickupQueue(MAX_QUE_SIZE), pollingInterval);
        queServer.start();
    }

    /**
     * Shut down our que server thread and deregister the place
     */
    @Override
    public void shutDown() {
        logger.info("*** Shutting Down: " + keys.get(0));
        if (queServer != null) {
            logger.info("*** Stopping queue monitor ");
            queServer.shutdown();
        }
        super.shutDown();
    }

    /**
     * Pause the QueServer to stop taking work
     */
    @Override
    public void pause() {
        logger.info("*** Pausing {} for {}", queServer.getClass().getName(), getClass().getName());
        queServer.pause();
    }

    /**
     * Unpause the QueServer to start taking work
     */
    @Override
    public void unpause() {
        logger.info("*** Unpausing {} for {}", queServer.getClass().getName(), getClass().getName());
        queServer.unpause();
    }

    /**
     * Check the status of the QueServer to see if it is taking work
     *
     * @return true if the QueServer is paused, false otherwise
     */
    @Override
    public boolean isPaused() {
        return queServer.isPaused();
    }

    /**
     * Hook for subclasses to alter the file path perhaps based on the OS at runtime. Allows one set of configuration paths
     * to work on the system across operating systems.
     *
     * @param path file path to alter
     * @return altered path
     */
    protected String fixFilePath(String path) {
        path = path.replace('\\', '/');
        if (OS_IS_WINDOWS) {
            if (unixInRoot != null && winInRoot != null && path.startsWith(unixInRoot)) {
                path = path.replaceFirst("\\A.*" + unixInRoot, winInRoot);
                // hopefully it now looks like x:/slkfj/sdkfj/safkj
            }
        } else if (!OS_IS_WINDOWS && winInRoot != null && unixInRoot != null && path.startsWith(winInRoot)) {
            path = path.replaceFirst("\\A.*" + winInRoot, unixInRoot);
        }
        return path;
    }

    /**
     * Find a file in the holding area that matches our guy.
     */
    protected File findFileInHoldingArea(File f, String eatPrefix) {
        if (holdingArea != null) {
            String fpart = f.getName();
            ;
            if (eatPrefix != null) {
                fpart = f.getPath().substring(eatPrefix.length());
            }

            // See if it is sitting at the InProcess level
            File hf = new File(holdingArea + fpart);
            if (hf.exists()) {
                logger.debug("Data recovered from holding area " + hf);
                return hf;
            }
            logger.debug("File did not exist in InProcess area directoy as " + hf);


            // Or if it is one level down due to an emissary.node.name-emissary.node.port dir
            for (File subdir : new File(holdingArea).listFiles(new FileFilter() {
                @Override
                public boolean accept(File d) {
                    return d.isDirectory();
                }
            })) {
                File hdf = new File(subdir + fpart);
                if (hdf.exists()) {
                    logger.debug("Data recovered from holding subdir " + hdf);
                    return hdf;
                }
                logger.debug("File did not exist in nested InProcess area as " + hdf);
            }
        }
        return null;
    }

    /**
     * Call back from queue server when a new bundle is dequeued for processing.
     *
     * @param paths the dequeued item
     * @return true if the files in the WorkBundle were handled
     */
    protected boolean processBundle(WorkBundle paths) {
        boolean success = true;
        currentBundle = paths; // for use by callbacks
        String outputRoot = fixFilePath(paths.getOutputRoot());
        String prefix = "";
        if (null != paths.getEatPrefix()) {
            prefix = fixFilePath(paths.getEatPrefix());
        }

        for (String path : paths.getFileNameList()) {
            boolean wasInHoldingArea = false;
            String opath = path;
            path = fixFilePath(path);
            File f = new File(path);
            String fixedName = fixFileName(f.getName());
            // Ensure it exists
            if (!f.exists()) {
                // If the errorCount is > 0 look in the holding area
                if (paths.getErrorCount() > 0) {
                    logger.debug("Looking for " + f + " in holding area using eatPrefix of " + prefix);
                    File holdFile = findFileInHoldingArea(f, prefix);
                    if (holdFile != null) {
                        logger.info("Switching to found holdArea file " + holdFile);
                        wasInHoldingArea = true;
                        f = holdFile;
                    } else {
                        logger.debug("File was not found in holding area " + f + " using eatPrefix of " + prefix);
                    }
                } else {
                    logger.debug("File does not exist but had errorCount of 0 so not looking in holding area");
                }
            }

            if (!f.exists()) {
                // Try to get the data from the workspace
                logger.debug("Non-existent file " + opath);
                continue;
            }

            // Ensure it can be read
            if (!f.canRead()) {
                logger.warn("Sorry, Cannot read file: " + f.getPath());
                continue;
            }

            // Only process files here, but give a hook
            // for subclasses to handle other things
            if (!f.isFile()) {
                processDirectoryEntry(outputRoot, prefix, paths.getCaseId(), f, paths.getSimpleMode());
                continue;
            }

            // Make sure it is big enough to process
            if (f.length() <= minimumContentLength) {
                logger.warn("Sorry, This file is too small (" + f.length() + " <" + minimumContentLength + "): " + path);
                // No record is made of too small items
                continue;
            }

            // Make sure it is not too big to process
            boolean isOversize = false;
            if (maximumContentLength != -1 && f.length() > maximumContentLength) {
                logger.warn("Sorry, This file is too large (" + f.length() + " <" + maximumContentLength + "): " + path);
                isOversize = true;
                // Let it continue on knowing it is too big
                // as we may need a record of the file
            }

            // Possibly rename the file to a holding area
            // if one is defined
            File toProcess = getInProcessFileNameFor(f, wasInHoldingArea ? holdingArea : prefix);
            if (holdingArea != null && toProcess != null && !wasInHoldingArea) {
                if (!renameToInProcessAreaAs(f, toProcess)) {
                    logger.error("File: " + f.getPath() + " Could not be renamed to: " + toProcess.getPath());
                    continue;
                }
            } else {
                toProcess = f;
            }

            // Start the processing. The file may be in the original
            // location or may be in the holding area
            try {
                success = processDataFile(toProcess, fixedName, isOversize, paths.getSimpleMode(), outputRoot);
                logger.debug("Finished with processDataFile on " + toProcess + " as " + fixedName);
            } catch (Exception e) {
                // Return false and let another
                // processor have a try at this work bundle
                // TODO: What is some files work and some fail?
                handleErrorInBundledFile(toProcess, fixedName, isOversize, simpleMode, e);
                success = false;
                break;
            }
        }
        logger.debug("Finished processBundle " + paths.getBundleId() + " " + (success ? "success" : "failure"));
        return success;
    }

    protected void handleErrorInBundledFile(File toProcess, String fixedName, boolean isOversize, boolean simpleMode, Exception e) {
        // Error either way but louder if debug is on
        if (logger.isDebugEnabled()) {
            logger.error("Cannot complete " + toProcess.getPath() + " as " + fixedName + " [isOversize=" + isOversize + ", simpleMode="
                    + simpleMode + "]", e);
        } else {
            logger.error("Cannot complete " + toProcess.getPath() + " as " + fixedName, e);
        }

        String errDir = getErrorArea();
        // Move the problem file to the error area if there is one
        if (errDir != null) {
            if (!toProcess.renameTo(new File(errDir, toProcess.getName()))) {
                logger.error("Cannot rename " + toProcess.getName() + " to the error location " + errDir);
            } else {
                logger.error("Moved " + toProcess + " to the errorArea " + errDir);
            }
        } else {
            logger.error("There is no configured errorArea in which to drop failed input files like " + toProcess);
        }
    }

    /**
     * Add incoming information to the queue of file names to process and notify anyone waiting on the queue
     *
     * @param paths the WorkBundle object containing files to queue up
     * @return true if it was enqueued, false if we are too busy to handle it
     */
    @Override
    public boolean enque(WorkBundle paths) {
        return queServer.enque(paths);
    }

    /**
     * Return the size of the queue so push mode doesn't send us too much.
     *
     * @return available size on queue
     */
    @Override
    public int getQueSize() {
        return queServer.getQueSize();
    }

    /**
     * A little thread class to wake up once in a while and check the queue for data objects.
     */
    protected class FileQueServer extends QueServer {
        public FileQueServer(IPickUpSpace space, PickupQueue queue, long pollingInterval) {
            super(space, queue, pollingInterval, "FileQueServer");
        }

        /**
         * When taking an item from the queue process it our custom way
         *
         * @param path the bundle from the queue
         */
        @Override
        public boolean processQueueItem(WorkBundle path) {
            return processBundle(path);
        }
    }

    /**
     * Add in a target bin parameter with user and date Override point for subclasses
     *
     * @param d the nascent data object from the SessionProducer
     * @param f the file it came from
     */
    @Override
    protected void dataObjectCreated(IBaseDataObject d, File f) {
        super.dataObjectCreated(d, f);
        String fixedDirName = fixFileName(f.getParent()).replace('\\', '/');
        String eatPrefix = currentBundle.getEatPrefix();
        currentBundle.getOutputRoot();
        boolean simpleParam = Boolean.parseBoolean(d.getStringParameter("SIMPLE_MODE"));
        if (eatPrefix != null && eatPrefix.length() > 0 && fixedDirName.startsWith(eatPrefix)) {
            fixedDirName = fixedDirName.substring(eatPrefix.length());
        }


        // payloadHandler.setup(d);
        d.putParameter("TARGETBIN", fixedDirName);
        d.putParameter(emissary.parser.SessionParser.ORIG_DOC_SIZE_KEY, Integer.valueOf(d.dataLength()));
        d.setPriority(currentBundle.getPriority());

        // Fix up the complete path
        String ep = currentBundle.getEatPrefix();
        String fn = f.getAbsolutePath();
        if (ep != null && fn.startsWith(ep)) {
            fn = fn.substring(ep.length());
        }

        if (simpleParam) {
            d.putParameter("Original-Filename", fn);
        }

        d.putParameter("INPUT_FILEDATE", emissary.util.TimeUtil.getDateAsISO8601(f.lastModified()));
        d.putParameter("INPUT_FILENAME", f.getName());

        // Fix up the case/project metadata, e.g. PROJECT:GERONIMO22
        String cid = currentBundle.getCaseId();
        if (cid != null && cid.indexOf(":") > 0) {
            String[] parts = cid.split(":");
            if (d.getParameter(parts[0]) == null) {
                d.putParameter(parts[0], parts[1]);
            }

            if (simpleParam && fn != null && digest != null) {
                final MessageDigest theDigest = this.digest;
                synchronized (theDigest) {
                    theDigest.reset();
                    byte[] hash = theDigest.digest(fn.getBytes());
                    d.setFilename(parts[1] + "-" + emissary.util.Hexl.toUnformattedHexString(hash));
                }
            }
        } else {
            // Take care of the caseid
            String fixedCaseId = caseIdHook(cid, d.shortName(), f.toString(), d.getParameters());
            if (fixedCaseId == null) {
                // current yyyyjjj
                fixedCaseId = emissary.util.TimeUtil.getCurrentDateOrdinal();
            }
            d.putParameter("DATABASE_CASE_ID", fixedCaseId);
        }
    }

    /**
     * Generate a filename using the file's path and a prefix
     * 
     * @param filePath the path of the file
     * @param prefix a prefix to prepend to the resultant filename
     * @return the generated filename
     */
    protected String createFilename(String filePath, String prefix) {
        final MessageDigest theDigest = this.digest;
        synchronized (theDigest) {
            theDigest.reset();
            byte[] hash = theDigest.digest(filePath.getBytes());
            return new File(prefix + "-" + emissary.util.Hexl.toUnformattedHexString(hash)).getName();
        }
    }

    /**
     * Hook to allow derived classes to handle various aspects of caseId generation. This do-nothing impementation just
     * returns the caseId argument unchanged.
     *
     * @param initialCaseId the initial case id
     * @param sessionName name of the current session
     * @param fileName path and name of file from File.path()
     * @param metadata Map of data object metadata accumulated so far
     * @return fixed up name of the caseId
     */
    protected String caseIdHook(String initialCaseId, String sessionName, String fileName, Map<String, Collection<Object>> metadata) {
        return initialCaseId;
    }


    /**
     * Allow subclasses to do things with work bundles containing directory entries. This would be highly unusual.
     *
     * @param root the outputRoot of the current work bundle
     * @param prefix the prefix of the current work bundle
     * @param caseid the caseid of the current work bundle
     * @param dir the directory entry encountered
     * @param simpleMode true if the workBundle indicated simpleMode
     */
    protected void processDirectoryEntry(String root, String prefix, String caseid, File dir, boolean simpleMode) {
        if (dir != null) {
            logger.warn("Entry " + dir.getName() + " ignored");
        }
    }

    public static void main(String[] args) {
        mainRunner(FilePickUpClient.class.getName(), args);
    }

}
