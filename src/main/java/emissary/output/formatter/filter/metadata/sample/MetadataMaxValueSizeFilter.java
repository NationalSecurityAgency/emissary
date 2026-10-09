package emissary.output.formatter.filter.metadata.sample;

import emissary.config.Configurator;
import emissary.output.formatter.filter.metadata.MetadataFilter;

/**
 * Metadata filter that rejects a key/value pair whose value length exceeds the configured {@code
 * METADATA_MAX_VALUE_SIZE}.
 */
public class MetadataMaxValueSizeFilter extends MetadataFilter {

    public static final String MAX_VALUE_SIZE = "METADATA_MAX_VALUE_SIZE";

    private long maxValueSize;

    @Override
    public void configure(final Configurator config) {
        super.configure(config);
        if (config == null) {
            return;
        }
        this.maxValueSize = config.findSizeEntry(MAX_VALUE_SIZE, 0);
    }

    @Override
    public boolean allows(final String key) {
        return true;
    }

    @Override
    public boolean allows(final String key, final Object value) {
        return value == null || String.valueOf(value).length() <= this.maxValueSize;
    }
}
