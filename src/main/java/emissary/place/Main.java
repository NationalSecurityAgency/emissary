package emissary.place;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Pattern;

import emissary.config.ConfigUtil;
import emissary.core.Factory;
import emissary.core.Family;
import emissary.core.Form;
import emissary.core.IBaseDataObject;
import emissary.directory.DirectoryPlace;
import emissary.directory.EmissaryNode;
import emissary.kff.KffDataObjectHandler;
import emissary.log.MDCConstants;
import emissary.parser.ParserEOFException;
import emissary.parser.ParserException;
import emissary.parser.SessionParser;
import emissary.parser.SessionProducer;
import emissary.parser.SimpleParser;
import emissary.util.shell.Executrix;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * This class handles running a the main method from a ServiceProviderPlace instance in a well defined but extensible
 * way. We have common command line options from getStandardOptions that can be used <i>as-is</i> or can be retrieved
 * and augmented with additional options and passed into the command line parser and runner. The normal flow is to use
 * the scripts/run.sh script passing in the name of the class to run followed by options, followed by a file or set of
 * files to process through that place.
 */
public class Main {
    /** Name of the ServiceProviderPlace class to run */
    protected String placeClass;
    /** The arguments from the command line */
    protected String[] args;
    /** File args from command line after options parsed */
    protected String[] fileArgs;
    /** The instantiated service provider place */
    protected IServiceProviderPlace isp;
    /** The location of the config file or stream resource */
    protected String configLocation;
    /** Indicate if the default config has been overridden */
    private boolean configLocIsDefault = true;
    /** Current form for input data */
    protected String currentForm = emissary.core.Form.UNKNOWN;
    /** Where to send output */
    protected PrintStream outStream = System.out;
    /** Output directory for split option */
    protected String baseOutputDir = ".";
    /** True if output should be split to multiple files */
    protected boolean splitOutput = false;
    /** Recurse on input directories */
    protected boolean recurseOnFileArgs = false;
    /** Be extra quiet */
    protected boolean silent = false;
    /** Be extra verbose */
    protected boolean verbose = false;
    /** Print a short one-liner per input, good for ID places */
    protected boolean oneLiner = false;
    /** Regex for which metadata to print out */
    protected String[] metaPatterns;
    /** hash of parameters/values to set before process */
    protected HashMap<String, String> params = new HashMap<String, String>();
    /** Set to turn off Emissary node context and directory setup */
    protected boolean runWithoutContext = false;
    /** The command line options */
    protected Options options = getStandardOptions();
    /** set of alt views to print after processing */
    protected Set<String> viewsToPrint = new HashSet<String>();
    /** Class name of parser to run on input data */
    protected String parserName = SimpleParser.class.getName();
    /** Number of threads to use to process input files */
    protected int numThreads = 1;
    /** The workers that call the place */
    List<Worker> workers = new ArrayList<Worker>();
    /** Queue of payload object for workers to pull from */
    protected final LinkedList<IBaseDataObject> workQueue = new LinkedList<IBaseDataObject>();
    /** hashing support */
    protected KffDataObjectHandler kff = null;
    /** loop on input */
    protected boolean loopOnInput = false;


    // My logger (nb. not used for messages from the place being run)
    protected static final Logger logger = LoggerFactory.getLogger(Main.class);

    /**
     * Create an instance to run a job
     *
     * @param placeClass class name of the place to run
     * @param args the commandline args
     */
    public Main(String placeClass, String[] args) {
        this.placeClass = placeClass;
        this.configLocation = placeClass + ConfigUtil.CONFIG_FILE_ENDING;
        this.args = args;
        initKff();
    }

    /**
     * Initialize the Kff Handler with our policy settings
     */
    protected synchronized void initKff() {
        kff =
                new KffDataObjectHandler(KffDataObjectHandler.TRUNCATE_KNOWN_DATA, KffDataObjectHandler.SET_FORM_WHEN_KNOWN,
                        KffDataObjectHandler.SET_FILE_TYPE);
    }

