package emissary.output.filter;

import static emissary.roll.Roller.CFG_ROLL_INTERVAL;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.IBaseDataObject;
import emissary.output.io.DateStampFilenameGenerator;
import emissary.output.roller.IJournaler;
import emissary.output.roller.JournaledCoalescer;
import emissary.output.roller.journal.KeyedOutput;
import emissary.pool.AgentPool;
import emissary.roll.RollManager;
import emissary.roll.Roller;
import emissary.util.io.FileNameGenerator;
import org.apache.commons.lang.StringUtils;

public abstract class AbstractRollableFilter extends AbstractFilter {

    protected static final String configDir = System.getProperty(ConfigUtil.CONFIG_DIR_PROPERTY);

    public static final String OUTPUT_PATH = "OUTPUT_PATH";
    public static final String MAX_ROLL_FILE_SIZE = "MAX_FILE_SIZE";
    public static final String MAX_OUTPUT_APPENDERS = "MAX_OUTPUT_APPENDERS";
    public static final String ROLL_INTERVAL_UNIT = "ROLL_INTERVAL_UNIT";

    protected String defaultOutputPath = "./out";
    protected Path outputPath;
    protected int maxRollFileSize = 250 * 1024 * 1024;
    protected int maxOutputAppenders;
    protected long rollInterval = 10L;
    protected TimeUnit rollIntervalUnits = TimeUnit.MINUTES;
    protected Roller roller;
    protected IJournaler rollable;
    protected FileNameGenerator fileNameGenerator;
    protected boolean appendNewLine = true;

    /**
     * Method to convert payload(s) to an output type
     *
     * @param list the payload list
     * @param params the list of parameters
     * @return the byte representation of the payload(s)
     * @throws IOException if there is an issue outputting the data
     */
    public abstract byte[] convert(final List<IBaseDataObject> list, final Map<String, Object> params) throws IOException;

    /**
     * Initialization phase hook for the filter with provided filter configuration
     *
     * @param theConfigG passed in configuration object, usually DropOff's config
     * @param filterName the configured name of this filter or null for the default
     * @param theFilterConfig the configuration for the specific filter
     */
    @Override
    public void initialize(final Configurator theConfigG, final String filterName, final Configurator theFilterConfig) {
        super.initialize(theConfigG, filterName, theFilterConfig);
        initOutputConfig();
        initRollConfig();
        initFilenameGenerator();
        setupLocalOutputDir();
        setupRoller();
    }

    /**
     * Initialize the output config vars
     */
    protected void initOutputConfig() {
        this.defaultOutputPath = this.filterConfig.findStringEntry(OUTPUT_PATH, defaultOutputPath);
        this.outputPath = Paths.get(this.defaultOutputPath);
    }

    /**
     * Initialize a file name generator
     */
    protected void initFilenameGenerator() {
        this.fileNameGenerator = new DateStampFilenameGenerator(StringUtils.isNotBlank(filterName) ? "." + filterName.toLowerCase() : "");
    }

    /**
     * Initialize the roll specific vars
     */
    protected void initRollConfig() {
        this.maxRollFileSize = (int) this.filterConfig.findSizeEntry(MAX_ROLL_FILE_SIZE, maxRollFileSize);
        this.maxOutputAppenders = this.filterConfig.findIntEntry(MAX_OUTPUT_APPENDERS, AgentPool.computePoolSize());
        this.rollInterval = this.filterConfig.findLongEntry(CFG_ROLL_INTERVAL, rollInterval);
        this.rollIntervalUnits = TimeUnit.valueOf(this.filterConfig.findStringEntry(ROLL_INTERVAL_UNIT, rollIntervalUnits.toString()));
    }

    /**
     * Create the local output directories
     */
    protected void setupLocalOutputDir() {
        if (!Files.exists(this.outputPath)) {
            logger.info("Attempting to create {} output directory, {}", getFilterName(), this.outputPath);
            try {
                Files.createDirectories(this.outputPath);
            } catch (IOException e) {
                logger.error("Unable to create directory for () output, exiting immediately. ", getFilterName(), e);
                System.exit(1);
            }
        }
    }

    /**
     * Create the {@link JournaledCoalescer} and {@link Roller}
     */
    protected void setupRoller() {
        try {
            this.rollable = createRollable();
            this.roller = createRoller();
            manageRoller();
            logger.info("Added Roller for {} running every {} {}(s) or on size {} (bytes).", getFilterName(), this.rollInterval,
                    this.rollIntervalUnits, this.maxRollFileSize);
        } catch (Exception ex) {
            logger.error("Unable to instantiate Roller for handling {} file output", getFilterName(), ex);
            System.exit(1);
        }
    }

    /**
     * Create the rollable resource
     *
     * @return the specific journaled coalescer for the filter
     * @throws IOException if there is an issue with the output path
     * @throws InterruptedException if the journal is interrupted
     */
    protected IJournaler createRollable() throws IOException, InterruptedException {
        return new JournaledCoalescer(this.outputPath, this.fileNameGenerator, this.maxOutputAppenders);
    }

    /**
     * Create the object to manage the state of the roll
     *
     * @return the roller object
     */
    protected Roller createRoller() {
        return new Roller(this.maxRollFileSize, this.rollIntervalUnits, this.rollInterval, this.rollable);
    }

    /**
     * Add the roller to the roll manager
     */
    protected void manageRoller() {
        RollManager.getManager().addRoller(this.roller);
    }

    @Override
    public int filter(final IBaseDataObject payload, final Map<String, Object> params) {
        params.put(PRE_SORTED, "true");
        return filter(Collections.singletonList(payload), params);
    }

    @Override
    public int filter(final IBaseDataObject payload, final Map<String, Object> params, final OutputStream output) {
        params.put(PRE_SORTED, "true");
        return filter(Collections.singletonList(payload), params, output);
    }

    @Override
    public int filter(final List<IBaseDataObject> payloadList, final Map<String, Object> params) {
        int code;
        try (KeyedOutput ko = this.rollable.getOutput()) {
            params.put("CONTENT_URI_" + getFilterName(), "file://" + ko.getFinalDestination().toString());
            params.put("CONTENT_FORMAT_" + getFilterName(), getFilterName());
            code = filter(payloadList, params, ko);
            if (code == STATUS_SUCCESS) {
                ko.commit();
            }
        } catch (IOException e) {
            logger.error("IOException during dropoff.", e);
            code = STATUS_FAILURE;
        }
        return code;
    }

    @Override
    public int filter(final List<IBaseDataObject> list, final Map<String, Object> params, final OutputStream output) {
        // Important to process them in order if not already sorted
        if (params.get(PRE_SORTED) == null) {
            Collections.sort(list, new emissary.util.ShortNameComparator());
            params.put(IDropOffFilter.PRE_SORTED, Boolean.TRUE);
        }

        // We subtract 1 from the list because the first element is currently assumed to be the TLD
        list.get(0).putParameter("DESCENDANT_COUNT", list.size() - 1);

        try {
            output.write(convert(list, params));
            if (appendNewLine) {
                output.write("\n".getBytes());
            }
        } catch (IOException iox) {
            logger.warn("Could not write to log filter", iox);
            return STATUS_FAILURE;
        }
        return STATUS_SUCCESS;
    }


}
