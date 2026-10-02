package emissary.output.sink;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.IBaseDataObject;
import emissary.output.filter.IFilterCondition;
import emissary.output.formatter.IDropOffFormatter;
import emissary.output.formatter.filter.util.FilterClassFactory;
import emissary.output.sink.filter.AbstractPayloadFilter;
import emissary.output.sink.filter.PayloadFilterCondition;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Sink base: gates which payloads are written, serializes the rest to a stream. File destinations live in
 * {@link AbstractRollableSink}. The formatter shapes the bytes.
 */
public abstract class AbstractSink implements ISink {

    /** Config key listing whole-payload filter conditions. */
    public static final String PAYLOAD_FILTER = "PAYLOAD_FILTER";

    protected final Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    protected Configurator configG;
    /** Shared sink+formatter config; the layers read disjoint keys. */
    protected Configurator sinkConfig;
    protected String name = "Abstract";

    /** Data formatter used for output. */
    protected IDropOffFormatter formatter;

    /** Deny-first whole-payload filters, applied in order; all must accept. */
    protected List<IFilterCondition> payloadFilters = new ArrayList<>();

    @Override
    public void initialize(final Configurator configG, @Nullable final String name, final Configurator sinkConfig) {
        this.configG = configG;
        setName(name != null ? name : defaultName());
        this.sinkConfig = resolveSinkConfig(sinkConfig);
        initializePayloadFilters();
        this.formatter = createFormatter();
        this.formatter.initialize(configG, this.name, this.sinkConfig);
    }

    /** Default name when the place declares no NAME: prefix. */
    protected String defaultName() {
        return "SINK";
    }

    /** Build the payload filter chain; empty config uses the default deny filter. */
    protected void initializePayloadFilters() {
        this.payloadFilters.clear();
        final List<String> declared = this.sinkConfig != null ? this.sinkConfig.findEntries(PAYLOAD_FILTER) : Collections.emptyList();
        if (declared.isEmpty()) {
            final PayloadFilterCondition defaultFilter = new PayloadFilterCondition();
            defaultFilter.initialize(this.sinkConfig);
            this.payloadFilters.add(defaultFilter);
        } else {
            for (final IFilterCondition condition : FilterClassFactory.createFrom(this.sinkConfig, PAYLOAD_FILTER,
                    IFilterCondition.class, "IFilterCondition")) {
                if (condition instanceof AbstractPayloadFilter) {
                    ((AbstractPayloadFilter) condition).configure(this.sinkConfig);
                } else {
                    condition.initialize(this.sinkConfig);
                }
                this.payloadFilters.add(condition);
            }
        }
        logger.info("{} payload filters: {}", this.name, describePayloadFilters(false));
        if (this.logger.isDebugEnabled()) {
            logger.debug("{} payload filter details: {}", this.name, describePayloadFilters(true));
        }
    }

    private String describePayloadFilters(final boolean verbose) {
        if (this.payloadFilters.isEmpty()) {
            return "none (allow all)";
        }
        StringBuilder sb = new StringBuilder();
        for (final IFilterCondition filter : this.payloadFilters) {
            if (sb.length() > 0) {
                sb.append(verbose ? "; then " : " AND ");
            }
            sb.append(filter instanceof AbstractPayloadFilter
                    ? ((AbstractPayloadFilter) filter).describe(verbose)
                    : filter.getClass().getSimpleName());
        }
        return sb.toString();
    }

    /** Supplied config, else [package.NAME, Class-NAME, Class] preference lookup. */
    @Nullable
    protected Configurator resolveSinkConfig(@Nullable final Configurator supplied) {
        if (supplied != null) {
            return supplied;
        }
        final List<String> configPreferences = new ArrayList<>();
        if (this.name != null) {
            configPreferences.add(getClass().getPackage().getName() + "." + this.name + ConfigUtil.CONFIG_FILE_ENDING);
            configPreferences.add(getClass().getName() + "-" + this.name + ConfigUtil.CONFIG_FILE_ENDING);
        }
        configPreferences.add(getClass().getName() + ConfigUtil.CONFIG_FILE_ENDING);
        logger.debug("Looking for sink configuration preferences {}", configPreferences);
        try {
            return ConfigUtil.getConfigInfo(configPreferences);
        } catch (IOException iox) {
            logger.debug("Could not find sink configuration for {}", this.name);
            return null;
        }
    }

    /** Create the data formatter. */
    protected abstract IDropOffFormatter createFormatter();

    /** Whether serialized payloads are newline-terminated. */
    protected boolean isAppendNewLine() {
        return true;
    }

    @Override
    public String getName() {
        return this.name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    @Override
    public boolean accept(final IBaseDataObject d) {
        return this.payloadFilters.stream().allMatch(f -> f.accept(d));
    }

    @Override
    public boolean accept(final List<IBaseDataObject> list) {
        return this.payloadFilters.stream().allMatch(f -> f.accept(list));
    }

    @Override
    public WriteStatus write(final IBaseDataObject d, final Map<String, Object> params) {
        if (!accept(d)) {
            logger.debug("Skipping {} - not allowed by this sink", d.shortName());
            return WriteStatus.SKIPPED;
        }
        return write(Collections.singletonList(d), params);
    }

    @Override
    public WriteStatus write(final IBaseDataObject d, final Map<String, Object> params, final OutputStream output) {
        if (!accept(d)) {
            logger.debug("Skipping {} - not allowed by this sink", d.shortName());
            return WriteStatus.SKIPPED;
        }
        return write(Collections.singletonList(d), params, output);
    }

    @Override
    public WriteStatus write(final List<IBaseDataObject> list, final Map<String, Object> params, final OutputStream output) {
        if (!accept(list)) {
            logger.debug("Skipping list - not allowed by this sink");
            return WriteStatus.SKIPPED;
        }

        // First element is the TLD
        list.get(0).putParameter("DESCENDANT_COUNT", list.size() - 1);

        try {
            this.formatter.writeTo(output, list, params);
            if (isAppendNewLine()) {
                output.write("\n".getBytes(UTF_8));
            }
        } catch (IOException iox) {
            logger.warn("Could not write to sink output", iox);
            return WriteStatus.FAILURE;
        }
        return WriteStatus.SUCCESS;
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
}
