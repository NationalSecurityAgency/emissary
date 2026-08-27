package emissary.output;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.DataObjectFactory;
import emissary.core.Form;
import emissary.core.IBaseDataObject;
import emissary.directory.DirectoryEntry;
import emissary.output.sink.ISink;
import emissary.place.EmptyFormPlace;
import emissary.place.ServiceProviderPlace;
import emissary.util.DataUtil;
import emissary.util.DisposeHelper;
import emissary.util.ShortNameComparator;

import jakarta.annotation.Nullable;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DropOffPlaceV2 manages output from the system using a list of {@link ISink}s, each bundling a data formatter with
 * deny-first filters. Unlike the legacy {@link DropOffPlace}, sinks accept everything unless a filter denies a payload
 * or a specific view (no OUTPUT_TYPE allow list).
 */
public class DropOffPlaceV2 extends ServiceProviderPlace implements EmptyFormPlace {

    protected boolean doSynchronized = false;
    protected Set<String> elideContentForms;
    protected Set<String> noNukeForms;
    protected List<ISink> outputSinks = new ArrayList<>();
    protected boolean failurePolicyTerminate = true;
    protected DropOffUtil dropOffUtil;
    private boolean outputCompletionPayloadSize = false;

    /**
     * Primary place constructor
     *
     * @param configInfo our config stuff from the startup
     * @param dir string name of the directory to register into
     * @param placeLoc string form of our key
     */
    public DropOffPlaceV2(final String configInfo, final String dir, final String placeLoc) throws IOException {
        super(configInfo, dir, placeLoc);
        configurePlace();
    }

    /**
     * Test form of constructor
     */
    public DropOffPlaceV2(final String configInfo) throws IOException {
        this(configInfo, "DropOffPlaceV2.example.com:8001");
    }

    /**
     * Test form of constructor
     */
    protected DropOffPlaceV2(final String configInfo, final String placeLocation) throws IOException {
        super(configInfo, placeLocation);
        configurePlace();
    }

    public DropOffPlaceV2(final Configurator configInfo) throws IOException {
        this.configG = configInfo;
        configurePlace();
    }

    /**
     * Constructor for hooking in to all the defaults
     */
    public DropOffPlaceV2() throws IOException {
        configurePlace();
    }

    /**
     * Setup configuration items we need and build the output sinks
     */
    protected void configurePlace() {
        // Set configuration info on file paths
        this.dropOffUtil = new DropOffUtil(configG);
        this.doSynchronized = configG.findBooleanEntry("SYNCHRONIZED_PROCESS", false);
        this.failurePolicyTerminate = configG.findBooleanEntry("FAILURE_TERMINATES_CHAIN", true);
        this.outputCompletionPayloadSize = configG.findBooleanEntry("OUTPUT_COMPLETION_PAYLOAD_SIZE", false);
        // Build and store all the sinks that are desired IN THE ORDER SPECIFIED
        final List<String> sinkClasses = configG.findEntries("OUTPUT_SINK");
        initializeSinks(sinkClasses);
    }

