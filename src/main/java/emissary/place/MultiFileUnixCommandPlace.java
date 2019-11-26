package emissary.place;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import emissary.core.DataObjectFactory;
import emissary.core.Family;
import emissary.core.IBaseDataObject;
import emissary.core.ResourceException;
import emissary.directory.KeyManipulator;
import emissary.kff.KffDataObjectHandler;
import emissary.util.shell.Executrix;

public class MultiFileUnixCommandPlace extends MultiFileServerPlace implements IMultiFileUnixCommandPlace {
    protected boolean doSynchronized;
    protected List<String> newChildForms;
    protected String newParentForm;
    protected String newErrorForm;
    protected boolean keepFilesDebug = false;
    protected boolean nukeMyProxies = true;
    protected boolean recurseSubDirs = true;
    protected String apBinDir = null;
    protected List<String> binFiles = null;
    protected List<String> binFileExt = null;
    protected List<String> outDirs = null;
    protected String SINGLE_CHILD_FILETYPE = emissary.core.Form.UNKNOWN;
    protected boolean KEEP_PARENT_HASHES_FOR_SINGLE_CHILD = false;
    protected boolean KEEP_PARENT_FILETYPE_FOR_SINGLE_CHILD = false;
    protected static final String DEFAULT_NEW_PARENT_FORM = "SAFE_HTML";
    protected static final String DEFAULT_NEW_CHILD_FORM = emissary.core.Form.UNKNOWN;
    protected static final String DEFAULT_NEW_ERROR_FORM = emissary.core.Form.ERROR;
    protected boolean setTitleToFile = true;
    protected Map<String, String> fileTypesByExtension = new HashMap<String, String>();
    protected String contentFile = null;
    protected Executrix executrix;
    protected String logfilename;
    protected String charset = "UTF-8";

    String placeDisplayName = "Some Place";

    public MultiFileUnixCommandPlace() throws IOException {
        super();
        configurePlace();
    }

    public MultiFileUnixCommandPlace(InputStream configStream) throws IOException {
        super(configStream);
        configurePlace();
    }

    /** Distributed constructor */
    public MultiFileUnixCommandPlace(String configInfo, String dir, String placeLoc) throws IOException {
        super(configInfo, dir, placeLoc);
        configurePlace();
    }

    /** Test constructors */
    public MultiFileUnixCommandPlace(String configInfo) throws IOException {
        super(configInfo, "FooPlace.foo.bar.com:8001");
        configurePlace();
    }

    public MultiFileUnixCommandPlace(String configInfo, String loc) throws IOException {
        super(configInfo, loc);
        configurePlace();
    }

