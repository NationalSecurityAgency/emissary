package emissary.output.sink;

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

import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static emissary.roll.Roller.CFG_ROLL_INTERVAL;

/**
 * Sink with a rolling file destination: serialized payloads land in the output directory under generated names.
 */
public abstract class AbstractRollableSink extends AbstractSink {

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
    protected boolean enableObjectTrace = false;

    @Override
    public void initialize(final Configurator configG, @Nullable final String name, final Configurator sinkConfig) {
        super.initialize(configG, name, sinkConfig);
        initOutputConfig();
        initRollConfig();
        this.fileNameGenerator = createFilenameGenerator();
        setupLocalOutputDir();
        setupRoller();
    }

    /** Create the file name generator for rolled output. */
    protected FileNameGenerator createFilenameGenerator() {
        return new DateStampFilenameGenerator(
                StringUtils.isNotBlank(this.name) ? "." + this.name.toLowerCase(Locale.ROOT) : "");
    }

    /** Load the output path, preferring the main config, then the sink config. */
    protected void initOutputConfig() {
        String path = null;
        if (this.configG != null) {
            path = this.configG.findStringEntry(OUTPUT_PATH, null);
        }
        if (path == null && this.sinkConfig != null) {
            path = this.sinkConfig.findStringEntry(OUTPUT_PATH, null);
        }
        this.defaultOutputPath = path != null ? path : this.defaultOutputPath;
        this.outputPath = Path.of(this.defaultOutputPath);
    }

    /** Load rolling configuration. */
    protected void initRollConfig() {
        if (this.sinkConfig == null) {
            return;
        }
        this.maxRollFileSize = (int) this.sinkConfig.findSizeEntry(MAX_ROLL_FILE_SIZE, this.maxRollFileSize);
        this.maxOutputAppenders = this.sinkConfig.findIntEntry(MAX_OUTPUT_APPENDERS, AgentPool.computePoolSize());
        this.rollInterval = this.sinkConfig.findLongEntry(CFG_ROLL_INTERVAL, this.rollInterval);
        this.rollIntervalUnits = TimeUnit.valueOf(this.sinkConfig.findStringEntry(ROLL_INTERVAL_UNIT, this.rollIntervalUnits.toString()));
        this.enableObjectTrace = this.sinkConfig.findBooleanEntry(ENABLE_OBJECT_TRACE, this.enableObjectTrace);
    }

    /** Create the local output directories. */
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

    /** Create the rollable resource and its roller. */
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

    /** Create the rollable resource. */
    protected IJournaler createRollable() throws IOException, InterruptedException {
        return new JournaledCoalescer(this.outputPath, this.fileNameGenerator, this.maxOutputAppenders);
    }

    /** Create the roller managing the roll state. */
    protected Roller createRoller() {
        return new Roller(this.rollIntervalUnits, this.rollInterval, this.rollable, this.maxRollFileSize);
    }

    /** Register the roller with the roll manager. */
    protected void manageRoller() {
        RollManager.getManager().addRoller(this.roller);
    }

    @Override
    public WriteStatus write(final List<IBaseDataObject> list, final Map<String, Object> params) {
        if (!accept(list)) {
            logger.debug("Skipping list - not allowed by this sink");
            return WriteStatus.SKIPPED;
        }
        WriteStatus code;
        try (KeyedOutput ko = this.rollable.getOutput()) {
            params.put("CONTENT_URI_" + getName(), "file://" + ko.getFinalDestination().toString());
            params.put("CONTENT_FORMAT_" + getName(), getName());
            code = write(list, params, ko);
            if (code == WriteStatus.SUCCESS) {
                ko.commit();
            }

            // Emit object tracing events
            if (this.enableObjectTrace) {
                for (IBaseDataObject d : list) {
                    ObjectTracingService.emitLifecycleEvent(d, d.getFilename(), ObjectTracing.Stage.DROP_OFF, true, this.name,
                            String.valueOf(ko.getFinalDestination().getFileName()));
                }
            }
        } catch (IOException e) {
            logger.error("IOException during dropoff.", e);
            code = WriteStatus.FAILURE;
        }
        return code;
    }

    @Override
    public WriteStatus write(final IBaseDataObject d, final Map<String, Object> params) {
        if (!accept(d)) {
            logger.debug("Skipping {} - not allowed by this sink", d.shortName());
            return WriteStatus.SKIPPED;
        }
        return write(Collections.singletonList(d), params);
    }
}