    /**
     * Start up the requested sinks
     *
     * @param sinkClasses the name:class values of the configured sinks for this drop off
     */
    protected void initializeSinks(final List<String> sinkClasses) {
        for (final String entry : sinkClasses) {
            final String name;
            final String clazz;
            Configurator sinkConfig = null;
            final int colpos = entry.indexOf(':');
            if (colpos > -1) {
                name = entry.substring(0, colpos);
                clazz = entry.substring(colpos + 1);
                final String sinkConfigName = configG.findStringEntry(name);
                if (sinkConfigName != null) {
                    try {
                        sinkConfig = ConfigUtil.getConfigInfo(sinkConfigName);
                    } catch (IOException configError) {
                        logger.warn("Specified sink configuration {} cannot be loaded", sinkConfigName);
                        continue;
                    }
                }
            } else {
                name = null;
                clazz = entry;
            }

            try {
                final Object sink = emissary.core.Factory.create(clazz);
                if (sink != null && sink instanceof ISink) {
                    final ISink s = (ISink) sink;
                    s.initialize(configG, name, sinkConfig);
                    addSink(s);
                } else {
                    logger.error("Misconfigured sink {} is not an ISink instance, ignoring it", clazz);
                }
            } catch (RuntimeException ex) {
                logger.error("Unable to create or initialize {}", clazz, ex);
            }
        }

        // Collect the set of content types to elide
        this.elideContentForms = configG.findEntriesAsSet("ELIDE_CONTENT");

        // collect the set of no-nuke forms
        this.noNukeForms = configG.findEntriesAsSet("NO_NUKE_FORM");

        if (logger.isInfoEnabled()) {
            logger.debug("Setting ELIDE_CONTENT forms to " + this.elideContentForms);
            final StringBuilder sb = new StringBuilder("Output Sinks:");
            if (this.outputSinks.size() > 0) {
                for (final ISink s : this.outputSinks) {
                    sb.append(" ").append(s.getName()).append("(").append(s.getClass().getName()).append(")");
                }
            } else {
                sb.append(" NONE!");
            }
            logger.info(sb.toString());
        }

        if (logger.isDebugEnabled()) {
            final IBaseDataObject fakePayload = DataObjectFactory.getInstance(new byte[0], "fakename", Form.UNKNOWN);
            for (final ISink sink : getSinks()) {
                final String name = sink.getName();
                final String spec = sink.getOutputSpec();

                logger.debug("Adding sink={}, spec={}, sample={}, class={}", name, spec,
                        this.dropOffUtil.getPathFromSpec(spec, fakePayload), sink.getClass().getSimpleName());
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void shutDown() {
        super.shutDown();
        for (final ISink sink : this.outputSinks) {
            logger.debug("Shutdown sink {}", sink.getName());
            sink.close();
        }
    }

    /**
     * "HD" agent calls this method when visiting the place. If you use {@link emissary.core.MobileAgent} this method is
     * never called. This method overrides {@link ServiceProviderPlace} and allows this processing place to have access to
     * all payloads wanting to be dropped off in a single list.
     *
     * @param payloadList list of IBaseDataObject from an {@link emissary.core.HDMobileAgent}
     */
    @Override
    public List<IBaseDataObject> agentProcessHeavyDuty(final List<IBaseDataObject> payloadList) throws Exception {

        logger.debug("Entering DropOffPlaceV2.agentProcessHeavyDuty with {} payload items", payloadList.size());

        // Prepare each incoming payload object
        for (final IBaseDataObject d : payloadList) {
            try {
                // checking to see if any object in the tree is marked as not outputable
                if (!d.isOutputable()) {
                    logger.info("Skipping object since it is not able to be output ID:{}", this.dropOffUtil.getBestId(d, payloadList.get(0)));
                    return Collections.emptyList();
                }

                // Process the payload item with HDcontext=true
                processData(d, true);
            } catch (RuntimeException e) {
                logger.error("Place.process threw:", e);
                d.addProcessingError("agentProcessHD(" + myKey + "): " + e);

                if (!d.currentForm().equals(Form.ERROR)) {
                    d.pushCurrentForm(Form.ERROR);
                }
            }
        }

        // Prepare the data and metadata for sink output
        final Map<String, Object> sinkParams = new HashMap<>();
        preWriteHook(payloadList, sinkParams);

        // Run the sinks on the output, indicating that the records are pre-sorted, if the sink cares
        runOutputSinks(payloadList, sinkParams);

        // Any cleanup operations needed
        postWriteHook(payloadList, sinkParams);

        if (!payloadList.isEmpty()) {
            // Should have been sorted by the prewrite hook

            // Just report the TLD object ID
            final IBaseDataObject tld = payloadList.get(0);

            if (outputCompletionPayloadSize && tld.hasContent()) {
                logger.info(
                        "Finished DropOff for object {}, with external id: {}, with total processing time: {}ms, with filetype: {}, payload size: {} bytes",
                        tld.getInternalId(), this.dropOffUtil.getBestId(tld, tld),
                        Duration.between(tld.getCreationTimestamp(), Instant.now()).toMillis(),
                        tld.getFileType(), tld.getChannelSize());
            } else {
                logger.info("Finished DropOff for object {}, with external id: {}, with total processing time: {}ms, with filetype: {}",
                        tld.getInternalId(), this.dropOffUtil.getBestId(tld, tld),
                        Duration.between(tld.getCreationTimestamp(), Instant.now()).toMillis(),
                        tld.getFileType());
            }
        }

        // Execute 'Dispose Runnables' to tidy up resources used with SeekableByteChannelFactory implementations
        DisposeHelper.execute(payloadList);

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

        // synchronization can be set by config file entry
        if (this.doSynchronized) {
            synchronized (this) {
                processData(tData, false);
            }
        } else {
            processData(tData, false);
        }

        // Execute 'Dispose Runnables' to tidy up resources used with SeekableByteChannelFactory implementations
        DisposeHelper.execute(tData);
    }

    /**
     * Prepare a list of payload objects to be written
     *
     * @param payloadList the list of items that were eligible for output
     * @param params metadata needed for the output sinks
     */
    public void preWriteHook(final List<IBaseDataObject> payloadList, final Map<String, Object> params) {
        // Sort the list of records
        Collections.sort(payloadList, new ShortNameComparator());
        params.put(ISink.PRE_SORTED, Boolean.TRUE);
        params.put(ISink.TLD_PARAM, payloadList.get(0));

        // Prepare the metadata
        this.dropOffUtil.processMetadata(payloadList);
    }

    /**
     * Clean up after all sinks are done
     *
     * @param payloadList the list of items that were eligible for output
     * @param params metadata needed for the output sinks
     */
    public void postWriteHook(final List<IBaseDataObject> payloadList, final Map<String, Object> params) {
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
     * @param haveList true if in HD context
     */
    protected void processData(final IBaseDataObject tData, final boolean haveList) {

        logger.debug("DropOffV2 is working on {}, current form is {}", tData.shortName(), tData.getAllCurrentForms());

        final StringBuilder poppedForms = new StringBuilder();

        String prevBin = "";

        // skip the I/O for some types for all sinks
        for (int i = 0; i < tData.currentFormSize(); i++) {
            final String cf = tData.currentFormAt(i);
            if (this.elideContentForms.contains(cf)) {
                tData.setData(("[[ " + tData.getAllCurrentForms() + " content elided in DropOffPlaceV2. ]]").getBytes());
            }
        }

        // Write out data for all the destinations we area proxy for, popping them off the stack as they are handled.
        final Set<String> serviceProxies = getProxies();
        final Set<String> cfSet = new HashSet<>();
        for (int i = 0; i < tData.currentFormSize(); i++) {
            final String cf = tData.currentFormAt(i);

            if (serviceProxies.contains(cf) || serviceProxies.contains("*")) {
                if (!prevBin.equals(cf) && (i > 0) && !cfSet.contains(cf) && !("UNKNOWN".equals(cf) || cf.endsWith("-PROCESSED"))) {
                    final DirectoryEntry de = getDirectoryEntry();
                    de.setDataType("[" + cf + "]");
                    tData.appendTransformHistory(de.getKey());
                }

                // Accumulate forms we have handled in poppedForms
                if (poppedForms.length() > 0) {
                    poppedForms.append(" ");
                }
                poppedForms.append(cf);
                cfSet.add(cf);

                prevBin = cf;
            }
        }

        // Record the list of forms
        tData.setParameter("POPPED_FORMS", poppedForms.toString());

        // Do the output now if we aren't in HD mode
        if (!haveList) {
            final Map<String, Object> params = new HashMap<>();
            runOutputSinks(tData, params);
            this.nukeMyProxies(tData);
            logger.debug("DropOffV2 finished with {}", tData.shortName());
        }
    }

    /**
     * Run all the output sinks
     *
     * @param target either IBaseDataObject or List thereof
     * @param params other parameters that sinks need
     */
    @SuppressWarnings("unchecked")
    protected void runOutputSinks(final Object target, final Map<String, Object> params) {

        IBaseDataObject doTarget = null;
        List<IBaseDataObject> listTarget = null;
        if (target instanceof IBaseDataObject) {
            doTarget = (IBaseDataObject) target;
        } else if (target instanceof List) {
            listTarget = (List<IBaseDataObject>) target;
        } else {
            logger.error("Cannot run sink on {}", target.getClass().getName());
            return;
        }

        // Write output onto each of the sinks that have been configured, as long as they work
        for (final ISink sink : this.outputSinks) {
            final long start = System.currentTimeMillis();

            // call the sink to output its data
            int status = ISink.STATUS_FAILURE;
            try {
                if (listTarget != null && sink.isAllowed(listTarget)) {
                    status = sink.write(listTarget, params);
                } else if (doTarget != null && sink.isAllowed(doTarget)) {
                    status = sink.write(doTarget, params);
                } else {
                    logger.debug("Sink {} not allowed for {}", sink.getName(), listTarget != null ? "list" : "single payload");
                    status = ISink.STATUS_SUCCESS;
                }
                logger.debug("Sink {} took {}s - {}", sink.getName(), (System.currentTimeMillis() - start) / 1000.0, status);
            } catch (RuntimeException e) {
                logger.error("Sink {} failed", sink.getName(), e);
            }

            if ((status != ISink.STATUS_SUCCESS) && this.failurePolicyTerminate) {
                logger.error("DropOff sink chain terminated at {} due to error return status", sink.getName());
                break;
            }
        }
    }

    /**
     * Provide access to the sinks
     *
     * @return a copy of the list of sinks
     */
    public List<ISink> getSinks() {
        return new ArrayList<>(this.outputSinks);
    }

    /**
     * Provide access to sink names
     *
     * @return a list of sink names or an empty list if none
     */
    public List<String> getSinkNamesList() {
        final List<String> names = new ArrayList<>();
        for (final ISink s : this.outputSinks) {
            names.add(s.getName());
        }
        return names;
    }

    /**
     * Provide access to sink by name
     *
     * @return the named sink or null if none by that name
     */
    @Nullable
    public ISink getSink(final String name) {
        for (final ISink s : this.outputSinks) {
            if (s.getName().equals(name)) {
                return s;
            }
        }
        return null;
    }

    public DropOffUtil getDropOffUtil() {
        return this.dropOffUtil;
    }

    /**
     * Add a sink
     *
     * @param sink the new sink to add, must already be configured and initialized
     */
    public void addSink(final ISink sink) {
        this.outputSinks.add(sink);
    }
}