    /**
     * Configure local special variables
     * <ul>
     * <li>CONTENT_FILE: file name to become new parent</li>
     * <li>SYNCHRONIZED_PROCESS: decide whether to synchronize, default false</li>
     * <li>NUKE_MY_PROXIES: remove all place proxies from incoming payload, default true</li>
     * <li>KEEP_FILES_DEBUG: keep all files resulting from exec, default false</li>
     * <li>RECURSE_SUBDIRS: when picking up results, default true</li>
     * <li>AP_BIN_DIR: where to find executables</li>
     * <li>OUT_DIRS: where output goes, default '.'</li>
     * <li>BIN_EXTENSIONS: extension of files to skip when picking up results</li>
     * <li>BIN_FILES: filenames to skip when picking up results</li>
     * <li>NEW_ERROR_FORM: form for error result, default ERROR</li>
     * <li>NEW_PARENT_FORM: form on parent for success, default UNKNOWN, can use '&lt;null&gt;'</li>
     * <li>NEW_CHILD_FORM: new form on extracted data, default UNKNOWN</li>
     * <li>SINGLE_CHILD_FILETYPE: file type for single child</li>
     * <li>KEEP_PARENT_HASHES_FOR_SINGLE_CHILD: when single child is promoted determines if original parent hashes are kept
     * or not, default false</li>
     * <li>KEEP_PARENT_FILETYPE_FOR_SINGLE_CHILD: when single child is promoted determines if original parent filetype is
     * kept or not, default false</li>
     * <li>SET_TITLE_TO_FILENAME: whether to use filename in doc title, defaul true</li>
     * <li>SERVICE_DISPLAY_NAME: pretty name for sprout message</li>
     * <li>CUSTOM_FILE_TYPES: special mapping to set type by file extension</li>
     * <li>LOG_FILE_NAME: name of output file to translate into logger commands, default: [servicename].log from key</li>
     * <li>OUTPUT_CHARSET: charset of the process output, default UTF-8</li>
     * </ul>
     */
    @Override
    public void configurePlace() {
        executrix = new Executrix(configG);
        contentFile = configG.findStringEntry("CONTENT_FILE", null);
        doSynchronized = configG.findBooleanEntry("SYNCHRONIZED_PROCESS", false);
        nukeMyProxies = configG.findBooleanEntry("NUKE_MY_PROXIES", true);
        keepFilesDebug = configG.findBooleanEntry("KEEP_FILES_DEBUG", false);
        recurseSubDirs = configG.findBooleanEntry("RECURSE_SUBDIRS", true);
        apBinDir = configG.findStringEntry("AP_BIN_DIR", "");
        outDirs = configG.findEntries("OUT_DIRS", ".");
        charset = configG.findStringEntry("OUTPUT_CHARSET", charset);

        binFileExt = configG.findEntries("BIN_EXTENSIONS");
        binFiles = configG.findEntries("BIN_FILES");

        newErrorForm = configG.findStringEntry("NEW_ERROR_FORM", DEFAULT_NEW_ERROR_FORM);
        newParentForm = configG.findStringEntry("NEW_PARENT_FORM", DEFAULT_NEW_PARENT_FORM);
        if (newParentForm.equals("<null>")) {
            newParentForm = null;
        }
        newChildForms = configG.findEntries("NEW_CHILD_FORM", DEFAULT_NEW_CHILD_FORM);
        SINGLE_CHILD_FILETYPE = configG.findStringEntry("SINGLE_CHILD_FILETYPE", SINGLE_CHILD_FILETYPE);
        KEEP_PARENT_HASHES_FOR_SINGLE_CHILD = configG.findBooleanEntry("KEEP_PARENT_HASHES_FOR_SINGLE_CHILD", KEEP_PARENT_HASHES_FOR_SINGLE_CHILD);
        KEEP_PARENT_FILETYPE_FOR_SINGLE_CHILD =
                configG.findBooleanEntry("KEEP_PARENT_FILETYPE_FOR_SINGLE_CHILD", KEEP_PARENT_FILETYPE_FOR_SINGLE_CHILD);

        setTitleToFile = configG.findBooleanEntry("SET_TITLE_TO_FILENAME", true);
        placeDisplayName = configG.findStringEntry("SERVICE_DISPLAY_NAME", placeName);
        logfilename = configG.findStringEntry("LOG_FILE_NAME", KeyManipulator.getServiceName(keys.get(0)) + ".log");

        for (String name : configG.findEntries("CUSTOM_FILE_TYPES")) {
            String tmp = configG.findStringEntry(name + "_EXT", null);
            if (tmp == null) {
                logger.warn("Type missing for " + name);
                continue;
            }
            fileTypesByExtension.put(tmp, name);
        }
    }


    /**
     * get the log file name
     */
    public String getLogFileName() {
        return logfilename;
    }

    /**
     * Set a custom executrix, allows easier mocking among other things
     * 
     * @param e the new executrix instance to use
     */
    public void setExecutrix(Executrix e) {
        executrix = e;
    }

    /**
     * Set the output type programatically
     */
    public void setStdOutputCommand() {
        executrix.setOutputStd();
        logger.debug("Output type set to STD");
    }

    /**
     * Set the output type programatically
     */
    public void setFileOutputCommand() {
        executrix.setOutputFile();
        logger.debug("Output type set to FILE");
    }


    /**
     * Log the messages found in the log file
     * 
     * @param tempDir the directory where the command executed
     */
    protected void logMessages(String tempDir) {
        // if there is a log file, read it and log the messages
        try {
            String lfn = tempDir + "/" + logfilename;
            byte[] logdata = Executrix.readDataFromFile(lfn, true);
            if (logdata != null) {
                for (String message : new String(logdata, charset).split("\n")) {
                    logger.info(message);
                }
            }
        } catch (Exception ignore) {
            logger.debug("Error logging messages", ignore);
        }
    }

