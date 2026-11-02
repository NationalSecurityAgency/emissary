package emissary.output;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.DataObjectFactory;
import emissary.core.Form;
import emissary.core.IBaseDataObject;
import emissary.output.filter.IDropOffFilter;

import jakarta.annotation.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DropOffPlace manages the output from the system It has evolved into a controller of sorts with way too many options,
 * that controls which types of output are desired and called the appropriate output helper for the desired output.
 **/
public class DropOffPlace extends AbstractDropOffPlace {

    protected List<IDropOffFilter> outputFilters = new ArrayList<>();

    /**
     * Primary place constructor
     * 
     * @param configInfo our config stuff from the startup
     * @param dir string name of the directory to register into
     * @param placeLoc string form of our key
     */
    public DropOffPlace(final String configInfo, final String dir, final String placeLoc) throws IOException {
        super(configInfo, dir, placeLoc);
        configurePlace();
    }

    /**
     * Test form of constructor
     */
    public DropOffPlace(final String configInfo) throws IOException {
        this(configInfo, "DropOffPlace.example.com:8001");
    }

    /**
     * Test form of constructor
     */
    protected DropOffPlace(final String configInfo, final String placeLocation) throws IOException {
        super(configInfo, placeLocation);
        configurePlace();
    }

    public DropOffPlace(final Configurator configInfo) throws IOException {
        super(configInfo);
        configurePlace();
    }

    /**
     * Constructor for hooking in to all the defaults
     */
    public DropOffPlace() throws IOException {
        configurePlace();
    }

    /**
     * Setup configuration items we need and build the output filter
     */
    protected void configurePlace() {
        configureOutput();
    }

    @Override
    protected void initializeOutputs() {
        // Build and store all the filter that are desired IN THE ORDER SPECIFIED
        initializeFilters(configG.findEntries("OUTPUT_FILTER"));
    }

    @Override
    protected boolean acceptForOutput(final IBaseDataObject data, final List<IBaseDataObject> family) {
        return data.isOutputable();
    }

    /**
     * Start up the requested filter
     * 
     * @param filterClasses the name:class values of the configured filter for this drop off
     */
    protected void initializeFilters(final List<String> filterClasses) {
        for (final String entry : filterClasses) {
            final String name;
            final String clazz;
            Configurator filterConfig = null;
            final int colpos = entry.indexOf(':');
            if (colpos > -1) {
                name = entry.substring(0, colpos);
                clazz = entry.substring(colpos + 1);
                final String filterConfigName = configG.findStringEntry(name);
                if (filterConfigName != null) {
                    try {
                        filterConfig = ConfigUtil.getConfigInfo(filterConfigName);
                    } catch (IOException configError) {
                        logger.warn("Specified filter configuration {} cannot be loaded", filterConfigName);
                        continue;
                    }
                }
            } else {
                name = null;
                clazz = entry;
            }

            try {
                final Object filter = emissary.core.Factory.create(clazz);
                if (filter != null && filter instanceof IDropOffFilter) {
                    final IDropOffFilter f = (IDropOffFilter) filter;
                    f.initialize(configG, name, filterConfig);
                    addFilter(f);
                } else {
                    logger.error("Misconfigured filter {} is not an IDropOffFilter instance, ignoring it", clazz);
                }
            } catch (RuntimeException ex) {
                logger.error("Unable to create or initialize {}", clazz, ex);
            }
        }

        if (logger.isInfoEnabled()) {
            logger.debug("Setting ELIDE_CONTENT forms to " + this.elideContentForms);
            final StringBuilder sb = new StringBuilder("Output Filters:");
            if (this.outputFilters.size() > 0) {
                for (final IDropOffFilter f : this.outputFilters) {
                    sb.append(" ").append(f.getFilterName()).append("(").append(f.getClass().getName()).append(")");
                }
            } else {
                sb.append(" NONE!");
            }
            logger.info(sb.toString());
        }

        if (logger.isDebugEnabled()) {
            final IBaseDataObject fakePayload = DataObjectFactory.getInstance(new byte[0], "fakename", Form.UNKNOWN);
            for (final IDropOffFilter filter : getFilters()) {
                final String name = filter.getFilterName();
                final String spec = filter.getOutputSpec();

                logger.debug("Adding filter={}, spec={}, sample={}, class={}", name, spec, this.dropOffUtil.getPathFromSpec(spec, fakePayload),
                        filter.getClass().getSimpleName());
            }
        }
    }

