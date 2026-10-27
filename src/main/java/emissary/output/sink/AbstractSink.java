package emissary.output.sink;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.EmissaryRuntimeException;
import emissary.core.IBaseDataObject;
import emissary.output.policy.Decision;
import emissary.output.policy.OutputPolicy;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Sink base: gates which payloads are written and sends the rest to a serializer. File destinations live in
 * {@link AbstractRollableSink}.
 */
public abstract class AbstractSink implements ISink {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    protected Configurator configG;
    /** The sink configuration, which also carries the output policy. */
    protected Configurator sinkConfig;
    protected String name = "Abstract";
    protected String outputSpec;
    protected String errorSpec;

    /** The single authority for whether a payload, view, or parameter is emitted. */
    protected OutputPolicy policy = OutputPolicy.allowAll();
    private boolean prepared;

    @Override
    public void initialize(final Configurator configG, @Nullable final String name, final Configurator sinkConfig) {
        if (!this.prepared) {
            prepare(configG, name, sinkConfig);
        }
        initializePrepared();
        this.prepared = false;
    }

    /** Compile settings without opening destinations, allowing the dispatcher to validate all sinks first. */
    public void prepare(final Configurator configG, @Nullable final String name, @Nullable final Configurator sinkConfig) {
        this.configG = configG;
        setName(name != null ? name : defaultName());
        this.sinkConfig = resolveSinkConfig(sinkConfig);
        this.policy = OutputPolicy.from(this.sinkConfig);
        warnIfNoOutputableRule();
        this.outputSpec = configG.findStringEntry("OUTPUT_SPEC_" + this.name, null);
        this.errorSpec = configG.findStringEntry("ERROR_SPEC_" + this.name, null);
        this.prepared = true;
    }

    /** Non-outputable records are policy data, not a silent gate; warn when no stage constrains the flag. */
    private void warnIfNoOutputableRule() {
        if (this.sinkConfig == null) {
            return;
        }
        for (final String stage : this.sinkConfig.findEntries("POLICY_STAGE")) {
            if (stage != null && stage.contains("outputable")) {
                return;
            }
        }
        logger.warn("Sink {} policy has no outputable rule; non-outputable payloads will be written. "
                + "Add POLICY_STAGE = \"rule deny payload outputable=false\" to skip them.", this.name);
    }

    /** Open resources after all configured destinations have passed validation. */
    public void initializePrepared() {
        initializeOutput();
        logger.info("{} policy: {}", this.name, this.policy.describe());
    }

    /** Default name when the place declares no NAME: prefix. */
    protected String defaultName() {
        return "SINK";
    }

    /** Supplied config, otherwise the standard class configuration. Sink labels do not select configuration files. */
    protected Configurator resolveSinkConfig(@Nullable final Configurator supplied) {
        if (supplied != null) {
            return supplied;
        }
        try {
            return ConfigUtil.getConfigInfo(getClass());
        } catch (IOException iox) {
            throw new EmissaryRuntimeException("Unable to load sink configuration for " + getClass().getName(), iox);
        }
    }

    /** Create any state needed to serialize output. */
    protected void initializeOutput() {
        // serializers are stateless by default
    }

    /** Write the payloads to the sink's output. */
    protected abstract void writePayloads(OutputStream out, List<IBaseDataObject> list, Map<String, Object> params) throws IOException;

    /** Whether serialized payloads are newline-terminated. */
    protected boolean isAppendNewLine() {
        return true;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public OutputPolicy getPolicy() {
        return this.policy;
    }

    public void setName(final String name) {
        this.name = name;
    }

    @Override
    public boolean accept(final IBaseDataObject d) {
        return accept(Collections.singletonList(d));
    }

    @Override
    public boolean accept(final List<IBaseDataObject> list) {
        return decideFamily(list).isAllowed();
    }

    /** Family decision with denial logged so skips are explainable at DEBUG without the trace endpoint. */
    protected Decision decideFamily(final List<IBaseDataObject> list) {
        final Decision decision = this.policy.decideFamily(list);
        if (decision.isDenied()) {
            logger.debug("Skipping {} payload(s) - {}: {}", list.size(), decision.stage(), decision.detail());
        }
        return decision;
    }

    @Override
    public WriteStatus write(final IBaseDataObject d, final Map<String, Object> params) {
        return write(Collections.singletonList(d), params);
    }

    @Override
    public WriteStatus write(final IBaseDataObject d, final Map<String, Object> params, final OutputStream output) {
        return write(Collections.singletonList(d), params, output);
    }

    @Override
    public WriteStatus write(final List<IBaseDataObject> list, final Map<String, Object> params, final OutputStream output) {
        if (!decideFamily(list).isAllowed()) {
            return WriteStatus.SKIPPED;
        }

        return writeAccepted(list, params, output);
    }

    /** Serialize an already accepted family, without re-running custom eligibility filters. */
    protected WriteStatus writeAccepted(final List<IBaseDataObject> list, final Map<String, Object> params, final OutputStream output) {

        // First element is the TLD
        list.get(0).putParameter("DESCENDANT_COUNT", list.size() - 1);

        try {
            writePayloads(output, list, params);
            if (isAppendNewLine()) {
                output.write("\n".getBytes(UTF_8));
            }
        } catch (IOException iox) {
            logger.warn("Could not write to sink output", iox);
            return WriteStatus.FAILURE;
        }
        return WriteStatus.SUCCESS;
    }

    /** Name written by the serializer. Most sinks preserve the original parameter name. */
    @Override
    public String outputParameterName(final String key) {
        return key;
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
        // no resources by default
    }
}
