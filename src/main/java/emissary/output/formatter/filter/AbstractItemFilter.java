package emissary.output.formatter.filter;

import emissary.config.Configurator;
import emissary.core.IBaseDataObject;

import jakarta.annotation.Nullable;

/** Base for an item filter: one allow/deny test per parameter or view. */
public abstract class AbstractItemFilter implements Filter {

    /** Whether this item may be output. */
    public boolean test(@Nullable final IBaseDataObject d, final OutputItem item) {
        return true;
    }

    /** Configure this filter. */
    @Override
    public void configure(@Nullable final Configurator config) {
        // nothing configured by default
    }

    /** Concise label for this filter. */
    public final String describe() {
        return describe(false);
    }

    /** Description of this filter, optionally including its configured state. */
    public String describe(final boolean verbose) {
        return getClass().getSimpleName();
    }
}