    /**
     * Return the standard command line options for running a place
     *
     * @return a Jakarta Commons CLI Options object
     */
    public static Options getStandardOptions() {
        Options options = new Options();
        options.addOption("1", "short", false, "short one-line listing per input");
        options.addOption("i", "loop", false, "loop on input");
        options.addOption("a", "altview", true, "name of alternate view to print");
        options.addOption("c", "config", true, "specify config file, resource, or stream name");
        options.addOption("d", "directory", true, "specify output directory for split output");
        options.addOption("h", "help", false, "print usage information");
        options.addOption("l", "level", true, "level for class logger (DEBUG|WARN|INFO|ERROR|FATAL)");
        options.addOption("L", "LEVEL", true, "level for root logger (DEBUG|WARN|INFO|ERROR|FATAL)");
        options.addOption("m", "metadata", true, "print value of named metadata slot (regex ok)");
        options.addOption("o", "output", true, "specify output filename");
        options.addOption("P", "parser", true, "class name of parser to use when loading data");
        options.addOption("p", "parameter", true, "DataObject parameter to set (key=value)");
        options.addOption("R", "recursive", false, "recurse on input directories");
        options.addOption("s", "silent", false, "dont print anything to output");
        options.addOption("S", "split", false, "split the output into separate files");
        options.addOption("t", "type", true, "type of data (current form)");
        options.addOption("T", "threads", true, "number of threads to use (default is 1)");
        options.addOption("v", "verbose", false, "print verbose output");
        options.addOption("X", "nocontext", false, "run without initializing context");
        return options;
    }

    /**
     * Get the currently configured options in order to change them
     *
     * @return the Jakarta Commons CLI Options object currently configured
     */
    public Options getOptions() {
        return options;
    }

    /**
     * Set a new options object. To start with the defaults use getStandardOptions, add your stuff, and set it here.
     *
     * @param options the new options that will be accepted
     */
    public void setOptions(Options options) {
        this.options = options;
    }

    /**
     * Print help and usage info based on the options
     */
    public void printUsage() {
        // automatically generate the help statement
        System.out.println("Emissary Version: " + new emissary.util.Version().toString());
        HelpFormatter formatter = new HelpFormatter();
        String header = null;
        String footer = "    [file1 [ file2 ... fileN]]";
        boolean autoUsage = false;
        formatter.printHelp(placeClass, header, options, footer, autoUsage);
    }

    /**
     * Set the place config location to the specified file, resource, or stream
     *
     * @param s the string config location
     */
    public void setConfigLocation(String s) {
        configLocation = s;
        configLocIsDefault = false;
    }

    /**
     * Get config location value
     */
    public String getConfigLocation() {
        return configLocation;
    }

    /**
     * Set the parser class name
     */
    public void setParserName(String s) {
        parserName = s;
    }

    /**
     * Get the parser class name
     */
    public String getParserName() {
        return parserName;
    }

    /**
     * Set the current form for payloads to process
     *
     * @param s the new current form value
     */
    public void setCurrentForm(String s) {
        currentForm = s;
    }

    /**
     * Get current form value in use
     */
    public String getCurrentForm() {
        return currentForm;
    }

    /**
     * Set number of threads
     */
    public void setThreads(String s) {
        setThreads(Integer.parseInt(s));
    }

    /**
     * Set number of threads
     */
    public void setThreads(int i) {
        numThreads = i;
    }

    /**
     * Get the number of threads to use
     */
    public int getThreads() {
        return numThreads;
    }

    /**
     * Set to recurse on file args specified on command line
     *
     * @param value the new value for the recurse flag
     */
    public void setRecursive(boolean value) {
        logger.debug("Set recursive " + value);
        recurseOnFileArgs = value;
    }

    /**
     * Get value of recursion field
     */
    public boolean isRecursive() {
        return recurseOnFileArgs;
    }

