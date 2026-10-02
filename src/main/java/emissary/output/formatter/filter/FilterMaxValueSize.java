package emissary.output.formatter.filter;

import emissary.config.Configurator;
import emissary.core.IBaseDataObject;

import jakarta.annotation.Nullable;

/**
 * Denies parameter values longer than the configured {@code METADATA_MAX_VALUE_SIZE}. Views always pass.
 */
public class FilterMaxValueSize extends AbstractItemFilter {

    public static final String MAX_VALUE_SIZE = "METADATA_MAX_VALUE_SIZE";

    private long maxValueSize;

    @Override
    public void configure(@Nullable final Configurator config) {
        super.configure(config);
        if (config == null) {
            return;
        }
        this.maxValueSize = config.findSizeEntry(MAX_VALUE_SIZE, 0);
    }

    @Override
    public boolean test(@Nullable final IBaseDataObject d, final String key, @Nullable final Object value) {
        return value == null || String.valueOf(value).length() <= this.maxValueSize;
    }

    @Override
    public String describe(final boolean verbose) {
        return super.describe(verbose) + (verbose ? " " + MAX_VALUE_SIZE + " = " + this.maxValueSize : "");
    }
}
