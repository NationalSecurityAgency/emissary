package emissary.output.filter;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.EmissaryRuntimeException;
import emissary.core.IBaseDataObject;
import emissary.output.DropOffUtil;
import emissary.util.JavaCharSet;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Provides the base mechanism for a drop off filter
 */
public abstract class AbstractFilter implements IDropOffFilter {
    /** A static convenience logger */
    protected static Logger slogger = LoggerFactory.getLogger(AbstractFilter.class);

    /** get a logger configured on the impl's classname */
    protected Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    /** hold onto the parent configurator */
    protected Configurator configG;

    /** hold onto the specific filter configurator */
    protected Configurator filterConfig;

    /** hold onto the filter name, impl should set this */
    protected String filterName = "Abstract";

    /** hold the output specification, if any, for this filter */
    protected String outputSpec;

    /** hold the error specification, if any, for this filter */
    protected String errorSpec;

    /** hold the filter condition specification, if any, for this filter */
    protected String filterConditionSpec;

    /** hold the filter condition, if any, for this filter */
    private IFilterCondition filterCondition;

    /**
     * A set of FileType and FileTYpe.ViewName strings controlling what can be output by this filter
     */
    protected Set<String> outputTypes = Collections.emptySet();

    /** String to use when dealing with the primary view specifically */
    public static final String PRIMARY_VIEW_NAME = "PrimaryView";
    public static final String PRIMARY_VIEW = "." + PRIMARY_VIEW_NAME;

    /** Primary view wildcard string */
    public static final String ALL_PRIMARY_VIEWS = "*" + PRIMARY_VIEW;

    /** String to use when dealing with a language in a view */
    public static final String LANGUAGE_VIEW_NAME = "Language";
    public static final String LANGUAGE_VIEW = "." + LANGUAGE_VIEW_NAME;

    /** Language wildcard string */
    public static final String ALL_LANGUAGE_VIEWS = "*" + LANGUAGE_VIEW;

    /** Alternate view wildcard string */
    public static final String ALL_ALT_VIEWS = "*.AlternateView";

    /** Metadata view name */
    public static final String METADATA_VIEW_NAME = "Metadata";
    public static final String METADATA_VIEW = "." + METADATA_VIEW_NAME;

    /* alternate views to NOT output if only a file type/form is specified */
    protected Set<String> denylist = Collections.emptySet();
    protected Set<String> wildCardDenylist = new HashSet<>();

    @Nullable
    protected DropOffUtil dropOffUtil = null;

    /**
     * Initialization phase hook for the filter with default preferences for the runtime configuration of the filter
     */
    @Override
    public void initialize(final Configurator theConfigG, final String filterName) {
        loadFilterConfiguration(null);
        initialize(theConfigG, filterName, this.filterConfig);
    }

    /**
     * Initialization phase hook for the filter with provided filter configuration
     * 
     * @param theConfigG passed in configuration object, usually DropOff's config
     * @param filterName the configured name of this filter or null for the default
     * @param theFilterConfig the configuration for the specific filter
     */
    @Override
    public void initialize(final Configurator theConfigG, @Nullable final String filterName,
            final Configurator theFilterConfig) {
        this.configG = theConfigG;
        if (filterName != null) {
            setFilterName(filterName);
        }
        loadFilterConfiguration(theFilterConfig);
        loadFilterCondition(theConfigG);
        loadOutputSpec(theConfigG);
        this.dropOffUtil = new DropOffUtil(theConfigG);
        initializeOutputTypes(this.filterConfig);
    }

    private final void loadFilterCondition(final Configurator parentConfig) {
        this.filterConditionSpec = parentConfig.findStringEntry("FILTER_CONDITION_" + getFilterName(), null);

        // format FILTER_CONDITION_<filtername> = profilename:clazz just like dropoff filter config
        if (!StringUtils.isEmpty(filterConditionSpec)) {
            final String name;
            final String clazz;
            Configurator filterConfig = null;
            final int colpos = filterConditionSpec.indexOf(':');
            if (colpos > -1) {
                name = filterConditionSpec.substring(0, colpos);
                clazz = filterConditionSpec.substring(colpos + 1);
                final String filterConfigName = parentConfig.findStringEntry(name);
                if (filterConfigName != null) {
                    try {
                        filterConfig = ConfigUtil.getConfigInfo(filterConfigName);
                    } catch (IOException configError) {
                        logger.warn("Specified filter configuration {} cannot be loaded", filterConfigName);
                        return;
                    }
                }
            } else {
                clazz = filterConditionSpec;
            }

            try {
                final Object filterConditionObj = emissary.core.Factory.create(clazz);

                if (filterConditionObj != null && filterConditionObj instanceof IFilterCondition) {
                    this.filterCondition = (IFilterCondition) filterConditionObj;
                    // initialize using the config
                    filterCondition.initialize(filterConfig);
                } else {
                    logger.warn("Failed to initialize filter condition {}. Filter does not implement IFilterCondition", getFilterName());
                }
            } catch (Throwable t) {
                // failed to initialize
                logger.error("Failed in initialize filter condition {} with argument {} and message {}", getFilterName(), filterConditionSpec,
                        t.getMessage(), t);
            }

        }
    }

