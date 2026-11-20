package emissary.output.formatter;

import emissary.config.Configurator;
import emissary.core.IBaseDataObject;
import emissary.output.DropOffUtil;
import emissary.output.formatter.filter.AbstractItemFilter;
import emissary.output.formatter.filter.ConfigKeys;
import emissary.output.formatter.filter.FilterEmit;
import emissary.output.formatter.filter.FilterEmit.EmitMode;
import emissary.output.formatter.filter.util.FilterClassFactory;
import emissary.util.JavaCharSet;

import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Base formatter for drop-off output, writing everything unless denied.
 */
public abstract class AbstractFormatter implements IDropOffFormatter {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    protected Configurator configG;

    @Nullable
    protected Configurator formatterConfig;

    protected String name = "Abstract";

    /** what to emit ({@code EMIT}, default both) */
    protected EmitMode emitMode = EmitMode.BOTH;

    /** filters applied to parameters and views, ANDed in declared order */
    protected List<AbstractItemFilter> outputFilters = List.of();

    /** parameter name prefixes to strip before output ({@code STRIP_PARAM_PREFIX}) */
    private final Set<String> stripPrefixes = new HashSet<>();

    protected String outputSpec;

    protected String errorSpec;

    @Nullable
    protected DropOffUtil dropOffUtil = null;

    /** Name of the primary view */
    public static final String PRIMARY_VIEW_NAME = "PrimaryView";
    public static final String PRIMARY_VIEW = "." + PRIMARY_VIEW_NAME;

    /** Name of the language view */
    public static final String LANGUAGE_VIEW_NAME = "Language";
    public static final String LANGUAGE_VIEW = "." + LANGUAGE_VIEW_NAME;

    /** Wildcard matching all alternate views */
    public static final String ALL_ALT_VIEWS = "*.AlternateView";

    /** Name of the metadata view */
    public static final String METADATA_VIEW_NAME = "Metadata";
    public static final String METADATA_VIEW = "." + METADATA_VIEW_NAME;

