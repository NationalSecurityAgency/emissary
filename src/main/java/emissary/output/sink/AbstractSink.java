package emissary.output.sink;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.IBaseDataObject;
import emissary.output.filter.IFilterCondition;
import emissary.output.filter.PayloadFilterCondition;
import emissary.output.formatter.IDropOffFormatter;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Base sink that bundles a data formatter with deny-first filters. The sink owns whole-payload eligibility; content
 * (view) and parameter selection is the formatter's concern. The sink resolves the output file path from the formatter,
 * opens the file, and passes the OutputStream to the formatter for serialization.
 */
public abstract class AbstractSink implements ISink {

    protected Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    protected Configurator configG;
    protected Configurator sinkConfig;
    protected String name = "Abstract";

    /** the data formatter for this sink */
    protected IDropOffFormatter formatter;

    /** deny-first filter for whole-payload eligibility */
    protected IFilterCondition payloadFilter = new PayloadFilterCondition();

    @Override
    public void initialize(final Configurator configG, @Nullable final String name, final Configurator sinkConfig) {
        this.configG = configG;
        if (name != null) {
            this.name = name;
        }
        this.sinkConfig = resolveSinkConfig(sinkConfig);
        this.payloadFilter.initialize(this.sinkConfig);
        this.formatter = createFormatter();
        this.formatter.initialize(configG, this.name, this.sinkConfig);
    }

    /**
     * Resolve the sink configuration, preferring the supplied one and otherwise looking up a resource by name/class.
     *
     * @param supplied the configuration explicitly provided, or null to fall back to resource lookup
     * @return the resolved configuration, or null if none could be loaded
     */
    @Nullable
    protected Configurator resolveSinkConfig(@Nullable final Configurator supplied) {
        if (supplied != null) {
            return supplied;
        }

        final List<String> configPreferences = new ArrayList<>();
        configPreferences.add(getClass().getPackage().getName() + "." + this.name + ConfigUtil.CONFIG_FILE_ENDING);
        configPreferences.add(getClass().getName() + "-" + this.name + ConfigUtil.CONFIG_FILE_ENDING);
        configPreferences.add(getClass().getName() + ConfigUtil.CONFIG_FILE_ENDING);

        try {
            return ConfigUtil.getConfigInfo(configPreferences);
        } catch (IOException iox) {
            logger.debug("Could not find {} configuration for {}", getClass().getSimpleName(), this.name, iox);
            return null;
        }
    }

    /**
     * Hook for subclasses to create the data formatter.
     *
     * @return the formatter this sink writes through
     */
    protected abstract IDropOffFormatter createFormatter();

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public boolean isAllowed(final IBaseDataObject d) {
        return this.payloadFilter.accept(d);
    }

    @Override
    public boolean isAllowed(final List<IBaseDataObject> list) {
        return this.payloadFilter.accept(list);
    }

    @Override
    public int write(final IBaseDataObject d, final Map<String, Object> params) {
        if (!isAllowed(d)) {
            logger.debug("Skipping {} - not allowed by this sink", d.shortName());
            return ISink.STATUS_SUCCESS;
        }
        final Path outputFile = this.formatter.resolveOutputFile(d);
        return writeToFile(outputFile, out -> this.formatter.write(d, params, out));
    }

    @Override
    public int write(final List<IBaseDataObject> list, final Map<String, Object> params) {
        if (!isAllowed(list)) {
            logger.debug("Skipping list - not allowed by this sink");
            return ISink.STATUS_SUCCESS;
        }
        final IBaseDataObject target = list.get(0);
        final Path outputFile = this.formatter.resolveOutputFile(target);
        return writeToFile(outputFile, out -> this.formatter.write(list, params, out));
    }

    @Override
    public int write(final IBaseDataObject d, final Map<String, Object> params, final OutputStream output) {
        if (!isAllowed(d)) {
            logger.debug("Skipping {} - not allowed by this sink", d.shortName());
            return ISink.STATUS_SUCCESS;
        }
        return this.formatter.write(d, params, output);
    }

    @Override
    public int write(final List<IBaseDataObject> list, final Map<String, Object> params, final OutputStream output) {
        if (!isAllowed(list)) {
            logger.debug("Skipping list - not allowed by this sink");
            return ISink.STATUS_SUCCESS;
        }
        return this.formatter.write(list, params, output);
    }

    @Override
    public String getOutputSpec() {
        return this.formatter.getOutputSpec();
    }

    @Override
    public String getErrorSpec() {
        return this.formatter.getErrorSpec();
    }

    @Override
    public void close() {
        if (this.formatter != null) {
            this.formatter.close();
        }
    }

    @FunctionalInterface
    private interface WriteAction {
        int write(OutputStream out) throws IOException;
    }

    private int writeToFile(final Path outputFile, final WriteAction action) {
        try {
            Files.createDirectories(outputFile.getParent());
            try (FileOutputStream fos = new FileOutputStream(outputFile.toFile())) {
                return action.write(fos);
            }
        } catch (IOException e) {
            logger.error("Cannot write output to {}", outputFile, e);
            return ISink.STATUS_FAILURE;
        }
    }
}