    /**
     * Set to loop on input
     *
     * @param value the new value for the loop flag
     */
    public void setLoopOnInput(boolean value) {
        logger.debug("Set loop " + value);
        loopOnInput = value;
    }

    /**
     * Get value of loop on input field
     */
    public boolean isLoopOnInput() {
        return loopOnInput;
    }

    /**
     * Set value of verbose flag
     *
     * @param value the new value for verbose flag
     */
    public void setVerbose(boolean value) {
        logger.debug("Setting verbose " + value);
        verbose = value;
    }

    /**
     * Get value of verbose flag
     */
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Set the value of the oneLiner flag
     *
     * @param value the new value for the oneLiner flag
     */
    public void setOneLiner(boolean value) {
        logger.debug("Setting one liner " + value);
        oneLiner = value;
    }

    /**
     * Get the value of the oneLiner flag
     */
    public boolean isOneLiner() {
        return oneLiner;
    }

    /**
     * Get the value of the no context flag
     */
    public boolean isRunWithoutContext() {
        return runWithoutContext;
    }

    /**
     * Set the value of the no context flag
     */
    public void setRunWithoutContext(boolean val) {
        logger.debug("Set runWithoutContext " + val);
        runWithoutContext = val;
    }

    /**
     * Get file args remaining after option processing
     *
     * @return the list of file arguments or an empty list
     */
    public List<String> getFileArgs() {
        List<String> l = new ArrayList<String>();
        for (int i = 0; fileArgs != null && i < fileArgs.length; i++) {
            l.add(fileArgs[i]);
        }
        return l;
    }

    /**
     * Set value of silent
     *
     * @param value the new value of the silent flag
     */
    public void setSilent(boolean value) {
        logger.debug("Set silent " + value);
        silent = value;
    }

    /**
     * Get the value of the silent flag
     */
    public boolean isSilent() {
        return silent;
    }

    /**
     * Set the split output flag
     */
    public void setSplitOutput(boolean value) {
        logger.debug("Set split output " + value);
        splitOutput = value;
    }

    /**
     * Get the value of the splitOutput flag
     */
    public boolean isSplitOutput() {
        return splitOutput;
    }

    /**
     * Set the value of the base directory for split output
     */
    public void setBaseOutputDir(String value) {
        baseOutputDir = value;
    }

    /**
     * Get the value of the base output directory for split output
     */
    public String getBaseOutputDir() {
        return baseOutputDir;
    }

    /**
     * Instantiate the parser when one is specified for the bytes of data This could be expanded to use RAF parsers, NIO
     * parsers, etc.
     */
    protected SessionParser createParser(byte[] data) {
        if (getParserName() == null) {
            return null;
        }
        return (SessionParser) Factory.create(getParserName(), data);
    }

    /**
     * Instantiate the specified processing place
     *
     * @return true if it is created
     */
    protected boolean createPlace() {
        String classOnlyName = placeClass.substring(placeClass.lastIndexOf(".") + 1);

        // See if we need to alter the default config location to use the old
        // style class no-package config name
        if (configLocIsDefault) {
            String cf = ConfigUtil.getConfigFile(configLocation);
            File fcf = new File(cf);
            if (!fcf.exists()) {
                cf = ConfigUtil.getConfigFile(classOnlyName + ConfigUtil.CONFIG_FILE_ENDING);
                fcf = new File(cf);
                if (fcf.exists()) {
                    // Save it if long name does not exist but short one does
                    setConfigLocation(classOnlyName + ConfigUtil.CONFIG_FILE_ENDING);
                }
            }
        }

        boolean pseudoNodeCreated = false;
        try {
            EmissaryNode node = new EmissaryNode();
            if (!node.isValid()) {
                System.setProperty(EmissaryNode.NODE_NAME_PROPERTY, "localhost");
                System.setProperty(EmissaryNode.NODE_PORT_PROPERTY, "0000");
                node = new EmissaryNode();
                pseudoNodeCreated = true;

            }
            String placeLoc = "http://" + node.getNodeName() + ":" + node.getNodePort() + "/" + classOnlyName;
            isp =
                    (IServiceProviderPlace) Factory.create(placeClass, configLocation, "EMISSARY_DIRECTORY_SERVICES.STUDY.DIRECTORY_PLACE.http://"
                            + node.getNodeName() + ":" + node.getNodePort() + "/DirectoryPlace", placeLoc);

        } catch (Throwable ex) {
            logger.error("Cannot create " + placeClass + ": " + ex.getMessage());
            return false;
        } finally {
            if (pseudoNodeCreated) {
                System.clearProperty(EmissaryNode.NODE_NAME_PROPERTY);
                System.clearProperty(EmissaryNode.NODE_PORT_PROPERTY);
            }
        }

        return true;
    }

