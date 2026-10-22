package emissary.output.sink;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.Factory;
import emissary.core.IBaseDataObject;
import emissary.output.filter.IFilterCondition;
import emissary.output.formatter.IDropOffFormatter;
import emissary.output.sink.filter.PayloadFilterCondition;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Base sink that bundles a data formatter with deny-first filters. The sink owns whole-payload eligibility; content
 * (view) and parameter selection is the formatter's concern. The sink delegates to the formatter for output, which may
 * use rolling file output via the roller framework.
 */
public abstract class AbstractSink implements ISink {

    /** Config key listing the whole-payload filter conditions for this sink, applied in order. */
    public static final String PAYLOAD_FILTER = "PAYLOAD_FILTER";

    protected Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    protected Configurator configG;
    protected Configurator sinkConfig;
    protected String name = "Abstract";

    /** the data formatter for this sink */
    protected IDropOffFormatter formatter;

    /** deny-first filters for whole-payload eligibility, applied in order; all must accept */
    protected List<IFilterCondition> payloadFilters = new ArrayList<>();

    @Override
    public void initialize(final Configurator configG, @Nullable final String name, final Configurator sinkConfig) {
        this.configG = configG;
        if (name != null) {
            this.name = name;
        }
        this.sinkConfig = resolveSinkConfig(sinkConfig);
        initializePayloadFilters();
        this.formatter = createFormatter();
        this.formatter.initialize(configG, this.name, this.sinkConfig);
    }

    /**
     * Build the whole-payload filter chain. With no declared {@link #PAYLOAD_FILTER} entries the default deny-first
     * {@link PayloadFilterCondition} is used; otherwise every declared condition (a class or {@code name:class} entry) is
     * instantiated via {@link Factory} and initialized from the sink configuration, in declared order.
     */
    protected void initializePayloadFilters() {
        this.payloadFilters.clear();
        final List<String> declared = this.sinkConfig != null ? this.sinkConfig.findEntries(PAYLOAD_FILTER) : Collections.emptyList();
        if (declared.isEmpty()) {
            final PayloadFilterCondition defaultFilter = new PayloadFilterCondition();
            defaultFilter.initialize(this.sinkConfig);
            this.payloadFilters.add(defaultFilter);
            return;
        }
        for (final String entry : declared) {
            final String clazz = parseClassName(entry);
            try {
                final Object filter = Factory.create(clazz);
                if (filter instanceof IFilterCondition) {
                    final IFilterCondition condition = (IFilterCondition) filter;
                    condition.initialize(this.sinkConfig);
                    this.payloadFilters.add(condition);
                } else {
                    logger.error("Misconfigured payload filter {} is not an IFilterCondition instance, ignoring it", clazz);
                }
            } catch (RuntimeException ex) {
                logger.error("Unable to create or initialize payload filter {}", clazz, ex);
            }
        }
    }

    private static String parseClassName(final String entry) {
        final int colpos = entry.indexOf(':');
        return colpos > -1 ? entry.substring(colpos + 1) : entry;
    }

    /**
     * Resolve the sink configuration, preferring the supplied one and otherwise looking up a resource by name/class and
     * startup flavor.
     *
     * @param supplied the configuration explicitly provided, or null to fall back to resource lookup
     * @return the resolved configuration, or null if none could be loaded
     */
    @Nullable
    protected Configurator resolveSinkConfig(@Nullable final Configurator supplied) {
        if (supplied != null) {
            return supplied;
        }
        return ConfigUtil.getConfigInfo(getClass(), this.name);
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
    public boolean accept(final IBaseDataObject d) {
        return this.payloadFilters.stream().allMatch(f -> f.accept(d));
    }

    @Override
    public boolean accept(final List<IBaseDataObject> list) {
        return this.payloadFilters.stream().allMatch(f -> f.accept(list));
    }

    @Override
    public int write(final IBaseDataObject d, final Map<String, Object> params) {
        if (!accept(d)) {
            logger.debug("Skipping {} - not allowed by this sink", d.shortName());
            return ISink.STATUS_SUCCESS;
        }
        return this.formatter.write(Collections.singletonList(d), params);
    }

    @Override
    public int write(final List<IBaseDataObject> list, final Map<String, Object> params) {
        if (!accept(list)) {
            logger.debug("Skipping list - not allowed by this sink");
            return ISink.STATUS_SUCCESS;
        }
        return this.formatter.write(list, params);
    }

    @Override
    public int write(final IBaseDataObject d, final Map<String, Object> params, final OutputStream output) {
        if (!accept(d)) {
            logger.debug("Skipping {} - not allowed by this sink", d.shortName());
            return ISink.STATUS_SUCCESS;
        }
        return this.formatter.write(d, params, output);
    }

    @Override
    public int write(final List<IBaseDataObject> list, final Map<String, Object> params, final OutputStream output) {
        if (!accept(list)) {
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
}