    /**
     * Run custom configuration
     * 
     * @param config the filter specific configurator
     */
    protected void initializeOutputTypes(@Nullable final Configurator config) {
        if (config != null) {
            this.outputTypes = config.findEntriesAsSet("OUTPUT_TYPE");
            this.logger.debug("Loaded {} output types for filter {}", this.outputTypes.size(), this.outputTypes);
            this.denylist = config.findEntriesAsSet("DENYLIST");
            this.validateDenylist();
            this.wildCardDenylist = this.denylist.stream()
                    .filter(i -> i.endsWith("*"))
                    .map(i -> i.substring(0, i.length() - 1))
                    .collect(Collectors.toSet());
            this.wildCardDenylist.forEach(i -> this.denylist.remove(i + "*"));
            this.logger.debug("Loaded {} ignorelist types for filter {}", this.denylist.size(), this.denylist);
            this.logger.debug("Loaded {} wildcard suffix ignorelist types for filter {}", this.wildCardDenylist.size(), this.wildCardDenylist);
        } else {
            this.logger.debug("InitializeCustom has null filter config");
        }
    }

    protected void validateDenylist() {
        Pattern charSet = Pattern.compile("^[\\w*]+[\\w*.]*[\\w*]+$"); // Match if acceptable characters are in correct order
        Pattern repeatedPeriods = Pattern.compile("\\.\\."); // Match if any sequential `.` characters
        Pattern typeWildcardFormat = Pattern.compile("^(\\*|\\w+)$"); // Match if String is `*` or word sequence
        Pattern viewWildcardFormat = Pattern.compile("^[\\w.]*\\*?$"); // Match if String is word sequence with optional `*` suffix

        for (String entry : this.denylist) {
            if (charSet.matcher(entry).matches() && !repeatedPeriods.matcher(entry).matches()) {
                String[] names = entry.split("\\.", 2);
                String filetype = names[0];
                if (!typeWildcardFormat.matcher(filetype).matches()) {
                    throw new EmissaryRuntimeException(String.format(
                            "Invalid filter configuration: `DENYLIST = %s` " +
                                    "filetype `%s` must be wildcard `*` only or sequence of [A-Z, a-z, 0-9, _].",
                            entry, filetype));
                }
                if (names.length > 1) {
                    String viewName = names[1];
                    if (viewName.chars().filter(ch -> ch == '.').count() > 0) {
                        logger.warn("`DENYLIST = {}` viewName `{}` should not contain any `.` characters", entry, viewName);
                    }
                    if (!viewWildcardFormat.matcher(viewName).matches()) {
                        throw new EmissaryRuntimeException(String.format(
                                "Invalid filter configuration: `DENYLIST = %s` " +
                                        "viewName `%s` must be sequence of [A-Z, a-z, 0-9, _] with optional wildcard `*` suffix.",
                                entry, viewName));
                    }
                }
            } else {
                throw new EmissaryRuntimeException(String.format(
                        "Invalid filter configuration: `DENYLIST = %s` " +
                                "must be one sequence of [A-Z, a-z, 0-9, _] or two sequences separated with `.` delimiter.",
                        entry));
            }
        }
    }

    /**
     * Return the name of this filter
     * 
     * @return the string name of the filter
     */
    @Override
    public String getFilterName() {
        return this.filterName;
    }

    /**
     * Set the filter name
     * 
     * @param s the new name to use for this filter instance
     */
    @Override
    public void setFilterName(final String s) {
        this.filterName = s;
    }