    /**
     * Initialize the formatter.
     *
     * @param configG the parent configuration
     * @param name the formatter name, or null for the default
     * @param formatterConfig the formatter-specific configuration
     */
    @Override
    public void initialize(final Configurator configG, @Nullable final String name, final Configurator formatterConfig) {
        this.configG = configG;
        setName(name != null ? name : defaultName());
        loadFormatterConfiguration(formatterConfig);
        loadOutputSpec(configG);
        this.dropOffUtil = new DropOffUtil(configG);
        String emit = this.formatterConfig == null ? null : this.formatterConfig.findStringEntry(FilterEmit.EMIT, null);
        if (emit != null) {
            try {
                this.emitMode = EmitMode.valueOf(emit.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                logger.warn("Unknown EMIT = \"{}\", using {}", emit, this.emitMode);
            }
        }
        this.outputFilters = buildOutputFilters();
        this.stripPrefixes.clear();
        if (this.formatterConfig != null) {
            this.stripPrefixes.addAll(this.formatterConfig.findEntriesAsSet(ConfigKeys.STRIP_PARAM_PREFIX));
        }
        logger.info("{} output filters: {} | (EMIT = {})", this.name, describeOutputFilters(false),
                this.emitMode.name().toLowerCase(Locale.ROOT));
        if (this.logger.isDebugEnabled()) {
            logger.debug("{} output filter details: {}", this.name, describeOutputFilters(true));
        }
    }

    /** Dimension veto first when restrictive, then configured filters in order. */
    private List<AbstractItemFilter> buildOutputFilters() {
        FilterEmit emitFilter = new FilterEmit();
        emitFilter.setEmitMode(this.emitMode);

        List<AbstractItemFilter> configured = FilterClassFactory.createFrom(this.formatterConfig, ConfigKeys.OUTPUT_FILTER,
                AbstractItemFilter.class, "output filter");
        configured.forEach(filter -> filter.configure(this.formatterConfig));
        if (this.emitMode == EmitMode.BOTH) {
            return configured;
        }

        List<AbstractItemFilter> all = new ArrayList<>(configured.size() + 1);
        all.add(emitFilter);
        all.addAll(configured);
        return all;
    }

    // emit control

    /** Whether content is emitted, honoring {@code EMIT}. */
    protected boolean isContentEmitAllowed() {
        return this.emitMode != EmitMode.METADATA;
    }

    /** Whether metadata is emitted, honoring {@code EMIT}. */
    protected boolean isMetadataEmitAllowed() {
        return this.emitMode != EmitMode.CONTENT;
    }

    // output filtering

    /** Log a denial: filter name plus parameter key or view name. Values are never logged. */
    private void logDenied(final AbstractItemFilter filter, final boolean isView, final String name) {
        if (this.logger.isTraceEnabled()) {
            this.logger.trace("Denied {} '{}' by {}", isView ? "view" : "param", name,
                    filter.getClass().getSimpleName());
        }
    }

    /**
     * Whether the named view may be emitted for the payload.
     */
    protected boolean isContentAllowed(final IBaseDataObject d, final String viewName) {
        for (final AbstractItemFilter filter : this.outputFilters) {
            if (!filter.test(d, viewName)) {
                logDenied(filter, true, viewName);
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the parameter key may be emitted.
     */
    protected boolean isMetadataAllowed(final String key) {
        for (final AbstractItemFilter filter : this.outputFilters) {
            if (!filter.test(null, key, null)) {
                logDenied(filter, false, key);
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the parameter value may be emitted.
     */
    protected boolean isMetadataAllowed(final String key, final Object value) {
        for (final AbstractItemFilter filter : this.outputFilters) {
            if (!filter.test(null, key, value)) {
                logDenied(filter, false, key);
                return false;
            }
        }
        return true;
    }

    /** Strip the first configured prefix from a parameter name. */
    protected String stripMetadataPrefix(final String name) {
        for (final String prefix : this.stripPrefixes) {
            if (name.startsWith(prefix)) {
                return name.substring(prefix.length());
            }
        }
        return name;
    }

    private String describeOutputFilters(final boolean verbose) {
        if (this.outputFilters.isEmpty()) {
            return "none (allow all)";
        }
        StringBuilder sb = new StringBuilder();
        for (final AbstractItemFilter filter : this.outputFilters) {
            if (sb.length() > 0) {
                sb.append(verbose ? "; then " : " AND ");
            }
            sb.append(filter.describe(verbose));
        }
        return sb.toString();
    }

    // configuration helpers

    /** Supplied (sink) configuration, or null for allow-all defaults. */
    protected String defaultName() {
        return "FORMATTER";
    }

    /**
     * Load the formatter configuration: the supplied (sink) configuration, or null for allow-all defaults.
     */
    protected void loadFormatterConfiguration(@Nullable final Configurator suppliedFormatterConfig) {
        this.formatterConfig = suppliedFormatterConfig;
        if (this.formatterConfig == null) {
            logger.debug("No {} configuration supplied, allowing all output", getClass().getSimpleName());
        }
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void setName(final String name) {
        this.name = name;
    }

    /** Load the output and error specs for this formatter. */
    protected void loadOutputSpec(final Configurator configG) {
        this.outputSpec = configG.findStringEntry("OUTPUT_SPEC_" + getName(), null);
        this.errorSpec = configG.findStringEntry("ERROR_SPEC_" + getName(), null);
        this.logger.debug("Output spec for {} is {}", getName(), this.outputSpec);
    }

    @Override
    public String getOutputSpec() {
        return this.outputSpec;
    }

    @Override
    public String getErrorSpec() {
        return this.errorSpec;
    }

    @Override
    public void close() {
        // pure serializers hold no resources by default
    }

    /** Decode bytes to a string, using the given charset or UTF-8. */
    protected String normalizeBytes(final byte[] value, final int start, final int len, @Nullable final String charset) {
        String s = null;

        if (charset != null) {
            try {
                s = new String(value, start, len, charset);
            } catch (UnsupportedEncodingException ex) {
                this.logger.debug("Error encoding string", ex);
            }
        }

        if (s == null) {
            // from exception or no charset
            s = new String(value, start, len, UTF_8);
        }

        return s;
    }

    /** Charset declared on the payload, or the default. */
    protected String getCharset(final IBaseDataObject d, final String defaultCharset) {
        String lang = d.getFontEncoding();
        if (lang == null || lang.toUpperCase(Locale.getDefault()).contains("ASCII") || lang.toUpperCase(Locale.getDefault()).contains("8859-1")) {
            final String s = d.getParameterAsString("HTML_CHARSET");
            if (s != null) {
                lang = s;
            }
        }
        if (lang == null || lang.toUpperCase(Locale.getDefault()).contains("ASCII") || lang.toUpperCase(Locale.getDefault()).contains("8859-1")) {
            final String s = d.getParameterAsString("MIME_CHARSET");
            if (s != null) {
                lang = s;
            }
        }
        if (lang == null) {
            return defaultCharset;
        } else {
            return JavaCharSet.get(lang);
        }
    }

    /** Whether a string is not blank */
    protected static boolean isNotBlank(final String s) {
        return StringUtils.isNotBlank(s);
    }
}
