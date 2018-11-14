package emissary.place;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import emissary.core.IBaseDataObject;
import emissary.core.ResourceException;
import emissary.directory.KeyManipulator;
import emissary.util.shell.Executrix;

/**
 * Run a command external to the Emissary JVM to process data
 */
public class UnixCommandPlace extends ServiceProviderPlace {
    protected boolean doSynchronized;
    protected String newForm;
    protected String newFormOnError;
    protected String metaDataTag = null;
    protected String alternateView = null;
    protected boolean addAsMetaData = false;
    protected boolean perlChop = false;
    protected boolean NUKE_ALL_PROXIES = false;
    protected boolean keepFilesDebug = false;
    protected String charset = "8859_1";
    protected String logfilename;

    protected Executrix executrix;

    /**
     * Create the place from the specified config file or resource
     * 
     * @param configInfo the config file or resource to use
     */
    public UnixCommandPlace(String configInfo) throws IOException {
        super(configInfo, "UnixCommandPlace.foo.bar.com:8001");
        configurePlace();
    }

    /**
     * Create the place from the specified config file or resource
     * 
     * @param configInfo the config file or resource to use
     * @param dir the name of the controlling directory to register with
     * @param placeLoc string name of this place
     */
    public UnixCommandPlace(String configInfo, String dir, String placeLoc) throws IOException {
        super(configInfo, dir, placeLoc);
        configurePlace();
    }

    /**
     * Create the place from the specified config stream data
     * 
     * @param configInfo the config file or resource to use
     * @param dir the name of the controlling directory to register with
     * @param placeLoc string name of this place
     */
    public UnixCommandPlace(InputStream configInfo, String dir, String placeLoc) throws IOException {
        super(configInfo, dir, placeLoc);
        configurePlace();
    }

    /**
     * Create the place from the specified config stream data
     * 
     * @param configInfo the config file or resource to use
     */
    public UnixCommandPlace(InputStream configInfo) throws IOException {
        super(configInfo);
        configurePlace();
    }