    /**
     * Load the filter configuration with precendence of provided, named, default Preference order for loading
     * configurations
     * <ol>
     * <li>[filter-package].FILTER_NAME.cfg</li>
     * <li>[filter-package].[filter-class]-FILTER_NAME.cfg</li>
     * <li>[filter-package].[filter-class].cfg</li>
     * </ol>
     * 
     * @param suppliedFilterConfig configuration to use when not null
     */
    protected void loadFilterConfiguration(@Nullable final Configurator suppliedFilterConfig) {
        if (suppliedFilterConfig != null) {
            this.filterConfig = suppliedFilterConfig;
            return;
        }

        final List<String> configPreferences = new ArrayList<>();

        if (getFilterName() != null) {
            configPreferences.add(this.getClass().getPackage().getName() + "." + getFilterName() + ConfigUtil.CONFIG_FILE_ENDING);
            configPreferences.add(this.getClass().getName() + "-" + getFilterName() + ConfigUtil.CONFIG_FILE_ENDING);
        }
        configPreferences.add(this.getClass().getName() + ConfigUtil.CONFIG_FILE_ENDING);

        this.logger.debug("Looking for filter configuration preferences {}", configPreferences);
        try {
            this.filterConfig = ConfigUtil.getConfigInfo(configPreferences);
        } catch (IOException iox) {
            this.logger.debug("Could not find filter configuration for {}", getFilterName(), iox);
        }
    }

    /**
     * Run the filter for a set of documents
     * 
     * @param list collection of IBaseDataObject to run the filter on
     * @param params map of params
     * @return status value
     */
    @Override
    public int filter(final List<IBaseDataObject> list, final Map<String, Object> params) {
        // Important to process them in order, if not already sorted

        int status = 0;
        for (final IBaseDataObject d : list) {
            status = filter(d, params);
        }
        return status;
    }

    /**
     * Run the filter for a set of documents
     * 
     * @param list collection of IBaseDataObject to run the filter on
     * @param params map of params
     * @param output the output stream
     * @return status value
     */
    @Override
    public int filter(final List<IBaseDataObject> list, final Map<String, Object> params, final OutputStream output) {

        int status = 0;
        for (final IBaseDataObject d : list) {
            status = filter(d, params, output);
        }
        return status;
    }

    /**
     * The method that all filter have to provide for stream based output
     */
    @Override
    public int filter(final IBaseDataObject payload, final Map<String, Object> params, final OutputStream output) {
        throw new IllegalArgumentException("Not supported, override to support");
    }

    @Override
    public boolean isOutputtable(final IBaseDataObject d) {
        return filterCondition == null || filterCondition.accept(d);
    }

    @Override
    public boolean isOutputtable(final List<IBaseDataObject> list) {
        return filterCondition == null || filterCondition.accept(list);
    }

    /**
     * Determine if the payload is outputtable by the filter
     * 
     * @param d the document
     * @param params map of params
     * @return true if the filter wants a crack at outputting this payload
     */
    @Override
    public boolean isOutputtable(final IBaseDataObject d, final Map<String, Object> params) {
        return true;
    }

    /**
     * Determine if the payload list is outputtable by the filter
     * 
     * @param list collection of IBaseDataObject to check for outputtability
     * @param params map of params
     * @return true if the filter wants a crack at outputting this payload
     */
    @Override
    public boolean isOutputtable(final List<IBaseDataObject> list, final Map<String, Object> params) {
        return true;
    }

    /**
     * Determine is this payload should be output by this filter Usually by the primary view or one of the alternate views
     * being on the outputTypes list from the run-time type configuration stream for the filter in questin.
     *
     * @param type of the data
     */
    protected boolean isOutputtable(final String type) {
        return this.outputTypes.contains("*") || this.outputTypes.contains(type);
    }

    /**
     * Determine is this payload should be output by this filter Usually by the primary view or one of the alternate views
     * being on the outputTypes list from the run-time type configuration stream for the filter in question.
     *
     * @param types types to check
     * @return true if any one of the types is outputtable
     */
    protected boolean isOutputtable(final Collection<String> types) {
        if (this.outputTypes.contains("*")) {
            this.logger.debug("Outputtable due to wildcard in output types");
            return true;
        }

        final boolean canOutput = !Collections.disjoint(this.outputTypes, types);
        if (canOutput && this.logger.isDebugEnabled()) {
            final Set<String> outputFor = new HashSet<>();
            for (final String s : this.outputTypes) {
                if (types.contains(s)) {
                    outputFor.add(s);
                }
            }
            this.logger.debug("Outputtable due to non-disjoint sets: {}", outputFor);
        }
        return canOutput;
    }

    /**
     * Close the filter
     */
    @Override
    public void close() {
        // nothing to do
    }

    /*
     * Extract my Output Spec from the supplied config info and save it
     */
    protected void loadOutputSpec(final Configurator theConfigG) {
        this.outputSpec = theConfigG.findStringEntry("OUTPUT_SPEC_" + getFilterName(), null);
        this.errorSpec = theConfigG.findStringEntry("ERROR_SPEC_" + getFilterName(), null);
        this.logger.debug("Output spec for {} is {}", getFilterName(), this.outputSpec);
    }

