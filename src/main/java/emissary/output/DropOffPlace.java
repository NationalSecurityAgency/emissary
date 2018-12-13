package emissary.output;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import emissary.config.Configurator;
import emissary.core.DataObjectFactory;
import emissary.core.Form;
import emissary.core.IBaseDataObject;
import emissary.output.filter.FilterUtil;
import emissary.output.filter.IDropOffFilter;
import emissary.place.ServiceProviderPlace;
import emissary.util.DataUtil;

/**
 * DropOffPlace manages the output from the system It has evolved into a controller of sorts with way too many options,
 * that controls which types of output are desired and called the appropriate output helper for the desired output.
 **/
public class DropOffPlace extends ServiceProviderPlace implements emissary.place.EmptyFormPlace {

    protected boolean doSynchronized = false;
    protected Set<String> elideContentForms;
    protected Set<String> noNukeForms;
    protected List<IDropOffFilter> outputFilters = new ArrayList<>();
    protected boolean failurePolicyTerminate = true;
    protected DropOffUtil dropOffUtil;

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
        this.configG = configInfo;
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
        // Set configuration info on file paths
        this.dropOffUtil = new DropOffUtil(configG);
        this.doSynchronized = configG.findBooleanEntry("SYNCHRONIZED_PROCESS", false);
        this.failurePolicyTerminate = configG.findBooleanEntry("FAILURE_TERMINATES_CHAIN", true);