    /**
     * Configure the instance
     * <ul>
     * <li>SYNCHRONIZED_PROCESS: true if you want to synchronize in java, default is false</li>
     * <li>NEW_FORM: new current form on success, default UNKNOWN</li>
     * <li>NEW_FORM_ON_ERROR: new current form on error, default ERROR</li>
     * <li>ADD_AS_ALTERNATE_VIEW: add output as an alt view using name provided as view name</li>
     * <li>ADD_AS_META_DATA: add output as metadata value using name provided as key</li>
     * <li>PERL_CHOP: if true chomp data returned, default false</li>
     * <li>NUKE_ALL_PROXIES: if true remove all of this places proxies from current form stack when done, default false</li>
     * <li>OUTPUT_CHARSET: charset of the process output, default 8859_1</li>
     * <li>KEEP_FILES_DEBUG: when true don't clean up after exec is finished, default false</li>
     * <li>LOG_FILE_NAME: name of output file to translate into logger commands, default: [servicename].log from key</li>
     * </ul>
     * Also all of the config values read by emissary.util.shell.Executrix are needed here
     */
    protected void configurePlace() {
        doSynchronized = configG.findBooleanEntry("SYNCHRONIZED_PROCESS", false);
        newForm = configG.findStringEntry("NEW_FORM", emissary.core.Form.UNKNOWN);
        if (newForm == null && keys.get(0).indexOf(".ID.") > -1) {
            newForm = emissary.core.Form.UNKNOWN;
        }
        if ("<null>".equals(newForm)) {
            newForm = null;
        }
        newFormOnError = configG.findStringEntry("NEW_FORM_ON_ERROR", emissary.core.Form.ERROR);
        alternateView = configG.findStringEntry("ADD_AS_ALTERNATE_VIEW", null);
        metaDataTag = configG.findStringEntry("ADD_AS_META_DATA", null);
        if (metaDataTag != null) {
            addAsMetaData = true;
        }
        perlChop = configG.findBooleanEntry("PERL_CHOP", false);
        NUKE_ALL_PROXIES = configG.findBooleanEntry("NUKE_ALL_PROXIES", false);
        keepFilesDebug = configG.findBooleanEntry("KEEP_FILES_DEBUG", false);
        charset = configG.findStringEntry("OUTPUT_CHARSET", charset);
        executrix = new Executrix(configG);
        logfilename = configG.findStringEntry("LOG_FILE_NAME", KeyManipulator.getServiceName(keys.get(0)) + ".log");
        logger.debug("Configured {} type process with charset {}", executrix.getOutput(), charset);
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
     * get the log file name
     */
    public String getLogFileName() {
        return logfilename;
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
     * Run the file process
     */
    public byte[] fileProcess(String[] cmd, String outputFile) {
        logger.debug("fileProcess({})", Arrays.asList(cmd));
        StringBuilder errbuf = new StringBuilder();
        int result = executrix.execute(cmd, null, errbuf);
        if (result != 0) {
            logger.warn("exec error in fileProcess: {} produced STDERR {}", Arrays.asList(cmd), errbuf.toString());
            return null;
        }
        return Executrix.readDataFromFile(outputFile);
    }

    /**
     * Run the stdout process
     * 
     * @param cmd command with arguments
     * @param chop if true chomp CRLF from output
     * @return bytes of output from command execution
     */
    public byte[] stdOutProcess(String[] cmd, boolean chop) {
        logger.debug("stdOutProcess({},{}) with charset {}", Arrays.asList(cmd), chop, charset);
        StringBuilder outbuf = new StringBuilder();
        StringBuilder errbuf = new StringBuilder();
        int result = executrix.execute(cmd, outbuf, errbuf, charset);
        if (result != 0) {
            logger.warn("exec error in stdOutProcess: {} produced STDERR {}", Arrays.asList(cmd), errbuf.toString());
            return null;
        }
        if (chop) {
            while (outbuf.length() > 0) {
                char c = outbuf.charAt(outbuf.length() - 1);
                if (c == '\n' || c == '\r') {
                    outbuf.setLength(outbuf.length() - 1);
                } else {
                    break;
                }
            }
        }

        try {
            return (outbuf.toString().getBytes(charset));
        } catch (UnsupportedEncodingException e) {
            logger.error("UnixCommandPlace.stdOutProcess charset problem", e);
            return (outbuf.toString().getBytes());
        }
    }

    /**
     * Validate that we should process this data
     */
    protected boolean validDataHook(IBaseDataObject d) {
        return d != null;
    }


    /**
     * Process the data coming from MobileAgent
     * 
     * @param theDataObject payload to process
     */
    @Override
    public void process(IBaseDataObject theDataObject) throws ResourceException {
        if (validDataHook(theDataObject)) {
            if (doSynchronized) {
                synchronizedProcess(theDataObject);
            } else {
                unSynchronizedProcess(theDataObject);
            }
        }
    }


    /**
     * Process the data in a synchronized wrapper
     * 
     * @param theDataObject payload to process
     */
    protected synchronized void synchronizedProcess(IBaseDataObject theDataObject) throws ResourceException {
        processData(theDataObject);
    }


    /**
     * Process the data in an un-synchronized wrapper
     * 
     * @param theDataObject payload to process
     */
    protected void unSynchronizedProcess(IBaseDataObject theDataObject) throws ResourceException {
        processData(theDataObject);
    }


    /**
     * Helper routine to run command on data
     * 
     * @param data the bytes to run the command on
     * @return byte array of output
     */
    protected byte[] runCommandOn(byte[] data) throws ResourceException {
        String[] names = executrix.makeTempFilenames();
        String tempDirName = names[Executrix.DIR];
        String inputFileName = names[Executrix.INPATH];
        String outputFileName = names[Executrix.OUTPATH];
        File tempDir = new File(tempDirName);
        byte[] outputData = null;

        try {
            if (!tempDir.mkdirs()) {
                logger.warn("Could not create temp directory for process {}", tempDirName);
                return outputData;
            }

            boolean written = Executrix.writeDataToFile(data, inputFileName, true);

            if (written) {
                String[] cmd = executrix.getCommand(names);

                if (executrix.getOutput().equals("FILE")) {
                    outputData = fileProcess(cmd, outputFileName);
                } else if (executrix.getOutput().equals("STD")) {
                    outputData = stdOutProcess(cmd, perlChop);
                } else {
                    logger.error("No output type specified");
                }

                logMessages(tempDirName);
            }

        } catch (Exception ex) {
            logger.warn("Bad execution of commands", ex);
            if (ex instanceof InterruptedException) {
                throw new ResourceException(ex); // framework notification to stop
            }
        } finally {
            if (!keepFilesDebug) {
                // delete all files here!!!
                Executrix.cleanupDirectory(tempDir);
            }
        }

        return outputData;

    }

    /**
     * Hook to add command ouput as an alternate view
     * 
     * @param tData the data object we ran the command on
     * @param newForm the name of the alternate view or null
     * @param outputData the result of running the command
     */
    protected void asAlternateViewHook(IBaseDataObject tData, String newForm, byte[] outputData) {
        tData.addAlternateView(newForm != null ? newForm : tData.currentForm(), outputData);
    }

    /**
     * Hook to add command output as metadata
     * 
     * @param tData the data object we ran the command on
     * @param tag the configured name of the new metadata item
     * @param outputData the result of running the command
     */
    protected void asMetaDataHook(IBaseDataObject tData, String tag, byte[] outputData) {
        tData.putParameter(metaDataTag, new String(outputData));
        if (keys.get(0).indexOf(".TRANSFORM.") == -1 && newForm != null) {
            tData.setCurrentForm(newForm);
        }
    }

    /**
     * Hook to set command output as the current form
     * 
     * @param tData the data object the command was run on
     * @param outputData the results of running the command
     */
    protected void asCurrentFormHook(IBaseDataObject tData, byte[] outputData) {
        tData.setCurrentForm(new String(outputData));
    }

    /**
     * Hook to add command output as the data element
     * 
     * @param tData the data object the command was run on
     * @param outputData the results of running the command
     */
    protected void asDataHook(IBaseDataObject tData, byte[] outputData) {
        tData.setData(outputData);
    }

    /**
     * Hook to handle error or null output from command
     * 
     * @param tData the data object the command was run on
     */
    protected void errorHook(IBaseDataObject tData) {
        if (newFormOnError != null) {
            tData.setCurrentForm(newFormOnError);
        }
        tData.addProcessingError("" + keys.get(0) + ": command produced null or no output");
    }

    /**
     * Hook for services not coded in this implementation
     * 
     * @param serviceType the configured service type
     * @param tData data object the command was run on
     * @param outputData results of the command that was run
     */
    protected void serviceHook(String serviceType, IBaseDataObject tData, byte[] outputData) {
        logger.warn("Unknown service type: {}", serviceType);
    }


    /**
     * Run the command and process the results
     * 
     * @param tData the data object to process
     */
    protected void processData(IBaseDataObject tData) throws ResourceException {

        byte[] outputData = runCommandOn(tData.data());
        String serviceType = KeyManipulator.getServiceType(keys.get(0));

        if (serviceType.equals("ID") || serviceType.equals("ANALYZE")) {
            if (outputData == null || outputData.length == 0) {
                errorHook(tData);
            } else if (addAsMetaData) {
                asMetaDataHook(tData, metaDataTag, outputData);
            } else if (alternateView != null) {
                asAlternateViewHook(tData, alternateView, outputData);
            } else {
                asCurrentFormHook(tData, outputData);
            }
        } else if (serviceType.equals("TRANSFORM")) {
            if (outputData == null || outputData.length == 0) {
                errorHook(tData);
            } else if (alternateView != null) {
                asAlternateViewHook(tData, alternateView, outputData);
            } else if (addAsMetaData) {
                asMetaDataHook(tData, metaDataTag, outputData);
            } else {
                asDataHook(tData, outputData);
                if (NUKE_ALL_PROXIES) {
                    nukeMyProxies(tData);
                    if (newForm != null) {
                        tData.pushCurrentForm(newForm);
                    }
                } else if (newForm != null) {
                    tData.setCurrentForm(newForm);
                }
            }
        } else {
            serviceHook(serviceType, tData, outputData);
        }
        return;
    }

    /**
     * Run the class
     */
    public static void main(String[] argv) {
        mainRunner(UnixCommandPlace.class, argv);
    }
}
