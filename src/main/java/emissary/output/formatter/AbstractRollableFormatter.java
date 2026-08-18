package emissary.output.formatter;

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
import emissary.spi.ObjectTracing;
import emissary.spi.ObjectTracingService;
import emissary.util.io.FileNameGenerator;

import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static emissary.roll.Roller.CFG_ROLL_INTERVAL;

/**
 * Base formatter that rolls its output to files, holding an embedded deny-first filter.
 */
public abstract class AbstractRollableFormatter extends AbstractFormatter {

    protected static final String configDir = System.getProperty(ConfigUtil.CONFIG_DIR_PROPERTY);

    public static final String OUTPUT_PATH = "OUTPUT_PATH";
    public static final String MAX_ROLL_FILE_SIZE = "MAX_FILE_SIZE";
    public static final String MAX_OUTPUT_APPENDERS = "MAX_OUTPUT_APPENDERS";
    public static final String ROLL_INTERVAL_UNIT = "ROLL_INTERVAL_UNIT";
    public static final String ENABLE_OBJECT_TRACE = "ENABLE_OBJECT_TRACE";

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
    protected boolean enableObjectTrace = false;

    /**
     * Stream payload(s) directly to the output. Concrete formatters should override this to write incrementally rather than
     * buffering the entire result in memory.
     *
     * @param out the output stream to write to
     * @param list the payload list
     * @param params the list of parameters
     * @throws IOException if there is an issue outputting the data
     */
    public abstract void writeTo(final OutputStream out, final List<IBaseDataObject> list, final Map<String, Object> params) throws IOException;

    /**
     * Convert payload(s) to an output byte array. Default implementation delegates to {@link #writeTo} via a temporary
     * buffer. Formatters that override {@link #writeTo} inherit this for backward-compatible callers.
     *
     * @param list the payload list
     * @param params the list of parameters
     * @return the byte representation of the payload(s)
     * @throws IOException if there is an issue outputting the data
     */
    public byte[] convert(final List<IBaseDataObject> list, final Map<String, Object> params) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(8192);
        writeTo(buf, list, params);
        return buf.toByteArray();
    }

    @Override
    public void initialize(final Configurator configG, final String name, final Configurator formatterConfig) {
        super.initialize(configG, name, formatterConfig);
        initOutputConfig();
        initRollConfig();
        initFilenameGenerator();
        setupLocalOutputDir();
        setupRoller();
    }

    /**
     * Initialize the output config vars. Checks the formatter-specific config first, then falls back to the main place
     * config.
     */
    protected void initOutputConfig() {
        String path = null;
        if (this.configG != null) {
            path = this.configG.findStringEntry(OUTPUT_PATH, null);
        }
        if (path == null && this.formatterConfig != null) {
            path = this.formatterConfig.findStringEntry(OUTPUT_PATH, null);
        }
        this.defaultOutputPath = path != null ? path : defaultOutputPath;
        this.outputPath = Path.of(this.defaultOutputPath);
    }

    /**
     * Initialize a file name generator
     */
    protected void initFilenameGenerator() {
        this.fileNameGenerator =
                new DateStampFilenameGenerator(StringUtils.isNotBlank(name) ? "." + name.toLowerCase(Locale.getDefault()) : "");
    }

    /**
     * Initialize the roll specific vars
     */
    protected void initRollConfig() {
        if (this.formatterConfig == null) {
            return;
        }
        this.maxRollFileSize = (int) this.formatterConfig.findSizeEntry(MAX_ROLL_FILE_SIZE, maxRollFileSize);
        this.maxOutputAppenders = this.formatterConfig.findIntEntry(MAX_OUTPUT_APPENDERS, AgentPool.computePoolSize());
        this.rollInterval = this.formatterConfig.findLongEntry(CFG_ROLL_INTERVAL, rollInterval);
        this.rollIntervalUnits = TimeUnit.valueOf(this.formatterConfig.findStringEntry(ROLL_INTERVAL_UNIT, rollIntervalUnits.toString()));
        this.enableObjectTrace = this.formatterConfig.findBooleanEntry(ENABLE_OBJECT_TRACE, enableObjectTrace);
    }

    /**
     * Create the local output directories
     */
    @SuppressWarnings("SystemExitOutsideMain")
    protected void setupLocalOutputDir() {
        if (!Files.exists(this.outputPath)) {
            logger.info("Attempting to create {} output directory, {}", getName(), this.outputPath);
            try {
                Files.createDirectories(this.outputPath);
            } catch (IOException e) {
                logger.error("Unable to create directory for {} output, exiting immediately.", getName(), e);
                System.exit(1);
            }
        }
    }

    /**
     * Create the {@link JournaledCoalescer} and {@link Roller}
     */
    @SuppressWarnings("SystemExitOutsideMain")
    protected void setupRoller() {
        try {
            this.rollable = createRollable();
            this.roller = createRoller();
            manageRoller();
            logger.info("Added Roller for {} running every {} {}(s) or on size {} (bytes).", getName(), this.rollInterval,
                    this.rollIntervalUnits, this.maxRollFileSize);
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.error("Unable to instantiate Roller for handling {} file output", getName(), ex);
            System.exit(1);
        }
    }

    /**
     * Create the rollable resource
     *
     * @return the specific journaled coalescer for the formatter
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
        return new Roller(this.rollIntervalUnits, this.rollInterval, this.rollable, this.maxRollFileSize);
    }

    /**
     * Add the roller to the roll manager
     */
    protected void manageRoller() {
        RollManager.getManager().addRoller(this.roller);
    }

    @Override
    public Path resolveOutputFile(final IBaseDataObject d) {
        return this.outputPath.resolve(this.fileNameGenerator.nextFileName());
    }

    @Override
    public int write(final IBaseDataObject payload, final Map<String, Object> params) {
        return write(Collections.singletonList(payload), params);
    }

    @Override
    public int write(final IBaseDataObject payload, final Map<String, Object> params, final OutputStream output) {
        return write(Collections.singletonList(payload), params, output);
    }

    @Override
    public int write(final List<IBaseDataObject> payloadList, final Map<String, Object> params) {
        int code;
        try (KeyedOutput ko = this.rollable.getOutput()) {
            params.put("CONTENT_URI_" + getName(), "file://" + ko.getFinalDestination().toString());
            params.put("CONTENT_FORMAT_" + getName(), getName());
            code = write(payloadList, params, ko);
            if (code == STATUS_SUCCESS) {
                ko.commit();
            }

            // Emit drop off object tracing events
            if (enableObjectTrace) {
                for (IBaseDataObject d : payloadList) {
                    ObjectTracingService.emitLifecycleEvent(d, d.getFilename(), ObjectTracing.Stage.DROP_OFF, true, this.name,
                            String.valueOf(ko.getFinalDestination().getFileName()));
                }
            }
        } catch (IOException e) {
            logger.error("IOException during dropoff.", e);
            code = STATUS_FAILURE;
        }
        return code;
    }

    @Override
    public int write(final List<IBaseDataObject> list, final Map<String, Object> params, final OutputStream output) {

        // We subtract 1 from the list because the first element is currently assumed to be the TLD
        list.get(0).putParameter("DESCENDANT_COUNT", list.size() - 1);

        try {
            writeTo(output, list, params);
            if (appendNewLine) {
                output.write("\n".getBytes());
            }
        } catch (IOException iox) {
            logger.warn("Could not write to log formatter", iox);
            return STATUS_FAILURE;
        }
        return STATUS_SUCCESS;
    }
}