        // Build and store all the filter that are desired IN THE ORDER SPECIFIED
        final List<String> filterClasses = configG.findEntries("OUTPUT_FILTER");
        initializeFilters(filterClasses);
    }

    /**
     * Start up the requested filter
     * 
     * @param filterClasses the name:class values of the configured filter for this drop off
     */
    protected void initializeFilters(final List<String> filterClasses) {

        outputFilters.addAll(FilterUtil.initializeFilters(filterClasses, configG));

        // Collect the set of content types to elide
        this.elideContentForms = configG.findEntriesAsSet("ELIDE_CONTENT");

        // collect the set of no-nuke forms
        this.noNukeForms = configG.findEntriesAsSet("NO_NUKE_FORM");

        if (logger.isInfoEnabled()) {
            logger.debug("Setting ELIDE_CONTENT forms to {}", this.elideContentForms);
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

    /** {@inheritDoc } */
    @Override
    public void shutDown() {
        super.shutDown();
        for (final IDropOffFilter filter : this.outputFilters) {
            logger.debug("Shutdown filter {}", filter.getFilterName());
            filter.close();
        }
    }

    /**
     * "HD" agent calls this method when visiting the place. If you use emissary.core.MobileAgent this method is never
     * called. This method overrides ServiceProviderPlace and allows this processing place to have access to all payloads
     * wanting to be dropped off in a single list.
     * 
     * @param payloadList list of IBaseDataObject from an HDMobileAgent
     */
    @Override
    public List<IBaseDataObject> agentProcessHeavyDuty(final List<IBaseDataObject> payloadList) throws Exception {

        logger.debug("Entering DropOffPlace.agentProcessHeavyDuty with {} payload items", payloadList.size());

        // Prepare each incoming payload object
        for (final IBaseDataObject d : payloadList) {
            try {
                // checking to see if any object in the tree is marked as not outputable
                if (!d.isOutputable()) {
                    logger.info("Skipping object since it is not able to be output ID:{}", this.dropOffUtil.getBestId(d, payloadList.get(0)));
                    return Collections.emptyList();
                }

                // Process the payload item with HDcontext=true
                processData(d);
            } catch (Exception e) {
                logger.error("Place.process threw:", e);
                d.addProcessingError("agentProcessHD(" + myKey + "): " + e);

                if (!d.currentForm().equals(Form.ERROR)) {
                    d.pushCurrentForm(Form.ERROR);
                }
            }
        }

        // Prepare the data and metadata for filter output
        final Map<String, Object> filterParams = preFilterHook(payloadList);

        // Run the filter on the output, indicating that
        // the records are pre-sorted, if the filter cares
        runOutputFilters(payloadList, filterParams);

        // Any cleanup operations needed
        postFilterHook(payloadList, filterParams);

        if (payloadList.size() > 0) {
            // Should have been sorted by the prefilter hook
            // Just report the TLD object ID
            final IBaseDataObject tld = payloadList.get(0);
            logger.info("Finished DropOff for object {}, with external id: {}, with total processing time(: {}ms, with filetype: {}",
                    tld.getInternalId(), this.dropOffUtil.getBestId(tld, tld), (new Date().getTime() - tld.getCreationTimestamp().getTime()),
                    tld.getFileType());
        }

        // This place does not sprout, return an empty list
        return Collections.emptyList();
    }

    /**
     * Called by MobileAgent through ServiceProviderPlace to handle a single payload
     * 
     * @param tData the payload to work on
     */
    @Override
    public void process(final IBaseDataObject tData) {
        if (DataUtil.isEmpty(tData)) {
            logger.warn("null/empty data object");
            return;
        }

        // checking to see if the object is marked as not outputable
        if (!tData.isOutputable()) {
            logger.warn("Skipping object since it is not able to be output ID:{}", this.dropOffUtil.getBestId(tData, tData));
            return;
        }

        // syncronization can be set by config file entry
        if (this.doSynchronized) {
            synchronized (this) {
                dropOffSingle(tData);
            }
        } else {
            dropOffSingle(tData);
        }
    }

    /**
     * Performs synchronization management for dropping off objects
     *
     * @param tData the payload to drop off
     */
    private void dropOffSingle(final IBaseDataObject tData) {
        processData(tData);
        // There currently are no extra params needed
        final Map<String, Object> filterParams = new HashMap<>();
        runOutputFilters(Collections.singletonList(tData), filterParams);

        // Actually remove the current forms we used or could have used
        this.nukeMyProxies(tData);
        logger.debug("DropOff finished with {}", tData.shortName());
    }

    /**
     * Prepare a list of payload object to be filtered
     * 
     * @param payloadList the list of items that were eligible for output
     * @return metadata needed for the output filter
     */
    public Map<String, Object> preFilterHook(final List<IBaseDataObject> payloadList) {
        return FilterUtil.preFilter(payloadList, dropOffUtil);
    }

    /**
     * Clean up after all filter are done
     * 
     * @param payloadList the list of items that were eligible for output
     * @param filterParams metadata needed for the output filter
     */
    public void postFilterHook(final List<IBaseDataObject> payloadList, final Map<String, Object> filterParams) {
        // remove the current forms we used or could have used
        for (final IBaseDataObject dataObject : payloadList) {
            // Save off no-nuke forms
            final List<String> saveForms = new ArrayList<>();
            for (final String nnf : this.noNukeForms) {
                if (dataObject.searchCurrentForm(nnf) > -1) {
                    saveForms.add(nnf);
                }
            }
            // nuke 'em
            this.nukeMyProxies(dataObject);

            // Restore the no-nukes
            for (final String sf : saveForms) {
                dataObject.pushCurrentForm(sf);
            }
        }

    }

    /**
     * Internal method to process a single data object
     * 
     * @param tData the payload to work on or prepare
     */
    protected void processData(final IBaseDataObject tData) {

        logger.debug("DropOff is working on {}, current form is {}", tData.shortName(), tData.getAllCurrentForms());

        // skip the I/O for some types for all filter
        for (int i = 0; i < tData.currentFormSize(); i++) {
            final String cf = tData.currentFormAt(i);
            if (elideContentForms.contains(cf)) {
                tData.setData(("[[ " + tData.getAllCurrentForms() + " content elided in DropOffPlace. ]]").getBytes());
            }
        }

        FilterUtil.processData(tData, noNukeForms, getDirectoryEntry());

    }

    /**
     * Run all the output filter
     * 
     * @param target either IBaseDataObject or List thereof
     * @param filterParams other parameters that filter need
     */
    @SuppressWarnings("unchecked")
    protected void runOutputFilters(final List<IBaseDataObject> target, final Map<String, Object> filterParams) {

        // Write output onto each of the filter that have been
        // configured, as long as they work
        for (final IDropOffFilter filter : this.outputFilters) {
            final long start = System.currentTimeMillis();

            // call the filter to output its data
            int filterStatus = IDropOffFilter.STATUS_FAILURE;
            try {
                if (target != null && filter.isOutputtable(target)) {
                    filterStatus = filter.filter(target, filterParams);
                } else {
                    logger.debug("Filter {} not Outputtable", filter.getFilterName());
                    filterStatus = IDropOffFilter.STATUS_SUCCESS;
                }
                logger.debug("Filter {} took {}s - {}", filter.getFilterName(), ((System.currentTimeMillis() - start) / 1000.0), filterStatus);
            } catch (Exception e) {
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
     */
    public String[] getFilterNames() {
        final List<String> fnames = new ArrayList<>();
        for (final IDropOffFilter f : this.outputFilters) {
            fnames.add(f.getFilterName());
        }
        return fnames.toArray(new String[0]);
    }

    /**
     * Provide access to filter by name
     * 
     * @return the named filter or null if none by that name
     */
    public IDropOffFilter getFilter(final String name) {
        for (final IDropOffFilter f : this.outputFilters) {
            if (f.getFilterName().equals(name)) {
                return f;
            }
        }
        return null;
    }

    public DropOffUtil getDropOffUtil() {
        return this.dropOffUtil;
    }

    /**
     * Add a filter
     * 
     * @param filter the new filter to add, must already be configured and initialized
     */
    public void addFilter(final IDropOffFilter filter) {
        this.outputFilters.add(filter);
    }

    /**
     * Run the command line interface
     */
    public static void main(final String[] argv) throws Exception {
        mainRunner(DropOffPlace.class.getName(), argv);
    }
}