    /**
     * Get bytes as UTF-8 converted from specified charset
     * 
     * @param value the contents
     * @param charset the charset of the bytes in value
     */
    protected String normalizeBytes(final byte[] value, final String charset) {
        return normalizeBytes(value, 0, value.length, charset);
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
            s = new String(value, start, len);
        }

        return s;
    }

    /**
     * Extract the charset from the payload or defaultCharset
     * 
     * @param d the payload
     * @param defaultCharset the default
     * @return the charset or defualtCharset if none
     */
    protected String getCharset(final IBaseDataObject d, final String defaultCharset) {
        String lang = d.getFontEncoding();
        if (lang == null || lang.toUpperCase().indexOf("ASCII") != -1 || lang.toUpperCase().indexOf("8859-1") != -1) {
            final String s = d.getStringParameter("HTML_CHARSET");
            if (s != null) {
                lang = s;
            }
        }
        if (lang == null || lang.toUpperCase().indexOf("ASCII") != -1 || lang.toUpperCase().indexOf("8859-1") != -1) {
            final String s = d.getStringParameter("MIME_CHARSET");
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

    /**
     * Makes a set of the file type, current form with and without the .PrimaryView qualifier and all the alternate view
     * names Result set can be passed to {@link #isOutputtable(Collection)} for checking
     *
     * @param d the payload
     */
    protected Set<String> getTypesToCheck(final IBaseDataObject d) {
        final Set<String> checkTypes = getPrimaryTypesToCheck(d);
        for (final String viewName : d.getAlternateViewNames()) {
            checkTypes.addAll(getTypesToCheckForNamedView(d, viewName));
        }
        checkTypes.addAll(getTypesToCheckForNamedView(d, METADATA_VIEW_NAME));
        checkTypes.add(ALL_ALT_VIEWS);
        return checkTypes;
    }

    protected Set<String> getTypesToCheckForNamedView(final IBaseDataObject d, final String viewName) {
        final Set<String> checkTypes = new HashSet<>();
        final String lang = this.dropOffUtil.getLanguage(d);
        final String fileType = DropOffUtil.getFileType(d);
        final String currentForm = d.currentForm();

        // skip over denylisted alt views
        if (denyListContains(fileType, viewName)) {
            return checkTypes;
        }

        checkTypes.add(fileType);
        checkTypes.add(fileType + "." + viewName);
        checkTypes.add("*." + viewName);

        if (!"NONE".equals(lang)) {
            checkTypes.add(lang);
            checkTypes.add(lang + "." + viewName);
            checkTypes.add(lang + "." + fileType);
            checkTypes.add(lang + "." + fileType + "." + viewName);
        }

        if (currentForm != null && !fileType.equals(currentForm)) {
            checkTypes.add(currentForm);
            checkTypes.add(currentForm + "." + viewName);
            if (!"NONE".equals(lang)) {
                checkTypes.add(lang + "." + currentForm);
                checkTypes.add(lang + "." + currentForm + "." + viewName);
            }
        }
        this.logger.debug("Types to be checked for named view {}: {}", viewName, checkTypes);
        return checkTypes;
    }

    protected boolean denyListContains(final String fileType, final String viewName) {
        String fullName = fileType + "." + viewName;
        if (this.denylist.contains(viewName) || this.denylist.contains(fullName)) {
            return true;
        }
        if (this.denylist.stream().anyMatch(i -> i.replaceAll("^\\*\\.", "").equals(viewName))) {
            return true;
        }
        return this.wildCardDenylist.stream().anyMatch(i -> fullName.startsWith(i) || fullName.startsWith(i.replaceAll("^\\*", fileType)));
    }

    /**
     * Makes a set of the file type, current form with and without the .PrimaryView qualifier. Result set can be passed to
     * {@link #isOutputtable(Collection)} for checking whether the primary view should be output
     *
     * @param d the payload
     */
    protected Set<String> getPrimaryTypesToCheck(final IBaseDataObject d) {
        final Set<String> checkTypes = getTypesToCheckForNamedView(d, PRIMARY_VIEW_NAME);
        final String lang = this.dropOffUtil.getLanguage(d);
        checkTypes.add(lang + LANGUAGE_VIEW);
        checkTypes.add(ALL_LANGUAGE_VIEWS);
        checkTypes.add(ALL_PRIMARY_VIEWS);
        return checkTypes;
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
    public Collection<String> getOutputTypes() {
        return new HashSet<>(this.outputTypes);
    }
}
