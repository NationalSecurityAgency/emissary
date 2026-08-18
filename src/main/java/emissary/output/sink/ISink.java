package emissary.output.sink;

import emissary.config.Configurator;
import emissary.core.IBaseDataObject;

import jakarta.annotation.Nullable;

import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * A named output destination that bundles a data formatter with deny-first filters selecting which parts of a payload
 * are emitted. The sink owns whole-payload eligibility; the underlying formatter owns how the data is written.
 * Everything is written unless denied, so an empty configuration emits everything.
 */
public interface ISink {

    /** Successful sink return value {@value} */
    int STATUS_FAILURE = -1;

    /** Failed writing to our output stream {@value} */
    int STATUS_OUTPUT_STREAM_FAILURE = -2;

    /** Successful sink return value {@value} */
    int STATUS_SUCCESS = 1;

    /** Used in params when the List of incoming records is presorted, value is {@value} */
    String PRE_SORTED = "PRE_SORTED_RECORDS";

    /** Used to param the TLD to the sink that don't get the whole list */
    String TLD_PARAM = "TLD";

    /** Return the name of this sink */
    String getName();

    /**
     * Initialization phase hook for the sink.
     *
     * @param configG the parent configuration, usually DropOffPlaceV2's config
     * @param name the configured name of this sink or null for the default
     * @param sinkConfig the configuration for the specific sink, or null
     */
    void initialize(Configurator configG, @Nullable String name, @Nullable Configurator sinkConfig);

    /** Determine if the whole payload is allowed to be emitted by this sink (defaults to allowed). */
    boolean isAllowed(IBaseDataObject d);

    /** Determine if a list of payloads is allowed to be emitted by this sink (defaults to allowed). */
    boolean isAllowed(List<IBaseDataObject> list);

    /** Write one payload. */
    int write(IBaseDataObject d, Map<String, Object> params);

    /** Write a set of payloads. */
    int write(List<IBaseDataObject> list, Map<String, Object> params);

    /** Write one payload to the provided output stream. */
    int write(IBaseDataObject d, Map<String, Object> params, OutputStream output);

    /** Write a set of payloads to the provided output stream. */
    int write(List<IBaseDataObject> list, Map<String, Object> params, OutputStream output);

    /** Get the output spec as built. */
    String getOutputSpec();

    /** Get the error spec as built. */
    String getErrorSpec();

    /** Close the sink. */
    void close();
}