    /**
     * Get list of things that were excreted from the process
     * 
     * @param tmpDir the process execution area
     * @param inputFileName name of input file so it can be skipped
     */
    protected List<File> getFileList(File tmpDir, String inputFileName) {
        List<File> outFiles = new ArrayList<File>();
        getFileList(tmpDir, inputFileName, outFiles);
        return outFiles;
    }

    /**
     * Get list of things that were excreted from the process by recursively walking the directory
     * 
     * @param tmpDir the process execution area
     * @param inputFileName name of input file so it can be skipped
     * @param outFiles list to which files that are found can be added
     */
    protected void getFileList(File tmpDir, String inputFileName, List<File> outFiles) {
        // Recursive call to walk the subtree of output files/dirs
        for (int d = 0; d < outDirs.size(); d++) {
            logger.debug("outDirs[" + d + "]=" + outDirs.get(d));
            File dir = null;
            if (outDirs.get(d).equals(".")) {
                dir = tmpDir;
            } else {
                dir = new File(tmpDir, outDirs.get(d));
            }
            if (!dir.exists()) {
                logger.warn("Output directory does not exist:" + outDirs.get(d));
                continue;
            }
            File[] files = dir.listFiles();
            for (int f = 0; f < files.length; f++) {
                if (!(files[f].exists() && files[f].canRead())) {
                    logger.warn("cannot access child[" + outFiles.size() + "]:" + files[f].getAbsolutePath());
                    continue;
                }
                if (files[f].isDirectory()) {
                    if (recurseSubDirs) {
                        getFileList(files[f], inputFileName, outFiles);
                    } else {
                        logger.debug("skipping directory: " + files[f].getPath());
                    }
                    continue;
                }
                String fname = files[f].getName();
                if (binFiles.contains(fname)) {
                    logger.debug("Ignoring file '" + fname + "' because it is in BIN_FILES");
                    continue;
                }
                if (fname.equals(getLogFileName())) {
                    logger.debug("Using file " + fname + " as log message source");
                    logMessages(tmpDir.getPath());
                    continue;
                }
                if (fname.indexOf(".") > -1 && binFileExt.contains(fname.substring(fname.lastIndexOf(".") + 1))) {
                    logger.debug("Ignoring file '" + fname + "' because it is in BIN_EXTENSIONS.");
                    continue;
                }
                if (contentFile != null && contentFile.equals(fname)) {
                    logger.debug("Ignoring file '" + fname + "' because it is to be the new parent data.");
                    continue;
                }
                if (inputFileName.endsWith(fname)) {
                    logger.debug("Ignoring file '" + fname + "' because it is the input file.");
                    continue;
                }
                if (files[f].length() == 0) {
                    logger.debug("Ignoring file '" + fname + "' because it is empty.");
                    continue;
                }
                logger.debug("Adding output file '" + fname + "' for processing");
                outFiles.add(files[f]);
            }
        }
        Collections.sort(outFiles, new FileNameComparator());
    }

    protected static class FileNameComparator implements Comparator<File> {
        @Override
        public int compare(File o1, File o2) {
            if (o1 == null && o2 != null) {
                return 1;
            }
            if (o1 != null && o2 == null) {
                return -1;
            }
            if (o1 == null && o2 == null) {
                return 0;
            }
            return o1.getName().compareTo(o2.getName());
        }
    }