    @Override
    protected void closeOutputs() {
        for (final IDropOffFilter filter : this.outputFilters) {
            logger.debug("Shutdown filter {}", filter.getFilterName());
            filter.close();
        }
    }

    @Override
    protected void runOutput(final Object target, final Map<String, Object> params) {
        runOutputFilters(target, params);
    }

    /**
     * Prepare a list of payload object to be filtered
     * 
     * @param payloadList the list of items that were eligible for output
     * @param filterParams metadata needed for the output filter
     */
    public void preFilterHook(final List<IBaseDataObject> payloadList, final Map<String, Object> filterParams) {
        preWriteHook(payloadList, filterParams);
    }

    /**
     * Clean up after all filter are done
     * 
     * @param payloadList the list of items that were eligible for output
     * @param filterParams metadata needed for the output filter
     */
    public void postFilterHook(final List<IBaseDataObject> payloadList, final Map<String, Object> filterParams) {
        postWriteHook(payloadList, filterParams);
    }

    /**
     * Run all the output filter
     * 
     * @param target either IBaseDataObject or List thereof
     * @param filterParams other parameters that filter need
     */
    @SuppressWarnings("unchecked")
    protected void runOutputFilters(final Object target, final Map<String, Object> filterParams) {

        IBaseDataObject doTarget = null;
        List<IBaseDataObject> listTarget = null;
        if (target instanceof IBaseDataObject) {
            doTarget = (IBaseDataObject) target;
        } else if (target instanceof List) {
            listTarget = (List<IBaseDataObject>) target;
        } else {
            logger.error("Cannot run filter on {}", target.getClass().getName());
            return;
        }

        // Write output onto each of the filter that have been
        // configured, as long as they work
        for (final IDropOffFilter filter : this.outputFilters) {
            final long start = System.currentTimeMillis();

            // call the filter to output its data
            int filterStatus = IDropOffFilter.STATUS_FAILURE;
            try {
                if (listTarget != null && filter.isOutputtable(listTarget)) {
                    filterStatus = filter.filter(listTarget, filterParams);
                } else if (doTarget != null && filter.isOutputtable(doTarget)) {
                    filterStatus = filter.filter(doTarget, filterParams);
                } else {
                    logger.debug("Filter {} not Outputtable for {}", filter.getFilterName(), listTarget != null ? "list" : "single payload");
                    filterStatus = IDropOffFilter.STATUS_SUCCESS;
                }
                logger.debug("Filter {} took {}s - {}", filter.getFilterName(), (System.currentTimeMillis() - start) / 1000.0, filterStatus);
            } catch (RuntimeException e) {
                logger.error("Filter {} failed", filter.getFilterName(), e);
            }

            if ((filterStatus != IDropOffFilter.STATUS_SUCCESS) && this.failurePolicyTerminate) {
                logger.error("DropOff Filter chain terminated at {} due to error return status", filter.getFilterName());
                break;
            }
        }
    }

    /**
     * Provide access to the filter
     * 
     * @return a copy of the list of filter
     */
    public List<IDropOffFilter> getFilters() {
        return new ArrayList<>(this.outputFilters);
    }

    /**
     * Provide access to filter names
     * 
     * @return an array of filter names or an empty array if none
     * @deprecated use {@link #getFilterNamesList()}
     */
    @Deprecated
    @SuppressWarnings("AvoidObjectArrays")
    public String[] getFilterNames() {
        return getFilterNamesList().toArray(new String[0]);
    }

    /**
     * Provide access to filter names
     *
     * @return a list of filter names or an empty list if none
     */
    public List<String> getFilterNamesList() {
        final List<String> fnames = new ArrayList<>();
        for (final IDropOffFilter f : this.outputFilters) {
            fnames.add(f.getFilterName());
        }
        return fnames;
    }

    /**
     * Provide access to filter by name
     * 
     * @return the named filter or null if none by that name
     */
    @Nullable
    public IDropOffFilter getFilter(final String name) {
        for (final IDropOffFilter f : this.outputFilters) {
            if (f.getFilterName().equals(name)) {
                return f;
            }
        }
        return null;
    }

    /**
     * Add a filter
     * 
     * @param filter the new filter to add, must already be configured and initialized
     */
    public void addFilter(final IDropOffFilter filter) {
        this.outputFilters.add(filter);
    }

}
