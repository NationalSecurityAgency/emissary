package emissary.output.formatter;

import emissary.config.Configurator;
import emissary.core.IBaseDataObject;

import jakarta.annotation.Nullable;

import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Interface for a drop-off formatter: a component that shapes and writes payloads to output.
 */
public interface IDropOffFormatter {

    /** Successful formatter return value {@value} */
    int STATUS_FAILURE = -1;

    /** Failed writing to formatter's target output stream {@value} */
    int STATUS_OUTPUT_STREAM_FAILURE = -2;

    /** Successful formatter return value {@value} */
    int STATUS_SUCCESS = 1;

    /**
     * Used in params when the formatter should understand that the List of incoming records is presorted, value is {@value}
     */
    String PRE_SORTED = "PRE_SORTED_RECORDS";

    /**
     * Used to param the TLD to the formatter that don't get the whole list
     */
    String TLD_PARAM = "TLD";

    /**
     * Return the name of this formatter
     */
    String getName();

    /**
     * Set the name of this formatter
     */
    void setName(String name);

    /**
     * Initialization phase hook for the formatter using default preferences for the runtime formatter configuration
     *
     * @param configG passed in configuration object, usually DropOffPlaceV2's config
     * @param name the configured name of this formatter or null for the default
     */
    default void initialize(Configurator configG, @Nullable String name) {
        initialize(configG, name, null);
    }

    /**
     * Initialization phase hook for the formatter
     *
     * @param configG passed in configuration object, usually DropOffPlaceV2's config
     * @param name the configured name of this formatter or null for the default
     * @param formatterConfig the configuration for the specific formatter
     */
    void initialize(Configurator configG, @Nullable String name, @Nullable Configurator formatterConfig);

    /**
     * Write one payload
     *
     * @param d the payload
     * @param params map of params
     * @return status value
     */
    int write(IBaseDataObject d, Map<String, Object> params);

    /**
     * Write a set of payloads
     *
     * @param list collection of payloads
     * @param params map of params
     * @return status value
     */
    int write(List<IBaseDataObject> list, Map<String, Object> params);

    /**
     * Write one payload to the provided output stream
     *
     * @param d the payload
     * @param params map of params
     * @param output the output stream
     * @return status value
     */
    int write(IBaseDataObject d, Map<String, Object> params, OutputStream output);

    /**
     * Write a set of payloads to the provided output stream
     *
     * @param list collection of payloads
     * @param params map of params
     * @param output the output stream
     * @return status value
     */
    int write(List<IBaseDataObject> list, Map<String, Object> params, OutputStream output);

    /**
     * Get the output spec as built
     */
    String getOutputSpec();

    /**
     * Get the error spec as built
     */
    String getErrorSpec();

    /**
     * Resolve the output file path for a given payload. The formatter controls both the output directory and filename.
     *
     * @param d the payload to resolve a path for
     * @return the resolved output file path
     */
    Path resolveOutputFile(IBaseDataObject d);

    /**
     * Close the formatter
     */
    void close();
}