    /**
     * Process the result files, turning them into attachments Override hooks: initSprout, preSprout,
     * postSprout,finishSprout
     * 
     * @param parent the original payload
     * @param files the result files
     * @param tempDirName execution area name
     * @param newData data for parent accumulator
     * @return list of attachments
     */
    protected List<IBaseDataObject> sproutResults(IBaseDataObject parent, List<File> files, String tempDirName, StringBuilder newData) {
        List<IBaseDataObject> sprouts = new ArrayList<IBaseDataObject>();

        if (files.size() < 1) {
            logger.warn("NO OUTPUT FILES FOUND!");
            return sprouts;
        }

        int fileCount = files.size();
        int actualFileCount = 0;
        int birthOrder = parent.getNumChildren() + 1;

        if (fileCount > 0 || contentFile != null) {
            parent.setNumChildren(fileCount);
            initSprout(parent, files, newData, tempDirName);

            for (int j = 0; j < fileCount; j++) {
                File f = files.get(j);

                logger.debug("Handling data file " + f.getName());

                if (!f.canRead() || !f.isFile()) {
                    logger.debug("Cannot read from " + f.getAbsolutePath());
                    continue;
                }

                byte[] theData = Executrix.readDataFromFile(f.getAbsolutePath());
                if (theData == null) {
                    logger.debug("Cannot read data from " + f.getAbsolutePath());
                    continue;
                }

                if (!preSprout(theData, parent, f, birthOrder, fileCount, newData)) {
                    continue;
                }

                Map<String, Object> metaData = new HashMap<String, Object>();
                if (setTitleToFile) {
                    metaData.put("DocumentTitle", f.getName());
                }

                List<String> tmpForms = getFormsFromFile(f);

                IBaseDataObject dObj = DataObjectFactory.getInstance(theData, parent.getFilename() + Family.SEP + birthOrder, tmpForms.get(0));

                dObj.putParameters(metaData);
                sprouts.add(dObj);

                actualFileCount++;
                birthOrder++;
                postSprout(theData, parent, f, birthOrder, fileCount, actualFileCount, newData, dObj);
            }

            finishSprout(parent, fileCount, actualFileCount, newData);

            try {
                parent.setData(newData.toString().getBytes(charset));
            } catch (UnsupportedEncodingException e) {
                logger.debug("SproutResults charset problem", e);
                parent.setData(newData.toString().getBytes());
            }
        }
        return sprouts;
    }


    /**
     * Determines the initial forms for a new data object based on the configuration settings and the file name. This method
     * may be customized in sub-classes, but at least one form value must be returned!
     */
    protected List<String> getFormsFromFile(File f) {
        List<String> tmpForms = new ArrayList<String>();

        for (String extension : fileTypesByExtension.keySet()) {
            String newForm = fileTypesByExtension.get(extension);
            if (f.getName().endsWith(extension)) {
                tmpForms.add(newForm);
            }
        }
        if (tmpForms.size() == 0) {
            for (int i = 0; i < newChildForms.size(); i++) {
                tmpForms.add(newChildForms.get(i));
            }
        }
        return tmpForms;
    }


    /**
     * Override hook when attachment processing is about to be started If CONTENT_FILE has been specified that data is read
     * and loaded into the newData accumulator now
     * 
     * @param parent the original payload
     * @param files the result files
     * @param newData accumulator for replacement parent data
     * @param dirName name of process execution area
     */
    protected void initSprout(IBaseDataObject parent, List<File> files, StringBuilder newData, String dirName) {
        logger.debug("initSprout hook contentFile=" + contentFile);
        if (contentFile != null) {
            byte[] fileData = Executrix.readDataFromFile(dirName + File.separator + contentFile);
            if (fileData != null) {
                newData.append(new String(fileData));
            } else {
                logger.debug("Can't find new content file:" + dirName + File.separator + contentFile);
            }
        }
    }


    /**
     * Override hook when attachment processing is finished
     * 
     * @param parent the original payload
     * @param numSubParts the number of attachments handled
     * @param actualFileCount the number of result files processed
     * @param newParentData accumulator for replacement parent data
     */
    protected void finishSprout(IBaseDataObject parent, int numSubParts, int actualFileCount, StringBuilder newParentData) {
        logger.debug("finishSprout hook");
    }

    /**
     * Override hook when an attachment begins processing
     * 
     * @param data the bytes of content for the attachment
     * @param parent the original payload
     * @param f the file the content comes from
     * @param birthOrder for this attachment
     * @param numSubParts the number of attachments handled
     * @param newParentData accumulator for replacement parent data
     * @return true to continue, false to skip this attachment
     */
    protected boolean preSprout(byte[] data, IBaseDataObject parent, File f, int birthOrder, int numSubParts, StringBuilder newParentData) {
        logger.debug("preSprout hook on " + f.getName() + " order=" + birthOrder);
        return true;
    }

    /**
     * Override hook when an attachment finished processing
     * 
     * @param data the bytes of content for the attachment
     * @param parent the original payload
     * @param f the file the content comes from
     * @param birthOrder for this attachment
     * @param numSubParts the number of attachments handled
     * @param actualFileCount count of files to process
     * @param newParentData accumulator for replacement parent data
     * @param theSprout the new data object
     */
    protected void postSprout(byte[] data, IBaseDataObject parent, File f, int birthOrder, int numSubParts, int actualFileCount,
            StringBuilder newParentData, IBaseDataObject theSprout) {
        logger.debug("postSprout hook on " + f.getName() + " order=" + birthOrder);
    }


