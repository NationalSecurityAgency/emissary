package emissary.pickup.file;

import java.io.File;
import java.io.FilenameFilter;

import emissary.core.Pausable;
import emissary.log.MDCConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Thread to monitor a directory for files
 */
public class FileDataServer extends Pausable {

    // Logger
    protected static final Logger logger = LoggerFactory.getLogger(FileDataServer.class);

    // Directory we will monitor
    protected String theDataDir;
    protected File theDirectory;

    // Ref to my owner
    protected FilePickUpPlace myParent = null;

    // protected int filesInList;

    // Thread safe termination
    protected boolean timeToShutdown = false;

    // How often to check in millis
    protected long pollingInterval = 1000;

    // How many files to group together
    protected int bundleSize = 20;

    /**
     * Create the directory monitor
     * 
     * @param inputDataDirectory directory path to monitor
     * @param parent the FPP that created me
     * @param pollingInterval how often to check for new files in millis
     */
    public FileDataServer(String inputDataDirectory, FilePickUpPlace parent, long pollingInterval) {

        // Name the thread
        super("FileInput-" + inputDataDirectory);

        myParent = parent;
        this.pollingInterval = pollingInterval;
        theDataDir = inputDataDirectory;
        theDirectory = new File(inputDataDirectory);

        if (!theDirectory.isDirectory()) {
            logger.warn(theDirectory.getName() + " is not a directory");
        }

        // Set our priority to below agent processing priority
        this.setPriority(Thread.NORM_PRIORITY - 2);

        this.setDaemon(true);
    }

    /**
     * Set the number of files to group when polling
     * 
     * @param sz the new value for bundleSize
     */
    public void setBundleSize(int sz) {
        bundleSize = sz;
    }

    /**
     * Implement the run method from Thread to start monitoring Runs until the shutdown() method is called
     */
    @Override
    public void run() {

        // Loop can be terminated by calling the shutdown() method
        while (!timeToShutdown) {

            if (checkPaused()) {
                continue;
            }

            String holdDir = myParent.getInProcessArea();
            String errDir = myParent.getErrorArea();

            // Process files currently in the pickup directory, list
            // the first bundleSize in a batch
            String[] fileList = theDirectory.list(new FilenameFilter() {
                final int MAXFILESTOLIST = bundleSize;
                int filesInList = 0;

                @Override
                public boolean accept(File dir, String name) {
                    return (!name.startsWith(".")) && ++filesInList <= MAXFILESTOLIST;
                }
            });

            // Rename all of the selected files out of the
            // polling area
            for (int i = 0; fileList != null && i < fileList.length; i++) {
                File f = new File(theDataDir, fileList[i]);

                if (!f.exists() || !f.isFile() || !f.canRead()) {
                    reportProblem(f, errDir);
                    continue;
                }

                // Move to in process area
                File newFile = new File(holdDir, fileList[i]);
                if (!f.renameTo(newFile)) {
                    // This is normal when many FileDataServers
                    // on multiple machines are looking at the
                    // same underlying filesystem space
                    logger.warn("FileDataServer - file: " + f.getPath() + " Could not be renamed to: " + newFile.getPath());
                    fileList[i] = null;
                }
            }

            int processedCount = 0;

            // Process the batch of files just collected, if any
            for (int i = 0; fileList != null && i < fileList.length; i++) {

                if (fileList[i] == null) {
                    continue;
                }

                // Notify parent to process file
                File newFile = new File(holdDir, fileList[i]);
                try {
                    MDC.put(MDCConstants.SHORT_NAME, fileList[i]);
                    myParent.processDataFile(newFile);
                    processedCount++;
                } catch (Exception e) {
                    logger.warn("***Cannot process {}", newFile, e);
                    boolean renamed = newFile.renameTo(new File(errDir, newFile.getName()));
                    if (!renamed) {
                        logger.warn("***Cannot move {} to the error directory {}", newFile, errDir);
                    }
                } finally {
                    MDC.remove(MDCConstants.SHORT_NAME);
                }
            }


            // Delay for the polling interval if there was
            // nothing to do for this last round, otherwise
            // get right back in there...
            if (processedCount == 0) {
                try {
                    Thread.sleep(pollingInterval);
                } catch (InterruptedException e) { /* Dont care */
                }
            }

        } // end while
    }

    /**
     * Report the problem file and move it to the error location
     * 
     * @param f the file having the problem
     */
    protected void reportProblem(File f, String errDir) {
        MDC.put(MDCConstants.SHORT_NAME, f.getPath());
        try {
            String n = f.getName();
            boolean renamed = false;

            if (f.exists()) {
                if (!f.canRead()) {
                    logger.warn("FileDataServer: cannot read file");
                    renamed = f.renameTo(new File(errDir, n));
                }

                else if (!f.isFile()) {
                    logger.warn("FileDataServer: file is not a normal file");
                    renamed = f.renameTo(new File(errDir, n));
                }

                else if (f.length() <= 0) {
                    logger.warn("FileDataServer: file has zero size");
                    renamed = f.renameTo(new File(errDir, n));
                }

                if (!renamed) {
                    logger.warn("File could not be moved");
                }
            } else {
                logger.warn("File does not exist");
            }
        } finally {
            MDC.remove(MDCConstants.SHORT_NAME);
        }
    }

    /**
     * Shutdown the thread
     */
    public void shutdown() {
        timeToShutdown = true;
    }

}