    /**
     * Run the main method in normal testing mode using the standard or configured options and taking the remaining
     * arguments as data files or directories of data files to be processed
     */
    public void run() {
        parseArguments();

        // Create the pool of workers
        logger.debug("Setting up " + getThreads() + " worker threads");
        for (int i = 0; i < getThreads(); i++) {
            Worker w = new Worker();
            new Thread(w, "MainWorker-" + i).start();
            workers.add(w);
        }

        if (isp != null) {
            do {
                // Process the data, loading it into the work queue
                processMainData(fileArgs, null);

                // Wait for the work queue to be empty
                int size = -1;
                synchronized (workQueue) {
                    size = workQueue.size();
                }
                while (size > 0) {
                    int workQueueSize = -1;
                    synchronized (workQueue) {
                        workQueueSize = workQueue.size();
                    }
                    if (workQueueSize < size) {
                        size = workQueueSize;
                        logger.debug("Waiting for work queue " + size + " remaining");
                    }
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ignore) {
                        // empty catch block
                    }
                }
            } while (loopOnInput);
        }

        // Shutdown the workers
        logger.debug("Stopping " + workers.size() + " workers");
        for (int i = 0; i < getThreads(); i++) {
            workers.get(i).stop();
        }

        synchronized (workQueue) {
            workQueue.notifyAll();
        }

