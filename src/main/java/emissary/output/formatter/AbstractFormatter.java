package emissary.output.formatter;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.IBaseDataObject;
import emissary.output.DropOffUtil;
import emissary.output.formatter.filter.FilterConfigKeys;
import emissary.output.formatter.filter.content.ContentFilter;
import emissary.output.formatter.filter.content.ContentFilterChain;
import emissary.output.formatter.filter.metadata.MetadataFilter;
import emissary.output.formatter.filter.metadata.MetadataFilterChain;
import emissary.output.formatter.filter.util.FilterClassFactory;
import emissary.util.JavaCharSet;

import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Provides the base mechanism for a drop-off formatter. Everything is allowed and written by default.
 */
public abstract class AbstractFormatter implements IDropOffFormatter {

    /** get a logger configured on the impl's classname */
    protected Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    /** hold onto the parent configurator */
    protected Configurator configG;

    /** hold onto the specific formatter configurator */
    @Nullable
    protected Configurator formatterConfig;

    /** hold onto the formatter name, impl should set this */
    protected String name = "Abstract";

    /** filters which content (views) are output via the configured allow/deny list */
    protected ContentFilter contentFilter;

    /** filters which metadata parameters are output via the configured allow/deny list */
    protected MetadataFilter metadataFilter;

    /** hold the output specification, if any, for this formatter */
    protected String outputSpec;

    /** hold the error specification, if any, for this formatter */
    protected String errorSpec;

    @Nullable
    protected DropOffUtil dropOffUtil = null;

    /** String to use when dealing with the primary view specifically */
    public static final String PRIMARY_VIEW_NAME = "PrimaryView";
    public static final String PRIMARY_VIEW = "." + PRIMARY_VIEW_NAME;

    /** String to use when dealing with a language in a view */
    public static final String LANGUAGE_VIEW_NAME = "Language";
    public static final String LANGUAGE_VIEW = "." + LANGUAGE_VIEW_NAME;

    /** Alternate view wildcard string */
    public static final String ALL_ALT_VIEWS = "*.AlternateView";

    /** Metadata view name */
    public static final String METADATA_VIEW_NAME = "Metadata";
    public static final String METADATA_VIEW = "." + METADATA_VIEW_NAME;

    /**
     * Initialization phase hook for the formatter with provided formatter configuration.
     *
     * @param configG passed in configuration object, usually the appender's config
     * @param name the configured name of this formatter or null for the default
     * @param formatterConfig the configuration for the specific formatter
     */
    @Override
    public void initialize(final Configurator configG, @Nullable final String name, final Configurator formatterConfig) {
        this.configG = configG;
        if (name != null) {
            setName(name);
        }
        loadFormatterConfiguration(formatterConfig);
        loadOutputSpec(configG);
        this.dropOffUtil = new DropOffUtil(configG);
        this.metadataFilter = FilterClassFactory.createFilterChain(this.formatterConfig, FilterConfigKeys.METADATA_FILTER,
                MetadataFilter.class, "MetadataFilter", MetadataFilterChain::new);
        this.contentFilter = FilterClassFactory.createFilterChain(this.formatterConfig, FilterConfigKeys.CONTENT_FILTER,
                ContentFilter.class, "ContentFilter", ContentFilterChain::new);
    }

    // ------------------------------------------------------------------
    // content (view) filtering
    // ------------------------------------------------------------------

    /**
     * Determine if a specific view may be output for the given payload.
     *
     * @param d the payload
     * @param viewName the view name
     * @return true to allow the view
     */
    protected boolean isContentAllowed(final IBaseDataObject d, final String viewName) {
        return contentFilter.allows(d, viewName);
    }

    // ------------------------------------------------------------------
    // metadata (parameter) filtering
    // ------------------------------------------------------------------

    /**
     * Determine if a parameter key should be output.
     *
     * @param key the parameter key
     * @return true to allow
     */
    protected boolean isMetadataAllowed(final String key) {
        return metadataFilter.allows(key);
    }

    /**
     * Determine if a specific parameter value should be output.
     *
     * @param key the parameter key
     * @param value the parameter value
     * @return true to allow
     */
    protected boolean isMetadataAllowed(final String key, final Object value) {
        return metadataFilter.allows(key, value);
    }

    /**
     * Strip any configured prefix from a parameter name.
     *
     * @param name the parameter name
     * @return the name with the first matching configured prefix removed
     */
    protected String stripMetadataPrefix(final String name) {
        return metadataFilter.stripPrefix(name);
    }

    // ------------------------------------------------------------------
    // configuration helpers
    // ------------------------------------------------------------------

    /**
     * Load the formatter configuration, preferring the supplied one and otherwise looking up a resource by name/class and
     * startup flavor.
     *
     * @param suppliedFormatterConfig configuration to use when not null
     */
    protected void loadFormatterConfiguration(@Nullable final Configurator suppliedFormatterConfig) {
        if (suppliedFormatterConfig != null) {
            this.formatterConfig = suppliedFormatterConfig;
            return;
        }
        this.formatterConfig = ConfigUtil.getConfigInfo(getClass(), this.name);
        if (this.formatterConfig == null) {
            logger.debug("Could not find {} configuration for {}", getClass().getSimpleName(), this.name);
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

    @Override
    public int write(final List<IBaseDataObject> list, final Map<String, Object> params) {
        int status = 0;
        for (final IBaseDataObject d : list) {
            status = write(d, params);
        }
        return status;
    }

    @Override
    public int write(final List<IBaseDataObject> list, final Map<String, Object> params, final OutputStream output) {
        int status = 0;
        for (final IBaseDataObject d : list) {
            status = write(d, params, output);
        }
        return status;
    }

    @Override
    public void close() {
        // nothing to do
    }

    /**
     * Extract my Output Spec from the supplied config info and save it
     */
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

    /**
     * Get bytes as UTF-8 converted from specified charset
     *
     * @param value the contents
     * @param start position to start subarray
     * @param len length of subarray
     * @param charset the charset of the bytes in value
     */
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

    /**
     * Extract the charset from the payload or defaultCharset
     *
     * @param d the payload
     * @param defaultCharset the default
     * @return the charset or defaultCharset if none
     */
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

    /** utility for StringUtils.isNotBlank */
    protected static boolean isNotBlank(final String s) {
        return StringUtils.isNotBlank(s);
    }
}
