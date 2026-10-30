package emissary.output;

import emissary.config.Configurator;
import emissary.core.DataObjectFactory;
import emissary.core.Form;
import emissary.core.IBaseDataObject;
import emissary.directory.DirectoryEntry;
import emissary.output.sink.ISink;
import emissary.output.sink.WriteStatus;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * DropOffPlaceV2 manages output from the system using a list of {@link ISink}s.
 */
public class DropOffPlaceV2 extends ServiceProviderPlace implements EmptyFormPlace {

    protected boolean doSynchronized = false;
    protected Set<String> elideContentForms;
    protected Set<String> noNukeForms;
    protected List<ISink> outputSinks = new ArrayList<>();
    protected boolean failurePolicyTerminate = true;
    protected DropOffUtil dropOffUtil;
    private boolean outputCompletionPayloadSize = false;

    /** Primary place constructor. */
    public DropOffPlaceV2(final String configInfo, final String dir, final String placeLoc) throws IOException {
        super(configInfo, dir, placeLoc);
        configurePlace();
    }

    /** Test constructor. */
    public DropOffPlaceV2(final String configInfo) throws IOException {
        this(configInfo, "DropOffPlaceV2.example.com:8001");
    }

    /** Test constructor. */
    protected DropOffPlaceV2(final String configInfo, final String placeLocation) throws IOException {
        super(configInfo, placeLocation);
        configurePlace();
    }

    public DropOffPlaceV2(final Configurator configInfo) throws IOException {
        this.configG = configInfo;
        configurePlace();
    }

    /** Default constructor. */
    public DropOffPlaceV2() throws IOException {
        configurePlace();
    }

    /**
     * Setup configuration items we need and build the output sinks
     */
    protected void configurePlace() {
        this.dropOffUtil = new DropOffUtil(configG);
        this.doSynchronized = configG.findBooleanEntry("SYNCHRONIZED_PROCESS", false);
        this.failurePolicyTerminate = configG.findBooleanEntry("FAILURE_TERMINATES_CHAIN", true);
        this.outputCompletionPayloadSize = configG.findBooleanEntry("OUTPUT_COMPLETION_PAYLOAD_SIZE", false);
        final List<String> sinkClasses = configG.findEntries("OUTPUT_SINK");
        initializeSinks(sinkClasses);
    }

