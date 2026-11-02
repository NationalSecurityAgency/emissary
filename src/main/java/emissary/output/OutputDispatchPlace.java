package emissary.output;

import emissary.config.Configurator;
import emissary.core.DataObjectFactory;
import emissary.core.EmissaryRuntimeException;
import emissary.core.Form;
import emissary.core.IBaseDataObject;
import emissary.output.sink.AbstractSink;
import emissary.output.sink.ISink;
import emissary.output.sink.ISink.WriteStatus;

import jakarta.annotation.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * OutputDispatchPlace manages output from the system using an ordered list of {@link ISink}s.
 *
 * <p>
 * Each sink reads one {@link emissary.output.policy.OutputPolicy} from its own configuration, so eligibility and
 * serialization share a single explainable decision. Outputability is policy data
 * ({@code rule deny payload outputable=false} in the shipped sink configurations), not a silent place-level gate.
 */
public class OutputDispatchPlace extends AbstractDropOffPlace {

    protected List<ISink> outputSinks = new ArrayList<>();

    /** Primary place constructor. */
    public OutputDispatchPlace(final String configInfo, final String dir, final String placeLoc) throws IOException {
        super(configInfo, dir, placeLoc);
        configurePlace();
    }

    /** Test constructor. */
    public OutputDispatchPlace(final String configInfo) throws IOException {
        this(configInfo, "OutputDispatchPlace.example.com:8001");
    }

    /** Test constructor. */
    protected OutputDispatchPlace(final String configInfo, final String placeLocation) throws IOException {
        super(configInfo, placeLocation);
        configurePlace();
    }

    public OutputDispatchPlace(final Configurator configInfo) throws IOException {
        super(configInfo);
        configurePlace();
    }

    /** Default constructor. */
    public OutputDispatchPlace() throws IOException {
        configurePlace();
    }

    /**
     * Setup configuration items we need and build the output sinks
     */
    protected void configurePlace() {
        configureOutput();
    }

    @Override
    protected void initializeOutputs() {
        initializeSinks(configG.findEntries("OUTPUT_SINK"));
    }

    /** Start the requested sinks in order. NAME: prefix is an optional label. */
    protected void initializeSinks(final List<String> sinkClasses) {
        final Set<String> names = new HashSet<>();
        final Set<Path> directories = new HashSet<>();
        final List<ISink> prepared = new ArrayList<>();
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
                    if (s instanceof AbstractSink) {
                        ((AbstractSink) s).prepare(configG, name, null);
                    } else {
                        s.initialize(configG, name, null);
                    }
                    if (!names.add(s.getName().toLowerCase(Locale.ROOT))) {
                        throw new EmissaryRuntimeException("Duplicate output sink name: " + s.getName());
                    }
                    final Path directory = s.getOutputDirectory();
                    if (directory != null && !directories.add(directory.toAbsolutePath().normalize())) {
                        throw new EmissaryRuntimeException("Output sinks must own separate journal directories: " + directory);
                    }
                    prepared.add(s);
                } else {
                    throw new EmissaryRuntimeException("Misconfigured sink " + clazz + " is not an ISink instance");
                }
            } catch (RuntimeException ex) {
                throw new EmissaryRuntimeException("Unable to create or initialize output sink " + entry, ex);
            }
        }

        for (final ISink sink : prepared) {
            if (sink instanceof AbstractSink) {
                sink.initialize(configG, sink.getName(), null);
            }
            addSink(sink);
        }

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

    @Override
    protected void closeOutputs() {
        for (final ISink sink : this.outputSinks) {
            logger.debug("Shutdown sink {}", sink.getName());
            sink.close();
        }
    }

    /** Run the output sinks over a payload or list. */
    @Override
    @SuppressWarnings("unchecked")
    protected void runOutput(final Object target, final Map<String, Object> params) {

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

    /** Sink by name (case-insensitive), or null. */
    @Nullable
    public ISink getSink(final String name) {
        for (final ISink s : this.outputSinks) {
            if (s.getName().equalsIgnoreCase(name)) {
                return s;
            }
        }
        return null;
    }

    /** Add an initialized sink. */
    public void addSink(final ISink sink) {
        this.outputSinks.add(sink);
    }
}
