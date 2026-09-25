package emissary.output.formatter.filter;

import emissary.config.Configurator;
import emissary.core.IBaseDataObject;

import jakarta.annotation.Nullable;

/**
 * Denies alternate views larger than the configured {@code CONTENT_MAX_VIEW_SIZE}. Parameters always pass.
 */
public class FilterMaxViewSize extends AbstractItemFilter {

    public static final String MAX_VIEW_SIZE = "CONTENT_MAX_VIEW_SIZE";

    private long maxViewSize;

    @Override
    public void configure(@Nullable final Configurator config) {
        super.configure(config);
        if (config == null) {
            return;
        }
        this.maxViewSize = config.findSizeEntry(MAX_VIEW_SIZE, 0);
    }

    @Override
    public boolean test(@Nullable final IBaseDataObject d, final OutputItem item) {
        if (!item.isView() || d == null) {
            return true;
        }
        byte[] view = d.getAlternateView(item.name());
        return view == null || view.length <= this.maxViewSize;
    }

    @Override
    public String describe(final boolean verbose) {
        return super.describe(verbose) + (verbose ? " " + MAX_VIEW_SIZE + " = " + this.maxViewSize : "");
    }
}