    /** Start the requested sinks in order. NAME: prefix is an optional label. */
    protected void initializeSinks(final List<String> sinkClasses) {
        for (final String entry : sinkClasses) {
            final String name;
            final String clazz;
            final int colpos = entry.indexOf(':');
            if (colpos > -1) {
                name = entry.substring(0, colpos);
                clazz = entry.substring(colpos + 1);
            } else {
                name = null;
                clazz = entry;
            }

            try {
                final Object sink = emissary.core.Factory.create(clazz);
                if (sink != null && sink instanceof ISink) {
                    final ISink s = (ISink) sink;
                    s.initialize(configG, name, null);
                    addSink(s);
                } else {
                    logger.error("Misconfigured sink {} is not an ISink instance, ignoring it", clazz);
                }
            } catch (RuntimeException ex) {
                logger.error("Unable to create or initialize {}", clazz, ex);
            }
        }

        this.elideContentForms = configG.findEntriesAsSet("ELIDE_CONTENT");

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

    /** HD entry point: drop off a list of payloads. */
    @Override
    public List<IBaseDataObject> agentProcessHeavyDuty(final List<IBaseDataObject> payloadList) throws Exception {

        logger.debug("Entering DropOffPlaceV2.agentProcessHeavyDuty with {} payload items", payloadList.size());

        // Drop payloads marked as not outputable; the rest of the batch still processes
        int skipped = 0;
        final Iterator<IBaseDataObject> ineligible = payloadList.iterator();
        while (ineligible.hasNext()) {
            final IBaseDataObject d = ineligible.next();
            if (!d.isOutputable()) {
                logger.info("Skipping object since it is not able to be output ID:{}", this.dropOffUtil.getBestId(d, d));
                ineligible.remove();
                skipped++;
            }
        }
        if (skipped > 0) {
            logger.info("Skipped {} of {} payload items as not outputable", skipped, skipped + payloadList.size());
        }
        if (payloadList.isEmpty()) {
            DisposeHelper.execute(payloadList);
            return Collections.emptyList();
        }

        for (final IBaseDataObject d : payloadList) {
            try {
                processData(d, true);
            } catch (RuntimeException e) {
                logger.error("Place.process threw:", e);
                d.addProcessingError("agentProcessHD(" + myKey + "): " + e);

                if (!d.currentForm().equals(Form.ERROR)) {
                    d.pushCurrentForm(Form.ERROR);
                }
            }
        }

        final Map<String, Object> sinkParams = new HashMap<>();
        preWriteHook(payloadList, sinkParams);

        runOutputSinks(payloadList, sinkParams);

        postWriteHook(payloadList, sinkParams);

        if (!payloadList.isEmpty()) {

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

        DisposeHelper.execute(payloadList);

        return Collections.emptyList();
    }

    /** Single-payload entry point. */
    @Override
    public void process(final IBaseDataObject tData) {
        if (DataUtil.isEmpty(tData)) {
            logger.warn("null/empty data object");
            return;
        }

        if (!tData.isOutputable()) {
            logger.warn("Skipping object since it is not able to be output ID:{}", this.dropOffUtil.getBestId(tData, tData));
            return;
        }

        if (this.doSynchronized) {
            synchronized (this) {
                processData(tData, false);
            }
        } else {
            processData(tData, false);
        }

        DisposeHelper.execute(tData);
    }

    /** Sort the list and prepare metadata for sink output. */
    public void preWriteHook(final List<IBaseDataObject> payloadList, final Map<String, Object> params) {
        Collections.sort(payloadList, new ShortNameComparator());
        params.put(ISink.PRE_SORTED, Boolean.TRUE);
        params.put(ISink.TLD_PARAM, payloadList.get(0));

        this.dropOffUtil.processMetadata(payloadList);
    }

    /** Nuke handled forms after all sinks are done. */
    public void postWriteHook(final List<IBaseDataObject> payloadList, final Map<String, Object> params) {
        for (final IBaseDataObject dataObject : payloadList) {
            final List<String> saveForms = new ArrayList<>();
            for (final String nnf : this.noNukeForms) {
                if (dataObject.searchCurrentForm(nnf) > -1) {
                    saveForms.add(nnf);
                }
            }
            this.nukeMyProxies(dataObject);

            for (final String sf : saveForms) {
                dataObject.pushCurrentForm(sf);
            }
        }
    }

    /** Prepare a single payload for output. */
    protected void processData(final IBaseDataObject tData, final boolean haveList) {

        logger.debug("DropOffV2 is working on {}, current form is {}", tData.shortName(), tData.getAllCurrentForms());

        final StringBuilder poppedForms = new StringBuilder();

        String prevBin = "";

        for (int i = 0; i < tData.currentFormSize(); i++) {
            final String cf = tData.currentFormAt(i);
            if (this.elideContentForms.contains(cf)) {
                tData.setData(("[[ " + tData.getAllCurrentForms() + " content elided in DropOffPlaceV2. ]]").getBytes(UTF_8));
            }
        }

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

                if (poppedForms.length() > 0) {
                    poppedForms.append(" ");
                }
                poppedForms.append(cf);
                cfSet.add(cf);

                prevBin = cf;
            }
        }

        tData.setParameter("POPPED_FORMS", poppedForms.toString());

        if (!haveList) {
            final Map<String, Object> params = new HashMap<>();
            runOutputSinks(tData, params);
            this.nukeMyProxies(tData);
            logger.debug("DropOffV2 finished with {}", tData.shortName());
        }
    }

    /** Run the output sinks over a payload or list. */
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

        for (final ISink sink : this.outputSinks) {
            final long start = System.currentTimeMillis();

            WriteStatus status = WriteStatus.FAILURE;
            try {
                if (listTarget != null) {
                    status = sink.write(listTarget, params);
                } else if (doTarget != null) {
                    status = sink.write(doTarget, params);
                }
                logger.debug("Sink {} took {}s - {}", sink.getName(), (System.currentTimeMillis() - start) / 1000.0, status);
            } catch (RuntimeException e) {
                logger.error("Sink {} failed", sink.getName(), e);
            }

            if (status == WriteStatus.FAILURE && this.failurePolicyTerminate) {
                logger.error("DropOff sink chain terminated at {} due to error return status", sink.getName());
                break;
            }
        }
    }

    /** Configured sinks. */
    public List<ISink> getSinks() {
        return new ArrayList<>(this.outputSinks);
    }

    /** Configured sink names. */
    public List<String> getSinkNamesList() {
        final List<String> names = new ArrayList<>();
        for (final ISink s : this.outputSinks) {
            names.add(s.getName());
        }
        return names;
    }

    /** Sink by name, or null. */
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

    /** Add an initialized sink. */
    public void addSink(final ISink sink) {
        this.outputSinks.add(sink);
    }
}
