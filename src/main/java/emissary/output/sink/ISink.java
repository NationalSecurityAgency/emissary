package emissary.output.sink;

import emissary.config.Configurator;
import emissary.core.IBaseDataObject;

import jakarta.annotation.Nullable;

import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/** A named output destination. Everything is written unless denied. */
public interface ISink {

    /** Param set when the incoming record list is presorted */
    String PRE_SORTED = "PRE_SORTED_RECORDS";

    /** Param used to pass the TLD when the whole list is not given */
    String TLD_PARAM = "TLD";

    /** Name of this sink. */
    String getName();

    /**
     * Initialize the sink.
     *
     * @param configG the parent configuration
     * @param name the sink name, or null for the default
     * @param sinkConfig the sink-specific configuration, or null
     */
    void initialize(Configurator configG, @Nullable String name, @Nullable Configurator sinkConfig);

    /** Whether the whole payload may be emitted by this sink. */
    boolean accept(IBaseDataObject d);

    /** Whether the list of payloads may be emitted by this sink. */
    boolean accept(List<IBaseDataObject> list);

    /** Write one payload. */
    WriteStatus write(IBaseDataObject d, Map<String, Object> params);

    /** Write a set of payloads. */
    WriteStatus write(List<IBaseDataObject> list, Map<String, Object> params);

    /** Write one payload to the provided output stream. */
    WriteStatus write(IBaseDataObject d, Map<String, Object> params, OutputStream output);

    /** Write a set of payloads to the provided output stream. */
    WriteStatus write(List<IBaseDataObject> list, Map<String, Object> params, OutputStream output);

    /** Get the output spec as built. */
    String getOutputSpec();

    /** Get the error spec as built. */
    String getErrorSpec();

    /** Close the sink. */
    void close();
}