        // Wait for all workers to actually stop
        for (int i = 0; i < getThreads(); i++) {
            Worker w = workers.get(i);
            while (!w.isIdle()) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ignore) {
                    // empty catch block
                }
            }
        }
    }

    /**
     * Allow extenders to determine whether args are parsed here or not
     *
     * @param cmd the parsed command line
     * @return true if normal argument processing should continue
     */
    protected boolean preArgumentsHook(CommandLine cmd) {
        // Do nothing in this impl but allow the normal stuff to go on
        return true;
    }

    /**
     * Allow extenders to handle any additional arguments
     *
     * @param cmd the parsed command line
     */
    protected void postArgumentsHook(CommandLine cmd) {
        // Do nothing in this impl.
    }

    /**
     * Parse the command line arguments
     */
    public void parseArguments() {
        CommandLineParser parser = new PosixParser();
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args);
            fileArgs = cmd.getArgs();
            if (fileArgs != null) {
                logger.debug("Parsed arguments and have " + fileArgs.length + " file args remaining");
            }
        } catch (ParseException ex) {
            // oops, something went wrong
            logger.error(placeClass + ": Parsing failed.  Reason: " + ex.getMessage());
            printUsage();
            return;
        }

        boolean shouldContinue = preArgumentsHook(cmd);

        if (shouldContinue) {
            processParsedArguments(cmd);
            postArgumentsHook(cmd);
        }

    }

    protected void processParsedArguments(CommandLine cmd) {
        // Take care of the help option
        if (cmd.hasOption("h")) {
            printUsage();
            return;
        }

        // Set boolean flags
        setSilent(cmd.hasOption("s"));
        setVerbose(cmd.hasOption("v"));
        setRecursive(cmd.hasOption("R"));
        setLoopOnInput(cmd.hasOption("i"));
        setOneLiner(cmd.hasOption("1"));
        setRunWithoutContext(cmd.hasOption("X"));

        // Override config if specified
        if (cmd.hasOption("c")) {
            setConfigLocation(cmd.getOptionValue("c"));
        }

        // Set up a parser if one is specified
        if (cmd.hasOption("P")) {
            setParserName(cmd.getOptionValue("P"));
        }

        // Override current form if specified
        if (cmd.hasOption("t")) {
            setCurrentForm(cmd.getOptionValue("t"));
        }

        // Set up number of threads to use
        if (cmd.hasOption("T")) {
            logger.debug("Thread option is " + cmd.getOptionValue("T"));
            setThreads(cmd.getOptionValue("T"));
        }

        // save parameter key=value pairs to set
        if (cmd.hasOption("p")) {
            setParameters(cmd.getOptionValues("p"));
        }

        // Save regex's for looking at metadata
        setMetaPatterns(cmd.getOptionValues("m"));

        // Save alt view names to print in a set
        String[] altViews = cmd.getOptionValues("a");
        for (int i = 0; altViews != null && i < altViews.length; i++) {
            viewsToPrint.add(altViews[i]);
        }

        // Do some of the things that the normal context initializer does
        boolean pseudoNodeCreated = false;
        if (!isRunWithoutContext()) {
            try {
                EmissaryNode node = new EmissaryNode();
                if (!node.isValid()) {
                    System.setProperty(EmissaryNode.NODE_NAME_PROPERTY, "localhost");
                    System.setProperty(EmissaryNode.NODE_PORT_PROPERTY, "0000");
                    node = new EmissaryNode();
                    pseudoNodeCreated = true;
                }
                new DirectoryPlace("EMISSARY_DIRECTORY_SERVICES.STUDY.DIRECTORY_PLACE.http://" + node.getNodeName() + ":" + node.getNodePort()
                        + "/DirectoryPlace", node);
            } catch (IOException iox) {
                logger.debug("Could not create standalone directory", iox);
            } finally {
                if (pseudoNodeCreated) {
                    System.clearProperty(EmissaryNode.NODE_NAME_PROPERTY);
                    System.clearProperty(EmissaryNode.NODE_PORT_PROPERTY);
                }
            }

            // Initialize charset mappings
            emissary.util.JavaCharSetLoader.initialize();
            logger.debug("Initialized charset mapping subsystem...");

            // / Initialize the metadata dictionary
            emissary.core.MetadataDictionary.initialize();
            logger.debug("Initialized the metadata dictionary...");
        }

        // Create the place
        if (!createPlace()) {
            logger.warn("Unable to run main processing, no place created");
            return;
        }

        // Grab the base output directory for split
        if (cmd.hasOption("d")) {
            setBaseOutputDir(cmd.getOptionValue("d"));
        }

        // Turn on splitting of output if requested
        if (cmd.hasOption("S")) {
            setSplitOutput(true);
        }


        // Redirect the output stream if desired
        if (cmd.hasOption("o")) {
            try {
                setOutputStream(cmd.getOptionValue("o"));
            } catch (IOException iox) {
                logger.error("Cannot redirect output to " + cmd.getOptionValue("o") + ": " + iox.getMessage());
            }

        }
    }

    /**
     * Set the output stream to the new value
     *
     * @param stream the PrintStream to use further output
     */
    public void setOutputStream(PrintStream stream) {
        outStream = stream;
    }

    /**
     * Set the output stream using the name provided
     *
     * @param name name to use in creating a new PrintStream
     */
    public void setOutputStream(String name) throws IOException {
        outStream = new PrintStream(name);
    }

    /**
     * Set parameters on the new DataObject. Format of pattern is key=value.
     *
     * @param keyval the params to use
     */
    public void setParameters(String[] keyval) {
        for (String p : keyval) {
            if (p.indexOf("=") > 0) {
                // limit to two, so key=val1=val2 just turns into
                // "key" = "val1=val2"
                String[] kv = p.split("=", 2);
                params.put(kv[0], kv[1]);
            } else {
                logger.debug("param format must be key=value, skipping  " + p);
            }
        }
    }

    /**
     * return parameters on the new DataObject.
     */
    public HashMap<String, String> getParameters() {
        return params;
    }

    /**
     * Set the metadata print patterns
     *
     * @param patterns the patterns to use
     */
    public void setMetaPatterns(String[] patterns) {
        metaPatterns = patterns;
    }

    /**
     * Get a ref to the processing place being used
     */
    public IServiceProviderPlace getProcessingPlace() {
        return isp;
    }

    /**
     * Hook called before processHeavyDuty with every payload
     *
     * @param payload the just created payload
     * @param attachments empty list that can be populated
     * @return false to stop call to processHeavyDuty from continuing
     */
    protected boolean preProcessHook(IBaseDataObject payload, List<IBaseDataObject> attachments) {
        // Do nothing in this impl but allow processing to continue
        return true;
    }

    /**
     * Hook called after every return from processHeavyDuty
     *
     * @param payload the completed payload
     * @param att the list of attachments returned
     */
    protected void postProcessHook(IBaseDataObject payload, List<IBaseDataObject> att) {
        // Do nothing in this impl.
    }

    /**
     * Hook called after any exception thrown from the call to processHeavyDuty
     *
     * @param payload the completed payload
     */
    protected void postProcessErrorHook(IBaseDataObject payload) {
        // Do nothing in this impl.
    }

    /**
     * Hook called before printing the payload and attachments
     *
     * @param payload the processed payload
     * @param att the list of attachments
     * @return false to stop printing
     */
    protected boolean prePrintHook(IBaseDataObject payload, List<IBaseDataObject> att) {
        // Do nothing here but allow printing to continue
        logger.debug("Preprint hook called");
        return true;
    }

    /**
     * Hook called after printing the payload and attachments
     *
     * @param payload the processed payload
     * @param att the list of attachments
     */
    protected void postPrintHook(IBaseDataObject payload, List<IBaseDataObject> att) {
        // Do nothing in this impl
        logger.debug("Postprint hook called");
    }

    /**
     * Hook called before splitting the output to files
     *
     * @param payload the processed payload
     * @param att the list of attachments
     * @return false to stop the splitting
     */
    protected boolean preSplitHook(IBaseDataObject payload, List<IBaseDataObject> att) {
        // Do nothing here but allow the splitting to continue
        return true;
    }

    /**
     * Hook called after splitting the output
     *
     * @param payload the processed payload
     * @param att the list of attachments
     */
    protected void postSplitHook(IBaseDataObject payload, List<IBaseDataObject> att) {
        // Do nothing in this impl
    }

    /**
     * Process the file arguments from the command-line or recursed directory
     *
     * @param args the remaining (non-option) arguments
     * @param path the path we are operating in, for appending when doing file recursion
     */
    public void processMainData(String[] args, String path) {
        for (int i = 0; i < args.length; i++) {
            String spath = (path != null ? (path + "/") : "") + args[i];
            File f = new File(spath);
            if (f.isDirectory()) {
                if (isRecursive()) {
                    String[] files = f.list();
                    // recursion alert
                    logger.debug("Recursing into directory " + spath);
                    processMainData(files, spath);
                    logger.debug("Leaving recursed directory" + spath);
                    // end recursive call
                } else {
                    outStream.println(spath + ": DIRECTORY");
                }
                continue;
            }

            if (!f.canRead()) {
                outStream.println(spath + ": UNREADABLE");
                continue;
            }

            // Process the file
            processFile(spath);
        }
    }

    /**
     * Process the single named file
     */
    public void processFile(String spath) {
        // This could be expanded to allow configuration to choose
        // to use a RAF or NIO parser instead of forcing a byte array
        // parser
        byte[] content = Executrix.readDataFromFile(spath);
        if (content == null) {
            outStream.println(spath + ": UNREADABLE");
            return;
        }

        SessionParser sp = null;
        try {
            sp = createParser(content);
        } catch (Throwable t) {
            logger.error("Cannot create parser " + getParserName() + ": " + t);
            sp = new SimpleParser(content);
        }

        try {
            SessionProducer source = new SessionProducer(sp, currentForm);
            int count = 1;
            while (true) {
                IBaseDataObject payload = source.getNextSession(spath + (count > 1 ? ("-" + count) : ""));
                kff.hash(payload);

                // Set up filetype if non-default
                if (!emissary.core.Form.UNKNOWN.equals(currentForm)) {
                    payload.setFileType(currentForm);
                }

                // add command line metadata before processing
                payload.setParameters(params);

                // Push this payload onto a queue for the Worker consumers to pull from
                queuePayload(payload);

                if (count % numThreads == 0) {
                    Thread.yield();
                }

                count++;
            }
        } catch (ParserEOFException eof) {
            // expected at end of file
        } catch (ParserException ex) {
            logger.error("File " + spath + " cannot be parsed by " + getParserName() + ": " + ex);
        }
    }

    /**
     * Stuff the payload onto the queue for processing and notify workers
     */
    public void queuePayload(IBaseDataObject payload) {
        synchronized (workQueue) {
            workQueue.addLast(payload);
            workQueue.notifyAll();
        }
    }

    /**
     * Called by the Worker consumer to process the given payload
     */
    public void processPayload(IBaseDataObject payload) {
        List<IBaseDataObject> attachments = new ArrayList<IBaseDataObject>();
        MDC.put(MDCConstants.SHORT_NAME, payload.shortName());
        try {
            boolean shouldContinue = preProcessHook(payload, attachments);
            if (shouldContinue) {
                attachments = isp.agentProcessHeavyDuty(payload);
            }
            postProcessHook(payload, attachments);
        } catch (Throwable pex) {
            payload.replaceCurrentForm(Form.ERROR);
            attachments = Collections.emptyList();
            postProcessErrorHook(payload);
        } finally {
            MDC.remove(MDCConstants.SHORT_NAME);
        }

        if (!isSilent()) {
            boolean shouldPrint = prePrintHook(payload, attachments);

            if (shouldPrint) {
                if (isOneLiner()) {
                    printPayloadOneLiner(payload, attachments.size() + payload.getExtractedRecordCount());
                } else {
                    printPayload(payload);

                    for (IBaseDataObject att : attachments) {
                        printPayload(att);
                    }

                    if (payload.hasExtractedRecords()) {
                        for (IBaseDataObject rec : payload.getExtractedRecords()) {
                            printPayload(rec);
                        }
                    }
                }
            }

            postPrintHook(payload, attachments);
        }

        // Split output into files if specified
        // This is independent of printStream output
        // printing, isSlient, shouldPrint, etc.
        if (isSplitOutput()) {
            boolean shoudSplit = preSplitHook(payload, attachments);
            if (shoudSplit) {
                handleSplitOutput(payload, attachments);
            }
            postSplitHook(payload, attachments);
        }
    }

    /**
     * Print a one-liner for the payload
     *
     * @param payload the processed object
     * @param attachmentCount number of attachments
     */
    public void printPayloadOneLiner(IBaseDataObject payload, int attachmentCount) {
        String ft = payload.getFileType();
        String cf = payload.currentForm();
        outStream.println(payload.getFilename() + ": " + (payload.currentFormSize() > 1 ? payload.getAllCurrentForms() : payload.currentForm())
                + ((ft != null && !ft.equals(cf)) ? (" > " + payload.getFileType()) : "") + " "
                + (attachmentCount > 0 ? ("(" + attachmentCount + ")") : ""));
    }

    /**
     * Print out stuff related to a command-line processed payload
     *
     * @param payload the processed object
     */
    public void printPayload(IBaseDataObject payload) {
        if (payload == null) {
            return;
        }
        outStream.println(payload.getFilename());
        outStream.println("Current form: " + payload.getAllCurrentForms());
        outStream.println("File type: " + payload.getFileType());
        outStream.println("Encoding: " + payload.getFontEncoding());
        outStream.println("Length: " + payload.dataLength());
        if (payload.getNumAlternateViews() > 0) {
            outStream.println("Alt views: " + payload.getAlternateViewNames());
        }
        if (payload.getNumChildren() > 0) {
            outStream.println("Attachments: " + payload.getNumChildren());
        }

        if (metaPatterns != null && metaPatterns.length > 0) {
            for (Map.Entry<String, String> entry : payload.getCookedParameters().entrySet()) {
                for (int i = 0; i < metaPatterns.length; i++) {
                    if (Pattern.matches(metaPatterns[i], entry.getKey())) {
                        outStream.println("Metadata " + entry.getKey() + ": " + entry.getValue());
                    }
                }
            }
        }

        boolean needTrailingCr = true;

        if (payload.dataLength() > 0 && (isVerbose() || (viewsToPrint.contains("MAIN") && payload.getFilename().indexOf(Family.SEP) == -1))) {
            outStream.println("Data: <<EODATA");
            outStream.write(payload.data(), 0, payload.dataLength());
            outStream.println();
            outStream.println("EODATA");
            outStream.println();
            needTrailingCr = false;
        }

        for (String view : viewsToPrint) {
            byte[] av = payload.getAlternateView(view);
            if (av != null) {
                outStream.println("Alternate View " + view);
                outStream.write(av, 0, av.length);
                outStream.println();
                outStream.println();
                needTrailingCr = false;
            }
        }

        if (needTrailingCr) {
            outStream.println();
        }
    }

    /**
     * Split the output to separate files. This is for doing followon processing on the data, so just the data gets output
     * here. One payload or attachment per file. Directories will be created as needed underneath the baseOutputDir (can be
     * specified with -d)
     *
     * @param payload the processed payload
     * @param att the list of attachments
     */
    public void handleSplitOutput(IBaseDataObject payload, List<IBaseDataObject> att) {
        String fn = getBaseOutputDir() + "/" + payload.shortName() + "." + payload.currentForm();
        boolean status = Executrix.writeDataToFile(payload.data(), fn);
        if (status) {
            logger.debug("Wrote output to " + fn);
        } else {
            logger.error("Could not write output to " + fn);
        }

        for (IBaseDataObject part : att) {
            fn = getBaseOutputDir() + "/" + part.shortName() + "." + part.currentForm();
            status = Executrix.writeDataToFile(part.data(), fn);
            if (status) {
                logger.debug("Wrote attachment output to " + fn);
            } else {
                logger.error("Could not write output to " + fn);
            }
        }
    }

    /**
     * Process an IBaseDataObject from teh work queue
     */
    protected class Worker implements Runnable {
        boolean idle = true;
        boolean timeToStop = false;

        public boolean isIdle() {
            return idle;
        }

        public void stop() {
            timeToStop = true;
        }

        /**
         * From the Runnable interface
         */
        @Override
        public void run() {
            IBaseDataObject payload = null;

            while (!timeToStop) {
                // Try to get a payload
                synchronized (workQueue) {
                    try {
                        payload = workQueue.removeFirst();
                    } catch (NoSuchElementException ignore) {
                        // empty catch block
                    }
                }

                // Process a payload if there is one
                if (payload != null) {
                    idle = false;
                    processPayload(payload);
                    payload = null;
                    idle = true;
                }

                // Wait for a payload
                else if (!timeToStop) {
                    synchronized (workQueue) {
                        try {
                            workQueue.wait(1000);
                        } catch (InterruptedException ignore) {
                            // empty catch block
                        }
                    }
                }
            }
        }
    }
}