    /**
     * Process in a custom way when there is only one file result
     * 
     * @param d the parent payload
     * @param f the file to process
     * @return 0 when it works
     */
    protected int processSingleChild(IBaseDataObject d, File f) {
        byte[] theData = Executrix.readDataFromFile(f.getAbsolutePath());
        int result = processSingleChild(d, theData, f);
        return result;
    }


    /**
     * Process in a custom way when there is only one file result
     * 
     * @param d the parent payload
     * @param theData the bytes to process
     * @param f the file the data comes from
     * @return 0 when it works
     */
    protected int processSingleChild(IBaseDataObject d, byte[] theData, File f) {
        String filename = f.getName();
        d.setData(theData);
        if (setTitleToFile) {
            d.putParameter("DocumentTitle", filename);
        }
        List<String> tmpForms = getFormsFromFile(f);
        for (int i = 0; i < tmpForms.size(); i++) {
            d.pushCurrentForm(tmpForms.get(i));
        }
        d.setFileType(SINGLE_CHILD_FILETYPE);
        return (0);
    }

    /**
     * Process an incoming payload in synchronized fashion
     * 
     * @param theDataObject the payload to process
     */
    protected synchronized List<IBaseDataObject> synchronizedProcess(IBaseDataObject theDataObject) throws ResourceException {
        return processData(theDataObject);
    }


    /**
     * Process an incoming payload in non-synchronized fashion
     * 
     * @param theDataObject the payload to process
     */
    protected List<IBaseDataObject> unSynchronizedProcess(IBaseDataObject theDataObject) throws ResourceException {
        return processData(theDataObject);
    }


    /**
     * Process an incoming payload returning a list of attachments
     * 
     * @param tData the payload to process
     */
    @Override
    public List<IBaseDataObject> processHeavyDuty(IBaseDataObject tData) throws ResourceException {
        List<IBaseDataObject> entries = null;

        if (doSynchronized) {
            entries = synchronizedProcess(tData);
        } else {
            entries = unSynchronizedProcess(tData);
        }

        if (entries == null || entries.size() == 0) {
            logger.debug("no messages found in " + tData.getFilename());
            return Collections.emptyList();
        }

        addParentInformation(tData, entries);

        // we'll replace parent with its one child when we can
        if (executrix.getOutput().equals("FILE") && entries.size() == 1 && contentFile == null) {
            IBaseDataObject d = entries.get(0);
            tData.setData(d.data());
            if (KEEP_PARENT_HASHES_FOR_SINGLE_CHILD) {
                KffDataObjectHandler.removeHash(d);
            }
            tData.putUniqueParameters(d.getParameters());
            tData.setCurrentForm(d.currentForm());
            if (!KEEP_PARENT_FILETYPE_FOR_SINGLE_CHILD) {
                tData.setFileType(d.getFileType());
            }
            return Collections.emptyList(); // so we just continue with current
        }

        // Set the parent form after the addParentInformation call
        if (newParentForm != null) {
            logger.debug("Pushing new parent form " + newParentForm);
            tData.pushCurrentForm(newParentForm);
        }

        return entries;
    }

    /**
     * Process an incoming payload returning attachments This entry point is shared among all synchronized, unsynchronized,
     * normal and heavy-duty processing entry points.
     * 
     * @param tData the payload to process
     * @return attachments
     */
    protected List<IBaseDataObject> processData(IBaseDataObject tData) throws ResourceException {
        return processData(tData, 0, tData.dataLength());
    }


