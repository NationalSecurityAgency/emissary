package emissary.output;

import emissary.config.Configurator;
import emissary.core.Form;
import emissary.core.IBaseDataObject;
import emissary.directory.DirectoryEntry;
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

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Shared lifecycle for places that hand a payload family to a set of configured output destinations.
 *
 * <p>
 * {@link DropOffPlace} and {@link OutputDispatchPlace} differ only in the destination type and the pre-output
 * eligibility check, so this base owns the process/prepare/cleanup sequence once.
 */
public abstract class AbstractDropOffPlace extends ServiceProviderPlace implements EmptyFormPlace {

    private static final String PRE_SORTED = "PRE_SORTED_RECORDS";
    private static final String TLD_PARAM = "TLD";

    protected boolean doSynchronized = false;
    protected Set<String> elideContentForms = Collections.emptySet();
    protected Set<String> noNukeForms = Collections.emptySet();
    protected boolean failurePolicyTerminate = true;
    protected DropOffUtil dropOffUtil;
    private boolean outputCompletionPayloadSize = false;

    protected AbstractDropOffPlace(final String configInfo, final String dir, final String placeLoc) throws IOException {
        super(configInfo, dir, placeLoc);
    }

    protected AbstractDropOffPlace(final String configInfo, final String placeLocation) throws IOException {
        super(configInfo, placeLocation);
    }

    protected AbstractDropOffPlace(final Configurator configInfo) throws IOException {
        this.configG = configInfo;
    }

    protected AbstractDropOffPlace() throws IOException {}

    /** Build and register the configured destinations. */
    protected abstract void initializeOutputs();

    /** Run every configured destination over the target. */
    protected abstract void runOutput(Object target, Map<String, Object> params);

    /** Close every configured destination. */
    protected abstract void closeOutputs();

    /**
     * Whether the payload should be handed to the destinations. Subclasses that enforce a place-level gate override this;
     * the default accepts everything and leaves eligibility to each destination's policy.
     */
    protected boolean acceptForOutput(final IBaseDataObject data, @Nullable final List<IBaseDataObject> family) {
        return true;
    }

    /** Load the common configuration and then the destinations. */
    protected void configureOutput() {
        this.dropOffUtil = new DropOffUtil(configG);
        this.doSynchronized = configG.findBooleanEntry("SYNCHRONIZED_PROCESS", false);
        this.failurePolicyTerminate = configG.findBooleanEntry("FAILURE_TERMINATES_CHAIN", true);
        this.outputCompletionPayloadSize = configG.findBooleanEntry("OUTPUT_COMPLETION_PAYLOAD_SIZE", false);
        this.elideContentForms = configG.findEntriesAsSet("ELIDE_CONTENT");
        this.noNukeForms = configG.findEntriesAsSet("NO_NUKE_FORM");
        initializeOutputs();
    }

    @Override
    public void shutDown() {
        super.shutDown();
        closeOutputs();
    }

    @Override
    public List<IBaseDataObject> agentProcessHeavyDuty(final List<IBaseDataObject> payloadList) throws Exception {
        logger.debug("Entering {}.agentProcessHeavyDuty with {} payload items", getClass().getSimpleName(), payloadList.size());

        for (final IBaseDataObject d : payloadList) {
            try {
                if (!acceptForOutput(d, payloadList)) {
                    logger.info("Skipping object since it is not able to be output ID:{}",
                            this.dropOffUtil.getBestId(d, payloadList.get(0)));
                    return Collections.emptyList();
                }

                processData(d, true);
            } catch (RuntimeException e) {
                logger.error("Place.process threw:", e);
                d.addProcessingError("agentProcessHD(" + myKey + "): " + e);

                if (!d.currentForm().equals(Form.ERROR)) {
                    d.pushCurrentForm(Form.ERROR);
                }
            }
        }

        if (payloadList.isEmpty()) {
            DisposeHelper.execute(payloadList);
            return Collections.emptyList();
        }

        final Map<String, Object> params = new HashMap<>();
        preWriteHook(payloadList, params);
        runOutput(payloadList, params);
        postWriteHook(payloadList, params);

        logCompletion(payloadList);

        DisposeHelper.execute(payloadList);
        return Collections.emptyList();
    }

    @Override
    public void process(final IBaseDataObject tData) {
        if (DataUtil.isEmpty(tData)) {
            logger.warn("null/empty data object");
            return;
        }

        if (!acceptForOutput(tData, null)) {
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

    /** Sort the list and prepare metadata for output. */
    public void preWriteHook(final List<IBaseDataObject> payloadList, final Map<String, Object> params) {
        Collections.sort(payloadList, new ShortNameComparator());
        params.put(PRE_SORTED, Boolean.TRUE);
        params.put(TLD_PARAM, payloadList.get(0));

        this.dropOffUtil.processMetadata(payloadList);
    }

    /** Nuke handled forms after all destinations are done. */
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
        logger.debug("{} is working on {}, current form is {}", getClass().getSimpleName(), tData.shortName(),
                tData.getAllCurrentForms());

        final StringBuilder poppedForms = new StringBuilder();

        String prevBin = "";

        prepareContent(tData);

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
            runOutput(tData, params);
            this.nukeMyProxies(tData);
            logger.debug("{} finished with {}", getClass().getSimpleName(), tData.shortName());
        }
    }

    private void logCompletion(final List<IBaseDataObject> payloadList) throws IOException {
        if (payloadList.isEmpty()) {
            return;
        }

        final IBaseDataObject tld = payloadList.get(0);
        if (this.outputCompletionPayloadSize && tld.hasContent()) {
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

    /** Preprocessing which changes content before the destination policy runs. */
    public boolean elidesContent(final IBaseDataObject payload) {
        return payload.getAllCurrentForms().stream().anyMatch(this.elideContentForms::contains);
    }

    /** Shared by actual output and dry-run explanations. */
    public void prepareContent(final IBaseDataObject payload) {
        if (elidesContent(payload)) {
            payload.setData(("[[ " + payload.getAllCurrentForms() + " content elided in " + getClass().getSimpleName() + ". ]]")
                    .getBytes(UTF_8));
        }
    }

    public DropOffUtil getDropOffUtil() {
        return this.dropOffUtil;
    }
}
