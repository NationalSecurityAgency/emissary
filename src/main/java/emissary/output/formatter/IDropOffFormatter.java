package emissary.output.formatter;

import emissary.config.Configurator;
import emissary.core.IBaseDataObject;

import jakarta.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * Interface for a drop-off formatter: a pure serializer that shapes payloads into bytes. Eligibility (which payloads)
 * and destination (where bytes land) are the sink's job.
 */
public interface IDropOffFormatter {

    /** Name of this formatter. */
    String getName();

    /** Set the name of this formatter. */
    void setName(String name);

    /**
     * Initialize the formatter.
     *
     * @param configG the parent configuration
     * @param name the formatter name, or null for the default
     */
    default void initialize(Configurator configG, @Nullable String name) {
        initialize(configG, name, null);
    }

    /**
     * Initialize the formatter.
     *
     * @param configG the parent configuration
     * @param name the formatter name, or null for the default
     * @param formatterConfig the formatter-specific configuration (usually the sink's)
     */
    void initialize(Configurator configG, @Nullable String name, @Nullable Configurator formatterConfig);

    /**
     * Stream the payloads to the output.
     *
     * @throws IOException if serialization fails
     */
    void writeTo(OutputStream out, List<IBaseDataObject> list, Map<String, Object> params) throws IOException;

    /**
     * Convert the payloads to a byte array.
     *
     * @throws IOException if serialization fails
     */
    default byte[] convert(List<IBaseDataObject> list, Map<String, Object> params) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(8192);
        writeTo(buf, list, params);
        return buf.toByteArray();
    }

    /** Get the output spec as built. */
    String getOutputSpec();

    /** Get the error spec as built. */
    String getErrorSpec();

    /** Close the formatter. */
    void close();
}