    /**
     * Process an incoming payload returning attachments, using only some of the data This entry point is shared among all
     * synchronized, unsynchronized, normal and heavy-duty processing entry points.
     * 
     * @param tData the payload to process
     * @param start offset in data to start
     * @param len length of data to use
     * @return attachments
     */
    protected List<IBaseDataObject> processData(IBaseDataObject tData, int start, int len) throws ResourceException {
        List<IBaseDataObject> sprouts = new ArrayList<IBaseDataObject>();

        // Validate parameters
        if (tData == null) {
            logger.debug("Received null data object!");
            return sprouts;
        }

        // Validate data
        if (tData.data() == null) {
            logger.debug("Received null data: " + tData);
            tData.addProcessingError("NULL data in " + placeName + ".process");
            tData.pushCurrentForm(newErrorForm);
            return sprouts;
        }

        // Validate start and len
        if (start < 0 || len <= 0 || (start + len) > tData.dataLength()) {
            logger.debug("Invalid start/len for data " + start + "/" + len);
            tData.addProcessingError("Invalid data " + start + "/" + len);
            tData.pushCurrentForm(newErrorForm);
            return sprouts;
        }

        // make the directory and write the input file.
        String[] names = null;
        File f = null;
        int result = -1;
        try {
            names = executrix.writeDataToNewTempDir(tData.data(), start, len);
            f = new File(names[Executrix.INPATH]);
            logger.debug("Wrote file out to " + f.getPath());

            // Create the command string and run it
            String[] cmd = executrix.getCommand(names);
            StringBuilder parentData = new StringBuilder();

            logger.debug("Generated command " + Arrays.asList(cmd));

            if (executrix.getOutput().equals("FILE")) {
                result = processCommand(cmd);
            } else if (executrix.getOutput().equals("STD")) {
                StringBuilder errbuf = new StringBuilder();
                result = processCommand(cmd, parentData, errbuf);
                if (errbuf.length() > 0) {
                    tData.addProcessingError(errbuf.toString());
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("STD process => " + parentData.toString() + " ERR process => " + errbuf.toString());
                }
            }

            // Clean out any proxies that would cause an infinite loop.
            // we will add new forms later.
            if (nukeMyProxies) {
                nukeMyProxies(tData);
            }
            logger.debug("Parent forms " + tData.getAllCurrentForms() + ", nuke=" + nukeMyProxies);

            // Generate the list of resulting files in the directory(s)
            List<File> files = getFileList(f.getParentFile(), f.getName());

            if (files != null && files.size() > 0) {
                sprouts = sproutResults(tData, files, f.getParent(), parentData);
            }
        } catch (Exception ex) {
            logger.error("Problem in command execution", ex);
            if (ex instanceof InterruptedException) {
                throw new ResourceException(ex); // framework notification to stop
            }
        } finally {
            // Delete the temporary directory and all of its contents.
            if (f != null) {
                cleanupFiles(f.getParentFile());
            }
        }

        // If there was not result, then report it in 2 places.
        if (sprouts.size() == 0) {
            logger.debug("Command failed. nothing to sprout for file " + tData.getFilename() + "(result=" + result + ")");
            tData.addProcessingError("ERROR in " + placeName + ". Exec returned errno " + result);
            tData.pushCurrentForm(newErrorForm);
        }
        return sprouts;
    }

    /**
     * Execute the command and args in the array
     * 
     * @param cmd the command and args to execute
     * @return the process errno status value
     */
    protected int processCommand(String[] cmd) {
        StringBuilder outbuf = new StringBuilder();
        StringBuilder errbuf = new StringBuilder();
        int status = processCommand(cmd, outbuf, errbuf);
        if (outbuf.length() > 0 && logger.isDebugEnabled()) {
            logger.debug("Discarding process stdout " + outbuf.toString());
        }
        if (errbuf.length() > 0 && logger.isDebugEnabled()) {
            logger.debug("Discarding process stderr " + errbuf.toString());
        }
        return status;
    }

    /**
     * Execute the command and args in the array
     * 
     * @param cmd the command and args to execute
     * @param stdout builder to append stdout from process
     * @param stderr builder to append stderr from process
     * @return the process errno status value
     */
    protected int processCommand(String[] cmd, StringBuilder stdout, StringBuilder stderr) {
        return executrix.execute(cmd, stdout, stderr, charset);
    }


    /**
     * API Compatibility wrapper for Executrix cleanup method
     * 
     * @param tempDir the directory to remove
     */
    protected void cleanupFiles(File tempDir) {
        if (!keepFilesDebug) {
            cleanupDirectory(tempDir);
        }
    }

    /**
     * API Compatibility wrapper for Executrix cleanup method
     * 
     * @param dir the directory to remove
     */
    protected void cleanupDirectory(File dir) {
        boolean status = Executrix.cleanupDirectory(dir);
        if (!status) {
            logger.debug("Could not remove temp directory " + dir);
        }
    }

    /**
     * Test main
     */
    public static void main(String[] argv) {
        mainRunner(MultiFileUnixCommandPlace.class.getName(), argv);
    }
}
